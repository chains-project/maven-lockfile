package io.github.chains_project.maven_lockfile;

import com.google.common.base.Strings;
import io.github.chains_project.maven_lockfile.checksum.AbstractChecksumCalculator;
import io.github.chains_project.maven_lockfile.checksum.ChecksumModes;
import io.github.chains_project.maven_lockfile.checksum.FileSystemChecksumCalculator;
import io.github.chains_project.maven_lockfile.checksum.RemoteChecksumCalculator;
import io.github.chains_project.maven_lockfile.data.Config;
import io.github.chains_project.maven_lockfile.data.Environment;
import io.github.chains_project.maven_lockfile.data.FeatureConfig;
import io.github.chains_project.maven_lockfile.data.ValidationPolicy;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.DefaultProjectBuildingRequest;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuildingRequest;
import org.apache.maven.shared.dependency.graph.DependencyCollectorBuilder;
import org.eclipse.aether.RepositorySystem;

public abstract class AbstractLockfileMojo extends AbstractMojo {

    /**
     * The Maven project for which we are generating a lock file.
     */
    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    protected MavenProject project;

    @Parameter(defaultValue = "${session}", readonly = true, required = true)
    protected MavenSession session;

    /**
     * The dependency collector builder to use.
     */
    @Component(hint = "default")
    protected DependencyCollectorBuilder dependencyCollectorBuilder;

    @Component
    protected RepositorySystem repositorySystem;

    @Parameter(property = "includeMavenPlugins")
    protected Boolean includeMavenPlugins;

    @Parameter(property = "allowValidationFailure")
    protected Boolean allowValidationFailure;

    @Parameter(property = "allowPomValidationFailure")
    protected Boolean allowPomValidationFailure;

    @Parameter(property = "allowMavenPluginValidationFailure")
    protected Boolean allowMavenPluginValidationFailure;

    // For CI with GitHub Actions, set to true; runner environments (Maven version, OS) can change between updates and
    // are not fully controllable.
    @Parameter(property = "allowEnvironmentalValidationFailure")
    protected Boolean allowEnvironmentalValidationFailure;

    @Parameter(property = "includeEnvironment")
    protected Boolean includeEnvironment;

    @Parameter(property = "includeBoms")
    protected Boolean includeBoms;

    @Parameter(property = "allowBomValidationFailure")
    protected Boolean allowBomValidationFailure;

    @Parameter(property = "includeParentPom")
    protected Boolean includeParentPom;

    @Parameter(property = "allowParentPomValidationFailure")
    protected Boolean allowParentPomValidationFailure;

    @Parameter(property = "includeMavenExtensions")
    protected Boolean includeMavenExtensions;

    @Parameter(property = "allowMavenExtensionsValidationFailure")
    protected Boolean allowMavenExtensionsValidationFailure;

    /**
     * <b>Experimental.</b> Merge artifacts dynamically resolved during a real build into the lockfile.
     * Behaviour and lockfile output may change or be removed in any release without notice.
     */
    @Parameter(property = "hermetic")
    protected Boolean hermetic;

    @Parameter(defaultValue = "${maven.version}")
    protected String mavenVersion;

    @Parameter(defaultValue = "${java.version}")
    protected String javaVersion;

    @Parameter(property = "checksumAlgorithm")
    protected String checksumAlgorithm;

    // No defaultValue: null means "not set", so a regenerated lockfile keeps the stored checksumMode (see
    // mergeConfigWithCliArgs). The default for new lockfiles is applied in getConfig().
    @Parameter(property = "checksumMode")
    protected String checksumMode;

    // No defaultValue: null means "not set", so validate can warn when it is passed (see Deprecations).
    @Parameter(property = "reduced")
    protected Boolean reduced;

    @Parameter(defaultValue = "false", property = "skip")
    protected boolean skip;

    @Parameter(defaultValue = "lockfile.json", property = "lockfileName")
    protected String lockfileName;

    @Parameter(defaultValue = "${mojoExecution}", readonly = true)
    protected MojoExecution mojo;

    protected Environment generateMetaInformation() {
        String osName = System.getProperty("os.name");
        return new Environment(osName, mavenVersion, javaVersion);
    }

    protected AbstractChecksumCalculator getChecksumCalculator(Config config) throws MojoExecutionException {
        return getChecksumCalculator(config, false);
    }

    protected AbstractChecksumCalculator getChecksumCalculator(Config config, boolean forceLocalChecksumMode)
            throws MojoExecutionException {
        ProjectBuildingRequest artifactBuildingRequest = newResolveArtifactProjectBuildingRequest();
        ProjectBuildingRequest pluginBuildingRequest = newResolvePluginProjectBuildingRequest();

        ChecksumModes checksumModeEnum = config.getChecksumMode();
        if (forceLocalChecksumMode) {
            checksumModeEnum = ChecksumModes.LOCAL;
        }

        switch (checksumModeEnum) {
            case LOCAL:
                return new FileSystemChecksumCalculator(
                        repositorySystem,
                        artifactBuildingRequest,
                        pluginBuildingRequest,
                        config.getChecksumAlgorithm());
            case REMOTE:
                return new RemoteChecksumCalculator(
                        config.getChecksumAlgorithm(), artifactBuildingRequest, pluginBuildingRequest);
            default:
                throw new MojoExecutionException("Invalid checksum mode: " + checksumModeEnum);
        }
    }

    protected Config getConfig() {
        String chosenChecksumAlgorithm = Strings.isNullOrEmpty(checksumAlgorithm) ? "SHA-256" : checksumAlgorithm;
        ChecksumModes chosenChecksumMode =
                Strings.isNullOrEmpty(checksumMode) ? ChecksumModes.REMOTE : ChecksumModes.fromName(checksumMode);
        Config.OnValidationFailure onValidationFailure = Boolean.TRUE.equals(allowValidationFailure)
                ? Config.OnValidationFailure.Warn
                : Config.OnValidationFailure.Error;
        Config.ReductionState reductionState =
                Boolean.TRUE.equals(reduced) ? Config.ReductionState.Reduced : Config.ReductionState.NonReduced;
        // hermetic defaults to false (opt-in) when not explicitly set
        Config.HermeticInclusion hermeticInclusion =
                Boolean.TRUE.equals(hermetic) ? Config.HermeticInclusion.Include : Config.HermeticInclusion.Exclude;
        // New lockfiles include every feature and fail on its mismatches, unless a flag says otherwise.
        FeatureConfig defaults = new FeatureConfig(true, ValidationPolicy.Error);
        return new Config(
                merge(defaults, includeMavenPlugins, allowMavenPluginValidationFailure),
                onValidationFailure,
                merge(ValidationPolicy.Error, allowPomValidationFailure),
                merge(defaults, includeEnvironment, allowEnvironmentalValidationFailure),
                reductionState,
                mojo.getPlugin().getVersion(),
                chosenChecksumMode,
                chosenChecksumAlgorithm,
                merge(defaults, includeBoms, allowBomValidationFailure),
                merge(defaults, includeParentPom, allowParentPomValidationFailure),
                merge(defaults, includeMavenExtensions, allowMavenExtensionsValidationFailure),
                hermeticInclusion);
    }

    protected ProjectBuildingRequest newResolveArtifactProjectBuildingRequest() throws MojoExecutionException {
        ProjectBuildingRequest buildingRequest = new DefaultProjectBuildingRequest(session.getProjectBuildingRequest());
        buildingRequest.setRemoteRepositories(project.getRemoteArtifactRepositories());
        return buildingRequest;
    }

    protected ProjectBuildingRequest newResolvePluginProjectBuildingRequest() throws MojoExecutionException {
        ProjectBuildingRequest buildingRequest = new DefaultProjectBuildingRequest(session.getProjectBuildingRequest());
        buildingRequest.setRemoteRepositories(project.getPluginArtifactRepositories());
        return buildingRequest;
    }

    /**
     * Returns a Config that starts from {@code base} (typically the stored lockfile config) and overrides
     * only the fields that were explicitly set via CLI or pom.xml configuration (non-null). When a flag
     * is null (not explicitly set), the stored value from the lockfile config is preserved.
     */
    protected Config mergeConfigWithCliArgs(Config base) {
        ChecksumModes chosenChecksumMode =
                Strings.isNullOrEmpty(checksumMode) ? base.getChecksumMode() : ChecksumModes.fromName(checksumMode);
        String chosenChecksumAlgorithm =
                Strings.isNullOrEmpty(checksumAlgorithm) ? base.getChecksumAlgorithm() : checksumAlgorithm;
        Config.OnValidationFailure onValidationFailure = allowValidationFailure != null
                ? (allowValidationFailure ? Config.OnValidationFailure.Warn : Config.OnValidationFailure.Error)
                : base.getOnValidationFailure();
        Config.HermeticInclusion hermeticInclusion = hermetic != null
                ? (hermetic ? Config.HermeticInclusion.Include : Config.HermeticInclusion.Exclude)
                : base.getHermeticInclusion();
        return new Config(
                merge(base.mavenPlugins(), includeMavenPlugins, allowMavenPluginValidationFailure),
                onValidationFailure,
                merge(base.getOnPomValidationFailure(), allowPomValidationFailure),
                merge(base.environment(), includeEnvironment, allowEnvironmentalValidationFailure),
                base.getReductionState(),
                base.getMavenLockfileVersion(),
                chosenChecksumMode,
                chosenChecksumAlgorithm,
                merge(base.boms(), includeBoms, allowBomValidationFailure),
                merge(base.parentPom(), includeParentPom, allowParentPomValidationFailure),
                merge(base.mavenExtensions(), includeMavenExtensions, allowMavenExtensionsValidationFailure),
                hermeticInclusion);
    }

    /** {@code base}, with the flags that were set replacing its values. */
    private static FeatureConfig merge(FeatureConfig base, Boolean include, Boolean allowFailure) {
        return new FeatureConfig(
                include != null ? include : base.isIncluded(), merge(base.getOnFailure(), allowFailure));
    }

    private static ValidationPolicy merge(ValidationPolicy base, Boolean allowFailure) {
        return allowFailure != null ? ValidationPolicy.of(allowFailure) : base;
    }
}
