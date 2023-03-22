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
import org.apache.maven.plugin.logging.SystemStreamLog;
import org.eclipse.aether.artifact.Artifact;

public class DependencyGraph {

    public static final String CHECKSUM_ALGORITHM = "SHA-256";
    private List<DependencyNode> graph;

    public List<DependencyNode> getRoots() {
        return graph.stream().filter(node -> node.getParent() == null).collect(Collectors.toList());
    }

    private DependencyGraph(List<DependencyNode> graph) {
        this.graph = graph;
    }

    public static DependencyGraph of(Graph<? extends Artifact> mavenDependencyGraph) {

        Map<Artifact, DependencyNode> nodes = new HashMap<>();
        for (var node : mavenDependencyGraph.nodes()) {
            var groupId = GroupId.of(node.getGroupId());
            var artifactId = ArtifactId.of(node.getArtifactId());
            var version = VersionNumber.of(node.getVersion());
            var checksum = calculateChecksum(node).orElse("");
            if (checksum.isBlank()) {
                new SystemStreamLog().warn("Could not calculate checksum for artifact " + node);
            }
            nodes.put(node, new DependencyNode(artifactId, groupId, version, CHECKSUM_ALGORITHM, checksum));
        }
        for (var edge : mavenDependencyGraph.edges()) {
            var source = edge.source();
            var target = edge.target();
            nodes.get(target).setParent(toNodeId(nodes.get(source)));
            nodes.get(source).addChild(nodes.get(target));
        }
        ArrayList<DependencyNode> roots = new ArrayList<>();
        roots.addAll(nodes.values());
        Collections.sort(roots);

        return new DependencyGraph(roots);
    }

    private static NodeId toNodeId(DependencyNode node) {
        return new NodeId(node.getGroupId(), node.getArtifactId(), node.getVersion());
    }

    private static Optional<String> calculateChecksum(Artifact artifact) {
        if (artifact.getFile() == null) {
            new SystemStreamLog().error("Artifact " + artifact + " has no file");
            return Optional.empty();
        }
        try {
            MessageDigest messageDigest = MessageDigest.getInstance(CHECKSUM_ALGORITHM);
            byte[] fileBuffer = Files.readAllBytes(artifact.getFile().toPath());
            byte[] artifactHash = messageDigest.digest(fileBuffer);
            return Optional.of(new BigInteger(1, artifactHash).toString(16));
        } catch (Exception e) {
            new SystemStreamLog().error("Could not calculate checksum for artifact " + artifact, e);
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
