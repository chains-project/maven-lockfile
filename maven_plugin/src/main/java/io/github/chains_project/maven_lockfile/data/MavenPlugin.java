package io.github.chains_project.maven_lockfile.data;

import java.util.Objects;

/**
 * This class represents a maven plugin. It contains a group id, an artifact id, and a version number. A plugin is uniquely identified by its group id, artifact id, and version number.
 * A maven plugin is a dependency that is used to build a project. It is not a dependency of the project itself.
 */
public class MavenPlugin {

    private final GroupId groupId;
    private final ArtifactId artifactId;
    private final VersionNumber version;

    public MavenPlugin(GroupId groupId, ArtifactId artifactId, VersionNumber version) {
        this.groupId = groupId;
        this.artifactId = artifactId;
        this.version = version;
    }

    public GroupId getGroupId() {
        return groupId;
    }

    public ArtifactId getArtifactId() {
        return artifactId;
    }

    public VersionNumber getVersion() {
        return version;
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
        if (!(obj instanceof MavenPlugin)) {
            return false;
        }
        MavenPlugin other = (MavenPlugin) obj;
        return Objects.equals(groupId, other.groupId)
                && Objects.equals(artifactId, other.artifactId)
                && Objects.equals(version, other.version);
    }
}
