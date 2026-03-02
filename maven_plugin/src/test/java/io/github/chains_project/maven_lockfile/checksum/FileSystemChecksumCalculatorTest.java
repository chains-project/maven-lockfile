package io.github.chains_project.maven_lockfile.checksum;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.chains_project.maven_lockfile.data.RepositoryId;
import io.github.chains_project.maven_lockfile.data.ResolvedUrl;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.handler.DefaultArtifactHandler;
import org.apache.maven.artifact.repository.ArtifactRepositoryPolicy;
import org.apache.maven.artifact.repository.MavenArtifactRepository;
import org.apache.maven.artifact.repository.layout.DefaultRepositoryLayout;
import org.apache.maven.artifact.versioning.VersionRange;
import org.apache.maven.project.DefaultProjectBuildingRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class FileSystemChecksumCalculatorTest {

    @TempDir
    Path tempDir;

    @Test
    void snapshotArtifactResolvedUrlUsesBaseVersion() throws Exception {
        // contract: for SNAPSHOT artifacts downloaded from a remote repository, the resolved
        // URL path should use the base version (e.g. "1.0-SNAPSHOT") as the directory, not
        // the timestamped version (e.g. "1.0-20231010.123456-1"), to match Maven's local
        // repository layout.
        String groupId = "com.example";
        String artifactId = "my-artifact";
        String timestampedVersion = "1.0-20231010.123456-1";
        String baseVersion = "1.0-SNAPSHOT";
        String repositoryId = "central";
        String repositoryUrl = "https://repo.maven.apache.org/maven2/";

        // Set up the fake artifact directory in the temp local repo, using baseVersion as directory
        Path artifactDir = tempDir.resolve("com/example/my-artifact/" + baseVersion);
        Files.createDirectories(artifactDir);

        // Create fake artifact file
        Path artifactFile = artifactDir.resolve(artifactId + "-" + timestampedVersion + ".jar");
        Files.createFile(artifactFile);

        // Create _remote.repositories file referencing the repository by ID
        Path remoteReposFile = artifactDir.resolve("_remote.repositories");
        Files.writeString(remoteReposFile, artifactId + "-" + timestampedVersion + ".jar>" + repositoryId + "=\n");

        // Create a SNAPSHOT artifact with a timestamped version but a SNAPSHOT base version
        DefaultArtifact artifact = new DefaultArtifact(
                groupId,
                artifactId,
                VersionRange.createFromVersion(timestampedVersion),
                "compile",
                "jar",
                null,
                new DefaultArtifactHandler("jar"));
        artifact.setBaseVersion(baseVersion);
        artifact.setFile(artifactFile.toFile());

        // Set up the building request with a remote repository
        DefaultProjectBuildingRequest buildingRequest = new DefaultProjectBuildingRequest();
        MavenArtifactRepository centralRepo = new MavenArtifactRepository(
                repositoryId,
                repositoryUrl,
                new DefaultRepositoryLayout(),
                new ArtifactRepositoryPolicy(),
                new ArtifactRepositoryPolicy());
        buildingRequest.setRemoteRepositories(List.of(centralRepo));

        // Use null resolver: resolveDependency will fail and return the original artifact unchanged
        FileSystemChecksumCalculator calculator =
                new FileSystemChecksumCalculator(null, buildingRequest, buildingRequest, "SHA-256");

        RepositoryInformation result = calculator.getArtifactResolvedField(artifact);

        // URL directory must use the baseVersion ("1.0-SNAPSHOT"), not the timestamped version
        String expectedUrl = repositoryUrl.replaceAll("/$", "") + "/com/example/my-artifact/" + baseVersion + "/"
                + artifactId + "-" + timestampedVersion + ".jar";
        assertThat(result.getResolvedUrl()).isEqualTo(ResolvedUrl.of(expectedUrl));
        assertThat(result.getRepositoryId()).isEqualTo(RepositoryId.of(repositoryId));
    }

    @Test
    void nonSnapshotArtifactResolvedUrlUsesVersion() throws Exception {
        // contract: for non-SNAPSHOT artifacts, the resolved URL path uses the same version
        // for both the directory and the filename.
        String groupId = "com.example";
        String artifactId = "my-artifact";
        String version = "1.0.0";
        String repositoryId = "central";
        String repositoryUrl = "https://repo.maven.apache.org/maven2/";

        Path artifactDir = tempDir.resolve("com/example/my-artifact/" + version);
        Files.createDirectories(artifactDir);

        Path artifactFile = artifactDir.resolve(artifactId + "-" + version + ".jar");
        Files.createFile(artifactFile);

        Path remoteReposFile = artifactDir.resolve("_remote.repositories");
        Files.writeString(remoteReposFile, artifactId + "-" + version + ".jar>" + repositoryId + "=\n");

        DefaultArtifact artifact = new DefaultArtifact(
                groupId,
                artifactId,
                VersionRange.createFromVersion(version),
                "compile",
                "jar",
                null,
                new DefaultArtifactHandler("jar"));
        artifact.setFile(artifactFile.toFile());

        DefaultProjectBuildingRequest buildingRequest = new DefaultProjectBuildingRequest();
        MavenArtifactRepository centralRepo = new MavenArtifactRepository(
                repositoryId,
                repositoryUrl,
                new DefaultRepositoryLayout(),
                new ArtifactRepositoryPolicy(),
                new ArtifactRepositoryPolicy());
        buildingRequest.setRemoteRepositories(List.of(centralRepo));

        FileSystemChecksumCalculator calculator =
                new FileSystemChecksumCalculator(null, buildingRequest, buildingRequest, "SHA-256");

        RepositoryInformation result = calculator.getArtifactResolvedField(artifact);

        String expectedUrl = repositoryUrl.replaceAll("/$", "") + "/com/example/my-artifact/" + version + "/"
                + artifactId + "-" + version + ".jar";
        assertThat(result.getResolvedUrl()).isEqualTo(ResolvedUrl.of(expectedUrl));
        assertThat(result.getRepositoryId()).isEqualTo(RepositoryId.of(repositoryId));
    }

    @Test
    void versionSuffixArtifactResolvedUrlUsesVersion() throws Exception {
        // contract: for non-SNAPSHOT artifacts with a non-semantic version suffix,
        // the resolved URL path uses the same version for both the directory and the filename.
        String groupId = "com.example";
        String artifactId = "my-artifact";
        String version = "1.0.0-redhat-0012";
        String repositoryId = "redhat-ga";
        String repositoryUrl = "https://maven.repository.redhat.com/ga/";

        Path artifactDir = tempDir.resolve("com/example/my-artifact/" + version);
        Files.createDirectories(artifactDir);

        Path artifactFile = artifactDir.resolve(artifactId + "-" + version + ".jar");
        Files.createFile(artifactFile);

        Path remoteReposFile = artifactDir.resolve("_remote.repositories");
        Files.writeString(remoteReposFile, artifactId + "-" + version + ".jar>" + repositoryId + "=\n");

        DefaultArtifact artifact = new DefaultArtifact(
                groupId,
                artifactId,
                VersionRange.createFromVersion(version),
                "compile",
                "jar",
                null,
                new DefaultArtifactHandler("jar"));
        artifact.setFile(artifactFile.toFile());

        DefaultProjectBuildingRequest buildingRequest = new DefaultProjectBuildingRequest();
        MavenArtifactRepository redHatGARepo = new MavenArtifactRepository(
                repositoryId,
                repositoryUrl,
                new DefaultRepositoryLayout(),
                new ArtifactRepositoryPolicy(),
                new ArtifactRepositoryPolicy());
        buildingRequest.setRemoteRepositories(List.of(redHatGARepo));

        FileSystemChecksumCalculator calculator =
                new FileSystemChecksumCalculator(null, buildingRequest, buildingRequest, "SHA-256");

        RepositoryInformation result = calculator.getArtifactResolvedField(artifact);

        String expectedUrl = repositoryUrl.replaceAll("/$", "") + "/com/example/my-artifact/" + version + "/"
                + artifactId + "-" + version + ".jar";
        assertThat(result.getResolvedUrl()).isEqualTo(ResolvedUrl.of(expectedUrl));
        assertThat(result.getRepositoryId()).isEqualTo(RepositoryId.of(repositoryId));
    }

}
