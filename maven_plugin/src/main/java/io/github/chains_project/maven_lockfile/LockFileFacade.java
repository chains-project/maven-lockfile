package io.github.chains_project.maven_lockfile;

import com.google.common.graph.GraphBuilder;
import com.google.common.graph.MutableGraph;
import io.github.chains_project.maven_lockfile.checksum.AbstractChecksumCalculator;
import io.github.chains_project.maven_lockfile.checksum.RepositoryInformation;
import io.github.chains_project.maven_lockfile.data.*;
import io.github.chains_project.maven_lockfile.graph.DependencyGraph;
import io.github.chains_project.maven_lockfile.reporting.PluginLogManager;
import io.github.chains_project.maven_lockfile.resolvers.BomResolver;
import io.github.chains_project.maven_lockfile.resolvers.ProjectBuilder;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.factory.ArtifactFactory;
import org.apache.maven.artifact.handler.DefaultArtifactHandler;
import org.apache.maven.artifact.resolver.filter.ArtifactFilter;
import org.apache.maven.artifact.resolver.filter.ScopeArtifactFilter;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Extension;
import org.apache.maven.model.Plugin;
import org.apache.maven.project.*;
import org.apache.maven.project.DefaultProjectBuildingRequest;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuildingRequest;
import org.apache.maven.shared.dependency.graph.DependencyCollectorBuilder;
import org.apache.maven.shared.dependency.graph.DependencyNode;
import org.apache.maven.shared.dependency.graph.traversal.DependencyNodeVisitor;
import org.apache.maven.shared.transfer.artifact.resolve.ArtifactResolver;
import org.apache.maven.shared.transfer.artifact.resolve.ArtifactResult;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.DependencyRequest;
import org.eclipse.aether.resolution.DependencyResolutionException;
import org.eclipse.aether.resolution.DependencyResult;
import org.eclipse.aether.util.artifact.JavaScopes;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Entry point for the lock file generation. This class is responsible for generating the lock file for a project.
 *
 */
public class LockFileFacade {

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
     * @param repositorySystem The repository system for resolving artifacts.
     * @return  A lock file for the project.
     */
    public static LockFile generateLockFileFromProject(
            MavenSession session,
            MavenProject project,
            DependencyCollectorBuilder dependencyCollectorBuilder,
            AbstractChecksumCalculator checksumCalculator,
            MetaData metadata,
            RepositorySystem repositorySystem) {
        PluginLogManager.getLog().info(String.format("Generating lock file for project %s", project.getArtifactId()));
        Set<MavenPlugin> plugins = new TreeSet<>();
        if (metadata.getConfig().isIncludeMavenPlugins()) {
            plugins = getAllPlugins(project, session, dependencyCollectorBuilder, checksumCalculator);
        }

        Set<MavenExtension> extensions = getAllExtensions(
                project, session, dependencyCollectorBuilder, checksumCalculator, repositorySystem);

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
        var pom = constructRecursivePom(project, checksumCalculator);
        var boms = resolveBoms(graph, session, project, checksumCalculator);

        return new LockFile(
                GroupId.of(project.getGroupId()),
                ArtifactId.of(project.getArtifactId()),
                VersionNumber.of(project.getVersion()),
                pom,
                roots,
                plugins,
                extensions,
                metadata,
                boms);
    }

    private static Set<MavenExtension> getAllExtensions(
            MavenProject project,
            MavenSession session,
            DependencyCollectorBuilder dependencyCollectorBuilder,
            AbstractChecksumCalculator checksumCalculator,
            RepositorySystem repositorySystem) {
        Set<MavenExtension> extensions = new TreeSet<>();

        List<Extension> buildExtensions = project.getBuildExtensions();
        if (buildExtensions == null || buildExtensions.isEmpty()) {
            return extensions;
        }

        RepositorySystemSession repoSession = session.getRepositorySession();
        List<RemoteRepository> repositories = project.getRemotePluginRepositories();

        // Collect all extensions as dependencies
        List<org.eclipse.aether.graph.Dependency> extensionDependencies = new ArrayList<>();
        for (Extension extension : buildExtensions) {
            org.eclipse.aether.artifact.Artifact artifact = new org.eclipse.aether.artifact.DefaultArtifact(
                    extension.getGroupId(), extension.getArtifactId(), "jar", extension.getVersion());
            extensionDependencies.add(new org.eclipse.aether.graph.Dependency(artifact, JavaScopes.RUNTIME));
        }

        // Resolve all extensions and their dependencies in one call
        CollectRequest collectRequest = new CollectRequest();
        collectRequest.setDependencies(extensionDependencies);
        collectRequest.setRepositories(repositories);

        DependencyRequest dependencyRequest = new DependencyRequest();
        dependencyRequest.setCollectRequest(collectRequest);

        try {
            DependencyResult dependencyResult = repositorySystem.resolveDependencies(repoSession, dependencyRequest);

            // Process each resolved extension (direct dependencies from the root)
            for (org.eclipse.aether.graph.DependencyNode node : dependencyResult.getRoot().getChildren()) {
                org.eclipse.aether.artifact.Artifact artifact = node.getArtifact();

                // Convert Aether artifact to Maven artifact for compatibility with checksumCalculator
                Artifact mavenArtifact = new org.apache.maven.artifact.DefaultArtifact(
                        artifact.getGroupId(),
                        artifact.getArtifactId(),
                        artifact.getVersion(),
                        "compile",
                        artifact.getExtension(),
                        artifact.getClassifier(),
                        new DefaultArtifactHandler(artifact.getExtension()));
                mavenArtifact.setFile(artifact.getFile());

                RepositoryInformation repositoryInformation =
                        checksumCalculator.getPluginResolvedField(mavenArtifact);

                // Resolve extension's transitive dependencies using the existing mechanism
                Set<io.github.chains_project.maven_lockfile.graph.DependencyNode> transitiveDeps =
                        resolveComponentDependencies(
                                mavenArtifact,
                                session,
                                project,
                                dependencyCollectorBuilder,
                                checksumCalculator,
                                Collections.emptyList());

                extensions.add(new MavenExtension(
                        GroupId.of(artifact.getGroupId()),
                        ArtifactId.of(artifact.getArtifactId()),
                        VersionNumber.of(artifact.getVersion()),
                        checksumCalculator.calculatePluginChecksum(mavenArtifact),
                        checksumCalculator.getChecksumAlgorithm(),
                        repositoryInformation.getResolvedUrl(),
                        repositoryInformation.getRepositoryId(),
                        transitiveDeps));
            }
        } catch (DependencyResolutionException e) {
            PluginLogManager.getLog().warn("Failed to resolve extension dependencies", e);
        }

        return extensions;
    }

    private static Set<MavenPlugin> getAllPlugins(
            MavenProject project,
            MavenSession session,
            DependencyCollectorBuilder dependencyCollectorBuilder,
            AbstractChecksumCalculator checksumCalculator) {
        Set<MavenPlugin> plugins = new TreeSet<>();

        // Build a map of user-declared plugin dependencies
        // Key: groupId:artifactId, Value: list of user-declared dependencies
        Map<String, List<Dependency>> userPluginDependencies = new HashMap<>();
        if (project.getBuild() != null && project.getBuild().getPlugins() != null) {
            for (Plugin plugin : project.getBuild().getPlugins()) {
                String key = plugin.getGroupId() + ":" + plugin.getArtifactId();
                if (plugin.getDependencies() != null
                        && !plugin.getDependencies().isEmpty()) {
                    userPluginDependencies.put(key, plugin.getDependencies());
                }
            }
        }

        for (Artifact pluginArtifact : project.getPluginArtifacts()) {
            RepositoryInformation repositoryInformation = checksumCalculator.getPluginResolvedField(pluginArtifact);
            String pluginKey = pluginArtifact.getGroupId() + ":" + pluginArtifact.getArtifactId();
            List<Dependency> userDeclaredDeps = userPluginDependencies.getOrDefault(pluginKey, Collections.emptyList());

            Set<io.github.chains_project.maven_lockfile.graph.DependencyNode> pluginDependencies =
                    resolveComponentDependencies(
                            pluginArtifact,
                            session,
                            project,
                            dependencyCollectorBuilder,
                            checksumCalculator,
                            userDeclaredDeps);
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
     * @param userDeclaredDeps User-declared dependencies for this plugin (from the project's pom.xml)
     * @return A set of dependency nodes representing the plugin's dependencies
     */
    private static Set<io.github.chains_project.maven_lockfile.graph.DependencyNode> resolveComponentDependencies(
            Artifact pluginArtifact,
            MavenSession session,
            MavenProject project,
            DependencyCollectorBuilder dependencyCollectorBuilder,
            AbstractChecksumCalculator checksumCalculator,
            List<Dependency> userDeclaredDeps) {
        PluginLogManager.getLog()
                .debug(String.format("Attempting to resolve dependencies for plugin %s", pluginArtifact));
        try {
            ProjectBuilder projectBuilder = new ProjectBuilder(session, project.getPluginArtifactRepositories());
            Optional<MavenProject> pluginProjectOptional = projectBuilder.buildFromGav(
                    pluginArtifact.getGroupId(), pluginArtifact.getArtifactId(), pluginArtifact.getBaseVersion());

            if (pluginProjectOptional.isEmpty()) {
                PluginLogManager.getLog().warn(String.format("Could not build project for plugin %s", pluginArtifact));
                return Collections.emptySet();
            }

            var pluginProject = pluginProjectOptional.get();

            int declaredDeps = pluginProject.getDependencies() != null
                    ? pluginProject.getDependencies().size()
                    : 0;
            PluginLogManager.getLog()
                    .debug(String.format(
                            "Built plugin project %s with %d declared dependencies", pluginArtifact, declaredDeps));

            // Merge user-declared dependencies into the plugin project
            // User-declared dependencies override the plugin's default dependencies (e.g., scope changes)
            if (!userDeclaredDeps.isEmpty()) {
                List<Dependency> pluginDeps = new ArrayList<>(pluginProject.getDependencies());
                // Build a map of existing dependencies for quick lookup
                Map<String, Dependency> existingDepsMap = new HashMap<>();
                for (Dependency dep : pluginDeps) {
                    String key = dep.getGroupId() + ":" + dep.getArtifactId();
                    existingDepsMap.put(key, dep);
                }

                for (Dependency userDep : userDeclaredDeps) {
                    String key = userDep.getGroupId() + ":" + userDep.getArtifactId();
                    if (existingDepsMap.containsKey(key)) {
                        // Replace existing dependency with user-declared one (overrides scope, version, etc.)
                        pluginDeps.remove(existingDepsMap.get(key));
                        PluginLogManager.getLog()
                                .debug(String.format(
                                        "Overriding plugin dependency %s with user-declared dependency (scope: %s -> %s)",
                                        key, existingDepsMap.get(key).getScope(), userDep.getScope()));
                    } else {
                        PluginLogManager.getLog()
                                .debug(String.format(
                                        "Adding user-declared dependency %s to plugin %s", key, pluginArtifact));
                    }
                    pluginDeps.add(userDep);
                }
                pluginProject.setDependencies(pluginDeps);
                PluginLogManager.getLog()
                        .debug(String.format(
                                "Plugin %s now has %d dependencies after merging user-declared dependencies",
                                pluginArtifact, pluginDeps.size()));
            }

            // Resolve dependencies using DependencyCollectorBuilder
            ProjectBuildingRequest dependencyBuildingRequest =
                    new DefaultProjectBuildingRequest(session.getProjectBuildingRequest());
            dependencyBuildingRequest.setProject(pluginProject);
            dependencyBuildingRequest.setRemoteRepositories(project.getPluginArtifactRepositories());

            // Filter artifacts to "compile+runtime" scopes. Maven plugins require their runtime
            // scope dependencies to be present alongside any compile-time dependencies.
            // Test scope dependencies of plugins should be excluded.
            ArtifactFilter filter = new ScopeArtifactFilter("compile+runtime");
            var rootNode = dependencyCollectorBuilder.collectDependencyGraph(dependencyBuildingRequest, filter);

            int rootChildren =
                    rootNode.getChildren() != null ? rootNode.getChildren().size() : 0;
            PluginLogManager.getLog()
                    .debug(String.format(
                            "Collected dependency graph for plugin %s, root node has %d children",
                            pluginArtifact, rootChildren));

            // Convert to DependencyGraph and extract root nodes
            MutableGraph<DependencyNode> graph = GraphBuilder.directed().build();
            rootNode.accept(new GraphBuildingNodeVisitor(graph));

            PluginLogManager.getLog()
                    .debug(String.format(
                            "Built graph with %d nodes for plugin %s",
                            graph.nodes().size(), pluginArtifact));

            DependencyGraph dependencyGraph = DependencyGraph.of(graph, checksumCalculator, false);

            // Get root dependency nodes (excluding the plugin project itself)
            Set<io.github.chains_project.maven_lockfile.graph.DependencyNode> roots = dependencyGraph.getRoots();
            PluginLogManager.getLog()
                    .info(String.format("Resolved %4d dependencies for plugin %s", roots.size(), pluginArtifact));
            return roots;

        } catch (Exception e) {
            PluginLogManager.getLog()
                    .warn(String.format("Could not resolve dependencies for plugin %s", pluginArtifact), e);
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
            PluginLogManager.getLog()
                    .info(String.format(
                            "Resolved %4d dependencies for project %s",
                            graph.nodes().size(), project));
            return DependencyGraph.of(graph, checksumCalculator, reduced);
        } catch (Exception e) {
            PluginLogManager.getLog().warn("Could not generate graph", e);
            return DependencyGraph.of(GraphBuilder.directed().build(), checksumCalculator, reduced);
        }
    }

    /**
     * Construct a Pom object containing a full tree of its parent POM references. These parent
     * POMs may be relative to the project being built, or are specified from an external POM.
     */
    private static Pom constructRecursivePom(
            MavenProject initialProject, AbstractChecksumCalculator checksumCalculator) {
        String checksumAlgorithm = checksumCalculator.getChecksumAlgorithm();

        List<MavenProject> recursiveProjects = new ArrayList<>();
        MavenProject currentProject = initialProject;
        recursiveProjects.add(currentProject);
        while (currentProject.hasParent()) {
            currentProject = currentProject.getParent();
            recursiveProjects.add(currentProject);
        }

        Pom lastPom = null;
        Collections.reverse(recursiveProjects);
        for (MavenProject project : recursiveProjects) {
            String relativePath = project.getFile() == null
                    ? null
                    : initialProject
                            .getBasedir()
                            .toPath()
                            .relativize(project.getFile().toPath())
                            .toString();
            String checksum = null;
            ResolvedUrl resolved = null;
            RepositoryId repoId = null;
            if (project.getFile() == null) {
                // External POM - get repository information
                Artifact artifact = project.getArtifact();
                Artifact pomArtifact = new DefaultArtifact(
                        artifact.getGroupId(),
                        artifact.getArtifactId(),
                        artifact.getVersion(),
                        artifact.getScope(),
                        "pom",
                        artifact.getClassifier(),
                        artifact.getArtifactHandler());
                checksum = checksumCalculator.calculateArtifactChecksum(pomArtifact);
                RepositoryInformation repoInfo = checksumCalculator.getArtifactResolvedField(pomArtifact);
                resolved = repoInfo.getResolvedUrl();
                repoId = repoInfo.getRepositoryId();
            } else {
                checksum = checksumCalculator.calculatePomChecksum(
                        project.getFile().toPath());
            }
            lastPom = new Pom(
                    GroupId.of(project.getGroupId()),
                    ArtifactId.of(project.getArtifactId()),
                    VersionNumber.of(project.getVersion()),
                    relativePath,
                    resolved,
                    repoId,
                    checksumAlgorithm,
                    checksum,
                    lastPom);
        }

        return lastPom;
    }

    /**
     * Resolve the BOM POMs for the current project and its dependencies.
     *
     * Note that this function will mutate the graph nodes by adding to each one the list of resolved BOMs.
     *
     * @param graph The dependency graph
     * @param session The Maven session
     * @param project The current Maven project
     * @param checksumCalculator The checksum calculator
     */
    private static Set<Pom> resolveBoms(
            DependencyGraph graph,
            MavenSession session,
            MavenProject project,
            AbstractChecksumCalculator checksumCalculator) {
        BomResolver resolver = new BomResolver(session, project.getRemoteArtifactRepositories(), checksumCalculator);
        resolver.resolveBomsForDependencies(graph);
        return resolver.resolveForProject(project);
    }
}
