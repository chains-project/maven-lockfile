package io.github.chains_project.maven_lockfile.data;

import com.google.common.base.Preconditions;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A single dependency that can be in a lock file.
 * It contains an artifact id, a group id, a version, the name of the algorithm used to calculate the checksum,
 * and the checksum itself.
 *
 * @author Arvid Siberov
 */
public class LockFileDependency {
    private final ArtifactId artifactId;
    private final GroupId groupId;
    private final VersionNumber version;
    private final String checksumAlgorithm;
    private final String checksum;
    private final String repoUrl;

    // @JsonAdapter(EmptyListToNullFactory.class)
    private final List<LockFileDependency> requires;

    public LockFileDependency(
            ArtifactId artifactId,
            GroupId groupId,
            VersionNumber version,
            String checksumAlgorithm,
            String checksum,
            String repoId) {
        this.artifactId = Preconditions.checkNotNull(artifactId);
        this.groupId = Preconditions.checkNotNull(groupId);
        this.version = Preconditions.checkNotNull(version);
        this.checksumAlgorithm = checksumAlgorithm;
        this.checksum = checksum;
        this.repoUrl = repoId;
        this.requires = new ArrayList<>();
    }

    public LockFileDependency(
            ArtifactId artifactId,
            GroupId groupId,
            VersionNumber version,
            String checksumAlgorithm,
            String checksum,
            String repoId,
            List<LockFileDependency> requires) {
        this.artifactId = Preconditions.checkNotNull(artifactId);
        this.groupId = Preconditions.checkNotNull(groupId);
        this.version = Preconditions.checkNotNull(version);
        this.checksumAlgorithm = checksumAlgorithm;
        this.checksum = checksum;
        this.repoUrl = repoId;
        this.requires = requires;
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
        if (!Objects.equals(artifactId, lockFileDependency.artifactId)) {
            System.out.println("Du bist zu dumm für artifaktid");
        }
        if (!Objects.equals(groupId, lockFileDependency.groupId)) {
            System.out.println("Du bist zu dumm für groupid");
        }
        if (!Objects.equals(version, lockFileDependency.version)) {
            System.out.println("Du bist zu dumm für version");
        }
        if (!Objects.equals(checksumAlgorithm, lockFileDependency.checksumAlgorithm)) {
            System.out.println("Du bist zu dumm für checksumAlgorithm");
        }
        if (!Objects.equals(checksum, lockFileDependency.checksum)) {
            System.out.println("Du bist zu dumm für checksum");
        }
        if (!Objects.equals(repoUrl, lockFileDependency.repoUrl)) {
            System.out.println("Du bist zu dumm für repoUrl");
        }
        if (!Objects.equals(requires, lockFileDependency.requires)) {
            System.out.println("Du bist zu dumm für requires");
        }
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
}
