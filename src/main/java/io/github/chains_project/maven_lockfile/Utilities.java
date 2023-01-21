package io.github.chains_project.maven_lockfile;

import io.github.chains_project.maven_lockfile.data.ArtifactId;
import io.github.chains_project.maven_lockfile.data.GroupId;
import io.github.chains_project.maven_lockfile.data.LockFile;
import io.github.chains_project.maven_lockfile.data.LockFileDependency;
import io.github.chains_project.maven_lockfile.data.VersionNumber;
import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import org.apache.maven.model.Dependency;
import org.apache.maven.plugin.logging.SystemStreamLog;
import org.apache.maven.project.MavenProject;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.collection.DependencyCollectionException;
import org.eclipse.aether.repository.LocalRepositoryManager;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.ArtifactResult;

/**
 * Utilities for the lock file plugin. These are shared between generating and validating the lock file.
 *
 * @author Arvid Siberov
 */
public class Utilities {

    private Utilities() {
        // Prevent instantiation
    }
    /**
     * Currently the only supported checksum algorithm.
     */
    public static final String CHECKSUM_ALGORITHM = "SHA-256";

    /**
     * Calculate the checksum of a file with a given path, using the specified algorithm.
     * @param artifactPath The path to the file to calculate the checksum of.
     * @param algorithm The algorithm to use for calculating the checksum, e.g. "SHA-256".
     *                  Should be a valid argument to <code>MessageDigest.getInstance()</code>
     * @return A string of the hexadecimal representation of the checksum.
     * @throws IOException if the path is not a file, or the file could not be read.
     * @throws NoSuchAlgorithmException if the algorithm is not supported.
     */
    public static String calculateChecksum(Path artifactPath, String algorithm)
            throws IOException, NoSuchAlgorithmException {
        if (!artifactPath.toFile().isFile()) {
            throw new IOException("Artifact path is not a file: " + artifactPath);
        }
        MessageDigest messageDigest = MessageDigest.getInstance(algorithm);
        byte[] fileBuffer = Files.readAllBytes(artifactPath);
        byte[] artifactHash = messageDigest.digest(fileBuffer);
        return new BigInteger(1, artifactHash).toString(16);
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
            MavenProject project, RepositorySystemSession repositorySystemSession, RepositorySystem repoSystem)
            throws IOException, NoSuchAlgorithmException {

        // Get all the artifacts for the dependencies in the project
        List<Dependency> dependencyArtifacts = project.getDependencies();
        List<LockFileDependency> dependencies = new ArrayList<>();
        for (var artifact : dependencyArtifacts) {

            GroupId groupId = GroupId.of(artifact.getGroupId());
            ArtifactId artifactId = ArtifactId.of(artifact.getArtifactId());
            VersionNumber version = VersionNumber.of(artifact.getVersion());

            try {
                ArtifactRequest artifactRequest = new ArtifactRequest();
                artifactRequest.setArtifact(new DefaultArtifact(
                        groupId.getValue() + ":" + artifactId.getValue() + ":" + version.getValue()));
                artifactRequest.setRepositories(project.getRemoteProjectRepositories());
                ArtifactResult resolvedArtifact = repoSystem.resolveArtifact(repositorySystemSession, artifactRequest);
                String remoteUrl = "";
                if (resolvedArtifact.getRepository() instanceof RemoteRepository) {
                    RemoteRepository remoteRepo = (RemoteRepository) resolvedArtifact.getRepository();
                    remoteUrl = remoteRepo.getUrl();
                }
                Path path = resolvedArtifact.getArtifact().getFile().toPath();
                String checksum = calculateChecksum(path, CHECKSUM_ALGORITHM);
                ;
                dependencies.add(new LockFileDependency(
                        artifactId,
                        groupId,
                        version,
                        CHECKSUM_ALGORITHM,
                        checksum,
                        remoteUrl,
                        getDependencies(project, repositorySystemSession, repoSystem, resolvedArtifact.getArtifact())));
            } catch (ArtifactResolutionException e) {
                new SystemStreamLog().warn("Could not resolve artifact: " + artifact, e);
            }
        }
        return new LockFile(
                ArtifactId.of(project.getArtifactId()), VersionNumber.of(project.getVersion()), dependencies);
    }

    private static List<LockFileDependency> getDependencies(
            MavenProject project,
            RepositorySystemSession repositorySystemSession,
            RepositorySystem repoSystem,
            Artifact artifact) {
        List<LockFileDependency> dependencies = new ArrayList<>();
        try {
            CollectRequest collectRequest = new CollectRequest();
            collectRequest.setRoot(new org.eclipse.aether.graph.Dependency(artifact, null));
            collectRequest.setRepositories(project.getRemoteProjectRepositories());
            var result = repoSystem.collectDependencies(repositorySystemSession, collectRequest);
            for (var dependency : result.getRoot().getChildren()) {
                var artifactResult = repoSystem.resolveArtifact(
                        repositorySystemSession,
                        new ArtifactRequest(dependency.getArtifact(), project.getRemoteProjectRepositories(), null));
                var path = getPathOfArtifact(
                        repositorySystemSession,
                        GroupId.of(dependency.getArtifact().getGroupId()),
                        ArtifactId.of(dependency.getArtifact().getArtifactId()),
                        VersionNumber.of(dependency.getArtifact().getVersion()));
                var checksum = calculateChecksum(path, CHECKSUM_ALGORITHM);
                var remoteUrl = "";
                if (artifactResult.getRepository() instanceof RemoteRepository) {
                    RemoteRepository remoteRepo = (RemoteRepository) artifactResult.getRepository();
                    remoteUrl = remoteRepo.getUrl();
                }
                dependencies.add(new LockFileDependency(
                        ArtifactId.of(dependency.getArtifact().getArtifactId()),
                        GroupId.of(dependency.getArtifact().getGroupId()),
                        VersionNumber.of(dependency.getArtifact().getVersion()),
                        CHECKSUM_ALGORITHM,
                        checksum,
                        remoteUrl));
            }

        } catch (DependencyCollectionException
                | ArtifactResolutionException
                | NoSuchAlgorithmException
                | IOException e) {
            e.printStackTrace();
        }

        return dependencies;
    }

    private static Path getPathOfArtifact(
            RepositorySystemSession repositorySystemSession,
            GroupId groupId,
            ArtifactId artifactId,
            VersionNumber version) {
        String coords = groupId.getValue() + ":" + artifactId.getValue() + ":" + version.getValue();
        Artifact artifact = new DefaultArtifact(coords);
        return getLocalArtifactPath(repositorySystemSession, artifact);
    }
}
