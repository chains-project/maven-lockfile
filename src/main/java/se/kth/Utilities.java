package se.kth;

import org.apache.maven.project.MavenProject;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.repository.LocalRepositoryManager;

import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Set;

/**
 * Utilities for the lock file plugin. These are shared between generating and validating the lock file.
 *
 * @author Arvid Siberov
 */
public class Utilities {
    /**
     * Currently the only supported checksum algorithm.
     */
    public static final String checksumAlgorithm = "SHA-256";

    /**
     * Calculate the checksum of a file with a given path, using the specified algorithm.
     * @param artifactPath The path to the file to calculate the checksum of.
     * @param algorithm The algorithm to use for calculating the checksum, e.g. "SHA-256".
     *                  Should be a valid argument to <code>MessageDigest.getInstance()</code>
     * @return A string of the hexadecimal representation of the checksum.
     * @throws IOException if the path is not a file, or the file could not be read.
     * @throws NoSuchAlgorithmException if the algorithm is not supported.
     */
    public static String calculateChecksum(Path artifactPath, String algorithm) throws IOException, NoSuchAlgorithmException {
        if (!artifactPath.toFile().isFile()) {
            throw new IOException("Artifact path is not a file: " + artifactPath);
        }
        MessageDigest messageDigest = MessageDigest.getInstance(algorithm);
        byte[] fileBuffer = Files.readAllBytes(artifactPath);
        byte[] artifactHash = messageDigest.digest(fileBuffer);
        String checksum = new BigInteger(1, artifactHash).toString(16);
        return checksum;
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
    public static LockFile generateLockFileFromProject(MavenProject project, RepositorySystemSession repositorySystemSession)
            throws IOException, NoSuchAlgorithmException {
        LockFile lockFile = new LockFile();

        // Get all the artifacts for the dependencies in the project
        Set<org.apache.maven.artifact.Artifact> dependencyArtifacts = project.getDependencyArtifacts();

        for (var a : dependencyArtifacts) {
            String groupId = a.getGroupId();
            String artifactId = a.getArtifactId();
            String version = a.getVersion();
            String coords = groupId + ":" + artifactId + ":" + version;
            Artifact artifact = new DefaultArtifact(coords);
            Path path = getLocalArtifactPath(repositorySystemSession, artifact);
            String checksum;
            checksum = calculateChecksum(path, checksumAlgorithm);

            lockFile.dependencies.add(new LockFileDependency(groupId, artifactId, version, checksumAlgorithm, checksum));
        }

        return lockFile;
    }
}
