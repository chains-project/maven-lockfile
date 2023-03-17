package io.github.chains_project.maven_lockfile;

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

@Mojo(
        name = "checkPackaged",
        defaultPhase = LifecyclePhase.PACKAGE,
        requiresDependencyResolution = ResolutionScope.RUNTIME,
        requiresOnline = true)
public class PackagedFlagMojo extends AbstractMojo {
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
     * Generate a lock file for the dependencies of the current project.
     * @throws MojoExecutionException
     */
    @Override
    public void execute() throws MojoExecutionException {
        return;
        /* try {
            getLog().info("Packaged flag");
            LockFile lockFileFromFile = LockFile.readLockFile(getLockFilePath(project));
            ArrayList<PackagedDependency> packagedDependencies = new ArrayList<>();
            for (var dep : project.getCompileClasspathElements()) {
                String basePath = repoSession.getLocalRepository().getBasedir().getPath();
                // best case the format of the depnendicy ist grouipd with / than artifactid with / than version with /
                // than artifactid-version.jar
                Path pathOfDep = Path.of(dep);
                if (!Files.exists(pathOfDep) || !Files.isRegularFile(pathOfDep)) {
                    getLog().warn("Could not find dependency: " + dep);
                    continue;
                }
                String coordinats = Path.of(basePath).relativize(pathOfDep).toString();
                String[] parts = coordinats.split("/");
                if (parts.length < 4) {
                    getLog().warn("Could not parse dependency: " + dep);
                    continue;
                }
                String artifactId = parts[parts.length - 3];
                String version = parts[parts.length - 2];
                String groupId = String.join(".", Arrays.copyOfRange(parts, 0, parts.length - 3));
                String checksum = Utilities.calculateChecksum(pathOfDep, "SHA-256");
                packagedDependencies.add(new PackagedDependency(
                        GroupId.of(groupId),
                        ArtifactId.of(artifactId),
                        VersionNumber.of(version),
                        "SHA-256",
                        checksum));
            }
            lockFileFromFile.setPackagedDependencies(packagedDependencies);
            Files.writeString(getLockFilePath(project), JsonUtils.toJson(lockFileFromFile));
        } catch (Exception e) {
            getLog().error(e);
            throw new MojoExecutionException("Error", e);
        }
        */
    }
}
