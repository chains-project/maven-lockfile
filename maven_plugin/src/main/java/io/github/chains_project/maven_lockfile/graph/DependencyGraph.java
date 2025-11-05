package io.github.chains_project.maven_lockfile.graph;

import com.google.common.graph.Graph;
import com.google.common.graph.MutableGraph;
import io.github.chains_project.maven_lockfile.checksum.AbstractChecksumCalculator;
import io.github.chains_project.maven_lockfile.checksum.RepositoryInformation;
import io.github.chains_project.maven_lockfile.data.ArtifactId;
import io.github.chains_project.maven_lockfile.data.Classifier;
import io.github.chains_project.maven_lockfile.data.GroupId;
import io.github.chains_project.maven_lockfile.data.MavenScope;
import io.github.chains_project.maven_lockfile.data.VersionNumber;
import java.util.*;
import java.util.stream.Collectors;
import org.apache.maven.shared.dependency.graph.internal.SpyingDependencyNodeUtils;

public class DependencyGraph {

    private final Set<DependencyNode> graph;

    public Set<DependencyNode> getRoots() {
        return graph.stream()
                .filter(node -> node.getParent() == null)
                .collect(Collectors.toCollection(
                        () -> new TreeSet<>(Comparator.comparing(DependencyNode::getComparatorString))));
    }

    private DependencyGraph(Set<DependencyNode> graph) {
        this.graph = graph == null ? Set.of() : graph;
    }

    /**
     * @return the graph
     */
    public Set<DependencyNode> getGraph() {
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

    public static DependencyGraph of(
            MutableGraph<org.apache.maven.shared.dependency.graph.DependencyNode> graph,
            AbstractChecksumCalculator calc,
            boolean reduced) {
        var roots = graph.nodes().stream()
                .filter(it -> graph.predecessors(it).isEmpty())
                .collect(Collectors.toList());
        Set<DependencyNode> nodes = new TreeSet<>(Comparator.comparing(DependencyNode::getComparatorString));
        for (var artifact : roots) {
            createDependencyNode(artifact, graph, calc, true, reduced).ifPresent(nodes::add);
        }
        // maven dependency tree contains the project itself as a root node. We remove it here.
        Set<DependencyNode> dependencyRoots = nodes.stream()
                .flatMap(v -> v.getChildren().stream())
                .collect(Collectors.toCollection(
                        () -> new TreeSet<>(Comparator.comparing(DependencyNode::getComparatorString))));
        dependencyRoots.forEach(v -> v.setParent(null));
        return new DependencyGraph(dependencyRoots);
    }

    private static Optional<DependencyNode> createDependencyNode(
            org.apache.maven.shared.dependency.graph.DependencyNode node,
            Graph<org.apache.maven.shared.dependency.graph.DependencyNode> graph,
            AbstractChecksumCalculator calc,
            boolean isRoot,
            boolean reduce) {
        var groupId = GroupId.of(node.getArtifact().getGroupId());
        var artifactId = ArtifactId.of(node.getArtifact().getArtifactId());
        var version = VersionNumber.of(node.getArtifact().getVersion());
        var classifier = Classifier.of(node.getArtifact().getClassifier());
        var checksum = isRoot ? "" : calc.calculateArtifactChecksum(node.getArtifact());
        var scope = MavenScope.fromString(node.getArtifact().getScope());
        var repositoryInformation =
                isRoot ? RepositoryInformation.Unresolved() : calc.getArtifactResolvedField(node.getArtifact());
        Optional<String> winnerVersion = SpyingDependencyNodeUtils.getWinnerVersion(node);
        boolean included = winnerVersion.isEmpty();
        // if there is no conflict marker for this node, we use the version from the artifact
        String baseVersion = included ? node.getArtifact().getVersion() : winnerVersion.get();
        if (reduce && !included) {
            return Optional.empty();
        }
        DependencyNode value = new DependencyNode(
                artifactId,
                groupId,
                version,
                classifier,
                scope,
                repositoryInformation.getResolvedUrl(),
                repositoryInformation.getRepositoryId(),
                calc.getChecksumAlgorithm(),
                checksum);
        value.setSelectedVersion(baseVersion);
        value.setIncluded(included);
        for (var artifact : graph.successors(node)) {
            createDependencyNode(artifact, graph, calc, false, reduce).ifPresent(value::addChild);
        }
        return Optional.of(value);
    }
}
