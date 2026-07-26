package io.github.chains_project.maven_lockfile.graph;

import io.github.chains_project.maven_lockfile.data.ArtifactId;
import io.github.chains_project.maven_lockfile.data.GroupId;
import io.github.chains_project.maven_lockfile.data.VersionNumber;
import java.util.Objects;

public class NodeId {

    private final GroupId groupId;
    private final ArtifactId artifactId;
    private final VersionNumber version;

    NodeId(GroupId groupId, ArtifactId artifactId, VersionNumber version) {
        this.groupId = groupId;
        this.artifactId = artifactId;
        this.version = version;
    }

    @Override
    public String toString() {
        return groupId.getValue() + ":" + artifactId.getValue() + ":" + version.getValue();
    }

    public static NodeId fromValue(String value) {
        String[] split = value.split(":");
        return new NodeId(GroupId.of(split[0]), ArtifactId.of(split[1]), VersionNumber.of(split[2]));
    }

    @Override
    public int hashCode() {
        return Objects.hash(groupId, artifactId, version);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof NodeId)) {
            return false;
        }
        NodeId other = (NodeId) obj;
        return Objects.equals(groupId, other.groupId)
                && Objects.equals(artifactId, other.artifactId)
                && Objects.equals(version, other.version);
    }
}
