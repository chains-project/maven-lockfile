package io.github.chains_project.maven_lockfile;

import com.google.common.graph.Graph;
import com.google.common.graph.GraphBuilder;
import com.google.common.graph.MutableGraph;
import io.github.chains_project.maven_lockfile.data.ArtifactId;
import io.github.chains_project.maven_lockfile.data.GroupId;
import io.github.chains_project.maven_lockfile.data.LockFile;
import io.github.chains_project.maven_lockfile.data.VersionNumber;
import io.github.chains_project.maven_lockfile.graph.DependencyGraph;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.log4j.Logger;
import org.apache.maven.project.MavenProject;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.graph.DependencyVisitor;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.repository.RepositoryPolicy;
import org.eclipse.aether.resolution.ArtifactRequest;

/**
 * Utilities for the lock file plugin. These are shared between generating and validating the lock file.
 *
 * @author Arvid Siberov
 */
public class LockFileFacade {

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
                    LOGGER.warn("Could not resolve artifact: " + node.getArtifact(), inner);
                }
                LOGGER.warn("Could not resolve artifact: " + node.getArtifact(), e);
            }
            // fallback to the original artifact
            return node.getArtifact();
        }
    }

    private static final Logger LOGGER = Logger.getLogger(LockFileFacade.class);

    /**
     * Generate a lock file for a project.
     * @param project The project to generate a lock file for.
     * @return A lock file for the project.
     */
    public static Path getLockFilePath(MavenProject project) {
        return Path.of(project.getBasedir().getAbsolutePath(), "lockfile.json");
    }

    /**
     * Generate a lock file for the dependencies of a project.
     * @param project The project to generate a lock file for.
     * @param repositorySystemSession The repository system session for the project.
     * @return A lock file for the project.
     */
    public static LockFile generateLockFileFromProject(
            MavenProject project, RepositorySystemSession repositorySystemSession, RepositorySystem repoSystem) {
        LOGGER.info("Generating lock file for project " + project.getArtifactId());
        // Get all the artifacts for the dependencies in the project
        var graph =
                DependencyGraph.of(LockFileFacade.createDependencyGraph(project, repositorySystemSession, repoSystem));
        var roots = graph.getGraph().stream().filter(v -> v.getParent() == null).collect(Collectors.toList());
        return new LockFile(
                GroupId.of(project.getGroupId()),
                ArtifactId.of(project.getArtifactId()),
                VersionNumber.of(project.getVersion()),
                roots);
    }

    public static List<RemoteRepository> newRepositories() {
        return new ArrayList<>(Collections.singletonList(newCentralRepository()));
    }

    public static List<Graph<Artifact>> createDependencyGraph(
            MavenProject project, RepositorySystemSession repositorySystemSession, RepositorySystem repoSystem) {
        List<Graph<Artifact>> graphs = new ArrayList<>();
        var list = newRepositories();
        // there is a feature in maven where it will not resolve dependencies from http repositories
        list.addAll(project.getRemoteProjectRepositories().stream()
                .filter(v -> v.getUrl().contains("https"))
                .collect(Collectors.toList()));

        for (var dep : project.getDependencies()) {
            try {
                MutableGraph<Artifact> graph = GraphBuilder.directed().build();
                ArtifactRequest artifactRequest = new ArtifactRequest();
                artifactRequest.setArtifact(dependencyToArtifact(dep));
                artifactRequest.setRepositories(list);

                var artifact = repoSystem.resolveArtifact(repositorySystemSession, artifactRequest);
                CollectRequest collectRequest = new CollectRequest();
                collectRequest.setRoot(new Dependency(artifact.getArtifact(), dep.getScope()));
                collectRequest.setRepositories(list);
                var result = repoSystem.collectDependencies(repositorySystemSession, collectRequest);
                Visitor visitor = new Visitor(repositorySystemSession, repoSystem, graph);
                var root = result.getRoot();
                root.accept(visitor);
                if (!graph.nodes().contains(artifact.getArtifact())) {
                    graph.addNode(artifact.getArtifact());
                }
                graphs.add(graph);
            } catch (Exception e) {
                LOGGER.warn("Could not resolve artifact: " + dep.getArtifactId(), e);
            }
        }
        return graphs;
    }

    private static RemoteRepository newCentralRepository() {
        return new RemoteRepository.Builder("central", "default", "https://repo.maven.apache.org/maven2/")
                .setPolicy(new RepositoryPolicy(true, "always", RepositoryPolicy.CHECKSUM_POLICY_FAIL))
                .build();
    }

    private static Artifact dependencyToArtifact(org.apache.maven.model.Dependency dependency) {
        return new DefaultArtifact(
                dependency.getGroupId(), dependency.getArtifactId(), dependency.getType(), dependency.getVersion());
    }

    private LockFileFacade() {
        // Prevent instantiation
    }
}
