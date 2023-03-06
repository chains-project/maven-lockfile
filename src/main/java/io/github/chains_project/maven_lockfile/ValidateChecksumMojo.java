package io.github.chains_project.maven_lockfile;

import static io.github.chains_project.maven_lockfile.Utilities.generateLockFileFromProject;
import static io.github.chains_project.maven_lockfile.Utilities.getLockFilePath;

import io.github.chains_project.maven_lockfile.data.LockFile;
import io.github.chains_project.maven_lockfile.data.LockFileDependency;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;

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

    /**
     * The current repository session, used for accessing the local artifact files, among other things
     */
    @Parameter(defaultValue = "${repositorySystemSession}", readonly = true, required = true)
    private RepositorySystemSession repoSession;
    /**
     * The entry point to Aether, i.e. the component doing all the work.
     */
    @Component
    private RepositorySystem repoSystem;

    /**
     * Validate the local copies of the dependencies against the project's lock file.
     * @throws MojoExecutionException
     */
    public void execute() throws MojoExecutionException {
        getLog().info("Validating lock file ...");
        try {
            LockFile lockFileFromFile = LockFile.readLockFile(getLockFilePath(project));
            LockFile lockFileFromProject = generateLockFileFromProject(project, repoSession, repoSystem);
            if (!lockFileFromFile.isEquivalentTo(lockFileFromProject)) {
                var missing = new ArrayList<LockFileDependency>(lockFileFromProject.getDependencies());
                missing.removeAll(lockFileFromFile.getDependencies());

                getLog().error("Failed verifying: " + JsonUtils.toJson(missing));
                throw new MojoExecutionException("Failed verifying lock file");
            }
        } catch (IOException e) {
            throw new MojoExecutionException("Could not read lock file", e);
        } catch (NoSuchAlgorithmException e) {
            throw new MojoExecutionException("No such algorithm", e);
        }
        getLog().info("Lockfile successfully validated.");
    }
}
