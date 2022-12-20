package se.kth;

public class LockFileDependency {
    public final String artifactId;
    public final String  groupId;
    public final String version;
    public final String checksumAlgorithm;
    public final String checksum;

    public LockFileDependency(String artifactId, String groupId, String version, String checksumAlgorithm, String checksum) {
        this.artifactId = artifactId;
        this.groupId = groupId;
        this.version = version;
        this.checksumAlgorithm = checksumAlgorithm;
        this.checksum = checksum;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }

        if (obj.getClass() != this.getClass()) {
            return false;
        }

        final LockFileDependency other = (LockFileDependency) obj;

        return this.artifactId.equals(other.artifactId) &&
               this.groupId.equals(other.groupId) &&
               this.version.equals(other.version) &&
               this.checksumAlgorithm.equals(other.checksumAlgorithm) &&
               this.checksum.equals(other.checksum);
    }

    @Override
    public int hashCode() {
        return artifactId.hashCode() +
               groupId.hashCode() +
               version.hashCode() +
               checksumAlgorithm.hashCode() +
               checksum.hashCode();
    }
}
