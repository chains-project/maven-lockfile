package io.github.chains_project.maven_lockfile.data;

public class Pom implements Comparable<Pom> {

    private final GroupId groupId;
    private final ArtifactId artifactId;
    private final VersionNumber version;
    private final String relativePath;
    private final ResolvedUrl resolved;
    private final RepositoryId repositoryId;
    private final String checksumAlgorithm;
    private final String checksum;
    private final Pom parent;

    public Pom(
            GroupId groupId,
            ArtifactId artifactId,
            VersionNumber version,
            String relativePath,
            ResolvedUrl resolved,
            RepositoryId repositoryId,
            String checksumAlgorithm,
            String checksum,
            Pom parent) {
        this.groupId = groupId;
        this.artifactId = artifactId;
        this.version = version;
        this.relativePath = relativePath;
        this.resolved = resolved;
        this.repositoryId = repositoryId;
        this.checksumAlgorithm = checksumAlgorithm;
        this.checksum = checksum;
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

    public String getRelativePath() {
        return relativePath;
    }

    public ResolvedUrl getResolved() {
        return resolved;
    }

    public RepositoryId getRepositoryId() {
        return repositoryId;
    }

    public String getChecksumAlgorithm() {
        return checksumAlgorithm;
    }

    public String getChecksum() {
        return checksum;
    }

    public Pom getParent() {
        return parent;
    }

    @Override
    public int compareTo(Pom o) {
        if (this.groupId.compareTo(o.groupId) != 0) {
            return this.groupId.compareTo(o.groupId);
        }

        if (this.artifactId.compareTo(o.artifactId) != 0) {
            return this.artifactId.compareTo(o.artifactId);
        }

        if (this.version.compareTo(o.version) != 0) {
            return this.version.compareTo(o.version);
        }

        String pathCmp = this.relativePath == null ? "" : this.relativePath;
        String oPathCmp = o.relativePath == null ? "" : o.relativePath;

        if (pathCmp.compareTo(oPathCmp) != 0) {
            return pathCmp.compareTo(oPathCmp);
        }

        ResolvedUrl resolvedCmp = this.resolved == null ? ResolvedUrl.Unresolved() : this.resolved;
        ResolvedUrl oResolvedCmp = o.resolved == null ? ResolvedUrl.Unresolved() : o.resolved;

        if (resolvedCmp.compareTo(oResolvedCmp) != 0) {
            return resolvedCmp.compareTo(oResolvedCmp);
        }

        RepositoryId repoIdCmp = this.repositoryId == null ? RepositoryId.None() : this.repositoryId;
        RepositoryId oRepoIdCmp = o.repositoryId == null ? RepositoryId.None() : o.repositoryId;

        if (repoIdCmp.compareTo(oRepoIdCmp) != 0) {
            return repoIdCmp.compareTo(oRepoIdCmp);
        }

        if (this.checksumAlgorithm.compareTo(o.checksumAlgorithm) != 0) {
            return this.checksumAlgorithm.compareTo(o.checksumAlgorithm);
        }

        if (this.checksum.compareTo(o.checksum) != 0) {
            return this.checksum.compareTo(o.checksum);
        }

        if (this.parent == null && o.parent != null) {
            return -1;
        }

        if (this.parent != null && o.parent == null) {
            return 1;
        }

        if (this.parent != null && o.parent != null && this.parent.compareTo(o.parent) != 0) {
            return this.parent.compareTo(o.parent);
        }

        return 0;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof Pom)) {
            return false;
        }
        Pom other = (Pom) obj;
        String pathCmp = this.relativePath == null ? "" : this.relativePath;
        String otherPathCmp = other.relativePath == null ? "" : other.relativePath;

        ResolvedUrl resolvedCmp = this.resolved == null ? ResolvedUrl.Unresolved() : this.resolved;
        ResolvedUrl otherResolvedCmp = other.resolved == null ? ResolvedUrl.Unresolved() : other.resolved;

        RepositoryId repoIdCmp = this.repositoryId == null ? RepositoryId.None() : this.repositoryId;
        RepositoryId otherRepoIdCmp = other.repositoryId == null ? RepositoryId.None() : other.repositoryId;

        boolean parentEqual = (this.parent == null && other.parent == null)
                || (this.parent != null && other.parent != null && this.parent.equals(other.parent));

        return this.groupId.equals(other.groupId)
                && this.artifactId.equals(other.artifactId)
                && this.version.equals(other.version)
                && pathCmp.equals(otherPathCmp)
                && resolvedCmp.equals(otherResolvedCmp)
                && repoIdCmp.equals(otherRepoIdCmp)
                && this.checksumAlgorithm.equals(other.checksumAlgorithm)
                && this.checksum.equals(other.checksum)
                && parentEqual;
    }
}
