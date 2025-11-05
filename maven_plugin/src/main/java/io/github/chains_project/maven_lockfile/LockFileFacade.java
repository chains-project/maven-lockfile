package io.github.chains_project.maven_lockfile;

import com.google.common.graph.GraphBuilder;
import com.google.common.graph.MutableGraph;
import io.github.chains_project.maven_lockfile.checksum.AbstractChecksumCalculator;
import io.github.chains_project.maven_lockfile.checksum.RepositoryInformation;
import io.github.chains_project.maven_lockfile.data.ArtifactId;
import io.github.chains_project.maven_lockfile.data.GroupId;
import io.github.chains_project.maven_lockfile.data.LockFile;
import io.github.chains_project.maven_lockfile.data.MavenPlugin;
import io.github.chains_project.maven_lockfile.data.MetaData;
import io.github.chains_project.maven_lockfile.data.Pom;
import io.github.chains_project.maven_lockfile.data.VersionNumber;
import io.github.chains_project.maven_lockfile.graph.DependencyGraph;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.project.DefaultProjectBuildingRequest;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuildingRequest;
import org.apache.maven.shared.dependency.graph.DependencyCollectorBuilder;
import org.apache.maven.shared.dependency.graph.DependencyNode;
import org.apache.maven.shared.dependency.graph.traversal.DependencyNodeVisitor;

/**
 * Entry point for the lock file generation. This class is responsible for generating the lock file for a project.
 *
 */
public class LockFileFacade {

    private static final Logger LOGGER = LogManager.getLogger(LockFileFacade.class);

    /**
     * This visitor is used to traverse the dependency graph and add the edges to the graph.
     */
    private static final class GraphBuildingNodeVisitor implements DependencyNodeVisitor {
        private final MutableGraph<DependencyNode> graph;
        /**
         * Create a new instance of the visitor.
         * @param graph  The graph to add the edges to.
         */
        private GraphBuildingNodeVisitor(MutableGraph<DependencyNode> graph) {
            this.graph = graph;
        }

        @Override
        public boolean visit(DependencyNode node) {
            node.getChildren().forEach(v -> graph.putEdge(node, v));
            return true;
        }

        @Override
        public boolean endVisit(DependencyNode node) {
            return true;
        }
    }

    /**
     * Generate a lock file for a project.
     * @param project The project to generate a lock file for.
     * @return A lock file for the project.
     */
    public static Path getLockFilePath(MavenProject project, String lockfileName) {
        return Path.of(project.getBasedir().getAbsolutePath(), lockfileName);
    }

    private LockFileFacade() {
        // Prevent instantiation
    }

    /**
     * Generate a lock file for a project. This method is responsible for generating the lock file for a project. It uses the dependency collector to generate the dependency graph and then resolves the dependencies.
     * @param session  The maven session.
     * @param project  The project to generate a lock file for.
     * @param dependencyCollectorBuilder  The dependency collector builder to use for generating the dependency graph.
     * @param checksumCalculator  The checksum calculator to use for calculating the checksums of the artifacts.
     * @param metadata The metadata to include in the lock file.
     * @return  A lock file for the project.
     */
    public static LockFile generateLockFileFromProject(
            MavenSession session,
            MavenProject project,
            DependencyCollectorBuilder dependencyCollectorBuilder,
            AbstractChecksumCalculator checksumCalculator,
            MetaData metadata) {
        LOGGER.info("Generating lock file for project {}", project.getArtifactId());
        Set<MavenPlugin> plugins = new TreeSet<>(Comparator.comparing(MavenPlugin::getChecksum));
        if (metadata.getConfig().isIncludeMavenPlugins()) {
            plugins = getAllPlugins(project, checksumCalculator);
        }
        // Get all the artifacts for the dependencies in the project
        var graph = LockFileFacade.graph(
                session,
                project,
                dependencyCollectorBuilder,
                checksumCalculator,
                metadata.getConfig().isReduced());
        var roots = graph.getGraph().stream()
                .filter(v -> v.getParent() == null)
                .collect(Collectors.toCollection(() -> new TreeSet<>(Comparator.comparing(
                        io.github.chains_project.maven_lockfile.graph.DependencyNode::getComparatorString))));
        var pom = new Pom(project, checksumCalculator);
        return new LockFile(
                GroupId.of(project.getGroupId()),
                ArtifactId.of(project.getArtifactId()),
                VersionNumber.of(project.getVersion()),
                pom,
                roots,
                plugins,
                metadata);
    }

    private static Set<MavenPlugin> getAllPlugins(MavenProject project, AbstractChecksumCalculator checksumCalculator) {
        Set<MavenPlugin> plugins = new TreeSet<>(Comparator.comparing(MavenPlugin::getChecksum));
        for (Artifact pluginArtifact : project.getPluginArtifacts()) {
            RepositoryInformation repositoryInformation = checksumCalculator.getPluginResolvedField(pluginArtifact);
            plugins.add(new MavenPlugin(
                    GroupId.of(pluginArtifact.getGroupId()),
                    ArtifactId.of(pluginArtifact.getArtifactId()),
                    VersionNumber.of(pluginArtifact.getVersion()),
                    repositoryInformation.getResolvedUrl(),
                    repositoryInformation.getRepositoryId(),
                    checksumCalculator.getChecksumAlgorithm(),
                    checksumCalculator.calculatePluginChecksum(pluginArtifact)));
        }
        return plugins;
    }

    private static DependencyGraph graph(
            MavenSession session,
            MavenProject project,
            DependencyCollectorBuilder dependencyCollectorBuilder,
            AbstractChecksumCalculator checksumCalculator,
            boolean reduced) {
        try {
            ProjectBuildingRequest buildingRequest =
                    new DefaultProjectBuildingRequest(session.getProjectBuildingRequest());

            buildingRequest.setProject(project);
            var rootNode = dependencyCollectorBuilder.collectDependencyGraph(buildingRequest, null);

            MutableGraph<DependencyNode> graph = GraphBuilder.directed().build();
            rootNode.accept(new GraphBuildingNodeVisitor(graph));
            return DependencyGraph.of(graph, checksumCalculator, reduced);
        } catch (Exception e) {
            LOGGER.warn("Could not generate graph", e);
            return DependencyGraph.of(GraphBuilder.directed().build(), checksumCalculator, reduced);
        }
    }
}
