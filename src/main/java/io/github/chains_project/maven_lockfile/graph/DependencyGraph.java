package io.github.chains_project.maven_lockfile.graph;

import com.google.common.graph.Graph;
import com.google.common.graph.MutableGraph;
import io.github.chains_project.maven_lockfile.checksum.AbstractChecksumCalculator;
import io.github.chains_project.maven_lockfile.checksum.RepositoryInformation;
import io.github.chains_project.maven_lockfile.data.ArtifactId;
import io.github.chains_project.maven_lockfile.data.ArtifactType;
import io.github.chains_project.maven_lockfile.data.Classifier;
import io.github.chains_project.maven_lockfile.data.GroupId;
import io.github.chains_project.maven_lockfile.data.MavenScope;
import io.github.chains_project.maven_lockfile.data.VersionNumber;
import io.github.chains_project.maven_lockfile.reporting.PluginLogManager;
import java.util.*;
import java.util.stream.Collectors;
import org.apache.maven.artifact.Artifact;
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

    /**
     * Uses breadth-first search to traverse the dependency graph and generate a flat dependency set.
     *
     * @return a set of all the dependencies
     */
    public Set<DependencyNode> getDependencySet() {
        var dependencySet = new TreeSet<DependencyNode>();
        var queue = new LinkedList<>(getRoots());

        while (!queue.isEmpty()) {
            DependencyNode current = queue.poll();
            if (dependencySet.add(current)) {
                // Only add children if this node was newly added (avoids cycles)
                queue.addAll(current.getChildren());
            }
        }

        return dependencySet;
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

        // Collect unique non-root artifacts and let the calculator pre-warm its cache
        Set<String> seen = new HashSet<>();
        List<Artifact> uniqueArtifacts = new ArrayList<>();
        for (var node : graph.nodes()) {
            if (!graph.predecessors(node).isEmpty()) {
                var a = node.getArtifact();
                String key = a.getGroupId() + ":" + a.getArtifactId() + ":" + a.getVersion() + ":"
                        + (a.getClassifier() != null ? a.getClassifier() : "") + ":" + a.getType();
                if (seen.add(key)) {
                    uniqueArtifacts.add(a);
                }
            }
        }
        calc.prewarmArtifactCache(uniqueArtifacts);

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
        PluginLogManager.getLog()
                .debug(String.format("Creating dependency node for: %s, root: %s", node.toNodeString(), isRoot));
        var groupId = GroupId.of(node.getArtifact().getGroupId());
        var artifactId = ArtifactId.of(node.getArtifact().getArtifactId());
        var version = VersionNumber.of(node.getArtifact().getVersion());
        var classifier = Classifier.of(node.getArtifact().getClassifier());
        var type = ArtifactType.of(node.getArtifact().getType());
        PluginLogManager.getLog().debug(String.format("Calculating checksum for %s", node.toNodeString()));
        var checksum = isRoot ? "" : calc.calculateArtifactChecksum(node.getArtifact());
        var scope = MavenScope.fromString(node.getArtifact().getScope());
        PluginLogManager.getLog().debug(String.format("Resolving repository information for %s", node.toNodeString()));
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
                type,
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
