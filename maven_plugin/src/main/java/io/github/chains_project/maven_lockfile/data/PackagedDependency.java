package io.github.chains_project.maven_lockfile.data;

import com.google.gson.annotations.SerializedName;
import java.util.Objects;

public class PackagedDependency {

    @SerializedName("groupID")
    private final GroupId groupId;

    @SerializedName("artifactID")
    private final ArtifactId artifactId;

    @SerializedName("version")
    private final VersionNumber version;

    private final String checksumAlgorithm;
    private final String checksum;

    public PackagedDependency(
            GroupId groupId, ArtifactId artifactId, VersionNumber version, String checksumAlgorithm, String checksum) {
        this.groupId = groupId;
        this.artifactId = artifactId;
        this.version = version;
        this.checksumAlgorithm = checksumAlgorithm;
        this.checksum = checksum;
    }

    public GroupId getGroupId() {
        return this.groupId;
    }

    public ArtifactId getArtifactId() {
        return this.artifactId;
    }

    public VersionNumber getVersion() {
        return this.version;
    }

    public String getChecksumAlgorithm() {
        return this.checksumAlgorithm;
    }

    public String getChecksum() {
        return this.checksum;
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) return true;
        if (!(o instanceof PackagedDependency)) {
            return false;
        }
        PackagedDependency packagedDependency = (PackagedDependency) o;
        return Objects.equals(groupId, packagedDependency.groupId)
                && Objects.equals(artifactId, packagedDependency.artifactId)
                && Objects.equals(version, packagedDependency.version)
                && Objects.equals(checksumAlgorithm, packagedDependency.checksumAlgorithm)
                && Objects.equals(checksum, packagedDependency.checksum);
    }

    @Override
    public int hashCode() {
        return Objects.hash(groupId, artifactId, version, checksumAlgorithm, checksum);
    }
}
