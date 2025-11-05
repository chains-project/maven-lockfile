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
    private final String checksumAlgorithm;
    private final String checksum;
    private final ResolvedUrl resolved;
    private final RepositoryId repositoryId;

    public MavenPlugin(
            GroupId groupId,
            ArtifactId artifactId,
            VersionNumber version,
            ResolvedUrl resolvedUrl,
            RepositoryId repositoryId,
            String checksumAlgorithm,
            String checksum) {
        this.groupId = groupId;
        this.artifactId = artifactId;
        this.version = version;
        this.resolved = resolvedUrl;
        this.repositoryId = repositoryId;
        this.checksumAlgorithm = checksumAlgorithm;
        this.checksum = checksum;
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

    public String getChecksum() {
        return checksum;
    }

    public String getChecksumAlgorithm() {
        return checksumAlgorithm;
    }

    public ResolvedUrl getResolved() {
        return resolved;
    }

    public RepositoryId getRepositoryId() {
        return repositoryId;
    }

    @Override
    public int hashCode() {
        return Objects.hash(groupId, artifactId, version, checksumAlgorithm, checksum);
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
                && Objects.equals(version, other.version)
                && Objects.equals(checksumAlgorithm, other.checksumAlgorithm)
                && Objects.equals(checksum, other.checksum);
    }
}
