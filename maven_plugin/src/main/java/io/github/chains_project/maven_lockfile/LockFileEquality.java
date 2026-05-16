package io.github.chains_project.maven_lockfile;

import io.github.chains_project.maven_lockfile.data.LockFile;
import io.github.chains_project.maven_lockfile.data.MavenPlugin;
import io.github.chains_project.maven_lockfile.data.Pom;
import io.github.chains_project.maven_lockfile.graph.DependencyNode;
import java.util.Collections;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Equality helpers used by {@link io.github.chains_project.maven_lockfile.reporting.ValidationPhases}
 * to compare an on-disk lockfile against a freshly-generated one.
 *
 * <p>Each method covers one independently-testable concern:
 * <ul>
 *   <li>{@link #coreEqual}: GAV, dependency nodes, and plugins — the minimum required for a valid lockfile.
 *   <li>{@link #bomsEqualForAll}: top-level BOMs and per-node BOMs across all dependencies and plugins.
 *   <li>{@link #parentPomsEqualForAll}: parentPom on every dependency node and plugin.
 * </ul>
 *
 * Maven extensions and environment are compared directly with {@link Objects#equals} in their
 * respective {@link io.github.chains_project.maven_lockfile.reporting.ValidationPhase} implementations.
 */
public final class LockFileEquality {

    private LockFileEquality() {}

    public static boolean coreEqual(LockFile a, LockFile b) {
        return Objects.equals(a.getName(), b.getName())
                && Objects.equals(a.getGroupId(), b.getGroupId())
                && Objects.equals(a.getVersion(), b.getVersion())
                && dependencySetsEqual(a.getDependencies(), b.getDependencies())
                && pluginSetsEqual(a.getMavenPlugins(), b.getMavenPlugins());
    }

    private static boolean dependencySetsEqual(Set<DependencyNode> a, Set<DependencyNode> b) {
        return a.size() == b.size()
                && a.stream().allMatch(nA -> b.stream().anyMatch(nB -> dependencyNodeEqual(nA, nB)));
    }

    private static boolean dependencyNodeEqual(DependencyNode a, DependencyNode b) {
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
                && dependencySetsEqual(a.getChildren(), b.getChildren());
    }

    private static boolean pluginSetsEqual(Set<MavenPlugin> a, Set<MavenPlugin> b) {
        return a.size() == b.size() && a.stream().allMatch(pA -> b.stream().anyMatch(pB -> mavenPluginEqual(pA, pB)));
    }

    private static boolean mavenPluginEqual(MavenPlugin a, MavenPlugin b) {
        return Objects.equals(a.getGroupId(), b.getGroupId())
                && Objects.equals(a.getArtifactId(), b.getArtifactId())
                && Objects.equals(a.getVersion(), b.getVersion())
                && Objects.equals(a.getChecksum(), b.getChecksum())
                && Objects.equals(a.getChecksumAlgorithm(), b.getChecksumAlgorithm())
                && Objects.equals(a.getResolved(), b.getResolved())
                && Objects.equals(a.getRepositoryId(), b.getRepositoryId())
                && dependencySetsEqual(a.getDependencies(), b.getDependencies());
    }

    public static boolean bomsEqualForAll(LockFile a, LockFile b, boolean compareParentChains) {
        if (!pomSetsEqual(a.getBoms(), b.getBoms(), compareParentChains)) return false;
        if (!nodeBomsEqual(a.getDependencies(), b.getDependencies(), compareParentChains)) return false;
        for (MavenPlugin pA : a.getMavenPlugins()) {
            Optional<MavenPlugin> pB = findMatchingPlugin(pA, b.getMavenPlugins());
            if (pB.isEmpty()) return false;
            if (!nodeBomsEqual(pA.getDependencies(), pB.get().getDependencies(), compareParentChains)) return false;
        }
        return true;
    }

    private static boolean nodeBomsEqual(Set<DependencyNode> a, Set<DependencyNode> b, boolean compareParentChains) {
        return a.size() == b.size()
                && a.stream().allMatch(nA -> b.stream()
                        .anyMatch(nB -> Objects.equals(nA.getGroupId(), nB.getGroupId())
                                && Objects.equals(nA.getArtifactId(), nB.getArtifactId())
                                && Objects.equals(nA.getVersion(), nB.getVersion())
                                && pomSetsEqual(nA.getBoms(), nB.getBoms(), compareParentChains)
                                && nodeBomsEqual(nA.getChildren(), nB.getChildren(), compareParentChains)));
    }

    static boolean pomSetsEqual(Set<Pom> a, Set<Pom> b, boolean compareParentChains) {
        Set<Pom> safeA = a == null ? Collections.emptySet() : a;
        Set<Pom> safeB = b == null ? Collections.emptySet() : b;
        return safeA.size() == safeB.size()
                && safeA.stream().allMatch(pA -> safeB.stream().anyMatch(pB -> pomEqual(pA, pB, compareParentChains)));
    }

    public static boolean pomEqual(Pom a, Pom b, boolean compareParentChains) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        return Objects.equals(a.getGroupId(), b.getGroupId())
                && Objects.equals(a.getArtifactId(), b.getArtifactId())
                && Objects.equals(a.getVersion(), b.getVersion())
                && Objects.equals(a.getRelativePath(), b.getRelativePath())
                && Objects.equals(a.getResolved(), b.getResolved())
                && Objects.equals(a.getRepositoryId(), b.getRepositoryId())
                && Objects.equals(a.getChecksumAlgorithm(), b.getChecksumAlgorithm())
                && Objects.equals(a.getChecksum(), b.getChecksum())
                && (!compareParentChains || pomEqual(a.getParent(), b.getParent(), true));
    }

    public static boolean parentPomsEqualForAll(LockFile a, LockFile b) {
        if (!nodeParentPomsEqual(a.getDependencies(), b.getDependencies())) return false;
        for (MavenPlugin pA : a.getMavenPlugins()) {
            Optional<MavenPlugin> pB = findMatchingPlugin(pA, b.getMavenPlugins());
            if (pB.isEmpty()) return false;
            if (!Objects.equals(pA.getParentPom(), pB.get().getParentPom())) return false;
            if (!nodeParentPomsEqual(pA.getDependencies(), pB.get().getDependencies())) return false;
        }
        return true;
    }

    private static boolean nodeParentPomsEqual(Set<DependencyNode> a, Set<DependencyNode> b) {
        return a.size() == b.size()
                && a.stream().allMatch(nA -> b.stream()
                        .anyMatch(nB -> Objects.equals(nA.getGroupId(), nB.getGroupId())
                                && Objects.equals(nA.getArtifactId(), nB.getArtifactId())
                                && Objects.equals(nA.getVersion(), nB.getVersion())
                                && Objects.equals(nA.getParentPom(), nB.getParentPom())
                                && nodeParentPomsEqual(nA.getChildren(), nB.getChildren())));
    }

    private static Optional<MavenPlugin> findMatchingPlugin(MavenPlugin target, Set<MavenPlugin> plugins) {
        return plugins.stream()
                .filter(p -> Objects.equals(target.getGroupId(), p.getGroupId())
                        && Objects.equals(target.getArtifactId(), p.getArtifactId())
                        && Objects.equals(target.getVersion(), p.getVersion()))
                .findFirst();
    }
}
