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
}
