package io.github.chains_project.maven_lockfile;

import io.github.chains_project.maven_lockfile.checksum.AbstractChecksumCalculator;
import io.github.chains_project.maven_lockfile.checksum.FileSystemChecksumCalculator;
import io.github.chains_project.maven_lockfile.checksum.RemoteChecksumCalculator;
import io.github.chains_project.maven_lockfile.data.Config;
import io.github.chains_project.maven_lockfile.data.Metadata;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.DefaultProjectBuildingRequest;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuildingRequest;
import org.apache.maven.shared.dependency.graph.DependencyCollectorBuilder;
import org.apache.maven.shared.transfer.dependencies.resolve.DependencyResolver;

public abstract class AbstractLockfileMojo extends AbstractMojo {

    /**
     * The Maven project for which we are generating a lock file.
     */
    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    protected MavenProject project;

    @Parameter(defaultValue = "${session}", readonly = true, required = true)
    protected MavenSession session;

    /**
     * The dependency collector builder to use.
     */
    @Component(hint = "default")
    protected DependencyCollectorBuilder dependencyCollectorBuilder;

    @Component
    protected DependencyResolver dependencyResolver;

    @Parameter(defaultValue = "false", property = "includeMavenPlugins")
    protected String includeMavenPlugins;

    @Parameter(defaultValue = "${maven.version}")
    protected String mavenVersion;

    @Parameter(defaultValue = "${java.version}")
    protected String javaVersion;

    @Parameter(defaultValue = "sha1", property = "checksumAlgorithm")
    protected String checksumAlgorithm;

    @Parameter(defaultValue = "maven_local", property = "checksumMode")
    protected String checksumMode;

    @Parameter(defaultValue = "false", property = "reduced")
    protected String reduced;

    @Parameter(defaultValue = "false", property = "skip")
    protected String skip;

    @Parameter(defaultValue = "${mojoExecution}", readonly = true)
    protected MojoExecution mojo;

    protected Metadata generateMetaInformation() {
        String osName = System.getProperty("os.name");
        return new Metadata(osName, mavenVersion, javaVersion);
    }

    protected AbstractChecksumCalculator getChecksumCalculator() throws MojoExecutionException {
        ProjectBuildingRequest buildingRequest = new DefaultProjectBuildingRequest(session.getProjectBuildingRequest());
        if (checksumMode.equals("maven_local")) {
            return new FileSystemChecksumCalculator(dependencyResolver, buildingRequest, checksumAlgorithm);
        } else if (checksumMode.equals("maven_central")) {
            return new RemoteChecksumCalculator(checksumAlgorithm);
        } else {
            throw new MojoExecutionException("Invalid checksum mode: " + checksumMode);
        }
    }

    protected AbstractChecksumCalculator getChecksumCalculator(Config config) throws MojoExecutionException {
        ProjectBuildingRequest buildingRequest = new DefaultProjectBuildingRequest(session.getProjectBuildingRequest());
        switch (config.getChecksumMode()) {
            case "maven_local":
                return new FileSystemChecksumCalculator(
                        dependencyResolver, buildingRequest, config.getChecksumAlgorithm());
            case "maven_central":
                return new RemoteChecksumCalculator(config.getChecksumAlgorithm());
            default:
                throw new MojoExecutionException("Invalid checksum mode: " + config.getChecksumMode());
        }
    }

    protected Config getConfig() {
        return new Config(
                Boolean.parseBoolean(includeMavenPlugins),
                Boolean.parseBoolean(reduced),
                mojo.getPlugin().getVersion(),
                checksumMode,
                checksumAlgorithm);
    }
}
