package io.github.chains_project.maven_lockfile.graph;

import com.google.common.graph.Graph;
import io.github.chains_project.maven_lockfile.data.ArtifactId;
import io.github.chains_project.maven_lockfile.data.GroupId;
import io.github.chains_project.maven_lockfile.data.VersionNumber;
import java.math.BigInteger;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import org.apache.log4j.Logger;
import org.apache.maven.artifact.Artifact;

public class DependencyGraph {

    private static final Logger LOGGER = Logger.getLogger(DependencyGraph.class);

    public static final String CHECKSUM_ALGORITHM = "SHA-256";
    private List<DependencyNode> graph;

    public List<DependencyNode> getRoots() {
        return graph.stream().filter(node -> node.getParent() == null).collect(Collectors.toList());
    }

    private DependencyGraph(List<DependencyNode> graph) {
        this.graph = graph;
    }

    /**
     * @return the graph
     */
    public List<DependencyNode> getGraph() {
        return graph;
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) return true;
        if (!(o instanceof DependencyGraph)) {
            return false;
        }
        DependencyGraph dependencyGraph = (DependencyGraph) o;
        return Objects.equals(graph, dependencyGraph.graph);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(graph);
    }

    public Optional<DependencyNode> getParentForNode(DependencyNode node) {
        return graph.stream().filter(n -> n.id.equals(node.getParent())).findFirst();
    }

    public static DependencyGraph of(Graph<Artifact> graph) {
        var roots = graph.nodes().stream()
                .filter(it -> graph.predecessors(it).isEmpty())
                .collect(Collectors.toList());
        List<DependencyNode> nodes = new ArrayList<>();
        for (var artifact : roots) {
            nodes.add(createDependencyNode(artifact, graph));
        }
        // maven dependency tree contains the project itself as a root node. We remove it here.
        List<DependencyNode> dependencyRoots =
                nodes.stream().flatMap(v -> v.getChildren().stream()).collect(Collectors.toList());
        dependencyRoots.forEach(v -> v.setParent(null));
        return new DependencyGraph(dependencyRoots);
    }

    private static DependencyNode createDependencyNode(Artifact node, Graph<Artifact> graph) {
        var groupId = GroupId.of(node.getGroupId());
        var artifactId = ArtifactId.of(node.getArtifactId());
        var version = VersionNumber.of(node.getVersion());
        var checksum = calculateChecksum(node).orElse("");
        if (checksum.isBlank()) {
            LOGGER.warn("Could not calculate checksum for artifact " + node);
        }
        DependencyNode value = new DependencyNode(artifactId, groupId, version, CHECKSUM_ALGORITHM, checksum);
        for (var artifact : graph.successors(node)) {
            value.addChild(createDependencyNode(artifact, graph));
        }
        return value;
    }

    private static Optional<String> calculateChecksum(Artifact artifact) {
        if (artifact.getFile() == null) {
            LOGGER.error("Artifact " + artifact + " has no file");
            return Optional.empty();
        }
        try {
            MessageDigest messageDigest = MessageDigest.getInstance(CHECKSUM_ALGORITHM);
            byte[] fileBuffer = Files.readAllBytes(artifact.getFile().toPath());
            byte[] artifactHash = messageDigest.digest(fileBuffer);
            return Optional.of(new BigInteger(1, artifactHash).toString(16));
        } catch (Exception e) {
            LOGGER.error("Could not calculate checksum for artifact " + artifact, e);
            return Optional.empty();
        }
    }
}
