package io.github.chains_project.maven_lockfile;

import com.google.common.graph.Graph;
import com.google.common.graph.GraphBuilder;
import com.google.common.graph.MutableGraph;
import io.github.chains_project.maven_lockfile.data.ArtifactId;
import io.github.chains_project.maven_lockfile.data.GroupId;
import io.github.chains_project.maven_lockfile.data.LockFile;
import io.github.chains_project.maven_lockfile.data.VersionNumber;
import io.github.chains_project.maven_lockfile.graph.DependencyGraph;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.maven.plugin.logging.SystemStreamLog;
import org.apache.maven.project.MavenProject;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.DependencyFilter;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.graph.DependencyVisitor;
import org.eclipse.aether.repository.LocalRepositoryManager;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.repository.RepositoryPolicy;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.DependencyRequest;
import org.eclipse.aether.resolution.DependencyResolutionException;
import org.eclipse.aether.resolution.DependencyResult;
import org.eclipse.aether.util.artifact.JavaScopes;
import org.eclipse.aether.util.filter.DependencyFilterUtils;

/**
 * Utilities for the lock file plugin. These are shared between generating and validating the lock file.
 *
 * @author Arvid Siberov
 */
public class LockFileFacade {

    private LockFileFacade() {
        // Prevent instantiation
    }

    /**
     * Generate a lock file for a project.
     * @param project The project to generate a lock file for.
     * @return A lock file for the project.
     */
    public static Path getLockFilePath(MavenProject project) {
        return Path.of(project.getBasedir().getAbsolutePath(), "lockfile.json");
    }

    /**
     * Returns the local file that an artifact has been resolved to
     * @param artifact the artifact to be resolved
     * @return the file constituting the local artifact
     */
    public static Path getLocalArtifactPath(RepositorySystemSession repositorySystemSession, Artifact artifact) {
        LocalRepositoryManager repoManager = repositorySystemSession.getLocalRepositoryManager();
        String pathStringRelativeToBaseDirectory = repoManager.getPathForLocalArtifact(artifact);
        File localRepositoryBaseDirectory = repoManager.getRepository().getBasedir();
        File artifactFile = new File(localRepositoryBaseDirectory, pathStringRelativeToBaseDirectory);
        return Path.of(artifactFile.getAbsolutePath());
    }

    /**
     * Generate a lock file for the dependencies of a project.
     * @param project The project to generate a lock file for.
     * @param repositorySystemSession The repository system session for the project.
     * @return A lock file for the project.
     * @throws IOException if the artifact file could not be read.
     * @throws NoSuchAlgorithmException if the checksum algorithm is not supported.
     */
    public static LockFile generateLockFileFromProject(
            MavenProject project, RepositorySystemSession repositorySystemSession, RepositorySystem repoSystem) {

        // Get all the artifacts for the dependencies in the project
        for (var artifact : project.getDependencies()) {
            var graph = DependencyGraph.of(LockFileFacade.foo(project, repositorySystemSession, repoSystem));
            var roots =
                    graph.getGraph().stream().filter(v -> v.getParent() == null).collect(Collectors.toList());
            return new LockFile(
                    GroupId.of(project.getGroupId()),
                    ArtifactId.of(project.getArtifactId()),
                    VersionNumber.of(project.getVersion()),
                    roots);
        }
        return new LockFile(
                GroupId.of(project.getGroupId()),
                ArtifactId.of(project.getArtifactId()),
                VersionNumber.of(project.getVersion()),
                new ArrayList<>());
    }

    public static Graph<Artifact> generateDependencyGraphForProject(
            MavenProject project, RepositorySystemSession repositorySystemSession, RepositorySystem repoSystem)
            throws DependencyResolutionException {
        MutableGraph<Artifact> graph = GraphBuilder.directed().build();

        var root = new DefaultArtifact(project.getGroupId(), project.getArtifactId(), null, project.getVersion());
        var list = newRepositories();
        // there is a feature in maven where it will not resolve dependencies from http repositories
        list.addAll(project.getRemoteProjectRepositories().stream()
                .filter(v -> v.getUrl().contains("https"))
                .collect(Collectors.toList()));
        for (var dep : project.getDependencies()) {
            var defaultArtifact =
                    new DefaultArtifact(dep.getGroupId(), dep.getArtifactId(), dep.getType(), dep.getVersion());
            try {
                ArtifactRequest artifactRequest = new ArtifactRequest();
                artifactRequest.setArtifact(defaultArtifact);
                artifactRequest.setRepositories(list);
                var result = repoSystem.resolveArtifact(repositorySystemSession, artifactRequest);
                graph.putEdge(root, result.getArtifact());
                CollectRequest collectRequest = new CollectRequest();
                collectRequest.setRoot(new Dependency(
                        result.getArtifact(), result.getArtifact().getClassifier()));
                collectRequest.setRepositories(list);
                DependencyFilter classpathFilter = DependencyFilterUtils.classpathFilter(
                        JavaScopes.TEST,
                        JavaScopes.COMPILE,
                        JavaScopes.RUNTIME,
                        JavaScopes.SYSTEM,
                        JavaScopes.PROVIDED);
                DependencyRequest dependencyRequest = new DependencyRequest(collectRequest, classpathFilter);

                var nodes = repoSystem.resolveDependencies(repositorySystemSession, dependencyRequest);
                addNodesToGraph(graph, nodes);
                graph.removeNode(root);
            } catch (Exception e) {
                new SystemStreamLog().warn("Could not resolve artifact: " + defaultArtifact, e);
            }
        }
        return graph;
    }

    private static void addNodesToGraph(MutableGraph<Artifact> graph, DependencyResult nodes) {
        nodes.getRoot().accept(new DependencyVisitor() {
            @Override
            public boolean visitEnter(DependencyNode node) {
                node.getChildren().forEach(it -> graph.putEdge(node.getArtifact(), it.getArtifact()));
                return true;
            }

            @Override
            public boolean visitLeave(DependencyNode node) {
                return true;
            }
        });
    }

    public static List<RemoteRepository> newRepositories() {
        return new ArrayList<>(Collections.singletonList(newCentralRepository()));
    }

    private static RemoteRepository newCentralRepository() {
        return new RemoteRepository.Builder("central", "default", "https://repo.maven.apache.org/maven2/")
                .setPolicy(new RepositoryPolicy(true, "always", RepositoryPolicy.CHECKSUM_POLICY_FAIL))
                .build();
    }

    public static Graph<Artifact> foo(
            MavenProject project, RepositorySystemSession repositorySystemSession, RepositorySystem repoSystem) {
        MutableGraph<Artifact> graph = GraphBuilder.directed().build();
        var list = newRepositories();
        // there is a feature in maven where it will not resolve dependencies from http repositories
        list.addAll(project.getRemoteProjectRepositories().stream()
                .filter(v -> v.getUrl().contains("https"))
                .collect(Collectors.toList()));
        for (var dep : project.getDependencies()) {
            ArtifactRequest artifactRequest = new ArtifactRequest();
            artifactRequest.setArtifact(dependencyToArtifact(dep));
            artifactRequest.setRepositories(list);
            try {
                var result = repoSystem.resolveArtifact(repositorySystemSession, artifactRequest);
                graph.addNode(result.getArtifact());
            } catch (Exception e) {
                new SystemStreamLog().warn("Could not resolve artifact: " + dep.getArtifactId(), e);
            }
        }

        for (var dep : project.getDependencies()) {
            try {

                CollectRequest collectRequest = new CollectRequest();
                collectRequest.setRoot(new Dependency(dependencyToArtifact(dep), dep.getScope()));
                collectRequest.setRepositories(list);
                var result = repoSystem.collectDependencies(repositorySystemSession, collectRequest);
                Visitor visitor = new Visitor(repositorySystemSession, repoSystem, graph);
                var root = result.getRoot();
                root.accept(visitor);
            } catch (Exception e) {
                new SystemStreamLog().warn("Could not resolve artifact: " + dep.getArtifactId(), e);
            }
        }
        return graph;
    }

    private static Artifact dependencyToArtifact(org.apache.maven.model.Dependency dependency) {
        return new DefaultArtifact(
                dependency.getGroupId(), dependency.getArtifactId(), dependency.getType(), dependency.getVersion());
    }

    private static class Visitor implements DependencyVisitor {

        private RepositorySystemSession session;
        private RepositorySystem repoSystem;
        private MutableGraph<Artifact> graph;

        public Visitor(RepositorySystemSession session, RepositorySystem repoSystem, MutableGraph<Artifact> graph) {
            this.session = session;
            this.repoSystem = repoSystem;
            this.graph = graph;
        }

        @Override
        public boolean visitEnter(DependencyNode node) {
            var artifact = resolveArtifact(node);
            node.getChildren().forEach(it -> graph.putEdge(artifact, resolveArtifact(it)));
            return true;
        }

        @Override
        public boolean visitLeave(DependencyNode node) {
            return true;
        }
        
        /**
         * Resolves the artifact for the given node. If the artifact is not found, it will try to resolve the pom instead.
         * @param node  the node to resolve the artifact for
         * @return  the resolved artifact
         */
        private Artifact resolveArtifact(DependencyNode node) {
            try {
                ArtifactRequest artifactRequest = new ArtifactRequest();
                artifactRequest.setArtifact(node.getArtifact());
                artifactRequest.setRepositories(node.getRepositories());
                var result = repoSystem.resolveArtifact(session, artifactRequest);
                return result.getArtifact();
            } catch (Exception e) {
                try {
                    ArtifactRequest artifactRequest = new ArtifactRequest();
                    artifactRequest.setArtifact(new DefaultArtifact(
                            node.getArtifact().getGroupId(),
                            node.getArtifact().getArtifactId(),
                            node.getArtifact().getClassifier(),
                            "pom",
                            node.getArtifact().getVersion()));
                    artifactRequest.setRepositories(node.getRepositories());
                    var result = repoSystem.resolveArtifact(session, artifactRequest);
                    return result.getArtifact();
                } catch (Exception inner) {
                    new SystemStreamLog().warn("Could not resolve artifact: " + node.getArtifact(), inner);
                }
                new SystemStreamLog().warn("Could not resolve artifact: " + node.getArtifact(), e);
            }
            // fallback to the original artifact
            return node.getArtifact();
        }
    }
}
