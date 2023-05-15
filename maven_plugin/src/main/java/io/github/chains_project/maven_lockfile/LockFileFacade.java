package io.github.chains_project.maven_lockfile;

import com.google.common.graph.GraphBuilder;
import com.google.common.graph.MutableGraph;
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
import org.apache.maven.model.Dependency;
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
     * This visitor is used to traverse the dependency graph and add the edges to the graph. It also resolves the dependencies. This is necessary because the dependency graph does not contain the resolved dependencies.
     */
    private static final class ResolvingDependencyNodeVisitor implements DependencyNodeVisitor {
        private final MutableGraph<Artifact> graph;
        private final DependencyResolver resolver;
        private final ProjectBuildingRequest buildingRequest;
        /**
         * Create a new instance of the visitor.
         * @param graph  The graph to add the edges to.
         * @param resolver  The dependency resolver to use for resolving the dependencies.
         * @param buildingRequest  The building request to use for resolving the dependencies.
         */
        private ResolvingDependencyNodeVisitor(
                MutableGraph<Artifact> graph, DependencyResolver resolver, ProjectBuildingRequest buildingRequest) {
            this.graph = graph;
            this.resolver = resolver;
            this.buildingRequest = buildingRequest;
        }

        @Override
        public boolean visit(DependencyNode node) {

            node.getChildren()
                    .forEach(v ->
                            graph.putEdge(resolveDependency(node.getArtifact()), resolveDependency(v.getArtifact())));
            return true;
        }
        /**
         * Create a dependency from an artifact. This is necessary because the API of the dependency resolver expects a dependency.
         * @param node  The artifact to create a dependency from.
         * @return  The dependency
         */
        private Dependency createDependency(Artifact node) {
            Dependency dependency = new Dependency();
            dependency.setGroupId(node.getGroupId());
            dependency.setArtifactId(node.getArtifactId());
            dependency.setVersion(node.getVersion());
            dependency.setScope(node.getScope());
            dependency.setType(node.getType());
            dependency.setClassifier(node.getClassifier());
            return dependency;
        }

        @Override
        public boolean endVisit(DependencyNode node) {
            return true;
        }

        private Artifact resolveDependency(Artifact artifact) {
            try {
                return resolver.resolveDependencies(buildingRequest, List.of(createDependency(artifact)), null, null)
                        .iterator()
                        .next()
                        .getArtifact();
            } catch (Exception e) {
                LOGGER.warn("Could not resolve artifact: " + artifact.getArtifactId(), e);
                return artifact;
            }
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
            boolean includeMavenPlugins,
            Metadata metadata) {
        LOGGER.info("Generating lock file for project " + project.getArtifactId());
        List<MavenPlugin> plugins = new ArrayList<>();
        if (includeMavenPlugins) {
            plugins = getAllPlugins(project);
        }
        // Get all the artifacts for the dependencies in the project
        var graph = LockFileFacade.graph(session, project, dependencyCollectorBuilder, resolver);
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
            DependencyResolver resolver) {
        try {
            ProjectBuildingRequest buildingRequest =
                    new DefaultProjectBuildingRequest(session.getProjectBuildingRequest());

            buildingRequest.setProject(project);
            var rootNode = dependencyCollectorBuilder.collectDependencyGraph(buildingRequest, null);
            MutableGraph<Artifact> graph = GraphBuilder.directed().build();
            rootNode.accept(new ResolvingDependencyNodeVisitor(graph, resolver, buildingRequest));

            return DependencyGraph.of(graph);
        } catch (Exception e) {
            LOGGER.warn("Could not generate graph", e);
            return DependencyGraph.of(GraphBuilder.directed().build());
        }
    }
}
