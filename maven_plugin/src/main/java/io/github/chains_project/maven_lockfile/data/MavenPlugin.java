package io.github.chains_project.maven_lockfile.data;

import io.github.chains_project.maven_lockfile.graph.DependencyNode;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;

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
    private final Set<DependencyNode> dependencies;

    public MavenPlugin(
            GroupId groupId,
            ArtifactId artifactId,
            VersionNumber version,
            ResolvedUrl resolvedUrl,
            RepositoryId repositoryId,
            String checksumAlgorithm,
            String checksum) {
        this(groupId, artifactId, version, resolvedUrl, repositoryId, checksumAlgorithm, checksum, null);
    }

    public MavenPlugin(
            GroupId groupId,
            ArtifactId artifactId,
            VersionNumber version,
            ResolvedUrl resolvedUrl,
            RepositoryId repositoryId,
            String checksumAlgorithm,
            String checksum,
            Set<DependencyNode> dependencies) {
        this.groupId = groupId;
        this.artifactId = artifactId;
        this.version = version;
        this.resolved = resolvedUrl;
        this.repositoryId = repositoryId;
        this.checksumAlgorithm = checksumAlgorithm;
        this.checksum = checksum;
        this.dependencies = dependencies == null ? Collections.emptySet() : dependencies;
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

    /**
     * @return the dependencies of this plugin
     */
    public Set<DependencyNode> getDependencies() {
        return dependencies;
    }

    @Override
    public int hashCode() {
        return Objects.hash(groupId, artifactId, version, checksumAlgorithm, checksum, dependencies);
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
                && Objects.equals(checksum, other.checksum)
                && Objects.equals(dependencies, other.dependencies);
    }

    @Override
    public String toString() {
        // only show the group id, artifact id, and version - this is sufficient for debugging purposes
        return "MavenPlugin [groupId=" + groupId + ", artifactId=" + artifactId + ", version=" + version + "]";
    }
}
