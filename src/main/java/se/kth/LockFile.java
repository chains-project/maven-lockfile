package se.kth;

import com.google.gson.Gson;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class LockFile {
    public List<LockFileDependency> dependencies = new ArrayList<>();

    public static LockFile readLockFile(Path lockFilePath) throws IOException {
        String lockFileContents = Files.readString(lockFilePath);
        Gson gson = new Gson();
        return gson.fromJson(lockFileContents, LockFile.class);
    }

    public boolean isEquivalentTo(LockFile other) {
        return differenceTo(other).isEmpty() && other.differenceTo(this).isEmpty();
    }

    public Set<LockFileDependency> differenceTo(LockFile other) {
        Set<LockFileDependency> thisSet = new HashSet<>(dependencies);
        Set<LockFileDependency> otherSet = Set.copyOf(other.dependencies);
        thisSet.removeAll(otherSet);
        return thisSet;
    }
}
