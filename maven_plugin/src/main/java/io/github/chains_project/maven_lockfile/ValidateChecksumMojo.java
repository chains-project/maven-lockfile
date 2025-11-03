package io.github.chains_project.maven_lockfile;

import static io.github.chains_project.maven_lockfile.LockFileFacade.getLockFilePath;

import io.github.chains_project.maven_lockfile.checksum.AbstractChecksumCalculator;
import io.github.chains_project.maven_lockfile.data.Config;
import io.github.chains_project.maven_lockfile.data.Environment;
import io.github.chains_project.maven_lockfile.data.LockFile;
import io.github.chains_project.maven_lockfile.data.MetaData;
import io.github.chains_project.maven_lockfile.reporting.LockFileDifference;
import java.io.IOException;
import java.util.Objects;
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
        if (Boolean.parseBoolean(skip)) {
            getLog().info("Skipping maven-lockfile");
            return;
        }
        try {
            getLog().info("Validating lock file ...");
            Environment environment = generateMetaInformation();

            LockFile lockFileFromFile = LockFile.readLockFile(getLockFilePath(project, lockfileName));
            Config config = lockFileFromFile.getConfig() == null ? getConfig() : lockFileFromFile.getConfig();
            if (lockFileFromFile.getConfig() == null) {
                getLog().warn("No config was found in the lock file. Using default config.");
            }
            MetaData metaData = new MetaData(environment, config);
            AbstractChecksumCalculator checksumCalculator = getChecksumCalculator(config, true);
            LockFile lockFileFromProject = LockFileFacade.generateLockFileFromProject(
                    session, project, dependencyCollectorBuilder, checksumCalculator, metaData);
            if (!Objects.equals(lockFileFromFile.getEnvironment(), lockFileFromProject.getEnvironment())) {
                String sb = "Lock file environment does not match project environment.\n"
                        + "Lockfile environment: " + lockFileFromFile.getEnvironment() + "\n"
                        + "Project environment:  " + lockFileFromProject.getEnvironment() + "\n";

                switch (config.getOnEnvironmentalValidationFailure()) {
                    case Warn:
                        getLog().warn(sb);
                        break;
                    case Error:
                        throw new MojoExecutionException("Failed verifying environment. " + sb);
                }
            }
            if (!Objects.equals(lockFileFromFile.getPom(), lockFileFromProject.getPom())) {
                String sb = "Pom checksum mismatch. Differences:" + "\n" + "Your lockfile pom path and checksum:\n"
                        + lockFileFromFile.getPom().getPath()
                        + " " + lockFileFromFile.getPom().getChecksum() + "\n" + "Your project pom path and checksum:\n"
                        + lockFileFromProject.getPom().getPath()
                        + " " + lockFileFromProject.getPom().getChecksum() + "\n";

                switch (config.getOnPomValidationFailure()) {
                    case Warn:
                        getLog().warn(sb);
                        break;
                    case Error:
                        throw new MojoExecutionException("Failed verifying lock file. " + sb);
                }
            }
            if (!lockFileFromFile.equals(lockFileFromProject)) {
                var diff = LockFileDifference.diff(lockFileFromFile, lockFileFromProject);
                String sb = "Lock file validation failed. Differences:" + "\n"
                        + "Your lockfile from file is for:"
                        + lockFileFromFile.getGroupId().getValue()
                        + ":" + lockFileFromFile.getName().getValue() + ":"
                        + lockFileFromFile.getVersion().getValue() + "\n" + "Your generated lockfile is for:"
                        + lockFileFromProject.getGroupId().getValue() + ":"
                        + lockFileFromProject.getName().getValue() + ":"
                        + lockFileFromProject.getVersion().getValue() + "\n" + "Missing dependencies in lock file:\n "
                        + JsonUtils.toJson(diff.getMissingDependenciesInFile())
                        + "\n"
                        + "Missing dependencies in project:\n "
                        + JsonUtils.toJson(diff.getMissingDependenciesInProject())
                        + "\n"
                        + "Missing plugins in lockfile:\n "
                        + JsonUtils.toJson(diff.getMissingPluginsInFile())
                        + "\n"
                        + "Missing plugins in project:\n "
                        + JsonUtils.toJson(diff.getMissingPluginsInProject())
                        + "\n";
                switch (config.getOnValidationFailure()) {
                    case Warn:
                        getLog().warn("Failed verifying lock file. " + sb);
                        break;
                    case Error:
                        throw new MojoExecutionException("Failed verifying lock file. " + sb);
                }
            }
        } catch (IOException e) {
            throw new MojoExecutionException("Could not read lock file", e);
        }
        getLog().info("Lockfile successfully validated.");
    }
}
