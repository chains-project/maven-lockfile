package io.github.chains_project.maven_lockfile.graph;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.chains_project.maven_lockfile.checksum.ChecksumModes;
import io.github.chains_project.maven_lockfile.data.ArtifactId;
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
                        Config.OnEnvironmentalValidationFailure.Error,
                        Config.EnvironmentInclusion.Include,
                        Config.ReductionState.NonReduced,
                        "1",
                        ChecksumModes.LOCAL,
                        "SHA-1"));
        var groupId = GroupId.of("g");
        var artifactId = ArtifactId.of("a");
        var version = VersionNumber.of("a");

        var lock1 = new LockFile(
                groupId,
                artifactId,
                version,
                new Pom("pom.xml", "SHA-256", "POM-CHECKSUM"),
                Set.of(dependencyNodeA(dependencyNodeAChild1(), dependencyNodeAChild2()), dependencyNodeB()),
                Set.of(pluginA(), pluginB()),
                metadata);

        var lock2 = new LockFile(
                groupId,
                artifactId,
                version,
                new Pom("pom.xml", "SHA-256", "POM-CHECKSUM"),
                Set.of(dependencyNodeB(), dependencyNodeA(dependencyNodeAChild1(), dependencyNodeAChild2())),
                Set.of(pluginB(), pluginA()),
                metadata);

        assertThat(lock1).isEqualTo(lock2);
    }

    private DependencyNode dependencyNodeA(DependencyNode child1, DependencyNode child2) {
        var node = new DependencyNode(
                ArtifactId.of("A"),
                GroupId.of("Ag"),
                VersionNumber.of("1"),
                null,
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
}
