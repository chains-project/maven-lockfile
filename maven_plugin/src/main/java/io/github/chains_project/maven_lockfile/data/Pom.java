package io.github.chains_project.maven_lockfile.data;

public class Pom implements Comparable<Pom> {

    private final GroupId groupId;
    private final ArtifactId artifactId;
    private final VersionNumber version;
    private final String relativePath;
    private final String checksumAlgorithm;
    private final String checksum;
    private final Pom parent;

    public Pom(
            GroupId groupId,
            ArtifactId artifactId,
            VersionNumber version,
            String relativePath,
            String checksumAlgorithm,
            String checksum,
            Pom parent) {
        this.groupId = groupId;
        this.artifactId = artifactId;
        this.version = version;
        this.relativePath = relativePath;
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

        if (this.parent != null && o.parent != null) {
            if (this.parent.compareTo(o.parent) != 0) {
                return this.parent.compareTo(o.parent);
            }
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
        return this.groupId.equals(other.groupId)
                && this.artifactId.equals(other.artifactId)
                && this.version.equals(other.version)
                && ((this.relativePath == null && other.relativePath == null)
                        || this.relativePath.equals(other.relativePath))
                && this.checksumAlgorithm.equals(other.checksumAlgorithm)
                && this.checksum.equals(other.checksum)
                && ((this.parent == null && other.parent == null) || this.parent.equals(other.parent));
    }
}
