package se.kth;

/**
 * A single dependency that can be in a lock file.
 * It contains an artifact id, a group id, a version, the name of the algorithm used to calculate the checksum,
 * and the checksum itself.
 *
 * @author Arvid Siberov
 */
public class LockFileDependency {
    public final String artifactId;
    public final String  groupId;
    public final String version;
    public final String checksumAlgorithm;
    public final String checksum;

    /**
     * Create a new lock file dependency.
     * @param artifactId the artifact id of the dependency, e.g. "com.google.code.gson"
     * @param groupId the group id of the dependency, e.g. "gson"
     * @param version the version of the dependency, e.g. "2.10"
     * @param checksumAlgorithm the name of the algorithm used to calculate the checksum, e.g. "SHA-256"
     * @param checksum the hexadecimal representation of the checksum of the dependency, e.g. "cdd163ce3598a20fc04eee71b140b24f6f2a3b35f0a499dbbdd9852e83fbfaf"
     */
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
