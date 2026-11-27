package io.github.chains_project.maven_lockfile;

import static io.github.chains_project.maven_lockfile.LockFileFacade.getLockFilePath;

import io.github.chains_project.maven_lockfile.checksum.AbstractChecksumCalculator;
import io.github.chains_project.maven_lockfile.checksum.RemoteChecksumCalculator;
import io.github.chains_project.maven_lockfile.data.Config;
import io.github.chains_project.maven_lockfile.data.Environment;
import io.github.chains_project.maven_lockfile.data.FeatureConfig;
import io.github.chains_project.maven_lockfile.data.LockFile;
import io.github.chains_project.maven_lockfile.data.LockFileFormatException;
import io.github.chains_project.maven_lockfile.data.MetaData;
import io.github.chains_project.maven_lockfile.data.ValidationPolicy;
import io.github.chains_project.maven_lockfile.reporting.PluginLogManager;
import io.github.chains_project.maven_lockfile.reporting.ValidationPhases;
import java.io.IOException;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.ArtifactUtils;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;

/**
 * Plugin goal that validates a project against its lock file.
 *
 */
@Mojo(
        name = "validate",
        defaultPhase = LifecyclePhase.COMPILE,
        requiresDependencyResolution = ResolutionScope.NONE,
        requiresOnline = true)
public class ValidateMojo extends AbstractLockfileMojo {

    // Each parameter below is null unless set. When set, it replaces for this run the value the lockfile's config
    // records, and a mismatch in that check logs a warning instead of failing the build.

    /** Covers the project's coordinates and its dependency tree. */
    @Parameter(property = "allowDependencyValidationFailure")
    Boolean allowDependencyValidationFailure;

    @Parameter(property = "allowPomValidationFailure")
    Boolean allowPomValidationFailure;

    /** Also covers build extensions and the checksum of the maven-lockfile release that generated the lockfile. */
    @Parameter(property = "allowMavenPluginValidationFailure")
    Boolean allowMavenPluginValidationFailure;

    @Parameter(property = "allowEnvironmentalValidationFailure")
    Boolean allowEnvironmentalValidationFailure;

    @Parameter(property = "allowBomValidationFailure")
    Boolean allowBomValidationFailure;

    @Parameter(property = "allowParentPomValidationFailure")
    Boolean allowParentPomValidationFailure;

    /**
     * Validate the local copies of the dependencies against the project's lock file.
     * @throws MojoExecutionException if the lock file is invalid or could not be read.
     */
    public void execute() throws MojoExecutionException {
        if (skip) {
            getLog().info("Skipping maven-lockfile");
            return;
        }
        PluginLogManager.setLog(getLog());
        rejectParametersOfOtherGoals(mojo, project, session);
        try {
            getLog().info("Validating lock file ...");
            LockFile lockFileFromFile = LockFile.readLockFile(getLockFilePath(project, lockfileName));
            Config baseConfig = lockFileFromFile.getConfig();
            if (baseConfig == null) {
                getLog().warn("No config was found in the lock file. Using default config.");
                baseConfig = new Config();
            }
            Config config = withParameters(baseConfig);
            Deprecations.warnAboutLockfile(this, mojo, lockfileName, lockFileFromFile.getLockfileVersion());
            Environment environment = null;
            if (config.environment().isIncluded()) {
                environment = generateMetaInformation();
            }
            MetaData metaData = new MetaData(environment, config);
            AbstractChecksumCalculator checksumCalculator = getChecksumCalculator(config, true);
            String recordedChecksum = baseConfig.getMavenLockfileChecksum();
            if (recordedChecksum != null && !recordedChecksum.isEmpty()) {
                // Compare with the published checksum of the maven-lockfile version that generated the lockfile,
                // which may not be the one running.
                Artifact generatingArtifact = ArtifactUtils.copyArtifact(
                        mojo.getMojoDescriptor().getPluginDescriptor().getPluginArtifact());
                generatingArtifact.setVersion(baseConfig.getMavenLockfileVersion());
                config.setMavenLockfileChecksum(new RemoteChecksumCalculator(
                                config.getChecksumAlgorithm(),
                                newResolveArtifactProjectBuildingRequest(),
                                newResolvePluginProjectBuildingRequest())
                        .calculatePluginChecksum(generatingArtifact));
            }
            LockFile lockFileFromProject = LockFileFacade.generateLockFileFromProject(
                    session, project, dependencyCollectorBuilder, checksumCalculator, metaData, repositorySystem);
            for (var phase : ValidationPhases.all()) {
                if (!phase.isEnabled(config)) continue;
                var failure = phase.validate(lockFileFromFile, lockFileFromProject, config);
                if (failure.isEmpty()) continue;
                if (phase.isWarn(config)) {
                    getLog().warn(failure.get());
                } else {
                    throw new MojoExecutionException(failure.get());
                }
            }

        } catch (LockFileFormatException e) {
            throw new MojoExecutionException(e.getMessage(), e);
        } catch (IOException e) {
            throw new MojoExecutionException("Could not read lock file", e);
        }
        getLog().info("Lockfile successfully validated.");
    }

    /** {@code base}, with the parameters that were set replacing its values. */
    Config withParameters(Config base) {
        return new Config(
                allow(base.mavenPlugins(), allowMavenPluginValidationFailure),
                allow(base.getOnDependencyValidationFailure(), allowDependencyValidationFailure),
                allow(base.environment(), allowEnvironmentalValidationFailure),
                allow(base.getOnPomValidationFailure(), allowPomValidationFailure),
                base.getReductionState(),
                base.getMavenLockfileVersion(),
                base.getChecksumMode(),
                base.getChecksumAlgorithm(),
                allow(base.boms(), allowBomValidationFailure),
                allow(base.parentPom(), allowParentPomValidationFailure),
                base.getHermeticInclusion());
    }

    private static FeatureConfig allow(FeatureConfig base, Boolean allowFailure) {
        return new FeatureConfig(base.isIncluded(), allow(base.getOnFailure(), allowFailure));
    }

    private static ValidationPolicy allow(ValidationPolicy base, Boolean allowFailure) {
        return allowFailure != null ? ValidationPolicy.of(allowFailure) : base;
    }
}
