package io.github.chains_project.maven_lockfile;

import static io.github.chains_project.maven_lockfile.LockFileFacade.getLockFilePath;

import io.github.chains_project.maven_lockfile.checksum.AbstractChecksumCalculator;
import io.github.chains_project.maven_lockfile.checksum.FileSystemChecksumCalculator;
import io.github.chains_project.maven_lockfile.checksum.RemoteChecksumCalculator;
import io.github.chains_project.maven_lockfile.data.LockFile;
import io.github.chains_project.maven_lockfile.data.Metadata;
import io.github.chains_project.maven_lockfile.reporting.LockFileDifference;
import java.io.IOException;
import java.util.Objects;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.DefaultProjectBuildingRequest;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuildingRequest;
import org.apache.maven.shared.dependency.graph.DependencyCollectorBuilder;
import org.apache.maven.shared.transfer.dependencies.resolve.DependencyResolver;

/**
 * Plugin goal that validates the checksums of the dependencies of a project against a lock file.
 *
 */
@Mojo(
        name = "validate",
        defaultPhase = LifecyclePhase.COMPILE,
        requiresDependencyResolution = ResolutionScope.COMPILE,
        requiresOnline = true)
public class ValidateChecksumMojo extends AbstractMojo {

    /**
     * The Maven project for which we are generating a lock file.
     */
    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    @Parameter(defaultValue = "${session}", readonly = true, required = true)
    private MavenSession session;

    /**
     * The dependency collector builder to use.
     */
    @Component(hint = "default")
    private DependencyCollectorBuilder dependencyCollectorBuilder;

    @Component
    private DependencyResolver dependencyResolver;

    @Parameter(defaultValue = "false", property = "includeMavenPlugins")
    private String includeMavenPlugins;

    @Parameter(defaultValue = "${maven.version}")
    private String mavenVersion;

    @Parameter(defaultValue = "${java.version}")
    private String javaVersion;

    @Parameter(defaultValue = "sha1", property = "checksumAlgorithm")
    private String checksumAlgorithm;

    @Parameter(defaultValue = "maven_local", property = "checksumMode")
    private String checksumMode;

    /**
     * Validate the local copies of the dependencies against the project's lock file.
     * @throws MojoExecutionException if the lock file is invalid or could not be read.
     */
    public void execute() throws MojoExecutionException {
        getLog().info("Validating lock file ...");
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
            LockFile lockFileFromFile = LockFile.readLockFile(getLockFilePath(project));
            LockFile lockFileFromProject = LockFileFacade.generateLockFileFromProject(
                    session,
                    project,
                    dependencyCollectorBuilder,
                    checksumCalculator,
                    Boolean.parseBoolean(includeMavenPlugins),
                    metadata);
            if (!Objects.equals(lockFileFromFile.getMetadata(), lockFileFromProject.getMetadata())) {
                getLog().warn(
                                "Lock file metadata does not match project metadata. This could be due to a change in the environment.");
            }
            if (!lockFileFromFile.equals(lockFileFromProject)) {
                var diff = LockFileDifference.diff(lockFileFromFile, lockFileFromProject);
                StringBuilder sb = new StringBuilder();
                sb.append("Lock file validation failed. Differences:");
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
