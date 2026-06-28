package io.github.chains_project.maven_lockfile;

import static io.github.chains_project.maven_lockfile.LockFileFacade.getLockFilePath;

import io.github.chains_project.maven_lockfile.checksum.AbstractChecksumCalculator;
import io.github.chains_project.maven_lockfile.data.Config;
import io.github.chains_project.maven_lockfile.data.Environment;
import io.github.chains_project.maven_lockfile.data.LockFile;
import io.github.chains_project.maven_lockfile.data.MetaData;
import io.github.chains_project.maven_lockfile.reporting.PluginLogManager;
import io.github.chains_project.maven_lockfile.reporting.ValidationPhases;
import java.io.IOException;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.ResolutionScope;

/**
 * Plugin goal that validates the checksums of the dependencies of a project against a lock file.
 *
 */
@Mojo(
        name = "validate",
        defaultPhase = LifecyclePhase.COMPILE,
        requiresDependencyResolution = ResolutionScope.COMPILE,
        requiresOnline = true)
public class ValidateChecksumMojo extends AbstractLockfileMojo {

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
        try {
            getLog().info("Validating lock file ...");
            LockFile lockFileFromFile = LockFile.readLockFile(getLockFilePath(project, lockfileName));
            Config baseConfig = lockFileFromFile.getConfig();
            if (baseConfig == null) {
                getLog().warn("No config was found in the lock file. Using default config.");
                baseConfig = getConfig();
            }
            Config config = mergeConfigWithCliArgs(baseConfig);
            Environment environment = null;
            if (config.isIncludeEnvironment()) {
                environment = generateMetaInformation();
            }
            MetaData metaData = new MetaData(environment, config);
            AbstractChecksumCalculator checksumCalculator = getChecksumCalculator(config, true);
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

        } catch (IOException e) {
            throw new MojoExecutionException("Could not read lock file", e);
        }
        getLog().info("Lockfile successfully validated.");
    }
}
