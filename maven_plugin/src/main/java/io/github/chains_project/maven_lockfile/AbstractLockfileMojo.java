package io.github.chains_project.maven_lockfile;

import com.google.common.base.Strings;
import io.github.chains_project.maven_lockfile.checksum.AbstractChecksumCalculator;
import io.github.chains_project.maven_lockfile.checksum.ChecksumModes;
import io.github.chains_project.maven_lockfile.checksum.FileSystemChecksumCalculator;
import io.github.chains_project.maven_lockfile.checksum.RemoteChecksumCalculator;
import io.github.chains_project.maven_lockfile.data.Config;
import io.github.chains_project.maven_lockfile.data.Environment;
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
import org.apache.maven.shared.transfer.dependencies.resolve.DependencyResolver;
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
    protected DependencyResolver dependencyResolver;

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

    @Parameter(defaultValue = "${maven.version}")
    protected String mavenVersion;

    @Parameter(defaultValue = "${java.version}")
    protected String javaVersion;

    @Parameter(property = "checksumAlgorithm")
    protected String checksumAlgorithm;

    @Parameter(defaultValue = "remote", property = "checksumMode")
    protected String checksumMode;

    @Parameter(defaultValue = "false", property = "reduced")
    protected boolean reduced;

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

    protected AbstractChecksumCalculator getChecksumCalculator() throws MojoExecutionException {
        ProjectBuildingRequest artifactBuildingRequest = newResolveArtifactProjectBuildingRequest();
        ProjectBuildingRequest pluginBuildingRequest = newResolvePluginProjectBuildingRequest();

        ChecksumModes checksumModeEnum = ChecksumModes.fromName(checksumMode);

        switch (checksumModeEnum) {
            case LOCAL:
                return new FileSystemChecksumCalculator(
                        dependencyResolver, artifactBuildingRequest, pluginBuildingRequest, checksumAlgorithm);
            case REMOTE:
                return new RemoteChecksumCalculator(checksumAlgorithm, artifactBuildingRequest, pluginBuildingRequest);
            default:
                throw new MojoExecutionException("Invalid checksum mode: " + checksumMode);
        }
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
                        dependencyResolver,
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
                Strings.isNullOrEmpty(checksumMode) ? ChecksumModes.LOCAL : ChecksumModes.fromName(checksumMode);
        // includeMavenPlugins defaults to true when not explicitly set
        Config.MavenPluginsInclusion mavenPluginsInclusion = Boolean.FALSE.equals(includeMavenPlugins)
                ? Config.MavenPluginsInclusion.Exclude
                : Config.MavenPluginsInclusion.Include;
        Config.OnValidationFailure onValidationFailure = Boolean.TRUE.equals(allowValidationFailure)
                ? Config.OnValidationFailure.Warn
                : Config.OnValidationFailure.Error;
        Config.OnPomValidationFailure onPomValidationFailure = Boolean.TRUE.equals(allowPomValidationFailure)
                ? Config.OnPomValidationFailure.Warn
                : Config.OnPomValidationFailure.Error;
        Config.OnMavenPluginValidationFailure onMavenPluginValidationFailure =
                Boolean.TRUE.equals(allowMavenPluginValidationFailure)
                        ? Config.OnMavenPluginValidationFailure.Warn
                        : Config.OnMavenPluginValidationFailure.Error;
        Config.OnEnvironmentalValidationFailure onEnvironmentalValidationFailure =
                Boolean.TRUE.equals(allowEnvironmentalValidationFailure)
                        ? Config.OnEnvironmentalValidationFailure.Warn
                        : Config.OnEnvironmentalValidationFailure.Error;
        // includeEnvironment defaults to true when not explicitly set
        Config.EnvironmentInclusion environmentInclusion = Boolean.FALSE.equals(includeEnvironment)
                ? Config.EnvironmentInclusion.Exclude
                : Config.EnvironmentInclusion.Include;
        Config.ReductionState reductionState =
                reduced ? Config.ReductionState.Reduced : Config.ReductionState.NonReduced;
        // include* flags default to true when not explicitly set
        Config.BomsInclusion bomsInclusion =
                Boolean.FALSE.equals(includeBoms) ? Config.BomsInclusion.Exclude : Config.BomsInclusion.Include;
        Config.OnBomValidationFailure onBomValidationFailure = Boolean.TRUE.equals(allowBomValidationFailure)
                ? Config.OnBomValidationFailure.Warn
                : Config.OnBomValidationFailure.Error;
        Config.ParentPomInclusion parentPomInclusion = Boolean.FALSE.equals(includeParentPom)
                ? Config.ParentPomInclusion.Exclude
                : Config.ParentPomInclusion.Include;
        Config.OnParentPomValidationFailure onParentPomValidationFailure =
                Boolean.TRUE.equals(allowParentPomValidationFailure)
                        ? Config.OnParentPomValidationFailure.Warn
                        : Config.OnParentPomValidationFailure.Error;
        Config.MavenExtensionsInclusion mavenExtensionsInclusion = Boolean.FALSE.equals(includeMavenExtensions)
                ? Config.MavenExtensionsInclusion.Exclude
                : Config.MavenExtensionsInclusion.Include;
        Config.OnMavenExtensionsValidationFailure onMavenExtensionsValidationFailure =
                Boolean.TRUE.equals(allowMavenExtensionsValidationFailure)
                        ? Config.OnMavenExtensionsValidationFailure.Warn
                        : Config.OnMavenExtensionsValidationFailure.Error;

        return new Config(
                mavenPluginsInclusion,
                onValidationFailure,
                onPomValidationFailure,
                onMavenPluginValidationFailure,
                onEnvironmentalValidationFailure,
                environmentInclusion,
                reductionState,
                mojo.getPlugin().getVersion(),
                chosenChecksumMode,
                chosenChecksumAlgorithm,
                bomsInclusion,
                onBomValidationFailure,
                parentPomInclusion,
                onParentPomValidationFailure,
                mavenExtensionsInclusion,
                onMavenExtensionsValidationFailure);
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
        Config.MavenPluginsInclusion pluginsInclusion = includeMavenPlugins != null
                ? (includeMavenPlugins ? Config.MavenPluginsInclusion.Include : Config.MavenPluginsInclusion.Exclude)
                : base.getMavenPluginsInclusion();
        Config.OnValidationFailure onValidationFailure = allowValidationFailure != null
                ? (allowValidationFailure ? Config.OnValidationFailure.Warn : Config.OnValidationFailure.Error)
                : base.getOnValidationFailure();
        Config.OnPomValidationFailure onPomValidationFailure = allowPomValidationFailure != null
                ? (allowPomValidationFailure ? Config.OnPomValidationFailure.Warn : Config.OnPomValidationFailure.Error)
                : base.getOnPomValidationFailure();
        Config.OnMavenPluginValidationFailure onMavenPluginValidationFailure = allowMavenPluginValidationFailure != null
                ? (allowMavenPluginValidationFailure
                        ? Config.OnMavenPluginValidationFailure.Warn
                        : Config.OnMavenPluginValidationFailure.Error)
                : base.getOnMavenPluginValidationFailure();
        Config.OnEnvironmentalValidationFailure onEnvFailure = allowEnvironmentalValidationFailure != null
                ? (allowEnvironmentalValidationFailure
                        ? Config.OnEnvironmentalValidationFailure.Warn
                        : Config.OnEnvironmentalValidationFailure.Error)
                : base.getOnEnvironmentalValidationFailure();
        Config.EnvironmentInclusion environmentInclusion = includeEnvironment != null
                ? (includeEnvironment ? Config.EnvironmentInclusion.Include : Config.EnvironmentInclusion.Exclude)
                : base.getEnvironmentInclusion();
        Config.BomsInclusion bomsInclusion = includeBoms != null
                ? (includeBoms ? Config.BomsInclusion.Include : Config.BomsInclusion.Exclude)
                : base.getBomsInclusion();
        Config.OnBomValidationFailure onBomValidationFailure = allowBomValidationFailure != null
                ? (allowBomValidationFailure ? Config.OnBomValidationFailure.Warn : Config.OnBomValidationFailure.Error)
                : base.getOnBomValidationFailure();
        Config.ParentPomInclusion parentPomInclusion = includeParentPom != null
                ? (includeParentPom ? Config.ParentPomInclusion.Include : Config.ParentPomInclusion.Exclude)
                : base.getParentPomInclusion();
        Config.OnParentPomValidationFailure onParentPomValidationFailure = allowParentPomValidationFailure != null
                ? (allowParentPomValidationFailure
                        ? Config.OnParentPomValidationFailure.Warn
                        : Config.OnParentPomValidationFailure.Error)
                : base.getOnParentPomValidationFailure();
        Config.MavenExtensionsInclusion mavenExtensionsInclusion = includeMavenExtensions != null
                ? (includeMavenExtensions
                        ? Config.MavenExtensionsInclusion.Include
                        : Config.MavenExtensionsInclusion.Exclude)
                : base.getMavenExtensionsInclusion();
        Config.OnMavenExtensionsValidationFailure onMavenExtensionsValidationFailure =
                allowMavenExtensionsValidationFailure != null
                        ? (allowMavenExtensionsValidationFailure
                                ? Config.OnMavenExtensionsValidationFailure.Warn
                                : Config.OnMavenExtensionsValidationFailure.Error)
                        : base.getOnMavenExtensionsValidationFailure();
        return new Config(
                pluginsInclusion,
                onValidationFailure,
                onPomValidationFailure,
                onMavenPluginValidationFailure,
                onEnvFailure,
                environmentInclusion,
                base.getReductionState(),
                base.getMavenLockfileVersion(),
                base.getChecksumMode(),
                base.getChecksumAlgorithm(),
                bomsInclusion,
                onBomValidationFailure,
                parentPomInclusion,
                onParentPomValidationFailure,
                mavenExtensionsInclusion,
                onMavenExtensionsValidationFailure);
    }
}
