package io.github.chains_project.maven_lockfile.graph;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.chains_project.maven_lockfile.LockFileEquality;
import io.github.chains_project.maven_lockfile.data.ArtifactId;
import io.github.chains_project.maven_lockfile.data.ArtifactType;
import io.github.chains_project.maven_lockfile.data.GroupId;
import io.github.chains_project.maven_lockfile.data.LockFile;
import io.github.chains_project.maven_lockfile.data.MavenPlugin;
import io.github.chains_project.maven_lockfile.data.MavenScope;
import io.github.chains_project.maven_lockfile.data.Pom;
import io.github.chains_project.maven_lockfile.data.RepositoryId;
import io.github.chains_project.maven_lockfile.data.ResolvedUrl;
import io.github.chains_project.maven_lockfile.data.VersionNumber;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link LockFileEquality}. Each test group targets one public method and includes
 * explicit "ignores" tests to document what each method does NOT compare.
 */
class LockFileEqualityTest {

    private static final GroupId G = GroupId.of("com.example");
    private static final ArtifactId A = ArtifactId.of("artifact");
    private static final VersionNumber V = VersionNumber.of("1.0");

    private static LockFile lockFile(Set<DependencyNode> deps, Set<MavenPlugin> plugins, Set<Pom> boms) {
        return new LockFile(G, A, V, projectPom(), deps, plugins, Set.of(), null, boms);
    }

    private static Pom projectPom() {
        return new Pom(G, A, V, "pom.xml", null, null, "SHA-256", "abc123", null);
    }

    private static Pom bomPom(String artifactId, String checksum) {
        return new Pom(G, ArtifactId.of(artifactId), V, null, null, null, "SHA-256", checksum, null);
    }

    private static Pom bomPomWithParent(String artifactId, String checksum, Pom parent) {
        return new Pom(G, ArtifactId.of(artifactId), V, null, null, null, "SHA-256", checksum, parent);
    }

    /** A minimal node with no children, no boms, no parentPom. */
    private static DependencyNode node(String artifactId, String checksum) {
        return new DependencyNode(
                ArtifactId.of(artifactId),
                G,
                V,
                null,
                ArtifactType.of("jar"),
                MavenScope.COMPILE,
                ResolvedUrl.Unresolved(),
                RepositoryId.None(),
                "SHA-256",
                checksum);
    }

    /** Same node but with a BOM attached. */
    private static DependencyNode nodeWithBom(String artifactId, String checksum, Pom bom) {
        DependencyNode n = node(artifactId, checksum);
        n.setBoms(Set.of(bom));
        return n;
    }

    /** Same node but with a parentPom attached. */
    private static DependencyNode nodeWithParentPom(String artifactId, String checksum, Pom parentPom) {
        DependencyNode n = node(artifactId, checksum);
        n.setParentPom(parentPom);
        return n;
    }

    private static MavenPlugin plugin(String artifactId, String checksum) {
        return new MavenPlugin(
                G, ArtifactId.of(artifactId), V, ResolvedUrl.Unresolved(), RepositoryId.None(), "SHA-256", checksum);
    }

    private static MavenPlugin pluginWithParentPom(String artifactId, String checksum, Pom parentPom) {
        return new MavenPlugin(
                G,
                ArtifactId.of(artifactId),
                V,
                ResolvedUrl.Unresolved(),
                RepositoryId.None(),
                "SHA-256",
                checksum,
                null,
                parentPom);
    }

    // -------------------------------------------------------------------------
    // coreEqual — GAV and dependency coordinates + checksums
    // Ignores: plugins, per-node BOMs, per-node parentPom, top-level BOMs, extensions
    // -------------------------------------------------------------------------

    @Test
    void coreEqual_trueWhenDepsAndPluginsMatch() {
        var a = lockFile(Set.of(node("dep", "c1")), Set.of(plugin("p", "pc1")), Set.of());
        var b = lockFile(Set.of(node("dep", "c1")), Set.of(plugin("p", "pc1")), Set.of());
        assertThat(LockFileEquality.coreEqual(a, b)).isTrue();
    }

    @Test
    void coreEqual_falseWhenDependencyChecksumDiffers() {
        var a = lockFile(Set.of(node("dep", "c1")), Set.of(), Set.of());
        var b = lockFile(Set.of(node("dep", "DIFFERENT")), Set.of(), Set.of());
        assertThat(LockFileEquality.coreEqual(a, b)).isFalse();
    }

    @Test
    void coreEqual_falseWhenDependencyMissing() {
        var a = lockFile(Set.of(node("dep", "c1")), Set.of(), Set.of());
        var b = lockFile(Set.of(), Set.of(), Set.of());
        assertThat(LockFileEquality.coreEqual(a, b)).isFalse();
    }

    @Test
    void coreEqual_ignoresPluginChecksumDiffers() {
        var a = lockFile(Set.of(), Set.of(plugin("p", "pc1")), Set.of());
        var b = lockFile(Set.of(), Set.of(plugin("p", "DIFFERENT")), Set.of());
        assertThat(LockFileEquality.coreEqual(a, b)).isTrue();
    }

    @Test
    void mavenPluginsEqual_falseWhenPluginChecksumDiffers() {
        var a = lockFile(Set.of(), Set.of(plugin("p", "pc1")), Set.of());
        var b = lockFile(Set.of(), Set.of(plugin("p", "DIFFERENT")), Set.of());
        assertThat(LockFileEquality.mavenPluginsEqual(a, b)).isFalse();
    }

    @Test
    void coreEqual_ignoresPerNodeBoms() {
        Pom bom = bomPom("bom-artifact", "bomchecksum");
        var a = lockFile(Set.of(nodeWithBom("dep", "c1", bom)), Set.of(), Set.of());
        var b = lockFile(Set.of(node("dep", "c1")), Set.of(), Set.of());
        assertThat(LockFileEquality.coreEqual(a, b)).isTrue();
    }

    @Test
    void coreEqual_ignoresPerNodeParentPom() {
        Pom parent = bomPom("parent", "pchecksum");
        var a = lockFile(Set.of(nodeWithParentPom("dep", "c1", parent)), Set.of(), Set.of());
        var b = lockFile(Set.of(node("dep", "c1")), Set.of(), Set.of());
        assertThat(LockFileEquality.coreEqual(a, b)).isTrue();
    }

    @Test
    void coreEqual_ignoresToplevelBoms() {
        Pom bom = bomPom("bom-artifact", "bomchecksum");
        var a = lockFile(Set.of(node("dep", "c1")), Set.of(), Set.of(bom));
        var b = lockFile(Set.of(node("dep", "c1")), Set.of(), Set.of());
        assertThat(LockFileEquality.coreEqual(a, b)).isTrue();
    }

    // -------------------------------------------------------------------------
    // bomsEqualForAll — top-level BOM sets, per-node BOM sets on deps and plugin deps
    // Optionally compares BOM POM parent chains (compareParentChains flag)
    // Ignores: node checksums/coordinates (only uses GAV as a matching key)
    // -------------------------------------------------------------------------

    @Test
    void bomsEqualForAll_trueWhenBomsMatch() {
        Pom bom = bomPom("bom", "bc");
        var a = lockFile(Set.of(nodeWithBom("dep", "c1", bom)), Set.of(), Set.of(bom));
        var b = lockFile(Set.of(nodeWithBom("dep", "c1", bom)), Set.of(), Set.of(bom));
        assertThat(LockFileEquality.bomsEqualForAll(a, b, false)).isTrue();
    }

    @Test
    void bomsEqualForAll_falseWhenToplevelBomDiffers() {
        Pom bom1 = bomPom("bom", "bc1");
        Pom bom2 = bomPom("bom", "DIFFERENT");
        var a = lockFile(Set.of(), Set.of(), Set.of(bom1));
        var b = lockFile(Set.of(), Set.of(), Set.of(bom2));
        assertThat(LockFileEquality.bomsEqualForAll(a, b, false)).isFalse();
    }

    @Test
    void bomsEqualForAll_falseWhenPerNodeBomDiffers() {
        Pom bom1 = bomPom("bom", "bc1");
        Pom bom2 = bomPom("bom", "DIFFERENT");
        var a = lockFile(Set.of(nodeWithBom("dep", "c1", bom1)), Set.of(), Set.of());
        var b = lockFile(Set.of(nodeWithBom("dep", "c1", bom2)), Set.of(), Set.of());
        assertThat(LockFileEquality.bomsEqualForAll(a, b, false)).isFalse();
    }

    @Test
    void bomsEqualForAll_ignoresBomParentChainWhenFlagIsFalse() {
        Pom parent1 = bomPom("parent", "p1");
        Pom parent2 = bomPom("parent", "DIFFERENT");
        Pom bom1 = bomPomWithParent("bom", "bc", parent1);
        Pom bom2 = bomPomWithParent("bom", "bc", parent2);
        var a = lockFile(Set.of(), Set.of(), Set.of(bom1));
        var b = lockFile(Set.of(), Set.of(), Set.of(bom2));
        assertThat(LockFileEquality.bomsEqualForAll(a, b, false)).isTrue();
    }

    @Test
    void bomsEqualForAll_falseWhenBomParentChainDiffersAndFlagIsTrue() {
        Pom parent1 = bomPom("parent", "p1");
        Pom parent2 = bomPom("parent", "DIFFERENT");
        Pom bom1 = bomPomWithParent("bom", "bc", parent1);
        Pom bom2 = bomPomWithParent("bom", "bc", parent2);
        var a = lockFile(Set.of(), Set.of(), Set.of(bom1));
        var b = lockFile(Set.of(), Set.of(), Set.of(bom2));
        assertThat(LockFileEquality.bomsEqualForAll(a, b, true)).isFalse();
    }

    // -------------------------------------------------------------------------
    // parentPomsEqualForAll — parentPom on every dependency node and plugin (recursive)
    // Ignores: BOMs, node checksums (only uses GAV as a matching key)
    // -------------------------------------------------------------------------

    @Test
    void parentPomsEqualForAll_trueWhenParentPomsMatch() {
        Pom parent = bomPom("parent", "pc");
        var a = lockFile(Set.of(nodeWithParentPom("dep", "c1", parent)), Set.of(), Set.of());
        var b = lockFile(Set.of(nodeWithParentPom("dep", "c1", parent)), Set.of(), Set.of());
        assertThat(LockFileEquality.parentPomsEqualForAll(a, b)).isTrue();
    }

    @Test
    void parentPomsEqualForAll_trueWhenBothParentPomsNull() {
        var a = lockFile(Set.of(node("dep", "c1")), Set.of(), Set.of());
        var b = lockFile(Set.of(node("dep", "c1")), Set.of(), Set.of());
        assertThat(LockFileEquality.parentPomsEqualForAll(a, b)).isTrue();
    }

    @Test
    void parentPomsEqualForAll_falseWhenNodeParentPomDiffers() {
        Pom parent1 = bomPom("parent", "pc1");
        Pom parent2 = bomPom("parent", "DIFFERENT");
        var a = lockFile(Set.of(nodeWithParentPom("dep", "c1", parent1)), Set.of(), Set.of());
        var b = lockFile(Set.of(nodeWithParentPom("dep", "c1", parent2)), Set.of(), Set.of());
        assertThat(LockFileEquality.parentPomsEqualForAll(a, b)).isFalse();
    }

    @Test
    void parentPomsEqualForAll_falseWhenPluginParentPomDiffers() {
        Pom parent1 = bomPom("parent", "pc1");
        Pom parent2 = bomPom("parent", "DIFFERENT");
        var a = lockFile(Set.of(), Set.of(pluginWithParentPom("p", "pc", parent1)), Set.of());
        var b = lockFile(Set.of(), Set.of(pluginWithParentPom("p", "pc", parent2)), Set.of());
        assertThat(LockFileEquality.parentPomsEqualForAll(a, b)).isFalse();
    }

    @Test
    void parentPomsEqualForAll_falseWhenOneNodeHasParentPomAndOtherDoesNot() {
        Pom parent = bomPom("parent", "pc1");
        var a = lockFile(Set.of(nodeWithParentPom("dep", "c1", parent)), Set.of(), Set.of());
        var b = lockFile(Set.of(node("dep", "c1")), Set.of(), Set.of());
        assertThat(LockFileEquality.parentPomsEqualForAll(a, b)).isFalse();
    }

    @Test
    void parentPomsEqualForAll_ignoresPerNodeBoms() {
        Pom parent = bomPom("parent", "pc");
        Pom bom = bomPom("bom", "bc");
        var a = lockFile(Set.of(nodeWithParentPom("dep", "c1", parent)), Set.of(), Set.of());
        var b = lockFile(Set.of(nodeWithBom("dep", "c1", bom)), Set.of(), Set.of());
        assertThat(LockFileEquality.parentPomsEqualForAll(a, b)).isFalse();
    }
}
