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
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.factory.ArtifactFactory;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.project.DefaultProjectBuildingRequest;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuilder;
import org.apache.maven.project.ProjectBuildingRequest;
import org.apache.maven.project.ProjectBuildingResult;
import org.apache.maven.shared.dependency.graph.DependencyCollectorBuilder;
import org.apache.maven.shared.dependency.graph.DependencyNode;
import org.apache.maven.shared.dependency.graph.traversal.DependencyNodeVisitor;
import org.apache.maven.shared.transfer.artifact.resolve.ArtifactResolver;
import org.apache.maven.shared.transfer.artifact.resolve.ArtifactResult;

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
            plugins = getAllPlugins(project, session, dependencyCollectorBuilder, checksumCalculator);
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

    private static Set<MavenPlugin> getAllPlugins(
            MavenProject project,
            MavenSession session,
            DependencyCollectorBuilder dependencyCollectorBuilder,
            AbstractChecksumCalculator checksumCalculator) {
        Set<MavenPlugin> plugins = new TreeSet<>(Comparator.comparing(MavenPlugin::getChecksum));
        for (Artifact pluginArtifact : project.getPluginArtifacts()) {
            RepositoryInformation repositoryInformation = checksumCalculator.getPluginResolvedField(pluginArtifact);
            Set<io.github.chains_project.maven_lockfile.graph.DependencyNode> pluginDependencies =
                    resolvePluginDependencies(
                            pluginArtifact, session, project, dependencyCollectorBuilder, checksumCalculator);
            plugins.add(new MavenPlugin(
                    GroupId.of(pluginArtifact.getGroupId()),
                    ArtifactId.of(pluginArtifact.getArtifactId()),
                    VersionNumber.of(pluginArtifact.getVersion()),
                    repositoryInformation.getResolvedUrl(),
                    repositoryInformation.getRepositoryId(),
                    checksumCalculator.getChecksumAlgorithm(),
                    checksumCalculator.calculatePluginChecksum(pluginArtifact),
                    pluginDependencies));
        }
        return plugins;
    }

    /**
     * Resolve the dependencies of a Maven plugin.
     *
     * @param pluginArtifact The plugin artifact to resolve dependencies for
     * @param session The Maven session
     * @param project The current Maven project (for repository configuration)
     * @param dependencyCollectorBuilder The dependency collector builder
     * @param checksumCalculator The checksum calculator
     * @return A set of dependency nodes representing the plugin's dependencies
     */
    private static Set<io.github.chains_project.maven_lockfile.graph.DependencyNode> resolvePluginDependencies(
            Artifact pluginArtifact,
            MavenSession session,
            MavenProject project,
            DependencyCollectorBuilder dependencyCollectorBuilder,
            AbstractChecksumCalculator checksumCalculator) {
        LOGGER.info(
                "Attempting to resolve dependencies for plugin {}:{}",
                pluginArtifact.getGroupId(),
                pluginArtifact.getArtifactId());
        try {
            // Resolve the plugin's POM artifact
            File pluginPomFile = null;

            // Try to get POM from the JAR file location first (faster if already resolved)
            File pluginJarFile = pluginArtifact.getFile();
            if (pluginJarFile != null && pluginJarFile.exists()) {
                // Construct POM file path by replacing .jar with .pom
                String jarPath = pluginJarFile.getAbsolutePath();
                String pomPath = jarPath.replace(".jar", ".pom");
                File potentialPomFile = new File(pomPath);
                if (potentialPomFile.exists()) {
                    pluginPomFile = potentialPomFile;
                }
            }

            // If POM not found, try to construct path using Maven repository layout
            if (pluginPomFile == null || !pluginPomFile.exists()) {
                try {
                    // Try to find POM in local repository using standard Maven layout
                    // Format: groupId/artifactId/version/artifactId-version.pom
                    var repositorySession = session.getRepositorySession();

                    // getBasedir is deprecated, but is compatible with Maven 3.9.x.
                    @SuppressWarnings("deprecation")
                    File localRepoBase = repositorySession.getLocalRepository().getBasedir();

                    String groupPath = pluginArtifact.getGroupId().replace(".", "/");
                    String artifactId = pluginArtifact.getArtifactId();
                    String version = pluginArtifact.getVersion();
                    String pomFileName = artifactId + "-" + version + ".pom";

                    Path localPomPath =
                            Paths.get(localRepoBase.getAbsolutePath(), groupPath, artifactId, version, pomFileName);
                    if (Files.exists(localPomPath)) {
                        pluginPomFile = localPomPath.toFile();
                    } else {
                        // If not in local repo, try to resolve it using artifact resolver
                        @SuppressWarnings("deprecation")
                        ArtifactFactory artifactFactory = session.getContainer().lookup(ArtifactFactory.class);
                        Artifact pomArtifact = artifactFactory.createArtifact(
                                pluginArtifact.getGroupId(),
                                pluginArtifact.getArtifactId(),
                                pluginArtifact.getVersion(),
                                null,
                                "pom");

                        ProjectBuildingRequest pomBuildingRequest =
                                new DefaultProjectBuildingRequest(session.getProjectBuildingRequest());
                        pomBuildingRequest.setRemoteRepositories(project.getPluginArtifactRepositories());

                        @SuppressWarnings("deprecation")
                        ArtifactResolver artifactResolver =
                                session.getContainer().lookup(ArtifactResolver.class);
                        ArtifactResult result = artifactResolver.resolveArtifact(pomBuildingRequest, pomArtifact);
                        if (result != null
                                && result.getArtifact() != null
                                && result.getArtifact().getFile() != null) {
                            pluginPomFile = result.getArtifact().getFile();
                        }
                    }
                } catch (Exception e) {
                    LOGGER.debug(
                            "Could not resolve POM artifact for plugin {}:{}: {}",
                            pluginArtifact.getGroupId(),
                            pluginArtifact.getArtifactId(),
                            e.getMessage());
                }
            }

            if (pluginPomFile == null || !pluginPomFile.exists()) {
                LOGGER.warn(
                        "Could not find POM file for plugin {}:{}, skipping dependency resolution",
                        pluginArtifact.getGroupId(),
                        pluginArtifact.getArtifactId());
                return Collections.emptySet();
            }

            LOGGER.debug(
                    "Resolving dependencies for plugin {}:{} using POM: {}",
                    pluginArtifact.getGroupId(),
                    pluginArtifact.getArtifactId(),
                    pluginPomFile.getAbsolutePath());

            // Build MavenProject from plugin POM
            ProjectBuildingRequest buildingRequest =
                    new DefaultProjectBuildingRequest(session.getProjectBuildingRequest());
            buildingRequest.setRemoteRepositories(project.getPluginArtifactRepositories());
            buildingRequest.setProcessPlugins(false);
            buildingRequest.setResolveDependencies(true);

            // Note: getContainer() is deprecated but there's no clear replacement in the current Maven API
            @SuppressWarnings("deprecation")
            ProjectBuilder projectBuilder = session.getContainer().lookup(ProjectBuilder.class);
            ProjectBuildingResult result = projectBuilder.build(pluginPomFile, buildingRequest);

            if (result.getProblems() != null && !result.getProblems().isEmpty()) {
                LOGGER.warn(
                        "Problems building plugin project for {}:{}: {}",
                        pluginArtifact.getGroupId(),
                        pluginArtifact.getArtifactId(),
                        result.getProblems());
            }

            MavenProject pluginProject = result.getProject();
            if (pluginProject == null) {
                LOGGER.warn(
                        "Could not build project for plugin {}:{}",
                        pluginArtifact.getGroupId(),
                        pluginArtifact.getArtifactId());
                return Collections.emptySet();
            }

            int declaredDeps = pluginProject.getDependencies() != null
                    ? pluginProject.getDependencies().size()
                    : 0;
            LOGGER.info(
                    "Built plugin project {}:{} with {} declared dependencies",
                    pluginArtifact.getGroupId(),
                    pluginArtifact.getArtifactId(),
                    declaredDeps);

            // Resolve dependencies using DependencyCollectorBuilder
            ProjectBuildingRequest dependencyBuildingRequest =
                    new DefaultProjectBuildingRequest(session.getProjectBuildingRequest());
            dependencyBuildingRequest.setProject(pluginProject);
            dependencyBuildingRequest.setRemoteRepositories(project.getPluginArtifactRepositories());

            var rootNode = dependencyCollectorBuilder.collectDependencyGraph(dependencyBuildingRequest, null);

            int rootChildren =
                    rootNode.getChildren() != null ? rootNode.getChildren().size() : 0;
            LOGGER.info(
                    "Collected dependency graph for plugin {}:{}, root node has {} children",
                    pluginArtifact.getGroupId(),
                    pluginArtifact.getArtifactId(),
                    rootChildren);

            // Convert to DependencyGraph and extract root nodes
            MutableGraph<DependencyNode> graph = GraphBuilder.directed().build();
            rootNode.accept(new GraphBuildingNodeVisitor(graph));

            LOGGER.debug(
                    "Built graph with {} nodes for plugin {}:{}",
                    graph.nodes().size(),
                    pluginArtifact.getGroupId(),
                    pluginArtifact.getArtifactId());

            DependencyGraph dependencyGraph = DependencyGraph.of(graph, checksumCalculator, false);

            // Get root dependency nodes (excluding the plugin project itself)
            Set<io.github.chains_project.maven_lockfile.graph.DependencyNode> roots = dependencyGraph.getRoots();
            LOGGER.info(
                    "Resolved {} dependencies for plugin {}:{}",
                    roots.size(),
                    pluginArtifact.getGroupId(),
                    pluginArtifact.getArtifactId());
            return roots;

        } catch (Exception e) {
            LOGGER.warn(
                    "Could not resolve dependencies for plugin {}:{}",
                    pluginArtifact.getGroupId(),
                    pluginArtifact.getArtifactId(),
                    e);
            return Collections.emptySet();
        }
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
