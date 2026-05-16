package io.github.chains_project.maven_lockfile;

import static io.github.chains_project.maven_lockfile.LockFileFacade.getLockFilePath;

import io.github.chains_project.maven_lockfile.checksum.AbstractChecksumCalculator;
import io.github.chains_project.maven_lockfile.data.Config;
import io.github.chains_project.maven_lockfile.data.Environment;
import io.github.chains_project.maven_lockfile.data.LockFile;
import io.github.chains_project.maven_lockfile.data.MavenPlugin;
import io.github.chains_project.maven_lockfile.data.MetaData;
import io.github.chains_project.maven_lockfile.graph.DependencyNode;
import io.github.chains_project.maven_lockfile.reporting.LockFileDifference;
import io.github.chains_project.maven_lockfile.reporting.PluginLogManager;
import java.io.IOException;
import java.util.Objects;
import java.util.Set;
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
        if (skip) {
            getLog().info("Skipping maven-lockfile");
            return;
        }
        PluginLogManager.setLog(getLog());
        try {
            getLog().info("Validating lock file ...");
            LockFile lockFileFromFile = LockFile.readLockFile(getLockFilePath(project, lockfileName));
            Config baseConfig = lockFileFromFile.getConfig();
            if (baseConfig == null) {
                getLog().warn("No config was found in the lock file. Using default config.");
                baseConfig = getConfig();
            }
            Config config = mergeConfigWithCliArgs(baseConfig);
            Environment environment = null;
            if (config.isIncludeEnvironment()) {
                environment = generateMetaInformation();
            }
            MetaData metaData = new MetaData(environment, config);
            AbstractChecksumCalculator checksumCalculator = getChecksumCalculator(config, true);
            LockFile lockFileFromProject = LockFileFacade.generateLockFileFromProject(
                    session, project, dependencyCollectorBuilder, checksumCalculator, metaData, repositorySystem);
            if (!Objects.equals(lockFileFromFile.getEnvironment(), lockFileFromProject.getEnvironment())) {
                String sb = "Lock file environment does not match project environment.\n"
                        + "Lockfile environment: " + lockFileFromFile.getEnvironment() + "\n"
                        + "Project environment:  " + lockFileFromProject.getEnvironment() + "\n";

                switch (config.getOnEnvironmentalValidationFailure()) {
                    case Warn:
                        getLog().warn(sb);
                        break;
                    case Error:
                        throw new MojoExecutionException("Failed verifying environment. " + sb);
                }
            }
            if (!Objects.equals(lockFileFromFile.getPom(), lockFileFromProject.getPom())) {
                String sb = "Pom checksum mismatch. Differences:\nYour lockfile pom:\n"
                        + JsonUtils.toJson(lockFileFromFile.getPom())
                        + "\n" + "Your project pom:\n"
                        + JsonUtils.toJson(lockFileFromProject.getPom())
                        + "\n";

                switch (config.getOnPomValidationFailure()) {
                    case Warn:
                        getLog().warn(sb);
                        break;
                    case Error:
                        throw new MojoExecutionException("Failed verifying lock file. " + sb);
                }
            }
            boolean skipParentPom = !lockFileHasParentPomData(lockFileFromFile);
            // Old-format lockfiles also lack parent chains on bom Pom objects; skip bom comparison
            // whenever parentPom data is absent, since both features were introduced together.
            boolean skipNodeBoms = skipParentPom || !lockFileHasNodeBomData(lockFileFromFile);
            boolean skipExtensions = lockFileFromFile.getMavenExtensions().isEmpty();
            if (skipParentPom || skipNodeBoms || skipExtensions) {
                getLog().warn("The on-disk lockfile is missing one or more fields introduced in recent"
                        + " plugin versions (parentPom, dependency BOMs, mavenExtensions)."
                        + " Those fields will be skipped during validation."
                        + " Please regenerate the lockfile with the current plugin version to enable full validation.");
            }
            boolean equal = lockFilesEqual(
                    lockFileFromFile, lockFileFromProject, skipParentPom, skipNodeBoms, skipExtensions);
            if (!equal) {
                var diff = LockFileDifference.diff(lockFileFromFile, lockFileFromProject);
                String sb = "Lock file validation failed. Differences:" + "\n"
                        + "Your lockfile from file is for:"
                        + lockFileFromFile.getGroupId().getValue()
                        + ":" + lockFileFromFile.getName().getValue() + ":"
                        + lockFileFromFile.getVersion().getValue() + "\n" + "Your generated lockfile is for:"
                        + lockFileFromProject.getGroupId().getValue() + ":"
                        + lockFileFromProject.getName().getValue() + ":"
                        + lockFileFromProject.getVersion().getValue() + "\n" + "Missing dependencies in lock file:\n "
                        + JsonUtils.toJson(diff.getMissingDependenciesInFile())
                        + "\n"
                        + "Missing dependencies in project:\n "
                        + JsonUtils.toJson(diff.getMissingDependenciesInProject())
                        + "\n"
                        + "Missing plugins in lockfile:\n "
                        + JsonUtils.toJson(diff.getMissingPluginsInFile())
                        + "\n"
                        + "Missing plugins in project:\n "
                        + JsonUtils.toJson(diff.getMissingPluginsInProject())
                        + "\n"
                        + "Missing extensions in lockfile:\n "
                        + JsonUtils.toJson(diff.getMissingExtensionsInFile())
                        + "\n"
                        + "Missing extensions in project:\n "
                        + JsonUtils.toJson(diff.getMissingExtensionsInProject())
                        + "\n";
                switch (config.getOnValidationFailure()) {
                    case Warn:
                        getLog().warn("Failed verifying lock file. " + sb);
                        break;
                    case Error:
                        throw new MojoExecutionException("Failed verifying lock file. " + sb);
                }
            }
        } catch (IOException e) {
            throw new MojoExecutionException("Could not read lock file", e);
        }
        getLog().info("Lockfile successfully validated.");
    }

    private static boolean lockFilesEqual(
            LockFile fromFile,
            LockFile fromProject,
            boolean skipParentPom,
            boolean skipNodeBoms,
            boolean skipExtensions) {
        if (!Objects.equals(fromFile.getName(), fromProject.getName())
                || !Objects.equals(fromFile.getGroupId(), fromProject.getGroupId())
                || !Objects.equals(fromFile.getVersion(), fromProject.getVersion())) {
            return false;
        }
        if (!depSetsEqual(fromFile.getDependencies(), fromProject.getDependencies(), skipParentPom, skipNodeBoms))
            return false;
        if (!pluginSetsEqual(fromFile.getMavenPlugins(), fromProject.getMavenPlugins(), skipParentPom, skipNodeBoms))
            return false;
        // Extensions: the on-disk lockfile pre-dates extension tracking when the set is empty,
        // so only compare when the on-disk lockfile already contains extension data.
        if (!skipExtensions
                && !Objects.equals(fromFile.getMavenExtensions(), fromProject.getMavenExtensions())) return false;
        return Objects.equals(fromFile.getBoms(), fromProject.getBoms());
    }

    private static boolean depSetsEqual(
            Set<DependencyNode> a, Set<DependencyNode> b, boolean skipParentPom, boolean skipNodeBoms) {
        if (a.size() != b.size()) return false;
        return a.stream()
                .allMatch(nodeA -> b.stream().anyMatch(nodeB -> depNodesEqual(nodeA, nodeB, skipParentPom, skipNodeBoms)));
    }

    private static boolean depNodesEqual(
            DependencyNode a, DependencyNode b, boolean skipParentPom, boolean skipNodeBoms) {
        return Objects.equals(a.getGroupId(), b.getGroupId())
                && Objects.equals(a.getArtifactId(), b.getArtifactId())
                && Objects.equals(a.getVersion(), b.getVersion())
                && Objects.equals(a.getClassifier(), b.getClassifier())
                && Objects.equals(a.getType(), b.getType())
                && Objects.equals(a.getChecksumAlgorithm(), b.getChecksumAlgorithm())
                && Objects.equals(a.getChecksum(), b.getChecksum())
                && Objects.equals(a.getScope(), b.getScope())
                && Objects.equals(a.getSelectedVersion(), b.getSelectedVersion())
                && Objects.equals(a.getParent(), b.getParent())
                && (skipNodeBoms || Objects.equals(a.getBoms(), b.getBoms()))
                && depSetsEqual(a.getChildren(), b.getChildren(), skipParentPom, skipNodeBoms);
    }

    private static boolean pluginSetsEqual(
            Set<MavenPlugin> a, Set<MavenPlugin> b, boolean skipParentPom, boolean skipNodeBoms) {
        if (a.size() != b.size()) return false;
        return a.stream()
                .allMatch(pA -> b.stream().anyMatch(pB -> pluginEquals(pA, pB, skipParentPom, skipNodeBoms)));
    }

    private static boolean pluginEquals(
            MavenPlugin a, MavenPlugin b, boolean skipParentPom, boolean skipNodeBoms) {
        return Objects.equals(a.getGroupId(), b.getGroupId())
                && Objects.equals(a.getArtifactId(), b.getArtifactId())
                && Objects.equals(a.getVersion(), b.getVersion())
                && Objects.equals(a.getChecksum(), b.getChecksum())
                && Objects.equals(a.getChecksumAlgorithm(), b.getChecksumAlgorithm())
                && Objects.equals(a.getResolved(), b.getResolved())
                && Objects.equals(a.getRepositoryId(), b.getRepositoryId())
                && (skipParentPom || Objects.equals(a.getParentPom(), b.getParentPom()))
                && depSetsEqual(a.getDependencies(), b.getDependencies(), skipParentPom, skipNodeBoms);
    }

    /** Returns true if any dependency node or plugin (transitively) carries a non-null parentPom. */
    private static boolean lockFileHasParentPomData(LockFile lockFile) {
        return lockFile.getDependencies().stream().anyMatch(ValidateChecksumMojo::nodeHasParentPom)
                || lockFile.getMavenPlugins().stream()
                        .anyMatch(p -> p.getParentPom() != null
                                || p.getDependencies().stream().anyMatch(ValidateChecksumMojo::nodeHasParentPom));
    }

    private static boolean nodeHasParentPom(DependencyNode node) {
        return node.getParentPom() != null
                || node.getChildren().stream().anyMatch(ValidateChecksumMojo::nodeHasParentPom);
    }

    /** Returns true if any dependency node (transitively) has a non-empty boms set. */
    private static boolean lockFileHasNodeBomData(LockFile lockFile) {
        return lockFile.getDependencies().stream().anyMatch(ValidateChecksumMojo::nodeHasBoms)
                || lockFile.getMavenPlugins().stream()
                        .anyMatch(p -> p.getDependencies().stream().anyMatch(ValidateChecksumMojo::nodeHasBoms));
    }

    private static boolean nodeHasBoms(DependencyNode node) {
        return !node.getBoms().isEmpty()
                || node.getChildren().stream().anyMatch(ValidateChecksumMojo::nodeHasBoms);
    }
}
