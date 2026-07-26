package io.github.chains_project.maven_lockfile.checksum;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.io.BaseEncoding;
import com.sun.net.httpserver.HttpServer;
import io.github.chains_project.maven_lockfile.data.RepositoryId;
import io.github.chains_project.maven_lockfile.data.ResolvedUrl;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Locale;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.handler.DefaultArtifactHandler;
import org.apache.maven.artifact.repository.ArtifactRepositoryPolicy;
import org.apache.maven.artifact.repository.MavenArtifactRepository;
import org.apache.maven.artifact.repository.layout.DefaultRepositoryLayout;
import org.apache.maven.artifact.versioning.VersionRange;
import org.apache.maven.project.DefaultProjectBuildingRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RemoteChecksumCalculatorTest {

    private static final byte[] ARTIFACT = "dummy-artifact-content".getBytes(StandardCharsets.UTF_8);

    private HttpServer server;
    private String serverUrl;

    @BeforeEach
    void startServer() throws IOException {
        // Each test registers its own context on this shared local HTTP server.
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.start();
        serverUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void acceptsUpperCaseRemoteSha1() {
        // A missing remote .sha256 (404) forces a download that is verified against the .sha1 file,
        // which a proxying repository manager may serve in UPPER-CASE hex. See issue #1599.
        server.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            byte[] body = path.endsWith(".jar.sha1")
                    ? hex("SHA-1").toUpperCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8)
                    : path.endsWith(".jar") ? ARTIFACT : new byte[0];
            exchange.sendResponseHeaders(body.length == 0 ? 404 : 200, body.length == 0 ? -1 : body.length);
            try (var os = exchange.getResponseBody()) {
                os.write(body);
            }
        });

        var repository = new MavenArtifactRepository(
                "test-repo",
                serverUrl + "/",
                new DefaultRepositoryLayout(),
                new ArtifactRepositoryPolicy(),
                new ArtifactRepositoryPolicy());
        var buildingRequest = new DefaultProjectBuildingRequest();
        buildingRequest.setRemoteRepositories(List.of(repository));
        var artifact = new DefaultArtifact(
                "com.example",
                "my-artifact",
                VersionRange.createFromVersion("1.0.0"),
                "compile",
                "jar",
                null,
                new DefaultArtifactHandler("jar"));

        var calculator = new RemoteChecksumCalculator("SHA-256", buildingRequest, buildingRequest);

        // A case-sensitive comparison rejects the upper-case .sha1 and yields ""; case-insensitive
        // matching accepts the artifact and returns its SHA-256.
        assertThat(calculator.calculateArtifactChecksum(artifact)).isEqualTo(hex("SHA-256"));
    }

    @Test
    void getArtifactResolvedField_returnsResolvedUrlForExistingArtifact() {
        // Arrange: mock a successful HEAD response for an artifact
        String groupId = "junit";
        String artifactId = "junit";
        String version = "4.13.2";
        String repositoryId = "test-repo";

        String artifactPath = "/junit/junit/4.13.2/junit-4.13.2.jar";
        server.createContext(artifactPath, exchange -> {
            // Return 200 OK for HEAD request
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });

        DefaultArtifact artifact = new DefaultArtifact(
                groupId,
                artifactId,
                VersionRange.createFromVersion(version),
                "compile",
                "jar",
                "",
                new DefaultArtifactHandler("jar"));

        DefaultProjectBuildingRequest buildingRequest = new DefaultProjectBuildingRequest();
        MavenArtifactRepository testRepo = new MavenArtifactRepository();
        testRepo.setId(repositoryId);
        testRepo.setUrl(serverUrl);
        testRepo.setLayout(new DefaultRepositoryLayout());
        buildingRequest.setRemoteRepositories(List.of(testRepo));

        RemoteChecksumCalculator calculator = new RemoteChecksumCalculator("SHA-256", buildingRequest, buildingRequest);

        // Act
        RepositoryInformation result = calculator.getArtifactResolvedField(artifact);

        // Assert: existing artifact should return resolved URL and repository ID
        String expectedUrl = serverUrl + artifactPath;
        assertThat(result.getResolvedUrl()).isEqualTo(ResolvedUrl.of(expectedUrl));
        assertThat(result.getRepositoryId()).isEqualTo(RepositoryId.of(repositoryId));
    }

    private static String hex(String algorithm) {
        try {
            return BaseEncoding.base16()
                    .encode(MessageDigest.getInstance(algorithm).digest(ARTIFACT))
                    .toLowerCase(Locale.ROOT);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
