package io.github.chains_project.maven_lockfile.graph;

import com.google.common.graph.Graph;
import io.github.chains_project.maven_lockfile.checksum.AbstractChecksumCalculator;
import io.github.chains_project.maven_lockfile.data.ArtifactId;
import io.github.chains_project.maven_lockfile.data.GroupId;
import io.github.chains_project.maven_lockfile.data.VersionNumber;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import org.apache.maven.artifact.Artifact;

public class DependencyGraph {

    public static final String CHECKSUM_ALGORITHM = "SHA-256";
    private final List<DependencyNode> graph;

    public List<DependencyNode> getRoots() {
        return graph.stream().filter(node -> node.getParent() == null).collect(Collectors.toList());
    }

    private DependencyGraph(List<DependencyNode> graph) {
        this.graph = graph == null ? List.of() : graph;
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

    public static DependencyGraph of(Graph<Artifact> graph, AbstractChecksumCalculator calc) {
        var roots = graph.nodes().stream()
                .filter(it -> graph.predecessors(it).isEmpty())
                .collect(Collectors.toList());
        List<DependencyNode> nodes = new ArrayList<>();
        for (var artifact : roots) {
            nodes.add(createDependencyNode(artifact, graph, calc));
        }
        // maven dependency tree contains the project itself as a root node. We remove it here.
        List<DependencyNode> dependencyRoots =
                nodes.stream().flatMap(v -> v.getChildren().stream()).collect(Collectors.toList());
        dependencyRoots.forEach(v -> v.setParent(null));
        return new DependencyGraph(dependencyRoots);
    }

    private static DependencyNode createDependencyNode(
            Artifact node, Graph<Artifact> graph, AbstractChecksumCalculator calc) {
        var groupId = GroupId.of(node.getGroupId());
        var artifactId = ArtifactId.of(node.getArtifactId());
        var version = VersionNumber.of(node.getVersion());
        var checksum = calc.calculateChecksum(node);

        DependencyNode value = new DependencyNode(artifactId, groupId, version, calc.getChecksumAlgorithm(), checksum);
        for (var artifact : graph.successors(node)) {
            value.addChild(createDependencyNode(artifact, graph, calc));
        }
        return value;
    }
}
