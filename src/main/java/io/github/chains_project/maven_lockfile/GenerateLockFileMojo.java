package io.github.chains_project.maven_lockfile;

import static io.github.chains_project.maven_lockfile.LockFileFacade.getLockFilePath;

import io.github.chains_project.maven_lockfile.checksum.AbstractChecksumCalculator;
import io.github.chains_project.maven_lockfile.data.Config;
import io.github.chains_project.maven_lockfile.data.Environment;
import io.github.chains_project.maven_lockfile.data.LockFile;
import io.github.chains_project.maven_lockfile.data.MetaData;
import io.github.chains_project.maven_lockfile.reporting.PluginLogManager;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;

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

    @Parameter(defaultValue = "true", property = "getConfigFromFile")
    boolean getConfigFromFile;

    /**
     * Generate a lock file for the dependencies of the current project.
     * @throws MojoExecutionException if the lock file could not be written or the generation failed.
     */
    public void execute() throws MojoExecutionException {
        if (skip) {
            getLog().info("Skipping maven-lockfile");
            return;
        }
        PluginLogManager.setLog(getLog());
        try {
            LockFile lockFileFromFile = Files.exists(getLockFilePath(project, lockfileName))
                    ? LockFile.readLockFile(getLockFilePath(project, lockfileName))
                    : null;
            Config config = getConfigFromFile ? getConfig(lockFileFromFile) : getConfig();
            if (config.isHermetic()) {
                ensureHermeticExtensionRegistered();
            }
            Environment environment = null;
            if (config.isIncludeEnvironment()) {
                environment = generateMetaInformation();
            }
            MetaData metaData = new MetaData(environment, config);

            if (lockFileFromFile == null) {
                getLog().info("No lockfile found. Generating new lockfile.");
            }
            AbstractChecksumCalculator checksumCalculator = getChecksumCalculator(config);
            LockFile lockFile = LockFileFacade.generateLockFileFromProject(
                    session, project, dependencyCollectorBuilder, checksumCalculator, metaData, repositorySystem);

            Path lockFilePath = LockFileFacade.getLockFilePath(project, lockfileName);
            Files.writeString(lockFilePath, JsonUtils.toJson(lockFile));
            getLog().info("Lockfile written to " + lockFilePath);
        } catch (IOException e) {
            getLog().error(e);
        }
    }

    /**
     * When {@code hermetic} is enabled, dynamic-resolution capture only works if this plugin is
     * also registered as a Maven core extension via {@code .mvn/extensions.xml} - core extensions
     * load before any Mojo runs, so this check can only warn/repair the file for the *next* build,
     * not the current one. Creates the file if it's missing entirely; warns (without overwriting)
     * if it exists but doesn't appear to reference this plugin.
     */
    private void ensureHermeticExtensionRegistered() {
        var multiModuleProjectDirectory = session.getRequest().getMultiModuleProjectDirectory();
        if (multiModuleProjectDirectory == null) {
            return;
        }
        String groupId = mojo.getPlugin().getGroupId();
        String artifactId = mojo.getPlugin().getArtifactId();
        String version = mojo.getPlugin().getVersion();
        Path extensionsDir = multiModuleProjectDirectory.toPath().resolve(".mvn");
        Path extensionsFile = extensionsDir.resolve("extensions.xml");

        Path projectBasedir = project.getBasedir().toPath().toAbsolutePath().normalize();
        if (!multiModuleProjectDirectory.toPath().toAbsolutePath().normalize().equals(projectBasedir)) {
            // Maven resolved the core-extension directory to an *ancestor* of this project (e.g.
            // no .mvn directory of its own, but nested inside some other Maven project's tree) -
            // do not write into a directory this project doesn't own.
            getLog().warn("hermetic=true, but the detected Maven core-extension directory ("
                    + multiModuleProjectDirectory + ") is not this project's own root (" + projectBasedir
                    + "), so it can't be safely created automatically. Register " + groupId + ":" + artifactId
                    + " as a core extension via " + extensionsFile + " yourself and re-run the build.");
            return;
        }

        if (Files.exists(extensionsFile)) {
            try {
                if (!Files.readString(extensionsFile).contains(artifactId)) {
                    getLog().warn("hermetic=true, but " + extensionsFile + " does not appear to register "
                            + groupId + ":" + artifactId + " as a core extension. Dynamically-resolved "
                            + "artifacts (e.g. Surefire's test-framework provider) will not be captured "
                            + "until it is added there and the build is re-run.");
                }
            } catch (IOException e) {
                getLog().warn("hermetic=true, but could not read " + extensionsFile, e);
            }
            return;
        }

        try {
            Files.createDirectories(extensionsFile.getParent());
            Files.writeString(
                    extensionsFile,
                    "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                            + "<extensions xmlns=\"http://maven.apache.org/EXTENSIONS/1.2.0\"\n"
                            + "            xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n"
                            + "            xsi:schemaLocation=\"http://maven.apache.org/EXTENSIONS/1.2.0"
                            + " http://maven.apache.org/xsd/core-extensions-1.2.0.xsd\">\n"
                            + "  <extension>\n"
                            + "    <groupId>" + groupId + "</groupId>\n"
                            + "    <artifactId>" + artifactId + "</artifactId>\n"
                            + "    <version>" + version + "</version>\n"
                            + "  </extension>\n"
                            + "</extensions>\n");
            getLog().warn("hermetic=true, but no " + extensionsFile + " was found, so dynamically-resolved "
                    + "artifacts could not be captured this run. Created " + extensionsFile + " registering "
                    + groupId + ":" + artifactId + " as a core extension - re-run the build for hermetic "
                    + "capture to take effect.");
        } catch (IOException e) {
            getLog().warn(
                            "hermetic=true, but no " + extensionsFile + " was found and it could not be created "
                                    + "automatically. Register " + groupId + ":" + artifactId
                                    + " as a core extension via "
                                    + extensionsFile + " to enable dynamic-resolution capture.",
                            e);
        }
    }

    private Config getConfig(LockFile lockFileFromFile) {
        if (lockFileFromFile == null || lockFileFromFile.getConfig() == null) {
            return getConfig();
        }

        Config config = lockFileFromFile.getConfig();

        return new Config(
                config.getMavenPluginsInclusion(),
                config.getOnValidationFailure(),
                config.getOnPomValidationFailure(),
                config.getOnMavenPluginValidationFailure(),
                config.getOnEnvironmentalValidationFailure(),
                config.getEnvironmentInclusion(),
                config.getReductionState(),
                mojo.getPlugin().getVersion(),
                config.getChecksumMode(),
                config.getChecksumAlgorithm(),
                config.getBomsInclusion(),
                config.getOnBomValidationFailure(),
                config.getParentPomInclusion(),
                config.getOnParentPomValidationFailure(),
                config.getMavenExtensionsInclusion(),
                config.getOnMavenExtensionsValidationFailure(),
                config.getHermeticInclusion());
    }
}
