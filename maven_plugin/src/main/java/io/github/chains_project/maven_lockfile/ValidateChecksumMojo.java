package io.github.chains_project.maven_lockfile;

import static io.github.chains_project.maven_lockfile.LockFileFacade.getLockFilePath;

import io.github.chains_project.maven_lockfile.checksum.AbstractChecksumCalculator;
import io.github.chains_project.maven_lockfile.data.Config;
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
            LockFile lockFileFromFile = LockFile.readLockFile(getLockFilePath(project));
            Config config = lockFileFromFile.getConfig() == null ? getConfig() : lockFileFromFile.getConfig();
            getLog().warn("No config was found in the lock file. Using default config.");
            AbstractChecksumCalculator checksumCalculator = getChecksumCalculator(config);
            LockFile lockFileFromProject = LockFileFacade.generateLockFileFromProject(
                    session, project, dependencyCollectorBuilder, checksumCalculator, config, metadata);
            if (!Objects.equals(lockFileFromFile.getMetadata(), lockFileFromProject.getMetadata())) {
                getLog().warn(
                                "Lock file metadata does not match project metadata. This could be due to a change in the environment.");
            }
            if (!lockFileFromFile.equals(lockFileFromProject)) {
                var diff = LockFileDifference.diff(lockFileFromFile, lockFileFromProject);
                StringBuilder sb = new StringBuilder();
                sb.append("Lock file validation failed. Differences:");
                sb.append("\n");
                sb.append("Your lockfile from file is for:"
                        + lockFileFromFile.getGroupId().getValue() + ":"
                        + lockFileFromFile.getName().getValue() + ":"
                        + lockFileFromFile.getVersion().getValue() + "\n");
                sb.append("Your generated lockfile is for:"
                        + lockFileFromProject.getGroupId().getValue() + ":"
                        + lockFileFromProject.getName().getValue() + ":"
                        + lockFileFromProject.getVersion().getValue() + "\n");
                sb.append("Missing dependencies in lock file:\n ");
                sb.append(JsonUtils.toJson(diff.getMissingDependenciesInFile()));
                sb.append("\n");
                sb.append("Missing dependencies in project:\n ");
                sb.append(JsonUtils.toJson(diff.getMissingDependenciesInProject()));
                sb.append("\n");
                sb.append("Missing plugins in lockfile:\n ");
                sb.append(JsonUtils.toJson(diff.getMissingPluginsInFile()));
                sb.append("\n");
                sb.append("Missing plugins in project:\n ");
                sb.append(JsonUtils.toJson(diff.getMissingPluginsInProject()));
                sb.append("\n");
                throw new MojoExecutionException("Failed verifying lock file" + sb);
            }
        } catch (IOException e) {
            throw new MojoExecutionException("Could not read lock file", e);
        }
        getLog().info("Lockfile successfully validated.");
    }
}
