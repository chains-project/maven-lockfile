package io.github.chains_project.maven_lockfile.data;

import com.google.gson.annotations.SerializedName;
import io.github.chains_project.maven_lockfile.JsonUtils;
import io.github.chains_project.maven_lockfile.graph.DependencyNode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

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

    private List<DependencyNode> dependencies;

    private List<MavenPlugin> mavenPlugins;

    public LockFile(
            GroupId groupId,
            ArtifactId name,
            VersionNumber versionNumber,
            List<DependencyNode> dependencies,
            List<MavenPlugin> mavenPlugins) {
        this.dependencies = dependencies == null ? Collections.emptyList() : dependencies;
        this.name = name;
        this.version = versionNumber;
        this.groupId = groupId;
        this.mavenPlugins = mavenPlugins == null ? Collections.emptyList() : mavenPlugins;
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
     * @return the dependencies
     */
    public List<DependencyNode> getDependencies() {
        return nullToEmpty(dependencies);
    }

    /**
     * @return the mavenPlugins
     */
    public List<MavenPlugin> getMavenPlugins() {
        return nullToEmpty(mavenPlugins);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, groupId, version, lockfileVersion, dependencies, nullToEmpty(mavenPlugins));
    }

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
                && Objects.equals(groupId, other.groupId)
                && Objects.equals(version, other.version)
                && lockfileVersion == other.lockfileVersion
                && Objects.equals(dependencies, other.dependencies)
                && Objects.equals(nullToEmpty(mavenPlugins), nullToEmpty(other.mavenPlugins));
    }

    private static <T> List<T> nullToEmpty(List<T> list) {
        return list == null ? Collections.emptyList() : list;
    }
}
