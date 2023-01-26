package io.github.chains_project.maven_lockfile.data;

import com.google.common.base.Preconditions;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * A single dependency that can be in a lock file.
 * It contains an artifact id, a group id, a version, the name of the algorithm used to calculate the checksum,
 * and the checksum itself.
 *
 */
public class LockFileDependency implements Comparable<LockFileDependency>{
    private final ArtifactId artifactId;
    private final GroupId groupId;
    private final VersionNumber version;
    private final String checksumAlgorithm;
    private final String checksum;
    private final String repoUrl;
    private final String scope;

    // @JsonAdapter(EmptyListToNullFactory.class)
    private final List<LockFileDependency> requires;


    public LockFileDependency(
            ArtifactId artifactId,
            GroupId groupId,
            VersionNumber version,
            String checksumAlgorithm,
            String checksum,
            String repoId,
            List<LockFileDependency> requires,
            String scope) {
        this.artifactId = Preconditions.checkNotNull(artifactId);
        this.groupId = Preconditions.checkNotNull(groupId);
        this.version = Preconditions.checkNotNull(version);
        this.checksumAlgorithm = checksumAlgorithm;
        this.checksum = checksum;
        this.repoUrl = repoId;
        Collections.sort(requires);
        this.requires = requires;
        this.scope = Preconditions.checkNotNull(scope);
    }

    public ArtifactId getArtifactId() {
        return this.artifactId;
    }

    public GroupId getGroupId() {
        return this.groupId;
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

    public String getRepoUrl() {
        return this.repoUrl;
    }

    public List<LockFileDependency> getRequires() {
        return requires;
    }

    @Override
    public String toString() {
        return "{" + " artifactId='"
                + getArtifactId() + "'" + ", groupId='"
                + getGroupId() + "'" + ", version='"
                + getVersion() + "'" + ", checksumAlgorithm='"
                + getChecksumAlgorithm() + "'" + ", checksum='"
                + getChecksum() + "'" + ", repoUrl='"
                + getRepoUrl() + "'" + ", requires='"
                + getRequires() + "'" + "}";
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) return true;
        if (!(o instanceof LockFileDependency)) {
            return false;
        }

        LockFileDependency lockFileDependency = (LockFileDependency) o;
        return Objects.equals(artifactId, lockFileDependency.artifactId)
                && Objects.equals(groupId, lockFileDependency.groupId)
                && Objects.equals(version, lockFileDependency.version)
                && Objects.equals(checksumAlgorithm, lockFileDependency.checksumAlgorithm)
                && Objects.equals(checksum, lockFileDependency.checksum)
                && Objects.equals(repoUrl, lockFileDependency.repoUrl)
                && Objects.equals(requires, lockFileDependency.requires);
    }

    @Override
    public int hashCode() {
        return Objects.hash(artifactId, groupId, version, checksumAlgorithm, checksum, repoUrl, requires);
    }

    @Override
    public int compareTo(LockFileDependency o) {
        int result = this.getGroupId().getValue().compareTo(o.getGroupId().getValue());
        if (result == 0) {
            result = this.getArtifactId().getValue().compareTo(o.getArtifactId().getValue());
            if (result == 0) {
                result = this.getVersion().getValue().compareTo(o.getVersion().getValue());
            }
        }
        return result;
    }
}
