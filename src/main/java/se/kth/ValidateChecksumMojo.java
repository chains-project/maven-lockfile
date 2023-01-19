package se.kth;

import static io.github.chains_project.maven_lockfile.Utilities.generateLockFileFromProject;
import static io.github.chains_project.maven_lockfile.Utilities.getLockFilePath;

import com.google.gson.Gson;
import io.github.chains_project.maven_lockfile.data.LockFile;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.eclipse.aether.RepositorySystemSession;

/**
 * Plugin goal that validates the checksums of the dependencies of a project against a lock file.
 *
 * @goal pin dependencies to hash values
 * @phase compile
 * @author Arvid Siberov
 */
@Mojo(name = "validate", defaultPhase = LifecyclePhase.COMPILE)
public class ValidateChecksumMojo extends AbstractMojo {
    /**
     * The Maven project for which we are generating a lock file.
     * @parameter defaultvalue = ${project}
     * @readonly
     * @required
     */
    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    /**
     * The current repository session, used for accessing the local artifact files, among other things
     */
    @Parameter(defaultValue = "${repositorySystemSession}")
    private RepositorySystemSession repoSession;

    /**
     * Validate the local copies of the dependencies against the project's lock file.
     * @throws MojoExecutionException
     */
    public void execute() throws MojoExecutionException {
        getLog().info("Validating lock file ...");
        try {
            LockFile lockFileFromFile = LockFile.readLockFile(getLockFilePath(project));
            LockFile lockFileFromProject = generateLockFileFromProject(project, repoSession, null);
            if (!lockFileFromFile.isEquivalentTo(lockFileFromProject)) {
                getLog().error("Failed verifying: "
                        + new Gson().toJson(lockFileFromFile.differenceTo(lockFileFromProject)));
                throw new MojoExecutionException("Failed verifying lock file");
                // return;
            }
        } catch (IOException e) {
            throw new MojoExecutionException("Could not read lock file", e);
        } catch (NoSuchAlgorithmException e) {
            throw new MojoExecutionException("No such algorithm", e);
        }

        getLog().info("Lockfile successfully validated.");
    }
}
