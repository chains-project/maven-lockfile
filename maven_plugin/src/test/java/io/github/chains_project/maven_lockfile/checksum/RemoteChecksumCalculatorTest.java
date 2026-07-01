package io.github.chains_project.maven_lockfile.checksum;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import io.github.chains_project.maven_lockfile.data.RepositoryId;
import io.github.chains_project.maven_lockfile.data.ResolvedUrl;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.List;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.handler.DefaultArtifactHandler;
import org.apache.maven.artifact.repository.MavenArtifactRepository;
import org.apache.maven.artifact.repository.layout.DefaultRepositoryLayout;
import org.apache.maven.artifact.versioning.VersionRange;
import org.apache.maven.project.DefaultProjectBuildingRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class RemoteChecksumCalculatorTest {

    private HttpServer httpServer;
    private String serverUrl;

    @BeforeEach
    void setUp() throws IOException {
        // Start a local HTTP server for testing
        httpServer = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        int port = httpServer.getAddress().getPort();
        serverUrl = "http://localhost:" + port;
        httpServer.start();
    }

    @AfterEach
    void tearDown() {
        if (httpServer != null) {
            httpServer.stop(0);
        }
    }

    @Test
    void getArtifactResolvedField_returnsResolvedUrlForExistingArtifact() {
        // Arrange: mock a successful HEAD response for an artifact
        String groupId = "junit";
        String artifactId = "junit";
        String version = "4.13.2";
        String repositoryId = "test-repo";

        String artifactPath = "/junit/junit/4.13.2/junit-4.13.2.jar";
        httpServer.createContext(artifactPath, exchange -> {
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
}
