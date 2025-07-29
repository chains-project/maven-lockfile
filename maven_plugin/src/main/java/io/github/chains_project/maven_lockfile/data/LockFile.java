package io.github.chains_project.maven_lockfile.data;

import com.google.gson.annotations.SerializedName;
import io.github.chains_project.maven_lockfile.JsonUtils;
import io.github.chains_project.maven_lockfile.graph.DependencyNode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;

/**
 * A lock file contains a list of dependencies, and the version of the lock file format.
 * It also contains the name and version of the project that the lock file belongs to.
 *
 */
public class LockFile {

    @SerializedName(
            value = "artifactId",
            alternate = {"artifactID"})
    private final ArtifactId name;

    @SerializedName(
            value = "groupId",
            alternate = {"groupID"})
    private final GroupId groupId;

    @SerializedName("version")
    private final VersionNumber version;

    @SerializedName("pom")
    private final Pom pom;

    @SerializedName("lockFileVersion")
    @SuppressWarnings("FieldMayBeFinal")
    private int lockfileVersion = 1; // TODO: we normally should create an enum with Name -> Numbers

    private final Set<DependencyNode> dependencies;

    private final Set<MavenPlugin> mavenPlugins;

    private final MetaData metaData;

    public LockFile(
            GroupId groupId,
            ArtifactId name,
            VersionNumber versionNumber,
            Pom pom,
            Set<DependencyNode> dependencies,
            Set<MavenPlugin> mavenPlugins,
            MetaData metaData) {
        this.groupId = groupId;
        this.name = name;
        this.version = versionNumber;
        this.pom = pom;
        this.dependencies = dependencies == null ? Collections.emptySet() : dependencies;
        this.mavenPlugins = mavenPlugins == null ? Collections.emptySet() : mavenPlugins;
        this.metaData = metaData;
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
    public Set<DependencyNode> getDependencies() {
        return nullToEmpty(dependencies);
    }
    /**
     * @return the groupId
     */
    public GroupId getGroupId() {
        return groupId;
    }
    /**
     * @return the name
     */
    public ArtifactId getName() {
        return name;
    }
    /**
     * @return the version
     */
    public VersionNumber getVersion() {
        return version;
    }

    /**
     * @return the link to pom file and checksum
     */
    public Pom getPom() {
        return pom;
    }

    /**
     * @return the mavenPlugins
     */
    public Set<MavenPlugin> getMavenPlugins() {
        return nullToEmpty(mavenPlugins);
    }
    /**
     * @return the metadata about the environment in which the lock file was generated
     */
    public Environment getEnvironment() {
        return metaData.getEnvironment();
    }

    /**
     * @return the config
     */
    public Config getConfig() {
        return metaData.getConfig();
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, groupId, version, lockfileVersion, pom, dependencies, nullToEmpty(mavenPlugins));
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
                && Objects.equals(nullToEmpty(dependencies), nullToEmpty(other.dependencies))
                && Objects.equals(nullToEmpty(mavenPlugins), nullToEmpty(other.mavenPlugins));
    }

    private static <T> Set<T> nullToEmpty(Set<T> set) {
        return set == null ? Collections.emptySet() : set;
    }
}
