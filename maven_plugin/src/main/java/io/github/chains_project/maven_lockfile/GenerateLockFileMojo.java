package io.github.chains_project.maven_lockfile;

import io.github.chains_project.maven_lockfile.checksum.AbstractChecksumCalculator;
import io.github.chains_project.maven_lockfile.checksum.FileSystemChecksumCalculator;
import io.github.chains_project.maven_lockfile.checksum.RemoteChecksumCalculator;
import io.github.chains_project.maven_lockfile.data.LockFile;
import io.github.chains_project.maven_lockfile.data.Metadata;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.DefaultProjectBuildingRequest;
import org.apache.maven.project.ProjectBuildingRequest;

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

    /**
     * Generate a lock file for the dependencies of the current project.
     * @throws MojoExecutionException if the lock file could not be written or the generation failed.
     */
    public void execute() throws MojoExecutionException {
        try {
            String osName = System.getProperty("os.name");
            Metadata metadata = new Metadata(osName, mavenVersion, javaVersion);
            AbstractChecksumCalculator checksumCalculator;
            ProjectBuildingRequest buildingRequest =
                    new DefaultProjectBuildingRequest(session.getProjectBuildingRequest());
            if (checksumMode.equals("maven_local")) {
                checksumCalculator =
                        new FileSystemChecksumCalculator(dependencyResolver, buildingRequest, checksumAlgorithm);
            } else if (checksumMode.equals("maven_central")) {
                checksumCalculator = new RemoteChecksumCalculator(checksumAlgorithm);
            } else {
                throw new MojoExecutionException("Invalid checksum mode: " + checksumMode);
            }
            LockFile lockFile = LockFileFacade.generateLockFileFromProject(
                    session,
                    project,
                    dependencyCollectorBuilder,
                    checksumCalculator,
                    Boolean.parseBoolean(includeMavenPlugins),
                    Boolean.parseBoolean(reduced),
                    metadata);

            Path lockFilePath = LockFileFacade.getLockFilePath(project);
            Files.writeString(lockFilePath, JsonUtils.toJson(lockFile));
            getLog().info("Lockfile written to " + lockFilePath);
        } catch (IOException e) {
            getLog().error(e);
        }
    }
}
