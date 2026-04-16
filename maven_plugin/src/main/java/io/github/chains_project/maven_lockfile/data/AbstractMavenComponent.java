package io.github.chains_project.maven_lockfile.data;

import io.github.chains_project.maven_lockfile.graph.DependencyNode;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;

/**
 * Base class for Maven components (plugins, extensions) that share common metadata structure.
 * Contains artifact coordinates, checksums, repository information, and dependencies.
 *
 * <p>Subclasses are compared first by their concrete type, then by GAV coordinates.
 * This ensures that a {@link MavenPlugin} and {@link MavenExtension} with the same
 * GAV are not considered equal, as they serve different roles in the build lifecycle.
 */
public abstract class AbstractMavenComponent implements Comparable<AbstractMavenComponent> {

    protected final GroupId groupId;
    protected final ArtifactId artifactId;
    protected final VersionNumber version;
    protected final String checksum;
    protected final String checksumAlgorithm;
    protected final ResolvedUrl resolved;
    protected final RepositoryId repositoryId;
    protected final Set<DependencyNode> dependencies;
    protected final Pom parent;

    protected AbstractMavenComponent(
            GroupId groupId,
            ArtifactId artifactId,
            VersionNumber version,
            String checksum,
            String checksumAlgorithm,
            ResolvedUrl resolved,
            RepositoryId repositoryId,
            Set<DependencyNode> dependencies, Pom parent) {
        this.groupId = groupId;
        this.artifactId = artifactId;
        this.version = version;
        this.checksum = checksum;
        this.checksumAlgorithm = checksumAlgorithm;
        this.resolved = resolved;
        this.repositoryId = repositoryId;
        this.dependencies = dependencies == null ? Collections.emptySet() : dependencies;
        this.parent = parent;
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

    public Set<DependencyNode> getDependencies() {
        return dependencies;
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                groupId, artifactId, version, checksum, checksumAlgorithm, resolved, repositoryId, dependencies, parent);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }

        if (obj == null || this.getClass() != obj.getClass()) {
            return false;
        }

        AbstractMavenComponent other = (AbstractMavenComponent) obj;
        return Objects.equals(groupId, other.groupId)
                && Objects.equals(artifactId, other.artifactId)
                && Objects.equals(version, other.version)
                && Objects.equals(checksum, other.checksum)
                && Objects.equals(checksumAlgorithm, other.checksumAlgorithm)
                && Objects.equals(resolved, other.resolved)
                && Objects.equals(repositoryId, other.repositoryId)
                && Objects.equals(dependencies, other.dependencies)
                && Objects.equals(parent, other.parent);
    }

    @Override
    public int compareTo(AbstractMavenComponent other) {
        if (this.getClass() != other.getClass()) {
            return this.getClass().getName().compareTo(other.getClass().getName());
        }

        int groupIdCompare = groupId.compareTo(other.groupId);
        if (groupIdCompare != 0) {
            return groupIdCompare;
        }
        int artifactIdCompare = artifactId.compareTo(other.artifactId);
        if (artifactIdCompare != 0) {
            return artifactIdCompare;
        }
        int versionCompare = version.compareTo(other.version);
        if (versionCompare != 0) {
            return versionCompare;
        }
        int checksumAlgorithmCompare = checksumAlgorithm.compareTo(other.checksumAlgorithm);
        if (checksumAlgorithmCompare != 0) {
            return checksumAlgorithmCompare;
        }
        return checksum.compareTo(other.checksum);
    }
}
