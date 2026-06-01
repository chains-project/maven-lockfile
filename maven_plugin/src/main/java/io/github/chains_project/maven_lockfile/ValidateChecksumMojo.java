package io.github.chains_project.maven_lockfile;

import static io.github.chains_project.maven_lockfile.LockFileFacade.getLockFilePath;

import io.github.chains_project.maven_lockfile.checksum.AbstractChecksumCalculator;
import io.github.chains_project.maven_lockfile.data.Config;
import io.github.chains_project.maven_lockfile.data.Environment;
import io.github.chains_project.maven_lockfile.data.LockFile;
import io.github.chains_project.maven_lockfile.data.MavenPlugin;
import io.github.chains_project.maven_lockfile.data.MetaData;
import io.github.chains_project.maven_lockfile.data.Pom;
import io.github.chains_project.maven_lockfile.graph.DependencyNode;
import io.github.chains_project.maven_lockfile.reporting.LockFileDifference;
import io.github.chains_project.maven_lockfile.reporting.PluginLogManager;
import java.io.IOException;
import java.util.Collections;
import java.util.Objects;
import java.util.Optional;
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

            // Phase 1: core equality (deps/plugins compared without parentPom, boms, extensions)
            boolean phase1Equal = coreEqual(lockFileFromFile, lockFileFromProject);
            if (!phase1Equal) {
                var diff = LockFileDifference.diff(lockFileFromFile, lockFileFromProject);
                String sb = "Lock file validation failed. Differences:" + "\n"
                        + "Your lockfile from file is for:"
                        + lockFileFromFile.getGroupId().getValue()
                        + ":" + lockFileFromFile.getName().getValue() + ":"
                        + lockFileFromFile.getVersion().getValue() + "\n" + "Your generated lockfile is for:"
                        + lockFileFromProject.getGroupId().getValue() + ":"
                        + lockFileFromProject.getName().getValue() + ":"
                        + lockFileFromProject.getVersion().getValue() + "\n"
                        + "Missing dependencies in lock file:\n "
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
                        + "\n";
                switch (config.getOnValidationFailure()) {
                    case Warn:
                        getLog().warn("Failed verifying lock file. " + sb);
                        break;
                    case Error:
                        throw new MojoExecutionException("Failed verifying lock file. " + sb);
                }
            }

            // Phase 2: bom membership (independent of Phase 1)
            if (config.isIncludeBoms()) {
                if (!bomsEqualForAll(lockFileFromFile, lockFileFromProject, config.isIncludeParentPom())) {
                    String msg = "Bom validation failed."
                            + " The BOM membership or BOM POM data of dependencies differs"
                            + " between the lockfile and the project.";
                    switch (config.getOnBomValidationFailure()) {
                        case Warn:
                            getLog().warn(msg);
                            break;
                        case Error:
                            throw new MojoExecutionException(msg);
                    }
                }
            }

            // Phase 3: parentPom on nodes/plugins (only when Phase 1 passed)
            if (phase1Equal && config.isIncludeParentPom()) {
                if (!parentPomsEqualForAll(lockFileFromFile, lockFileFromProject)) {
                    String msg = "Parent POM validation failed."
                            + " The parentPom of one or more dependencies or plugins differs"
                            + " between the lockfile and the project.";
                    switch (config.getOnParentPomValidationFailure()) {
                        case Warn:
                            getLog().warn(msg);
                            break;
                        case Error:
                            throw new MojoExecutionException(msg);
                    }
                }
            }

            // Phase 4: maven extensions (independent)
            if (config.isIncludeMavenExtensions()) {
                if (!Objects.equals(lockFileFromFile.getMavenExtensions(), lockFileFromProject.getMavenExtensions())) {
                    var diff = LockFileDifference.diff(lockFileFromFile, lockFileFromProject);
                    String msg = "Maven extensions validation failed.\n"
                            + "Missing extensions in lockfile:\n "
                            + JsonUtils.toJson(diff.getMissingExtensionsInFile())
                            + "\n"
                            + "Missing extensions in project:\n "
                            + JsonUtils.toJson(diff.getMissingExtensionsInProject())
                            + "\n";
                    switch (config.getOnMavenExtensionsValidationFailure()) {
                        case Warn:
                            getLog().warn(msg);
                            break;
                        case Error:
                            throw new MojoExecutionException(msg);
                    }
                }
            }

        } catch (IOException e) {
            throw new MojoExecutionException("Could not read lock file", e);
        }
        getLog().info("Lockfile successfully validated.");
    }

    // Phase 1: core equality — skips parentPom, per-node boms, top-level boms, and extensions
    private static boolean coreEqual(LockFile a, LockFile b) {
        if (!Objects.equals(a.getName(), b.getName())
                || !Objects.equals(a.getGroupId(), b.getGroupId())
                || !Objects.equals(a.getVersion(), b.getVersion())) {
            return false;
        }
        if (!depsEqualCore(a.getDependencies(), b.getDependencies())) return false;
        return pluginsEqualCore(a.getMavenPlugins(), b.getMavenPlugins());
    }

    private static boolean depsEqualCore(Set<DependencyNode> a, Set<DependencyNode> b) {
        if (a.size() != b.size()) return false;
        return a.stream().allMatch(nodeA -> b.stream().anyMatch(nodeB -> nodeEqualCore(nodeA, nodeB)));
    }

    private static boolean nodeEqualCore(DependencyNode a, DependencyNode b) {
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
                && depsEqualCore(a.getChildren(), b.getChildren());
    }

    private static boolean pluginsEqualCore(Set<MavenPlugin> a, Set<MavenPlugin> b) {
        if (a.size() != b.size()) return false;
        return a.stream().allMatch(pA -> b.stream().anyMatch(pB -> pluginEqualCore(pA, pB)));
    }

    private static boolean pluginEqualCore(MavenPlugin a, MavenPlugin b) {
        return Objects.equals(a.getGroupId(), b.getGroupId())
                && Objects.equals(a.getArtifactId(), b.getArtifactId())
                && Objects.equals(a.getVersion(), b.getVersion())
                && Objects.equals(a.getChecksum(), b.getChecksum())
                && Objects.equals(a.getChecksumAlgorithm(), b.getChecksumAlgorithm())
                && Objects.equals(a.getResolved(), b.getResolved())
                && Objects.equals(a.getRepositoryId(), b.getRepositoryId())
                && depsEqualCore(a.getDependencies(), b.getDependencies());
    }

    // Phase 2: bom equality — top-level boms + per-node boms; optionally skip Pom parent chains
    private static boolean bomsEqualForAll(LockFile a, LockFile b, boolean compareParentChains) {
        if (!pomSetsEqual(a.getBoms(), b.getBoms(), compareParentChains)) return false;
        if (!nodeBomsEqual(a.getDependencies(), b.getDependencies(), compareParentChains)) return false;
        for (MavenPlugin pA : a.getMavenPlugins()) {
            Optional<MavenPlugin> pBOpt = b.getMavenPlugins().stream()
                    .filter(pB -> Objects.equals(pA.getGroupId(), pB.getGroupId())
                            && Objects.equals(pA.getArtifactId(), pB.getArtifactId())
                            && Objects.equals(pA.getVersion(), pB.getVersion()))
                    .findFirst();
            if (!pBOpt.isPresent()) return false;
            if (!nodeBomsEqual(pA.getDependencies(), pBOpt.get().getDependencies(), compareParentChains)) return false;
        }
        return true;
    }

    private static boolean nodeBomsEqual(
            Set<DependencyNode> depsA, Set<DependencyNode> depsB, boolean compareParentChains) {
        if (depsA.size() != depsB.size()) return false;
        return depsA.stream().allMatch(nA -> depsB.stream()
                .anyMatch(nB -> Objects.equals(nA.getGroupId(), nB.getGroupId())
                        && Objects.equals(nA.getArtifactId(), nB.getArtifactId())
                        && Objects.equals(nA.getVersion(), nB.getVersion())
                        && pomSetsEqual(nA.getBoms(), nB.getBoms(), compareParentChains)
                        && nodeBomsEqual(nA.getChildren(), nB.getChildren(), compareParentChains)));
    }

    private static boolean pomSetsEqual(Set<Pom> a, Set<Pom> b, boolean compareParentChains) {
        Set<Pom> safeA = a == null ? Collections.emptySet() : a;
        Set<Pom> safeB = b == null ? Collections.emptySet() : b;
        if (safeA.size() != safeB.size()) return false;
        return safeA.stream()
                .allMatch(pomA -> safeB.stream().anyMatch(pomB -> pomEqual(pomA, pomB, compareParentChains)));
    }

    private static boolean pomEqual(Pom a, Pom b, boolean compareParentChains) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        boolean baseEqual = Objects.equals(a.getGroupId(), b.getGroupId())
                && Objects.equals(a.getArtifactId(), b.getArtifactId())
                && Objects.equals(a.getVersion(), b.getVersion())
                && Objects.equals(a.getRelativePath(), b.getRelativePath())
                && Objects.equals(a.getResolved(), b.getResolved())
                && Objects.equals(a.getRepositoryId(), b.getRepositoryId())
                && Objects.equals(a.getChecksumAlgorithm(), b.getChecksumAlgorithm())
                && Objects.equals(a.getChecksum(), b.getChecksum());
        if (!baseEqual) return false;
        return !compareParentChains || pomEqual(a.getParent(), b.getParent(), true);
    }

    // Phase 3: parentPom equality on all dependency nodes and plugins
    private static boolean parentPomsEqualForAll(LockFile a, LockFile b) {
        if (!nodeParentPomsEqual(a.getDependencies(), b.getDependencies())) return false;
        for (MavenPlugin pA : a.getMavenPlugins()) {
            Optional<MavenPlugin> pBOpt = b.getMavenPlugins().stream()
                    .filter(pB -> Objects.equals(pA.getGroupId(), pB.getGroupId())
                            && Objects.equals(pA.getArtifactId(), pB.getArtifactId())
                            && Objects.equals(pA.getVersion(), pB.getVersion()))
                    .findFirst();
            if (!pBOpt.isPresent()) return false;
            MavenPlugin pB = pBOpt.get();
            if (!Objects.equals(pA.getParentPom(), pB.getParentPom())) return false;
            if (!nodeParentPomsEqual(pA.getDependencies(), pB.getDependencies())) return false;
        }
        return true;
    }

    private static boolean nodeParentPomsEqual(Set<DependencyNode> depsA, Set<DependencyNode> depsB) {
        if (depsA.size() != depsB.size()) return false;
        return depsA.stream().allMatch(nA -> depsB.stream()
                .anyMatch(nB -> Objects.equals(nA.getGroupId(), nB.getGroupId())
                        && Objects.equals(nA.getArtifactId(), nB.getArtifactId())
                        && Objects.equals(nA.getVersion(), nB.getVersion())
                        && Objects.equals(nA.getParentPom(), nB.getParentPom())
                        && nodeParentPomsEqual(nA.getChildren(), nB.getChildren())));
    }
}
