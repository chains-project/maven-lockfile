package io.github.chains_project.maven_lockfile;

import com.google.common.graph.GraphBuilder;
import com.google.common.graph.MutableGraph;
import io.github.chains_project.maven_lockfile.checksum.AbstractChecksumCalculator;
import io.github.chains_project.maven_lockfile.data.ArtifactId;
import io.github.chains_project.maven_lockfile.data.GroupId;
import io.github.chains_project.maven_lockfile.data.LockFile;
import io.github.chains_project.maven_lockfile.data.MavenPlugin;
import io.github.chains_project.maven_lockfile.data.Metadata;
import io.github.chains_project.maven_lockfile.data.VersionNumber;
import io.github.chains_project.maven_lockfile.graph.DependencyGraph;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.log4j.Logger;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.project.DefaultProjectBuildingRequest;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuildingRequest;
import org.apache.maven.shared.dependency.graph.DependencyCollectorBuilder;
import org.apache.maven.shared.dependency.graph.DependencyNode;
import org.apache.maven.shared.dependency.graph.traversal.DependencyNodeVisitor;
import org.apache.maven.shared.transfer.dependencies.resolve.DependencyResolver;

/**
 * Entry point for the lock file generation. This class is responsible for generating the lock file for a project.
 *
 */
public class LockFileFacade {

    private static final Logger LOGGER = Logger.getLogger(LockFileFacade.class);

    /**
     * This visitor is used to traverse the dependency graph and add the edges to the graph.
     */
    private static final class GraphBuildingNodeVisitor implements DependencyNodeVisitor {
        private final MutableGraph<Artifact> graph;

        /**
         * Create a new instance of the visitor.
         * @param graph  The graph to add the edges to.
         */
        private GraphBuildingNodeVisitor(MutableGraph<Artifact> graph) {
            this.graph = graph;
        }

        @Override
        public boolean visit(DependencyNode node) {

            node.getChildren().forEach(v -> graph.putEdge(node.getArtifact(), v.getArtifact()));

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
    public static Path getLockFilePath(MavenProject project) {
        return Path.of(project.getBasedir().getAbsolutePath(), "lockfile.json");
    }

    private LockFileFacade() {
        // Prevent instantiation
    }
    /**
     * Generate a lock file for a project. This method is responsible for generating the lock file for a project. It uses the dependency collector to generate the dependency graph and then resolves the dependencies.
     * @param session  The maven session.
     * @param project  The project to generate a lock file for.
     * @param dependencyCollectorBuilder  The dependency collector builder to use for generating the dependency graph.
     * @param resolver  The dependency resolver to use for resolving the dependencies.
     * @param includeMavenPlugins  Whether to include maven plugins in the lock file.
     * @param metadata The metadata to include in the lock file.
     * @return  A lock file for the project.
     */
    public static LockFile generateLockFileFromProject(
            MavenSession session,
            MavenProject project,
            DependencyCollectorBuilder dependencyCollectorBuilder,
            DependencyResolver resolver,
            AbstractChecksumCalculator checksumCalculator,
            boolean includeMavenPlugins,
            Metadata metadata) {
        LOGGER.info("Generating lock file for project " + project.getArtifactId());
        List<MavenPlugin> plugins = new ArrayList<>();
        if (includeMavenPlugins) {
            plugins = getAllPlugins(project);
        }
        // Get all the artifacts for the dependencies in the project
        var graph = LockFileFacade.graph(session, project, dependencyCollectorBuilder, checksumCalculator);
        var roots = graph.getGraph().stream().filter(v -> v.getParent() == null).collect(Collectors.toList());
        return new LockFile(
                GroupId.of(project.getGroupId()),
                ArtifactId.of(project.getArtifactId()),
                VersionNumber.of(project.getVersion()),
                roots,
                plugins,
                metadata);
    }

    private static List<MavenPlugin> getAllPlugins(MavenProject project) {
        return project.getBuildPlugins().stream()
                .map(v -> new MavenPlugin(
                        GroupId.of(v.getGroupId()), ArtifactId.of(v.getArtifactId()), VersionNumber.of(v.getVersion())))
                .collect(Collectors.toList());
    }

    private static DependencyGraph graph(
            MavenSession session,
            MavenProject project,
            DependencyCollectorBuilder dependencyCollectorBuilder,
            AbstractChecksumCalculator checksumCalculator) {
        try {
            ProjectBuildingRequest buildingRequest =
                    new DefaultProjectBuildingRequest(session.getProjectBuildingRequest());

            buildingRequest.setProject(project);
            var rootNode = dependencyCollectorBuilder.collectDependencyGraph(buildingRequest, null);
            MutableGraph<Artifact> graph = GraphBuilder.directed().build();
            rootNode.accept(new GraphBuildingNodeVisitor(graph));
            return DependencyGraph.of(graph, checksumCalculator);
        } catch (Exception e) {
            LOGGER.warn("Could not generate graph", e);
            return DependencyGraph.of(GraphBuilder.directed().build(), checksumCalculator);
        }
    }
}
