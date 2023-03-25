package io.github.chains_project.maven_lockfile.graph;

import com.google.common.graph.Graph;
import io.github.chains_project.maven_lockfile.data.ArtifactId;
import io.github.chains_project.maven_lockfile.data.GroupId;
import io.github.chains_project.maven_lockfile.data.VersionNumber;
import java.math.BigInteger;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import org.apache.log4j.Logger;
import org.eclipse.aether.artifact.Artifact;

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

    public static DependencyGraph of(List<Graph<Artifact>> mavenDependencyGraph) {
        ArrayList<DependencyNode> roots = new ArrayList<>();
        LOGGER.debug(String.format(
                "Creating dependency graph from Maven dependency graph with %s subgraphs",
                mavenDependencyGraph.size()));
        for (Graph<Artifact> graph : mavenDependencyGraph) {
            LOGGER.debug(String.format(
                    "Creating dependency graph from Maven dependency graph with %s nodes and %s edges",
                    graph.nodes().size(), graph.edges().size()));
            Map<Artifact, DependencyNode> nodes = new HashMap<>();
            for (var node : graph.nodes()) {
                var groupId = GroupId.of(node.getGroupId());
                var artifactId = ArtifactId.of(node.getArtifactId());
                var version = VersionNumber.of(node.getVersion());
                var checksum = calculateChecksum(node).orElse("");
                if (checksum.isBlank()) {
                    LOGGER.warn("Could not calculate checksum for artifact " + node);
                }
                DependencyNode value = new DependencyNode(artifactId, groupId, version, CHECKSUM_ALGORITHM, checksum);
                if (graph.predecessors(node).isEmpty()) {
                    LOGGER.debug("Found root node: " + node);
                    roots.add(value);
                }
                nodes.put(node, value);
            }
            for (var edge : graph.edges()) {
                var source = edge.source();
                var target = edge.target();
                nodes.get(source).addChild(nodes.get(target));
            }
        }
        Collections.sort(roots);
        return new DependencyGraph(roots);
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
}
