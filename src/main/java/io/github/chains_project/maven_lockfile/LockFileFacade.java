package io.github.chains_project.maven_lockfile;

import com.google.common.graph.GraphBuilder;
import com.google.common.graph.MutableGraph;
import io.github.chains_project.maven_lockfile.checksum.AbstractChecksumCalculator;
import io.github.chains_project.maven_lockfile.checksum.RepositoryInformation;
import io.github.chains_project.maven_lockfile.data.*;
import io.github.chains_project.maven_lockfile.graph.DependencyGraph;
import io.github.chains_project.maven_lockfile.recorder.DynamicResolutionStore;
import io.github.chains_project.maven_lockfile.recorder.RecordedArtifact;
import io.github.chains_project.maven_lockfile.reporting.PluginLogManager;
import io.github.chains_project.maven_lockfile.resolvers.BomResolver;
import io.github.chains_project.maven_lockfile.resolvers.ProjectBuilder;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.ArtifactUtils;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.handler.DefaultArtifactHandler;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.resolver.filter.ArtifactFilter;
import org.apache.maven.artifact.resolver.filter.ScopeArtifactFilter;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Extension;
import org.apache.maven.model.Plugin;
import org.apache.maven.project.DefaultProjectBuildingRequest;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuildingRequest;
import org.apache.maven.shared.dependency.graph.DependencyCollectorBuilder;
import org.apache.maven.shared.dependency.graph.DependencyCollectorBuilderException;
import org.apache.maven.shared.dependency.graph.DependencyNode;
import org.apache.maven.shared.dependency.graph.traversal.DependencyNodeVisitor;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.*;
import org.eclipse.aether.util.artifact.JavaScopes;

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
        private final MavenProject project;

        /**
         * Create a new instance of the visitor.
         *
         * @param graph The graph to add the edges to.
         * @param project The project to resolve the dependencies for.
         *                This is useful to resolve `RELEASE` and `LATEST` versions, which are not resolved
         *                by dependency collector.
         */
        private GraphBuildingNodeVisitor(MutableGraph<DependencyNode> graph, MavenProject project) {
            this.graph = graph;
            this.project = project;
        }

        @Override
        public boolean visit(DependencyNode node) {
            String version = node.getArtifact().getVersion();
            if (isSpecialVersion(version)) {
                project.getArtifacts().stream()
                        .filter(a -> a.getDependencyConflictId()
                                .equals(node.getArtifact().getDependencyConflictId()))
                        .findFirst()
                        .ifPresent(resolved -> {
                            node.getArtifact().setVersion(resolved.getVersion());
                            node.getArtifact().setFile(resolved.getFile());
                        });
            }
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
     *
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
     *
     * @param session                    The maven session.
     * @param project                    The project to generate a lock file for.
     * @param dependencyCollectorBuilder The dependency collector builder to use for generating the dependency graph.
     * @param checksumCalculator         The checksum calculator to use for calculating the checksums of the artifacts.
     * @param metadata                   The metadata to include in the lock file.
     * @param repositorySystem           The repository system for resolving artifacts.
     * @return A lock file for the project.
     */
    public static LockFile generateLockFileFromProject(
            MavenSession session,
            MavenProject project,
            DependencyCollectorBuilder dependencyCollectorBuilder,
            AbstractChecksumCalculator checksumCalculator,
            MetaData metadata,
            RepositorySystem repositorySystem) {
        PluginLogManager.getLog().info(String.format("Generating lock file for project %s", project.getArtifactId()));
        Config config = metadata.getConfig();
        Set<MavenPlugin> plugins = new TreeSet<>();
        if (config.isIncludeMavenPlugins()) {
            plugins = getAllPlugins(project, session, dependencyCollectorBuilder, checksumCalculator, config);
        }

        Set<MavenExtension> extensions = new TreeSet<>();
        if (config.isIncludeMavenExtensions()) {
            extensions = getAllExtensions(
                    project, session, dependencyCollectorBuilder, checksumCalculator, repositorySystem, config);
        }

        // Get all the artifacts for the dependencies in the project
        DependencyGraph dependencyGraph = createDependencyGraph(
                project,
                session,
                project.getRemoteArtifactRepositories(),
                dependencyCollectorBuilder,
                checksumCalculator,
                null,
                config.isReduced());

        var roots = dependencyGraph.getRoots();
        var pom = constructRecursivePom(project, session, checksumCalculator);

        if (config.isIncludeParentPom() || config.isIncludeBoms()) {
            resolveParentsAndBomsForDependencies(dependencyGraph, session, project, checksumCalculator);
        }
        Set<Pom> boms = config.isIncludeBoms() ? resolveBoms(session, project, checksumCalculator) : new TreeSet<>();

        if (config.isHermetic()) {
            plugins =
                    attachDynamicallyResolvedDependencies(session, checksumCalculator, pom, roots, plugins, extensions);
        }

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

    /**
     * Merges artifacts a DynamicResolutionSpy extension recorded directly into the {@link
     * MavenPlugin} whose Mojo was executing when each one was resolved, skipping GAVs already
     * covered by the static graph. An artifact whose triggering plugin isn't one of {@code
     * plugins} is dropped with a warning rather than surfaced anywhere else.
     */
    private static Set<MavenPlugin> attachDynamicallyResolvedDependencies(
            MavenSession session,
            AbstractChecksumCalculator checksumCalculator,
            Pom projectPom,
            Set<io.github.chains_project.maven_lockfile.graph.DependencyNode> roots,
            Set<MavenPlugin> plugins,
            Set<MavenExtension> extensions) {
        var multiModuleProjectDirectory = session.getRequest().getMultiModuleProjectDirectory();
        if (multiModuleProjectDirectory == null) {
            return plugins;
        }

        Path recordedPath = DynamicResolutionStore.defaultPath(multiModuleProjectDirectory.toPath());
        List<RecordedArtifact> recorded;
        try {
            recorded = DynamicResolutionStore.read(recordedPath);
        } catch (IOException e) {
            PluginLogManager.getLog().warn("Could not read recorded dynamic resolutions", e);
            return plugins;
        }
        if (recorded.isEmpty()) {
            if (Files.exists(recordedPath)) {
                PluginLogManager.getLog()
                        .warn("DynamicResolutionSpy is attached but recorded no dynamically-resolved artifacts "
                                + "this session. If you expected some (e.g. Surefire's test-framework provider), "
                                + "make sure the build already ran past the phase that triggers them (e.g. `test` "
                                + "or `verify`) before this goal runs - this goal's default binding is "
                                + "generate-resources, which runs earlier in the lifecycle.");
            }
            return plugins;
        }

        Set<String> knownGavs = new HashSet<>();
        collectPomChainGavs(projectPom, knownGavs);
        roots.forEach(node -> collectGavs(node, knownGavs));
        plugins.forEach(plugin -> {
            knownGavs.add(gavKey(plugin.getGroupId(), plugin.getArtifactId(), plugin.getVersion()));
            collectPomChainGavs(plugin.getParentPom(), knownGavs);
            plugin.getDependencies().forEach(node -> collectGavs(node, knownGavs));
        });
        extensions.forEach(extension -> {
            knownGavs.add(gavKey(extension.getGroupId(), extension.getArtifactId(), extension.getVersion()));
            collectPomChainGavs(extension.getParentPom(), knownGavs);
            extension.getDependencies().forEach(node -> collectGavs(node, knownGavs));
        });

        // Jars first: anything not already in the static graph is genuinely new.
        Set<String> newGavs = new HashSet<>();
        Set<String> newGroupIds = new HashSet<>();
        Map<String, Set<io.github.chains_project.maven_lockfile.graph.DependencyNode>> newDependenciesByPlugin =
                new HashMap<>();
        for (RecordedArtifact artifact : recorded) {
            if ("pom".equals(artifact.getExtension()) || knownGavs.contains(gavKey(artifact))) {
                continue;
            }
            newGavs.add(gavKey(artifact));
            newGroupIds.add(artifact.getGroupId());
            addDynamicDependency(newDependenciesByPlugin, artifact, checksumCalculator);
        }

        // Then POMs with no matching jar (e.g. a pom-only parent like surefire-providers),
        // restricted to groupIds already confirmed dynamic above to filter out ordinary
        // version-mediation noise.
        for (RecordedArtifact artifact : recorded) {
            if (!"pom".equals(artifact.getExtension())) {
                continue;
            }
            String key = gavKey(artifact);
            if (knownGavs.contains(key) || newGavs.contains(key) || !newGroupIds.contains(artifact.getGroupId())) {
                continue;
            }
            addDynamicDependency(newDependenciesByPlugin, artifact, checksumCalculator);
        }

        if (newDependenciesByPlugin.isEmpty()) {
            return plugins;
        }

        Set<MavenPlugin> result = new TreeSet<>();
        for (MavenPlugin plugin : plugins) {
            String pluginKey = gavKey(
                    plugin.getGroupId().getValue(), plugin.getArtifactId().getValue());
            Set<io.github.chains_project.maven_lockfile.graph.DependencyNode> newDeps =
                    newDependenciesByPlugin.remove(pluginKey);
            if (newDeps == null) {
                result.add(plugin);
                continue;
            }
            Set<io.github.chains_project.maven_lockfile.graph.DependencyNode> mergedDeps = new TreeSet<>();
            mergedDeps.addAll(plugin.getDependencies());
            mergedDeps.addAll(newDeps);
            result.add(new MavenPlugin(
                    plugin.getGroupId(),
                    plugin.getArtifactId(),
                    plugin.getVersion(),
                    plugin.getResolved(),
                    plugin.getRepositoryId(),
                    plugin.getChecksumAlgorithm(),
                    plugin.getChecksum(),
                    mergedDeps,
                    plugin.getParentPom()));
        }
        newDependenciesByPlugin.forEach((pluginKey, deps) -> PluginLogManager.getLog()
                .warn(String.format(
                        "Dynamically-resolved dependencies attributed to plugin %s, which isn't part of this "
                                + "project's build plugins; dropping %d artifact(s): %s",
                        pluginKey, deps.size(), deps)));
        return result;
    }

    private static void addDynamicDependency(
            Map<String, Set<io.github.chains_project.maven_lockfile.graph.DependencyNode>> byPlugin,
            RecordedArtifact artifact,
            AbstractChecksumCalculator checksumCalculator) {
        if (artifact.getTriggeringPluginGroupId() == null || artifact.getTriggeringPluginArtifactId() == null) {
            PluginLogManager.getLog()
                    .warn("Dynamically-resolved artifact " + artifact + " has no known triggering plugin; skipping");
            return;
        }
        try {
            Artifact mavenArtifact = new DefaultArtifact(
                    artifact.getGroupId(),
                    artifact.getArtifactId(),
                    artifact.getVersion(),
                    "runtime",
                    artifact.getExtension(),
                    artifact.getClassifier(),
                    new DefaultArtifactHandler(artifact.getExtension()));
            // These artifacts are triggered by a plugin's Mojo execution (e.g. Surefire pulling in
            // its JUnit-Platform provider chain), so they were resolved via plugin repositories, not
            // the project's artifact repositories - use the plugin-scoped checksum/resolution path.
            RepositoryInformation repositoryInformation = checksumCalculator.getPluginResolvedField(mavenArtifact);
            String checksum = checksumCalculator.calculatePluginChecksum(mavenArtifact);
            io.github.chains_project.maven_lockfile.graph.DependencyNode node =
                    io.github.chains_project.maven_lockfile.graph.DependencyNode.of(
                            ArtifactId.of(artifact.getArtifactId()),
                            GroupId.of(artifact.getGroupId()),
                            VersionNumber.of(artifact.getVersion()),
                            Classifier.of(artifact.getClassifier()),
                            ArtifactType.of(artifact.getExtension()),
                            MavenScope.RUNTIME,
                            repositoryInformation.getResolvedUrl(),
                            repositoryInformation.getRepositoryId(),
                            checksumCalculator.getChecksumAlgorithm(),
                            checksum);
            String pluginKey = gavKey(artifact.getTriggeringPluginGroupId(), artifact.getTriggeringPluginArtifactId());
            byPlugin.computeIfAbsent(pluginKey, k -> new TreeSet<>()).add(node);
        } catch (Exception e) {
            PluginLogManager.getLog()
                    .warn(
                            String.format(
                                    "Could not resolve checksum for dynamically-resolved artifact %s; skipping",
                                    artifact),
                            e);
        }
    }

    private static String gavKey(RecordedArtifact artifact) {
        return artifact.getGroupId() + ":" + artifact.getArtifactId() + ":" + artifact.getVersion();
    }

    private static String gavKey(String groupId, String artifactId) {
        return groupId + ":" + artifactId;
    }

    private static void collectGavs(
            io.github.chains_project.maven_lockfile.graph.DependencyNode node, Set<String> gavs) {
        gavs.add(gavKey(node.getGroupId(), node.getArtifactId(), node.getVersion()));
        collectPomChainGavs(node.getParentPom(), gavs);
        node.getChildren().forEach(child -> collectGavs(child, gavs));
    }

    private static void collectPomChainGavs(Pom pom, Set<String> gavs) {
        while (pom != null) {
            gavs.add(gavKey(pom.getGroupId(), pom.getArtifactId(), pom.getVersion()));
            pom = pom.getParent();
        }
    }

    private static String gavKey(GroupId groupId, ArtifactId artifactId, VersionNumber version) {
        return groupId.getValue() + ":" + artifactId.getValue() + ":" + version.getValue();
    }

    private static Set<MavenExtension> getAllExtensions(
            MavenProject project,
            MavenSession session,
            DependencyCollectorBuilder dependencyCollectorBuilder,
            AbstractChecksumCalculator checksumCalculator,
            RepositorySystem repositorySystem,
            Config config) {
        Set<MavenExtension> extensions = new TreeSet<>();

        List<Extension> buildExtensions = project.getBuildExtensions();
        if (buildExtensions == null || buildExtensions.isEmpty()) {
            return extensions;
        }

        RepositorySystemSession repoSession = session.getRepositorySession();
        List<RemoteRepository> repositories = project.getRemotePluginRepositories();

        // Collect all extensions as dependencies
        List<org.eclipse.aether.graph.Dependency> extensionDependencies = buildExtensions.stream()
                .map(ext -> toExtensionDependency(ext, repositorySystem, repoSession, repositories))
                .flatMap(Optional::stream)
                .collect(Collectors.toList());

        // Resolve all extensions and their dependencies in one call
        CollectRequest collectRequest = new CollectRequest();
        collectRequest.setDependencies(extensionDependencies);
        collectRequest.setRepositories(repositories);

        DependencyRequest dependencyRequest = new DependencyRequest();
        dependencyRequest.setCollectRequest(collectRequest);

        ProjectBuilder extensionProjectBuilder = new ProjectBuilder(session, project.getPluginArtifactRepositories());

        try {
            DependencyResult dependencyResult = repositorySystem.resolveDependencies(repoSession, dependencyRequest);

            // Process each resolved extension (direct dependencies from the root)
            for (org.eclipse.aether.graph.DependencyNode node :
                    dependencyResult.getRoot().getChildren()) {
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

                RepositoryInformation repositoryInformation = checksumCalculator.getPluginResolvedField(mavenArtifact);

                Optional<MavenProject> extensionProjectOptional = extensionProjectBuilder.buildFromGav(
                        artifact.getGroupId(), artifact.getArtifactId(), artifact.getVersion());

                // Resolve extension's transitive dependencies using the existing mechanism
                Set<io.github.chains_project.maven_lockfile.graph.DependencyNode> transitiveDeps =
                        resolveComponentDependencies(
                                extensionProjectOptional.get(),
                                session,
                                project.getPluginArtifactRepositories(),
                                dependencyCollectorBuilder,
                                checksumCalculator,
                                Collections.emptyList(),
                                config);

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

    private static Optional<org.eclipse.aether.graph.Dependency> toExtensionDependency(
            Extension extension,
            RepositorySystem repositorySystem,
            RepositorySystemSession repoSession,
            List<RemoteRepository> repositories) {
        String version = extension.getVersion();
        if (version == null || version.isBlank() || isSpecialVersion(version)) {
            String requestedVersion = (version == null || version.isBlank()) ? "RELEASE" : version;
            try {
                VersionRequest request = new VersionRequest(
                        new org.eclipse.aether.artifact.DefaultArtifact(
                                extension.getGroupId(), extension.getArtifactId(), "jar", requestedVersion),
                        repositories,
                        null);
                version = repositorySystem.resolveVersion(repoSession, request).getVersion();
                PluginLogManager.getLog()
                        .warn(String.format(
                                "Extension %s:%s has no explicit version; resolved to %s",
                                extension.getGroupId(), extension.getArtifactId(), version));
            } catch (VersionResolutionException e) {
                PluginLogManager.getLog()
                        .warn(String.format(
                                "Skipping extension %s:%s: no version declared and could not resolve one",
                                extension.getGroupId(), extension.getArtifactId()));
                return Optional.empty();
            }
        }
        org.eclipse.aether.artifact.Artifact artifact = new org.eclipse.aether.artifact.DefaultArtifact(
                extension.getGroupId(), extension.getArtifactId(), "jar", version);
        return Optional.of(new org.eclipse.aether.graph.Dependency(artifact, JavaScopes.RUNTIME));
    }

    private static void resolveParentsAndBomsForDependencies(
            DependencyGraph graph,
            MavenSession session,
            MavenProject rootProject,
            AbstractChecksumCalculator checksumCalculator) {
        ProjectBuilder builder = new ProjectBuilder(session, rootProject.getRemoteArtifactRepositories());
        BomResolver bomResolver =
                new BomResolver(session, rootProject.getRemoteArtifactRepositories(), checksumCalculator);

        graph.getDependencySet().forEach(node -> {
            var projectOptional = builder.buildFromGav(
                    node.getGroupId().getValue(),
                    node.getArtifactId().getValue(),
                    node.getVersion().getValue());

            if (projectOptional.isEmpty()) {
                PluginLogManager.getLog()
                        .warn(String.format(
                                "Could not build project for dependency %s. Skipping parent and BOM resolution.",
                                node));
                return;
            }
            var mavenProject = projectOptional.get();

            if (mavenProject.hasParent()) {
                PluginLogManager.getLog().debug(String.format("Writting parent POM for dependency %s", node));

                // Optimization, not to resolve parent if already resolved
                // It has to be written in full form before lockfile v2
                Pom pom = constructRecursivePom(mavenProject.getParent(), session, checksumCalculator);
                node.setParentPom(pom);
            }

            Set<Pom> boms = bomResolver.resolveForProject(mavenProject);
            if (!boms.isEmpty()) {
                node.setBoms(boms);
            }
        });
    }

    private static Set<MavenPlugin> getAllPlugins(
            MavenProject project,
            MavenSession session,
            DependencyCollectorBuilder dependencyCollectorBuilder,
            AbstractChecksumCalculator checksumCalculator,
            Config config) {
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
        ProjectBuilder projectBuilder = new ProjectBuilder(session, project.getPluginArtifactRepositories());

        for (Artifact pluginArtifact : project.getPluginArtifacts()) {
            RepositoryInformation repositoryInformation = checksumCalculator.getPluginResolvedField(pluginArtifact);
            String pluginKey = pluginArtifact.getGroupId() + ":" + pluginArtifact.getArtifactId();
            List<Dependency> userDeclaredDeps = userPluginDependencies.getOrDefault(pluginKey, Collections.emptyList());

            Optional<MavenProject> pluginProjectOptional = projectBuilder.buildFromGav(
                    pluginArtifact.getGroupId(), pluginArtifact.getArtifactId(), pluginArtifact.getBaseVersion());

            if (pluginProjectOptional.isEmpty()) {
                PluginLogManager.getLog().warn(String.format("Could not build project for plugin %s", pluginArtifact));
                continue;
            }
            MavenProject pluginProject = pluginProjectOptional.get();

            Set<io.github.chains_project.maven_lockfile.graph.DependencyNode> pluginDependencies =
                    resolveComponentDependencies(
                            pluginProject,
                            session,
                            project.getPluginArtifactRepositories(),
                            dependencyCollectorBuilder,
                            checksumCalculator,
                            userDeclaredDeps,
                            config);

            Pom parent = resolvePluginParents(pluginProject, session, checksumCalculator);

            plugins.add(new MavenPlugin(
                    GroupId.of(pluginArtifact.getGroupId()),
                    ArtifactId.of(pluginArtifact.getArtifactId()),
                    VersionNumber.of(pluginArtifact.getVersion()),
                    repositoryInformation.getResolvedUrl(),
                    repositoryInformation.getRepositoryId(),
                    checksumCalculator.getChecksumAlgorithm(),
                    checksumCalculator.calculatePluginChecksum(pluginArtifact),
                    pluginDependencies,
                    parent));
        }
        return plugins;
    }

    private static Pom resolvePluginParents(
            MavenProject pluginProject, MavenSession session, AbstractChecksumCalculator checksumCalculator) {
        if (!pluginProject.hasParent()) {
            return null;
        }
        return constructRecursivePom(pluginProject.getParent(), session, checksumCalculator);
    }

    /**
     * Resolve the dependencies of a Maven plugin.
     *
     * @param pluginProject              The plugin project to resolve dependencies for
     * @param session                    The Maven session
     * @param repositories               The repositories to use for resolving dependencies
     * @param dependencyCollectorBuilder The dependency collector builder
     * @param checksumCalculator         The checksum calculator
     * @param userDeclaredDeps           User-declared dependencies for this plugin (from the project's pom.xml)
     * @return A set of dependency nodes representing the plugin's dependencies
     */
    private static Set<io.github.chains_project.maven_lockfile.graph.DependencyNode> resolveComponentDependencies(
            MavenProject pluginProject,
            MavenSession session,
            List<ArtifactRepository> repositories,
            DependencyCollectorBuilder dependencyCollectorBuilder,
            AbstractChecksumCalculator checksumCalculator,
            List<Dependency> userDeclaredDeps,
            Config config) {
        PluginLogManager.getLog()
                .debug(String.format("Attempting to resolve dependencies for plugin %s", pluginProject.getArtifact()));
        try {

            int declaredDeps = pluginProject.getDependencies() != null
                    ? pluginProject.getDependencies().size()
                    : 0;
            PluginLogManager.getLog()
                    .debug(String.format(
                            "Built plugin project %s with %d declared dependencies",
                            pluginProject.getArtifact(), declaredDeps));

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
                                        "Adding user-declared dependency %s to plugin %s",
                                        key, pluginProject.getArtifact()));
                    }
                    pluginDeps.add(userDep);
                }
                pluginProject.setDependencies(pluginDeps);
                PluginLogManager.getLog()
                        .debug(String.format(
                                "Plugin %s now has %d dependencies after merging user-declared dependencies",
                                pluginProject.getArtifact(), pluginDeps.size()));
            }

            // Filter artifacts to "compile+runtime" scopes. Maven plugins require their runtime
            // scope dependencies to be present alongside any compile-time dependencies.
            // Test scope dependencies of plugins should be excluded.
            ArtifactFilter filter = new ScopeArtifactFilter("compile+runtime");
            DependencyGraph dependencyGraph = createDependencyGraph(
                    pluginProject,
                    session,
                    repositories,
                    dependencyCollectorBuilder,
                    checksumCalculator,
                    filter,
                    false);

            if (config.isIncludeParentPom() || config.isIncludeBoms()) {
                resolveParentsAndBomsForDependencies(dependencyGraph, session, pluginProject, checksumCalculator);
            }

            // Get root dependency nodes (excluding the plugin project itself)
            Set<io.github.chains_project.maven_lockfile.graph.DependencyNode> roots = dependencyGraph.getRoots();
            PluginLogManager.getLog()
                    .info(String.format(
                            "Resolved %4d dependencies for plugin %s", roots.size(), pluginProject.getArtifact()));
            return roots;

        } catch (Exception e) {
            PluginLogManager.getLog()
                    .warn(
                            String.format("Could not resolve dependencies for plugin %s", pluginProject.getArtifact()),
                            e);
            return Collections.emptySet();
        }
    }

    private static DependencyGraph createDependencyGraph(
            MavenProject project,
            MavenSession session,
            List<ArtifactRepository> repositories,
            DependencyCollectorBuilder dependencyCollectorBuilder,
            AbstractChecksumCalculator checksumCalculator,
            ArtifactFilter filter,
            boolean reduced) {
        try {
            ProjectBuildingRequest buildingRequest =
                    new DefaultProjectBuildingRequest(session.getProjectBuildingRequest());
            buildingRequest.setProject(project);
            buildingRequest.setRemoteRepositories(repositories);

            DependencyNode rootNode = dependencyCollectorBuilder.collectDependencyGraph(buildingRequest, filter);

            MutableGraph<DependencyNode> graph = GraphBuilder.directed().build();
            rootNode.accept(new GraphBuildingNodeVisitor(graph, project));

            PluginLogManager.getLog()
                    .info(String.format(
                            "Resolved %4d dependencies for project %s",
                            graph.nodes().size(), project.getArtifactId()));

            // Reactor-local SNAPSHOTs are rebuilt every run, so their checksums drift and
            // are skipped during recording/validation.
            return DependencyGraph.of(graph, checksumCalculator, reduced, reactorGavs(session));
        } catch (DependencyCollectorBuilderException e) {
            PluginLogManager.getLog().warn("Could not generate graph", e);
            return DependencyGraph.of(GraphBuilder.directed().build(), checksumCalculator, reduced, Set.of());
        }
    }

    /**
     * Construct a Pom object containing a full tree of its parent POM references. These parent
     * POMs may be relative to the project being built, or are specified from an external POM.
     */
    private static Pom constructRecursivePom(
            MavenProject initialProject, MavenSession session, AbstractChecksumCalculator checksumCalculator) {
        String checksumAlgorithm = checksumCalculator.getChecksumAlgorithm();

        BomResolver bomResolver =
                new BomResolver(session, initialProject.getRemoteArtifactRepositories(), checksumCalculator);
        Set<String> reactorGavs = reactorGavs(session);
        List<MavenProject> recursiveProjects = new ArrayList<>();
        MavenProject currentProject = initialProject;
        recursiveProjects.add(currentProject);
        while (currentProject.hasParent()) {
            currentProject = currentProject.getParent();
            recursiveProjects.add(currentProject);
        }

        @SuppressWarnings("deprecation")
        Path localRepoBasePath =
                session.getRepositorySession().getLocalRepository().getBasedir().toPath();

        Pom lastPom = null;
        Collections.reverse(recursiveProjects);
        for (MavenProject project : recursiveProjects) {
            boolean cachedInLocalRepo =
                    project.getFile() != null && project.getFile().toPath().startsWith(localRepoBasePath);
            boolean isExternalPom = project.getFile() == null || cachedInLocalRepo;

            String relativePath = isExternalPom
                    ? null
                    : StreamSupport.stream(
                                    initialProject
                                            .getBasedir()
                                            .toPath()
                                            .relativize(project.getFile().toPath())
                                            .spliterator(),
                                    false)
                            .map(Path::toString)
                            .collect(Collectors.joining("/"));

            String checksum;
            ResolvedUrl resolved = null;
            RepositoryId repoId = null;
            if (reactorGavs.contains(
                    project.getGroupId() + ":" + project.getArtifactId() + ":" + project.getVersion())) {
                // Reactor-local SNAPSHOT pom is rebuilt every run, so its checksum drifts
                // and can't be pinned; leave it empty (releases still get a real checksum).
                checksum = "";
            } else if (isExternalPom) {
                // External POM (not in project directory) - get repository information
                Artifact artifact = project.getArtifact();
                // Use an explicit POM handler so getArtifactHandler().getExtension() reliably
                // returns "pom", which is required for parsing _remote.repositories correctly.
                Artifact pomArtifact = new DefaultArtifact(
                        artifact.getGroupId(),
                        artifact.getArtifactId(),
                        artifact.getVersion(),
                        artifact.getScope(),
                        "pom",
                        artifact.getClassifier(),
                        new org.apache.maven.artifact.handler.DefaultArtifactHandler("pom"));
                if (cachedInLocalRepo) {
                    // Pre-set the file so getArtifactResolvedField can read _remote.repositories
                    // even if the POM-type dependency resolution fails
                    pomArtifact.setFile(project.getFile());
                    checksum = checksumCalculator.calculatePomChecksum(
                            project.getFile().toPath());
                } else {
                    checksum = checksumCalculator.calculateArtifactChecksum(pomArtifact);
                }
                RepositoryInformation repoInfo = checksumCalculator.getArtifactResolvedField(pomArtifact);
                resolved = repoInfo.getResolvedUrl();
                repoId = repoInfo.getRepositoryId();
            } else {
                checksum = checksumCalculator.calculatePomChecksum(
                        project.getFile().toPath());
            }

            Set<Pom> boms = bomResolver.resolveForProject(project);
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
            if (!boms.isEmpty()) {
                lastPom.setBoms(boms);
            }
        }

        return lastPom;
    }

    /**
     * Resolve the BOM POMs for the current project.
     *
     * @param session            The Maven session
     * @param rootProject        The current Maven project (for repository configuration)
     * @param checksumCalculator The checksum calculator
     * @return A set of BOM POMs
     */
    private static Set<Pom> resolveBoms(
            MavenSession session, MavenProject rootProject, AbstractChecksumCalculator checksumCalculator) {
        BomResolver bomResolver =
                new BomResolver(session, rootProject.getRemoteArtifactRepositories(), checksumCalculator);
        return bomResolver.resolveForProject(rootProject);
    }

    private static boolean isSpecialVersion(String version) {
        return "RELEASE".equals(version) || "LATEST".equals(version);
    }

    /**
     * GAVs of the SNAPSHOT modules built in the current reactor. If there's only one
     * project, it's the root project the pom is being generated for, not a sibling module.
     */
    static Set<String> reactorGavs(MavenSession session) {
        List<MavenProject> projects = session.getProjects();
        if (projects.size() <= 1) {
            return Set.of();
        }
        return projects.stream()
                .filter(p -> ArtifactUtils.isSnapshot(p.getVersion()))
                .map(p -> p.getGroupId() + ":" + p.getArtifactId() + ":" + p.getVersion())
                .collect(Collectors.toSet());
    }
}
