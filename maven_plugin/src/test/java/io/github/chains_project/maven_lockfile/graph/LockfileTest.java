package io.github.chains_project.maven_lockfile.graph;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.chains_project.maven_lockfile.data.*;
import java.util.Set;
import org.junit.jupiter.api.Test;

public class LockfileTest {

    @Test
    void shouldLockFilesEqualWhenOrderIsChanged() {
        var metadata = new MetaData(
                new Environment("os", "mv", "jv"), new Config(true, false, true, false, "1", "maven_local", "sha1"));
        var groupId = GroupId.of("g");
        var artifactId = ArtifactId.of("a");
        var version = VersionNumber.of("a");

        var lock1 = new LockFile(
                groupId,
                artifactId,
                version,
                Set.of(dependencyNodeA(dependencyNodeAChild1(), dependencyNodeAChild2()), dependencyNodeB()),
                Set.of(pluginA(), pluginB()),
                metadata);

        var lock2 = new LockFile(
                groupId,
                artifactId,
                version,
                Set.of(dependencyNodeB(), dependencyNodeA(dependencyNodeAChild1(), dependencyNodeAChild2())),
                Set.of(pluginB(), pluginA()),
                metadata);

        assertThat(lock1).isEqualTo(lock2);
    }

    private DependencyNode dependencyNodeA(DependencyNode child1, DependencyNode child2) {
        var node = new DependencyNode(
                ArtifactId.of("A"), GroupId.of("Ag"), VersionNumber.of("1"), MavenScope.RUNTIME, "sha1", "A");

        node.addChild(child1);
        node.addChild(child2);
        return node;
    }

    private DependencyNode dependencyNodeB() {
        return new DependencyNode(
                ArtifactId.of("B"), GroupId.of("Bg"), VersionNumber.of("1"), MavenScope.RUNTIME, "sha1", "B");
    }

    private DependencyNode dependencyNodeAChild1() {
        return new DependencyNode(
                ArtifactId.of("A1"), GroupId.of("Ag1"), VersionNumber.of("1"), MavenScope.RUNTIME, "sha1", "1");
    }

    private DependencyNode dependencyNodeAChild2() {
        return new DependencyNode(
                ArtifactId.of("A2"), GroupId.of("Ag2"), VersionNumber.of("1"), MavenScope.RUNTIME, "sha1", "2");
    }

    private MavenPlugin pluginA() {
        return new MavenPlugin(GroupId.of("PgA"), ArtifactId.of("PA"), VersionNumber.of("1"), "sha1", "PA");
    }

    private MavenPlugin pluginB() {
        return new MavenPlugin(GroupId.of("PgB"), ArtifactId.of("PB"), VersionNumber.of("1"), "sha1", "PB");
    }
}
