package io.github.chains_project.maven_lockfile.graph;

import io.github.chains_project.maven_lockfile.data.ArtifactId;
import io.github.chains_project.maven_lockfile.data.GroupId;
import io.github.chains_project.maven_lockfile.data.VersionNumber;

public class NodeId {

    private GroupId groupId;
    private ArtifactId artifactId;
    private VersionNumber version;

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
}
