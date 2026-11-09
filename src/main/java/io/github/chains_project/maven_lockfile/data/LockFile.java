package io.github.chains_project.maven_lockfile.data;

import com.google.gson.annotations.SerializedName;
import io.github.chains_project.maven_lockfile.JsonUtils;
import io.github.chains_project.maven_lockfile.graph.DependencyNode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
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
     * Artifacts whose occurrences disagree on {@code checksumAlgorithm}, {@code checksum}, {@code resolved} or
     * {@code repositoryId}. Each artifact must have exactly one checksum and one source, so the lockfile pins one
     * file and one place to fetch it from; a missing value differs from any present one. Covers the project POM,
     * BOMs, parent POMs, dependencies, and plugins and extensions with their dependencies. A plugin or extension is
     * the same artifact as a JAR dependency with its coordinates, and a POM the same as a dependency of type pom.
     *
     * @return one {@code groupId:artifactId:version:classifier:type attribute [values]} line per attribute that
     *     differs, where {@code <none>} stands for a missing value
     */
    public List<String> conflictingArtifacts() {
        Map<String, Map<String, Set<String>>> attributesByArtifact = new TreeMap<>();
        if (pom != null) {
            addPom(attributesByArtifact, pom);
        }
        getBoms().forEach(bom -> addPom(attributesByArtifact, bom));
        getDependencies().forEach(node -> addNode(attributesByArtifact, node));
        Stream.concat(getMavenPlugins().stream(), getMavenExtensions().stream()).forEach(component -> {
            add(
                    attributesByArtifact,
                    coordinates(component.getGroupId(), component.getArtifactId(), component.getVersion(), "", "jar"),
                    component.getChecksumAlgorithm(),
                    component.getChecksum(),
                    component.getResolved(),
                    component.getRepositoryId());
            if (component.getParentPom() != null) {
                addPom(attributesByArtifact, component.getParentPom());
            }
            component.getDependencies().forEach(node -> addNode(attributesByArtifact, node));
        });
        List<String> conflicts = new ArrayList<>();
        attributesByArtifact.forEach((artifact, attributes) -> attributes.forEach((attribute, values) -> {
            if (values.size() > 1) {
                conflicts.add(artifact + " " + attribute + " " + values);
            }
        }));
        return conflicts;
    }

    private static final String NO_VALUE = "<none>";

    // TODO: merge addNode and addPom once DependencyNode, Pom and AbstractMavenComponent share a unified artifact
    // model; they differ only in the coordinates and nested artifacts each class exposes.
    private static void addNode(Map<String, Map<String, Set<String>>> attributesByArtifact, DependencyNode node) {
        add(
                attributesByArtifact,
                coordinates(
                        node.getGroupId(),
                        node.getArtifactId(),
                        node.getVersion(),
                        node.getClassifier() == null ? "" : node.getClassifier().getValue(),
                        node.getType() == null ? "jar" : node.getType().getValue()),
                node.getChecksumAlgorithm(),
                node.getChecksum(),
                node.getResolved(),
                node.getRepositoryId());
        if (node.getParentPom() != null) {
            addPom(attributesByArtifact, node.getParentPom());
        }
        if (node.getBoms() != null) {
            node.getBoms().forEach(bom -> addPom(attributesByArtifact, bom));
        }
        node.getChildren().forEach(child -> addNode(attributesByArtifact, child));
    }

    private static void addPom(Map<String, Map<String, Set<String>>> attributesByArtifact, Pom pom) {
        add(
                attributesByArtifact,
                coordinates(pom.getGroupId(), pom.getArtifactId(), pom.getVersion(), "", "pom"),
                pom.getChecksumAlgorithm(),
                pom.getChecksum(),
                pom.getResolved(),
                pom.getRepositoryId());
        if (pom.getParent() != null) {
            addPom(attributesByArtifact, pom.getParent());
        }
        if (pom.getBoms() != null) {
            pom.getBoms().forEach(bom -> addPom(attributesByArtifact, bom));
        }
    }

    private static String coordinates(
            GroupId groupId, ArtifactId artifactId, VersionNumber version, String classifier, String type) {
        return groupId.getValue() + ":" + artifactId.getValue() + ":" + version.getValue() + ":" + classifier + ":"
                + type;
    }

    private static void add(
            Map<String, Map<String, Set<String>>> attributesByArtifact,
            String artifact,
            String checksumAlgorithm,
            String checksum,
            ResolvedUrl resolved,
            RepositoryId repositoryId) {
        // Insertion order lists the attributes in lockfile order in the report.
        Map<String, Set<String>> attributes =
                attributesByArtifact.computeIfAbsent(artifact, ignored -> new LinkedHashMap<>());
        addValue(attributes, "checksumAlgorithm", checksumAlgorithm);
        addValue(attributes, "checksum", checksum);
        addValue(attributes, "resolved", resolved == null ? null : resolved.getValue());
        addValue(attributes, "repositoryId", repositoryId == null ? null : repositoryId.getValue());
    }

    private static void addValue(Map<String, Set<String>> attributes, String attribute, String value) {
        attributes
                .computeIfAbsent(attribute, ignored -> new TreeSet<>())
                .add(value == null || value.isEmpty() ? NO_VALUE : value);
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
