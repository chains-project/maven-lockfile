package io.github.chains_project.maven_lockfile.data;

import com.google.gson.annotations.SerializedName;
import io.github.chains_project.maven_lockfile.JsonUtils;
import io.github.chains_project.maven_lockfile.graph.DependencyNode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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

    private final Set<MavenExtension> mavenExtensions;

    private final MetaData metaData;

    private final Set<Pom> boms;

    public LockFile(
            GroupId groupId,
            ArtifactId name,
            VersionNumber versionNumber,
            Pom pom,
            Set<DependencyNode> dependencies,
            Set<MavenPlugin> mavenPlugins,
            Set<MavenExtension> mavenExtensions,
            MetaData metaData,
            Set<Pom> boms) {
        this.groupId = groupId;
        this.name = name;
        this.version = versionNumber;
        this.pom = pom;
        this.dependencies = dependencies == null ? Collections.emptySet() : dependencies;
        this.mavenPlugins = mavenPlugins == null ? Collections.emptySet() : mavenPlugins;
        this.mavenExtensions = mavenExtensions == null ? Collections.emptySet() : mavenExtensions;
        this.metaData = metaData;
        this.boms = boms;
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
     * Artifacts that occur more than once with different checksums, so the lockfile cannot pin which file is used.
     * Covers the dependencies, plugins and extensions with their dependencies, and skips occurrences without a
     * checksum. Other fields such as {@code resolved} may legitimately differ for the same file.
     *
     * @return one {@code groupId:artifactId:version:classifier:type [checksums]} line per conflicting artifact
     */
    public List<String> conflictingChecksums() {
        Map<String, Set<String>> checksums = new TreeMap<>();
        Stream.concat(mavenPlugins.stream(), mavenExtensions.stream()).forEach(component -> {
            String coordinates = component.getGroupId().getValue() + ":"
                    + component.getArtifactId().getValue() + ":"
                    + component.getVersion().getValue() + "::jar";
            addChecksum(checksums, coordinates, component.getChecksum());
            component.getDependencies().forEach(node -> addChecksums(checksums, node));
        });
        dependencies.forEach(node -> addChecksums(checksums, node));
        return checksums.entrySet().stream()
                .filter(entry -> entry.getValue().size() > 1)
                .map(entry -> entry.getKey() + " " + entry.getValue())
                .collect(Collectors.toList());
    }

    private static void addChecksums(Map<String, Set<String>> checksums, DependencyNode node) {
        String coordinates = node.getGroupId().getValue() + ":"
                + node.getArtifactId().getValue() + ":"
                + node.getVersion().getValue() + ":"
                + (node.getClassifier() == null ? "" : node.getClassifier().getValue()) + ":"
                + (node.getType() == null ? "jar" : node.getType().getValue());
        addChecksum(checksums, coordinates, node.getChecksum());
        node.getChildren().forEach(child -> addChecksums(checksums, child));
    }

    private static void addChecksum(Map<String, Set<String>> checksums, String coordinates, String checksum) {
        if (checksum != null && !checksum.isEmpty()) {
            checksums.computeIfAbsent(coordinates, ignored -> new TreeSet<>()).add(checksum);
        }
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
     * @return the bom poms
     */
    public Set<Pom> getBoms() {
        return nullToEmpty(boms);
    }

    /**
     * @return the mavenExtensions
     */
    public Set<MavenExtension> getMavenExtensions() {
        return nullToEmpty(mavenExtensions);
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
        return Objects.hash(
                name,
                groupId,
                version,
                lockfileVersion,
                pom,
                dependencies,
                nullToEmpty(mavenPlugins),
                nullToEmpty(mavenExtensions),
                nullToEmpty(boms));
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
                && Objects.equals(nullToEmpty(mavenPlugins), nullToEmpty(other.mavenPlugins))
                && Objects.equals(nullToEmpty(mavenExtensions), nullToEmpty(other.mavenExtensions))
                && Objects.equals(nullToEmpty(boms), nullToEmpty(other.boms));
    }

    private static <T> Set<T> nullToEmpty(Set<T> set) {
        return set == null ? Collections.emptySet() : set;
    }
}
