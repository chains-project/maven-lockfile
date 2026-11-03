package io.github.chains_project.maven_lockfile.checksum;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.github.chains_project.maven_lockfile.data.RepositoryId;
import io.github.chains_project.maven_lockfile.data.ResolvedUrl;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.apache.maven.RepositoryUtils;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.handler.DefaultArtifactHandler;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.repository.ArtifactRepositoryPolicy;
import org.apache.maven.artifact.repository.MavenArtifactRepository;
import org.apache.maven.artifact.repository.layout.DefaultRepositoryLayout;
import org.apache.maven.artifact.versioning.VersionRange;
import org.apache.maven.project.DefaultProjectBuildingRequest;
import org.apache.maven.project.ProjectBuildingRequest;
import org.codehaus.plexus.DefaultContainerConfiguration;
import org.codehaus.plexus.DefaultPlexusContainer;
import org.codehaus.plexus.PlexusConstants;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.impl.LocalRepositoryProvider;
import org.eclipse.aether.repository.LocalArtifactRegistration;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.LocalRepositoryManager;
import org.eclipse.aether.repository.RemoteRepository;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class FileSystemChecksumCalculatorTest {

    private static DefaultPlexusContainer container;
    private static RepositorySystem repositorySystem;

    @TempDir
    Path tempDir;

    @BeforeAll
    static void startRepositorySystem() throws Exception {
        // The resolver's own local repository managers record and look up artifact origins, so
        // these tests do not depend on the format of _remote.repositories. Only they are wired, as
        // the test classpath pairs resolver-api 2 with resolver-impl 1, whose installer cannot load.
        container = new DefaultPlexusContainer(new DefaultContainerConfiguration()
                .setClassPathScanning(PlexusConstants.SCANNING_INDEX)
                .setAutoWiring(true));
        LocalRepositoryProvider localRepositoryProvider = container.lookup(LocalRepositoryProvider.class);
        repositorySystem = mock(RepositorySystem.class);
        when(repositorySystem.newLocalRepositoryManager(any(), any(LocalRepository.class)))
                .thenAnswer(invocation -> localRepositoryProvider.newLocalRepositoryManager(
                        invocation.getArgument(0), invocation.getArgument(1)));
    }

    @AfterAll
    static void stopRepositorySystem() {
        container.dispose();
    }

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

        MavenArtifactRepository centralRepo = remoteRepository(repositoryId, repositoryUrl);
        RepositorySystemSession session = newSession(tempDir);
        artifact.setFile(download(session, artifact, centralRepo).toFile());

        FileSystemChecksumCalculator calculator = newCalculator(session, centralRepo);

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

        DefaultArtifact artifact = new DefaultArtifact(
                groupId,
                artifactId,
                VersionRange.createFromVersion(version),
                "compile",
                "jar",
                null,
                new DefaultArtifactHandler("jar"));

        MavenArtifactRepository centralRepo = remoteRepository(repositoryId, repositoryUrl);
        RepositorySystemSession session = newSession(tempDir);
        artifact.setFile(download(session, artifact, centralRepo).toFile());

        FileSystemChecksumCalculator calculator = newCalculator(session, centralRepo);

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

        DefaultArtifact artifact = new DefaultArtifact(
                groupId,
                artifactId,
                VersionRange.createFromVersion(version),
                "compile",
                "jar",
                null,
                new DefaultArtifactHandler("jar"));

        MavenArtifactRepository redHatGARepo = remoteRepository(repositoryId, repositoryUrl);
        RepositorySystemSession session = newSession(tempDir);
        artifact.setFile(download(session, artifact, redHatGARepo).toFile());

        FileSystemChecksumCalculator calculator = newCalculator(session, redHatGARepo);

        RepositoryInformation result = calculator.getArtifactResolvedField(artifact);

        String expectedUrl = repositoryUrl.replaceAll("/$", "") + "/com/example/my-artifact/" + version + "/"
                + artifactId + "-" + version + ".jar";
        assertThat(result.getResolvedUrl()).isEqualTo(ResolvedUrl.of(expectedUrl));
        assertThat(result.getRepositoryId()).isEqualTo(RepositoryId.of(repositoryId));
    }

    @Test
    void emptyClassifierMeansNoClassifier() throws Exception {
        // contract: an empty classifier, which build extensions carry over from their resolver
        // artifacts, denotes no classifier: the artifact is resolved, without a classifier in its URL.
        String repositoryUrl = "https://repo.maven.apache.org/maven2/";
        DefaultArtifact artifact = new DefaultArtifact(
                "com.example",
                "my-artifact",
                VersionRange.createFromVersion("1.0.0"),
                "compile",
                "jar",
                "",
                new DefaultArtifactHandler("jar"));
        MavenArtifactRepository centralRepo = remoteRepository("central", repositoryUrl);
        RepositorySystemSession session = newSession(tempDir);
        artifact.setFile(download(session, artifact, centralRepo).toFile());

        RepositoryInformation result = newCalculator(session, centralRepo).getArtifactResolvedField(artifact);

        assertThat(result.getResolvedUrl())
                .isEqualTo(ResolvedUrl.of(repositoryUrl + "com/example/my-artifact/1.0.0/my-artifact-1.0.0.jar"));
        assertThat(result.getRepositoryId()).isEqualTo(RepositoryId.of("central"));
    }

    @Test
    void locallyInstalledArtifactUsesOriginFromUserLocalRepository() throws Exception {
        // contract: an artifact copied into an isolated local repository is recorded there as
        // locally installed. Its origin is then taken from the user's local repository, which
        // recorded the remote repository it was downloaded from.
        String repositoryUrl = "https://repo.maven.apache.org/maven2/";
        DefaultArtifact artifact = new DefaultArtifact(
                "com.example",
                "my-artifact",
                VersionRange.createFromVersion("1.0.0"),
                "compile",
                "jar",
                null,
                new DefaultArtifactHandler("jar"));
        MavenArtifactRepository centralRepo = remoteRepository("central", repositoryUrl);

        Path userRepository = tempDir.resolve("user");
        download(newSession(userRepository), artifact, centralRepo);
        RepositorySystemSession isolatedSession = newSession(tempDir.resolve("isolated"));
        artifact.setFile(install(isolatedSession, artifact).toFile());

        FileSystemChecksumCalculator calculator = new FileSystemChecksumCalculator(
                repositorySystem,
                buildingRequest(isolatedSession, centralRepo),
                buildingRequest(isolatedSession, centralRepo),
                "SHA-256",
                userRepository);

        RepositoryInformation result = calculator.getArtifactResolvedField(artifact);

        assertThat(result.getResolvedUrl())
                .isEqualTo(ResolvedUrl.of(repositoryUrl + "com/example/my-artifact/1.0.0/my-artifact-1.0.0.jar"));
        assertThat(result.getRepositoryId()).isEqualTo(RepositoryId.of("central"));
    }

    @Test
    void artifactOutsideLocalRepositoryIsUnresolved() throws Exception {
        // contract: the origin recorded in the local repository belongs to the file there. A
        // file elsewhere with the same coordinates, such as a reactor module's jar, gets none.
        DefaultArtifact artifact = new DefaultArtifact(
                "com.example",
                "my-artifact",
                VersionRange.createFromVersion("1.0.0"),
                "compile",
                "jar",
                null,
                new DefaultArtifactHandler("jar"));
        MavenArtifactRepository centralRepo = remoteRepository("central", "https://repo.maven.apache.org/maven2/");
        RepositorySystemSession session = newSession(tempDir.resolve("repository"));
        download(session, artifact, centralRepo);

        Path moduleJar = tempDir.resolve("my-artifact/target/my-artifact-1.0.0.jar");
        Files.createDirectories(moduleJar.getParent());
        Files.createFile(moduleJar);
        artifact.setFile(moduleJar.toFile());

        RepositoryInformation result = newCalculator(session, centralRepo).getArtifactResolvedField(artifact);

        assertThat(result).isEqualTo(RepositoryInformation.Unresolved());
    }

    private static MavenArtifactRepository remoteRepository(String id, String url) {
        return new MavenArtifactRepository(
                id, url, new DefaultRepositoryLayout(), new ArtifactRepositoryPolicy(), new ArtifactRepositoryPolicy());
    }

    @SuppressWarnings("deprecation") // File-based API: the tests run against Maven 3.9 (Resolver 1)
    private static RepositorySystemSession newSession(Path localRepository) {
        DefaultRepositorySystemSession session = new DefaultRepositorySystemSession();
        session.setLocalRepositoryManager(
                repositorySystem.newLocalRepositoryManager(session, new LocalRepository(localRepository.toFile())));
        return session;
    }

    private static ProjectBuildingRequest buildingRequest(
            RepositorySystemSession session, ArtifactRepository remoteRepository) {
        DefaultProjectBuildingRequest buildingRequest = new DefaultProjectBuildingRequest();
        buildingRequest.setRepositorySession(session);
        buildingRequest.setRemoteRepositories(List.of(remoteRepository));
        return buildingRequest;
    }

    private static FileSystemChecksumCalculator newCalculator(
            RepositorySystemSession session, ArtifactRepository remoteRepository) {
        ProjectBuildingRequest buildingRequest = buildingRequest(session, remoteRepository);
        return new FileSystemChecksumCalculator(repositorySystem, buildingRequest, buildingRequest, "SHA-256");
    }

    /** Stores the artifact in the session's local repository as Maven does after downloading it. */
    private static Path download(
            RepositorySystemSession session,
            org.apache.maven.artifact.Artifact artifact,
            ArtifactRepository remoteRepository)
            throws IOException {
        RemoteRepository repository = RepositoryUtils.toRepo(remoteRepository);
        LocalRepositoryManager localRepositoryManager = session.getLocalRepositoryManager();
        Artifact cached = RepositoryUtils.toArtifact(artifact);
        Path file = createFile(
                localRepositoryManager, localRepositoryManager.getPathForRemoteArtifact(cached, repository, ""));
        localRepositoryManager.add(
                session, new LocalArtifactRegistration(cached.setFile(file.toFile()), repository, List.of("")));
        return file;
    }

    /** Stores the artifact in the session's local repository as {@code mvn install} does. */
    private static Path install(RepositorySystemSession session, org.apache.maven.artifact.Artifact artifact)
            throws IOException {
        LocalRepositoryManager localRepositoryManager = session.getLocalRepositoryManager();
        Artifact installed = RepositoryUtils.toArtifact(artifact);
        Path file = createFile(localRepositoryManager, localRepositoryManager.getPathForLocalArtifact(installed));
        localRepositoryManager.add(session, new LocalArtifactRegistration(installed.setFile(file.toFile())));
        return file;
    }

    @SuppressWarnings("deprecation") // File-based API: the tests run against Maven 3.9 (Resolver 1)
    private static Path createFile(LocalRepositoryManager localRepositoryManager, String relativePath)
            throws IOException {
        Path file = localRepositoryManager.getRepository().getBasedir().toPath().resolve(relativePath);
        Files.createDirectories(file.getParent());
        return Files.createFile(file);
    }
}
