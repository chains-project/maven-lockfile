package io.github.chains_project.maven_lockfile;

import static se.kth.Utilities.generateLockFileFromProject;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.NoSuchAlgorithmException;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.eclipse.aether.RepositorySystemSession;
import se.kth.LockFile;
import se.kth.Utilities;

/**
 * This plugin generates a lock file for a project. The lock file contains the checksums of all
 * dependencies of the project. This can be used to validate that the dependencies of a project
 * have not changed.
 *
 * @description Generate a lock file for the dependencies of the current project.
 */
@Mojo(
        name = "generate",
        defaultPhase = LifecyclePhase.GENERATE_RESOURCES,
        requiresDependencyResolution = ResolutionScope.COMPILE,
        requiresProject = true,
        requiresOnline = true)
public class GenerateLockFileMojo extends AbstractMojo {
    /**
     * The Maven project for which we are generating a lock file.
     */
    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    /**
     * The current repository session, used for accessing the local artifact files, among other things
     */
    @Parameter(defaultValue = "${repositorySystemSession}", readonly = true, required = true)
    private RepositorySystemSession repoSession;

    /**
     * Generate a lock file for the dependencies of the current project.
     * @throws MojoExecutionException
     */
    public void execute() throws MojoExecutionException {
        try {
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            LockFile lockFile = generateLockFileFromProject(project, repoSession);
            Path lockFilePath = Utilities.getLockFilePath(project);
            Files.writeString(lockFilePath, gson.toJson(lockFile));
            getLog().info("Lockfile written to " + lockFilePath);
        } catch (IOException | NoSuchAlgorithmException e) {
            getLog().error(e);
        }
    }
}
