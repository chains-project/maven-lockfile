package io.github.chains_project.maven_lockfile;

import static io.github.chains_project.maven_lockfile.LockFileFacade.getLockFilePath;

import io.github.chains_project.maven_lockfile.checksum.AbstractChecksumCalculator;
import io.github.chains_project.maven_lockfile.data.Config;
import io.github.chains_project.maven_lockfile.data.Environment;
import io.github.chains_project.maven_lockfile.data.LockFile;
import io.github.chains_project.maven_lockfile.data.MetaData;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;

/**
 * This plugin generates a lock file for a project. The lock file contains the checksums of all
 * dependencies of the project. This can be used to validate that the dependencies of a project
 * have not changed.
 *
 */
@Mojo(
        name = "generate",
        defaultPhase = LifecyclePhase.GENERATE_RESOURCES,
        requiresDependencyResolution = ResolutionScope.COMPILE,
        requiresOnline = true)
public class GenerateLockFileMojo extends AbstractLockfileMojo {

    @Parameter(defaultValue = "true", property = "getConfigFromFile")
    String getConfigFromFile;

    /**
     * Generate a lock file for the dependencies of the current project.
     * @throws MojoExecutionException if the lock file could not be written or the generation failed.
     */
    public void execute() throws MojoExecutionException {
        if (Boolean.parseBoolean(skip)) {
            getLog().info("Skipping maven-lockfile");
            return;
        }
        try {
            LockFile lockFileFromFile = Files.exists(getLockFilePath(project, lockfileName))
                    ? LockFile.readLockFile(getLockFilePath(project, lockfileName))
                    : null;
            Config config = Boolean.parseBoolean(getConfigFromFile) ? getConfig(lockFileFromFile) : getConfig();
            Environment environment = null;
            if (config.isIncludeEnvironment()) {
                environment = generateMetaInformation();
            }
            MetaData metaData = new MetaData(environment, config);

            if (lockFileFromFile == null) {
                getLog().info("No lockfile found. Generating new lockfile.");
            }
            AbstractChecksumCalculator checksumCalculator = getChecksumCalculator(config);
            LockFile lockFile = LockFileFacade.generateLockFileFromProject(
                    session, project, dependencyCollectorBuilder, checksumCalculator, metaData);

            Path lockFilePath = LockFileFacade.getLockFilePath(project, lockfileName);
            Files.writeString(lockFilePath, JsonUtils.toJson(lockFile));
            getLog().info("Lockfile written to " + lockFilePath);
        } catch (IOException e) {
            getLog().error(e);
        }
    }

    private Config getConfig(LockFile lockFileFromFile) {
        if (lockFileFromFile == null || lockFileFromFile.getConfig() == null) {
            return getConfig();
        }

        Config config = lockFileFromFile.getConfig();

        return new Config(
                config.getMavenPluginsInclusion(),
                config.getOnValidationFailure(),
                config.getOnPomValidationFailure(),
                config.getOnEnvironmentalValidationFailure(),
                config.getEnvironmentInclusion(),
                config.getReductionState(),
                mojo.getPlugin().getVersion(),
                config.getChecksumMode(),
                config.getChecksumAlgorithm());
    }
}
