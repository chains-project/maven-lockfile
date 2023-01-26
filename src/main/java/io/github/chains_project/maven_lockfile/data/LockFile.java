package io.github.chains_project.maven_lockfile.data;

import com.google.gson.annotations.SerializedName;
import io.github.chains_project.maven_lockfile.JsonUtils;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * A lock file contains a list of dependencies, and the version of the lock file format.
 * It also contains the name and version of the project that the lock file belongs to.
 *
 */
public class LockFile {

    @SerializedName("artifactID")
    private final ArtifactId name;

    @SerializedName("groupID")
    private final GroupId groupId;

    @SerializedName("version")
    private final VersionNumber version;

    @SerializedName("lockFileVersion")
    private int lockfileVersion = 1; // TODO: we normally should create an enum with Name -> Numbers

    private List<LockFileDependency> dependencies;

    public LockFile(
            GroupId groupId, ArtifactId name, VersionNumber versionNumber, List<LockFileDependency> dependencies) {
        this.dependencies = dependencies;
        this.name = name;
        this.version = versionNumber;
        this.groupId = groupId;
    }
    /**
     * Create a lock file object from a serialized JSON string.
     * @param lockFilePath the path to the lock file
     * @return a lock file object
     * @throws IOException if the lock file could not be read
     */
    public static LockFile readLockFile(Path lockFilePath) throws IOException {
        String lockFileContents = Files.readString(lockFilePath);
        return JsonUtils.fromJson(lockFileContents, LockFile.class);
    }

    /**
     * Returns true if the lock file contains the same dependencies as the given set of dependencies,
     * and all the checksums match.
     * @param other the lockfile to compare with
     * @return true if the lock file is equivalent
     */
    public boolean isEquivalentTo(LockFile other) {
        return differenceTo(other).isEmpty() && other.differenceTo(this).isEmpty();
    }

    /**
     * Returns a set of dependencies that are in this lock file but not in the other lock file.
     * These could be either completely new dependencies, or dependencies that have different checksums.
     * @param other the lock file to compare with
     * @return a set of dependencies that are in this lock file but not in the other lock file
     */
    public Set<LockFileDependency> differenceTo(LockFile other) {
        Set<LockFileDependency> thisSet = new HashSet<>(dependencies);
        Set<LockFileDependency> otherSet = Set.copyOf(other.getDependencies());
        thisSet.removeAll(otherSet);
        return thisSet;
    }

    /**
     * @return the dependencies
     */
    public List<LockFileDependency> getDependencies() {
        return dependencies;
    }
    /** (non-Javadoc)
     * @see Object#hashCode()
     */
    @Override
    public int hashCode() {
        return Objects.hash(name, version, lockfileVersion, dependencies);
    }
    /** (non-Javadoc)
     * @see Object#equals(Object)
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof LockFile)) {
            return false;
        }
        LockFile other = (LockFile) obj;
        return Objects.equals(name, other.name)
                && Objects.equals(version, other.version)
                && lockfileVersion == other.lockfileVersion
                && Objects.equals(dependencies, other.dependencies);
    }
}
