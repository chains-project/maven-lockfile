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
            try {
                var graph = DependencyGraph.of(
                        LockFileFacade.generateDependencyGraphForProject(project, repositorySystemSession, repoSystem));
                var roots = graph.getGraph().stream()
                        .filter(v -> v.getParent() == null)
                        .collect(Collectors.toList());
                return new LockFile(
                        GroupId.of(project.getGroupId()),
                        ArtifactId.of(project.getArtifactId()),
                        VersionNumber.of(project.getVersion()),
                        roots);

            } catch (DependencyResolutionException e) {
                new SystemStreamLog().warn("Could not resolve artifact: " + artifact.getArtifactId());
            }
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

            } catch (Exception e) {
                new SystemStreamLog().warn("Could not resolve artifact: " + defaultArtifact);
            }
            CollectRequest collectRequest = new CollectRequest();

            collectRequest.setRoot(new Dependency(defaultArtifact, null));
            collectRequest.setRepositories(list);
            DependencyFilter classpathFilter = DependencyFilterUtils.classpathFilter(
                    JavaScopes.TEST, JavaScopes.COMPILE, JavaScopes.RUNTIME, JavaScopes.SYSTEM, JavaScopes.PROVIDED);
            DependencyRequest dependencyRequest = new DependencyRequest(collectRequest, classpathFilter);
            var nodes = repoSystem.resolveDependencies(repositorySystemSession, dependencyRequest);
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
            graph.removeNode(root);
        }
        return graph;
    }

    public static List<RemoteRepository> newRepositories() {
        return new ArrayList<>(Collections.singletonList(newCentralRepository()));
    }

    private static RemoteRepository newCentralRepository() {
        return new RemoteRepository.Builder("central", "default", "https://repo.maven.apache.org/maven2/")
                .setPolicy(new RepositoryPolicy(true, "always", RepositoryPolicy.CHECKSUM_POLICY_FAIL))
                .build();
    }
}
