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

    @Parameter(property = "includeMavenPlugins", defaultValue = "false")
    protected boolean includeMavenPlugins;

    @Parameter(property = "allowValidationFailure", defaultValue = "false")
    protected boolean allowValidationFailure;

    @Parameter(property = "allowPomValidationFailure", defaultValue = "false")
    protected boolean allowPomValidationFailure;

    @Parameter(property = "allowEnvironmentalValidationFailure", defaultValue = "false")
    protected boolean allowEnvironmentalValidationFailure;

    @Parameter(property = "includeEnvironment", defaultValue = "true")
    protected boolean includeEnvironment;

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
        Config.MavenPluginsInclusion mavenPluginsInclusion =
                includeMavenPlugins ? Config.MavenPluginsInclusion.Include : Config.MavenPluginsInclusion.Exclude;
        Config.OnValidationFailure onValidationFailure =
                allowValidationFailure ? Config.OnValidationFailure.Warn : Config.OnValidationFailure.Error;
        Config.OnPomValidationFailure onPomValidationFailure =
                allowPomValidationFailure ? Config.OnPomValidationFailure.Warn : Config.OnPomValidationFailure.Error;
        Config.OnEnvironmentalValidationFailure onEnvironmentalValidationFailure = allowEnvironmentalValidationFailure
                ? Config.OnEnvironmentalValidationFailure.Warn
                : Config.OnEnvironmentalValidationFailure.Error;
        Config.EnvironmentInclusion environmentInclusion =
                includeEnvironment ? Config.EnvironmentInclusion.Include : Config.EnvironmentInclusion.Exclude;
        Config.ReductionState reductionState =
                reduced ? Config.ReductionState.Reduced : Config.ReductionState.NonReduced;

        return new Config(
                mavenPluginsInclusion,
                onValidationFailure,
                onPomValidationFailure,
                onEnvironmentalValidationFailure,
                environmentInclusion,
                reductionState,
                mojo.getPlugin().getVersion(),
                chosenChecksumMode,
                chosenChecksumAlgorithm);
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
     * only the fields for which a boolean flag was explicitly set to {@code true}. When a flag is
     * {@code false} (the default), the stored value is preserved so that existing lockfile configs are
     * not silently tightened by plugin defaults.
     */
    protected Config mergeConfigWithCliArgs(Config base) {
        Config.MavenPluginsInclusion pluginsInclusion =
                includeMavenPlugins ? Config.MavenPluginsInclusion.Include : base.getMavenPluginsInclusion();
        Config.OnValidationFailure onValidationFailure =
                allowValidationFailure ? Config.OnValidationFailure.Warn : base.getOnValidationFailure();
        Config.OnPomValidationFailure onPomValidationFailure =
                allowPomValidationFailure ? Config.OnPomValidationFailure.Warn : base.getOnPomValidationFailure();
        Config.OnEnvironmentalValidationFailure onEnvFailure = allowEnvironmentalValidationFailure
                ? Config.OnEnvironmentalValidationFailure.Warn
                : base.getOnEnvironmentalValidationFailure();
        Config.EnvironmentInclusion environmentInclusion =
                includeEnvironment ? Config.EnvironmentInclusion.Include : base.getEnvironmentInclusion();
        return new Config(
                pluginsInclusion,
                onValidationFailure,
                onPomValidationFailure,
                onEnvFailure,
                environmentInclusion,
                base.getReductionState(),
                base.getMavenLockfileVersion(),
                base.getChecksumMode(),
                base.getChecksumAlgorithm());
    }
}
