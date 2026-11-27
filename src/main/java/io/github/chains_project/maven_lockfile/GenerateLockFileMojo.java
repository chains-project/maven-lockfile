package io.github.chains_project.maven_lockfile;

import static io.github.chains_project.maven_lockfile.LockFileFacade.getLockFilePath;

import com.google.common.base.Strings;
import io.github.chains_project.maven_lockfile.checksum.AbstractChecksumCalculator;
import io.github.chains_project.maven_lockfile.checksum.ChecksumModes;
import io.github.chains_project.maven_lockfile.data.Config;
import io.github.chains_project.maven_lockfile.data.Environment;
import io.github.chains_project.maven_lockfile.data.FeatureConfig;
import io.github.chains_project.maven_lockfile.data.LockFile;
import io.github.chains_project.maven_lockfile.data.LockFileFormatException;
import io.github.chains_project.maven_lockfile.data.MetaData;
import io.github.chains_project.maven_lockfile.reporting.PluginLogManager;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
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
        requiresDependencyResolution = ResolutionScope.NONE,
        requiresOnline = true)
public class GenerateLockFileMojo extends AbstractLockfileMojo {

    // Each parameter below is null unless set, so that regenerating keeps the value the lockfile's config records.

    /** Include Maven plugins and build extensions. Defaults to {@code false}. */
    @Parameter(property = "includeMavenPlugins")
    Boolean includeMavenPlugins;

    @Parameter(property = "includeEnvironment")
    Boolean includeEnvironment;

    @Parameter(property = "includeBoms")
    Boolean includeBoms;

    @Parameter(property = "includeParentPom")
    Boolean includeParentPom;

    /**
     * <b>Experimental.</b> Merge artifacts dynamically resolved during a real build into the lockfile.
     * Behaviour and lockfile output may change or be removed in any release without notice.
     */
    @Parameter(property = "hermetic")
    Boolean hermetic;

    @Parameter(property = "reduced")
    Boolean reduced;

    @Parameter(property = "checksumMode")
    String checksumMode;

    @Parameter(property = "checksumAlgorithm")
    String checksumAlgorithm;

    @Parameter(defaultValue = "true", property = "getConfigFromFile")
    boolean getConfigFromFile;

    /**
     * The lockfile format to write. Defaults to the format of the existing lockfile, or the latest if there is none.
     */
    @Parameter(property = "lockfileVersion")
    Integer lockfileVersion;

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
        rejectParametersOfOtherGoals(mojo, project, session);
        try {
            LockFile lockFileFromFile = Files.exists(getLockFilePath(project, lockfileName))
                    ? LockFile.readLockFile(getLockFilePath(project, lockfileName))
                    : null;
            int formatVersion = lockfileVersion != null
                    ? lockfileVersion
                    : lockFileFromFile != null
                            ? lockFileFromFile.getLockfileVersion()
                            : LockFile.LATEST_LOCKFILE_VERSION;
            if (!LockFile.isSupportedLockfileVersion(formatVersion)) {
                throw new MojoExecutionException("Unsupported lockfileVersion " + formatVersion
                        + ". Supported versions are 2 to " + LockFile.LATEST_LOCKFILE_VERSION + ".");
            }
            boolean keepConfig = getConfigFromFile && lockFileFromFile != null && lockFileFromFile.getConfig() != null;
            Config config = withParameters(keepConfig ? lockFileFromFile.getConfig() : new Config());
            if (config.isHermetic() && !config.mavenPlugins().isIncluded()) {
                throw new MojoExecutionException("hermetic=true needs includeMavenPlugins=true, because the artifacts"
                        + " it captures are recorded on the plugins that resolve them.");
            }
            if (config.isHermetic()) {
                ensureHermeticExtensionRegistered();
            }
            Environment environment = null;
            if (config.environment().isIncluded()) {
                environment = generateMetaInformation();
            }
            MetaData metaData = new MetaData(environment, config);

            if (lockFileFromFile == null) {
                getLog().info("No lockfile found. Generating new lockfile.");
            }
            AbstractChecksumCalculator checksumCalculator = getChecksumCalculator(config);
            config.setMavenLockfileChecksum(checksumCalculator.calculatePluginChecksum(
                    mojo.getMojoDescriptor().getPluginDescriptor().getPluginArtifact()));
            LockFile lockFile = LockFileFacade.generateLockFileFromProject(
                    session, project, dependencyCollectorBuilder, checksumCalculator, metaData, repositorySystem);
            List<String> conflicts = lockFile.conflictingChecksums();
            if (!conflicts.isEmpty()) {
                throw new MojoExecutionException("These artifacts were resolved with different checksums, so the"
                        + " lockfile cannot pin which file is used:\n  " + String.join("\n  ", conflicts));
            }

            Path lockFilePath = LockFileFacade.getLockFilePath(project, lockfileName);
            Files.writeString(lockFilePath, lockFile.toJson(formatVersion));
            getLog().info("Lockfile written to " + lockFilePath);
        } catch (LockFileFormatException e) {
            throw new MojoExecutionException(e.getMessage(), e);
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

    /** {@code base}, with the parameters that were set replacing its values. */
    Config withParameters(Config base) {
        return new Config(
                include(base.mavenPlugins(), includeMavenPlugins),
                base.getOnDependencyValidationFailure(),
                include(base.environment(), includeEnvironment),
                base.getOnPomValidationFailure(),
                reduced != null
                        ? (reduced ? Config.ReductionState.Reduced : Config.ReductionState.NonReduced)
                        : base.getReductionState(),
                mojo.getPlugin().getVersion(),
                Strings.isNullOrEmpty(checksumMode) ? base.getChecksumMode() : ChecksumModes.fromName(checksumMode),
                Strings.isNullOrEmpty(checksumAlgorithm) ? base.getChecksumAlgorithm() : checksumAlgorithm,
                include(base.boms(), includeBoms),
                include(base.parentPom(), includeParentPom),
                hermetic != null
                        ? (hermetic ? Config.HermeticInclusion.Include : Config.HermeticInclusion.Exclude)
                        : base.getHermeticInclusion());
    }

    private static FeatureConfig include(FeatureConfig base, Boolean include) {
        return include != null ? new FeatureConfig(include, base.getOnFailure()) : base;
    }
}
