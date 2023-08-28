package io.github.chains_project.maven_lockfile;

import static io.github.chains_project.maven_lockfile.LockFileFacade.getLockFilePath;

import io.github.chains_project.maven_lockfile.checksum.AbstractChecksumCalculator;
import io.github.chains_project.maven_lockfile.data.LockFile;
import io.github.chains_project.maven_lockfile.data.Metadata;
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
        getLog().info("Validating lock file ...");
        if (Boolean.parseBoolean(skip)) {
            getLog().info("Skipping maven-lockfile");
        }
        try {
            Metadata metadata = generateMetaInformation();
            AbstractChecksumCalculator checksumCalculator = getChecksumCalculator();
            LockFile lockFileFromFile = LockFile.readLockFile(getLockFilePath(project));
            LockFile lockFileFromProject = LockFileFacade.generateLockFileFromProject(
                    session,
                    project,
                    dependencyCollectorBuilder,
                    checksumCalculator,
                    Boolean.parseBoolean(includeMavenPlugins),
                    Boolean.parseBoolean(reduced),
                    metadata);
            if (!Objects.equals(lockFileFromFile.getMetadata(), lockFileFromProject.getMetadata())) {
                getLog().warn(
                                "Lock file metadata does not match project metadata. This could be due to a change in the environment.");
            }
            if (!lockFileFromFile.equals(lockFileFromProject)) {
                var diff = LockFileDifference.diff(lockFileFromFile, lockFileFromProject);
                String sb = "Lock file validation failed. Differences:" +
                        "\n" +
                        "Your lockfile from file is for:"
                        + lockFileFromFile.getGroupId().getValue() + ":"
                        + lockFileFromFile.getName().getValue() + ":"
                        + lockFileFromFile.getVersion().getValue() + "\n" +
                        "Your generated lockfile is for:"
                        + lockFileFromProject.getGroupId().getValue() + ":"
                        + lockFileFromProject.getName().getValue() + ":"
                        + lockFileFromProject.getVersion().getValue() + "\n" +
                        "Missing dependencies in lock file:\n " +
                        JsonUtils.toJson(diff.getMissingDependenciesInFile()) +
                        "\n" +
                        "Missing dependencies in project:\n " +
                        JsonUtils.toJson(diff.getMissingDependenciesInProject()) +
                        "\n" +
                        "Missing plugins in lockfile:\n " +
                        JsonUtils.toJson(diff.getMissingPluginsInFile()) +
                        "\n" +
                        "Missing plugins in project:\n " +
                        JsonUtils.toJson(diff.getMissingPluginsInProject()) +
                        "\n";
                throw new MojoExecutionException("Failed verifying lock file" + sb);
            }
        } catch (IOException e) {
            throw new MojoExecutionException("Could not read lock file", e);
        }
        getLog().info("Lockfile successfully validated.");
    }
}
