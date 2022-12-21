package se.kth;

import com.google.gson.Gson;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A lock file that contains checksums for dependencies.
 * It can be serialised with Gson to JSON, which is the format used for the lock file on disk.
 *
 * @author Arvid Siberov
 */
public class LockFile {
    public List<LockFileDependency> dependencies = new ArrayList<>();

    /**
     * Create a lock file object from a serialized JSON string.
     * @param lockFilePath the path to the lock file
     * @return a lock file object
     * @throws IOException if the lock file could not be read
     */
    public static LockFile readLockFile(Path lockFilePath) throws IOException {
        String lockFileContents = Files.readString(lockFilePath);
        Gson gson = new Gson();
        return gson.fromJson(lockFileContents, LockFile.class);
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
        Set<LockFileDependency> otherSet = Set.copyOf(other.dependencies);
        thisSet.removeAll(otherSet);
        return thisSet;
    }
}
