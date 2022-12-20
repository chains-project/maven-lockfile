package se.kth;

import org.apache.maven.project.MavenProject;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.repository.LocalRepositoryManager;

import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class Utilities {
    public static String calculateChecksum(Path artifactPath, String algorithm) throws IOException, NoSuchAlgorithmException {
        MessageDigest messageDigest = MessageDigest.getInstance(algorithm);
        byte[] fileBuffer = Files.readAllBytes(artifactPath);
        byte[] artifactHash = messageDigest.digest(fileBuffer);
        String checksum = new BigInteger(1, artifactHash).toString(16);
        return checksum;
    }

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
}
