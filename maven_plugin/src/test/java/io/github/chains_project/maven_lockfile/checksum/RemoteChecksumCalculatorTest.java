package io.github.chains_project.maven_lockfile.checksum;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.io.BaseEncoding;
import com.sun.net.httpserver.HttpServer;
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
    private String repositoryUrl;

    @BeforeEach
    void startServer() throws Exception {
        // A missing remote .sha256 (404) forces a download that is verified against the .sha1 file,
        // which a proxying repository manager may serve in UPPER-CASE hex. See issue #1599.
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
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
        server.start();
        repositoryUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/";
    }

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void acceptsUpperCaseRemoteSha1() {
        var repository = new MavenArtifactRepository(
                "test-repo",
                repositoryUrl,
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
