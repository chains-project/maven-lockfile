package io.github.chains_project.maven_lockfile.graph;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.chains_project.maven_lockfile.checksum.ChecksumModes;
import io.github.chains_project.maven_lockfile.data.ArtifactId;
import io.github.chains_project.maven_lockfile.data.ArtifactType;
import io.github.chains_project.maven_lockfile.data.Config;
import io.github.chains_project.maven_lockfile.data.Environment;
import io.github.chains_project.maven_lockfile.data.GroupId;
import io.github.chains_project.maven_lockfile.data.LockFile;
import io.github.chains_project.maven_lockfile.data.MavenPlugin;
import io.github.chains_project.maven_lockfile.data.MavenScope;
import io.github.chains_project.maven_lockfile.data.MetaData;
import io.github.chains_project.maven_lockfile.data.Pom;
import io.github.chains_project.maven_lockfile.data.RepositoryId;
import io.github.chains_project.maven_lockfile.data.ResolvedUrl;
import io.github.chains_project.maven_lockfile.data.VersionNumber;
import java.util.Set;
import org.junit.jupiter.api.Test;

public class LockfileTest {

    @Test
    void shouldLockFilesEqualWhenOrderIsChanged() {
        var metadata = new MetaData(
                new Environment("os", "mv", "jv"),
                new Config(
                        Config.MavenPluginsInclusion.Include,
                        Config.OnValidationFailure.Error,
                        Config.OnPomValidationFailure.Error,
                        Config.OnMavenPluginValidationFailure.Error,
                        Config.OnEnvironmentalValidationFailure.Error,
                        Config.EnvironmentInclusion.Include,
                        Config.ReductionState.NonReduced,
                        "1",
                        ChecksumModes.LOCAL,
                        "SHA-1",
                        Config.BomsInclusion.Include,
                        Config.OnBomValidationFailure.Error,
                        Config.ParentPomInclusion.Include,
                        Config.OnParentPomValidationFailure.Error,
                        Config.MavenExtensionsInclusion.Include,
                        Config.OnMavenExtensionsValidationFailure.Error,
                        Config.HermeticInclusion.Exclude));
        var groupId = GroupId.of("g");
        var artifactId = ArtifactId.of("a");
        var version = VersionNumber.of("a");

        var lock1 = new LockFile(
                groupId,
                artifactId,
                version,
                new Pom(groupId, artifactId, version, "pom.xml", null, null, "SHA-256", "POM-CHECKSUM", null),
                Set.of(dependencyNodeA(dependencyNodeAChild1(), dependencyNodeAChild2()), dependencyNodeB()),
                Set.of(pluginA(), pluginB()),
                Set.of(),
                metadata,
                Set.of(bom()));

        var lock2 = new LockFile(
                groupId,
                artifactId,
                version,
                new Pom(groupId, artifactId, version, "pom.xml", null, null, "SHA-256", "POM-CHECKSUM", null),
                Set.of(dependencyNodeB(), dependencyNodeA(dependencyNodeAChild1(), dependencyNodeAChild2())),
                Set.of(pluginB(), pluginA()),
                Set.of(),
                metadata,
                Set.of(bom()));

        assertThat(lock1).isEqualTo(lock2);
    }

    @Test
    void shouldReportArtifactsWithDifferentChecksums() {
        var groupId = GroupId.of("g");
        var artifactId = ArtifactId.of("a");
        var version = VersionNumber.of("1");
        // The plugin's B has checksum `other` instead of `B` (from dependencyNodeB), and its A2 has no checksum
        // instead of `2` (from dependencyNodeAChild2): a missing checksum conflicts too.
        var otherB = jar("B", "Bg", "other");
        otherB.addChild(jar("A2", "Ag2", ""));
        var plugin = new MavenPlugin(
                GroupId.of("PgA"),
                ArtifactId.of("PA"),
                version,
                ResolvedUrl.Unresolved(),
                RepositoryId.None(),
                "SHA-1",
                "PA",
                Set.of(otherB, dependencyNodeAChild2()),
                null);
        var lockFile = new LockFile(
                groupId,
                artifactId,
                version,
                null,
                Set.of(dependencyNodeA(dependencyNodeAChild1(), dependencyNodeAChild2()), dependencyNodeB()),
                Set.of(plugin),
                Set.of(),
                null,
                Set.of());

        assertThat(lockFile.conflictingArtifacts())
                .containsExactly("Ag2:A2:1::jar checksum [2, <none>]", "Bg:B:1::jar checksum [B, other]");
    }

    @Test
    void shouldReportArtifactsResolvedFromDifferentRepositories() {
        var version = VersionNumber.of("1");
        // Same file, but the plugin's copy of B was recorded from another repository than the project's.
        var plugin = new MavenPlugin(
                GroupId.of("PgA"),
                ArtifactId.of("PA"),
                version,
                ResolvedUrl.Unresolved(),
                RepositoryId.None(),
                "SHA-1",
                "PA",
                Set.of(jarB("jboss", "https://jboss.example/Bg/B/1/B-1.jar")),
                null);
        var lockFile = new LockFile(
                GroupId.of("g"),
                ArtifactId.of("a"),
                version,
                null,
                Set.of(jarB("central", "https://central.example/Bg/B/1/B-1.jar")),
                Set.of(plugin),
                Set.of(),
                null,
                Set.of());

        assertThat(lockFile.conflictingArtifacts())
                .containsExactly(
                        "Bg:B:1::jar resolved [https://central.example/Bg/B/1/B-1.jar,"
                                + " https://jboss.example/Bg/B/1/B-1.jar]",
                        "Bg:B:1::jar repositoryId [central, jboss]");
    }

    @Test
    void shouldReportPomsResolvedFromDifferentRepositories() {
        var groupId = GroupId.of("g");
        var artifactId = ArtifactId.of("a");
        var version = VersionNumber.of("1");
        // The project's parent and a BOM are the same POM, recorded from different repositories.
        var parent = pomX("mirror", "https://mirror.example/Xg/X/1/X-1.pom");
        var bom = pomX("central", "https://central.example/Xg/X/1/X-1.pom");
        var lockFile = new LockFile(
                groupId,
                artifactId,
                version,
                new Pom(groupId, artifactId, version, "pom.xml", null, null, "SHA-256", "POM-CHECKSUM", parent),
                Set.of(),
                Set.of(),
                Set.of(),
                null,
                Set.of(bom));

        assertThat(lockFile.conflictingArtifacts())
                .containsExactly(
                        "Xg:X:1::pom resolved [https://central.example/Xg/X/1/X-1.pom,"
                                + " https://mirror.example/Xg/X/1/X-1.pom]",
                        "Xg:X:1::pom repositoryId [central, mirror]");
    }

    private DependencyNode jarB(String repositoryId, String resolved) {
        return new DependencyNode(
                ArtifactId.of("B"),
                GroupId.of("Bg"),
                VersionNumber.of("1"),
                null,
                ArtifactType.of("jar"),
                MavenScope.RUNTIME,
                ResolvedUrl.of(resolved),
                RepositoryId.of(repositoryId),
                "SHA-1",
                "B");
    }

    private Pom pomX(String repositoryId, String resolved) {
        return new Pom(
                GroupId.of("Xg"),
                ArtifactId.of("X"),
                VersionNumber.of("1"),
                null,
                ResolvedUrl.of(resolved),
                RepositoryId.of(repositoryId),
                "SHA-256",
                "X",
                null);
    }

    private DependencyNode jar(String artifactId, String groupId, String checksum) {
        return new DependencyNode(
                ArtifactId.of(artifactId),
                GroupId.of(groupId),
                VersionNumber.of("1"),
                null,
                ArtifactType.of("jar"),
                MavenScope.RUNTIME,
                ResolvedUrl.Unresolved(),
                RepositoryId.None(),
                "SHA-1",
                checksum);
    }

    private DependencyNode dependencyNodeA(DependencyNode child1, DependencyNode child2) {
        var node = new DependencyNode(
                ArtifactId.of("A"),
                GroupId.of("Ag"),
                VersionNumber.of("1"),
                null,
                ArtifactType.of("pom"),
                MavenScope.RUNTIME,
                ResolvedUrl.Unresolved(),
                RepositoryId.None(),
                "SHA-1",
                "A");

        node.addChild(child1);
        node.addChild(child2);
        return node;
    }

    private DependencyNode dependencyNodeB() {
        return new DependencyNode(
                ArtifactId.of("B"),
                GroupId.of("Bg"),
                VersionNumber.of("1"),
                null,
                ArtifactType.of("jar"),
                MavenScope.RUNTIME,
                ResolvedUrl.Unresolved(),
                RepositoryId.None(),
                "SHA-1",
                "B");
    }

    private DependencyNode dependencyNodeAChild1() {
        return new DependencyNode(
                ArtifactId.of("A1"),
                GroupId.of("Ag1"),
                VersionNumber.of("1"),
                null,
                ArtifactType.of("war"),
                MavenScope.RUNTIME,
                ResolvedUrl.Unresolved(),
                RepositoryId.None(),
                "SHA-1",
                "1");
    }

    private DependencyNode dependencyNodeAChild2() {
        return new DependencyNode(
                ArtifactId.of("A2"),
                GroupId.of("Ag2"),
                VersionNumber.of("1"),
                null,
                ArtifactType.of("jar"),
                MavenScope.RUNTIME,
                ResolvedUrl.Unresolved(),
                RepositoryId.None(),
                "SHA-1",
                "2");
    }

    private MavenPlugin pluginA() {
        return new MavenPlugin(
                GroupId.of("PgA"),
                ArtifactId.of("PA"),
                VersionNumber.of("1"),
                ResolvedUrl.Unresolved(),
                RepositoryId.None(),
                "SHA-1",
                "PA");
    }

    private MavenPlugin pluginB() {
        return new MavenPlugin(
                GroupId.of("PgB"),
                ArtifactId.of("PB"),
                VersionNumber.of("1"),
                ResolvedUrl.Unresolved(),
                RepositoryId.None(),
                "SHA-1",
                "PB");
    }

    private Pom bom() {
        return new Pom(
                GroupId.of("Bom"),
                ArtifactId.of("Bom"),
                VersionNumber.of("1"),
                null,
                null,
                null,
                "SHA-256",
                "CHECKSUM-BOM",
                null);
    }
}
