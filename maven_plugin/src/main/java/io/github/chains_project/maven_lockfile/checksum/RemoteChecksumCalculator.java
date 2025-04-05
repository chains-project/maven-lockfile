package io.github.chains_project.maven_lockfile.checksum;

import io.github.chains_project.maven_lockfile.data.ResolvedUrl;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.apache.log4j.Logger;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.project.ProjectBuildingRequest;

public class RemoteChecksumCalculator extends AbstractChecksumCalculator {

    private static final Logger LOGGER = Logger.getLogger(RemoteChecksumCalculator.class);

    private final ProjectBuildingRequest artifactBuildingRequest;
    private final ProjectBuildingRequest pluginBuildingRequest;

    public RemoteChecksumCalculator(
            String checksumAlgorithm,
            ProjectBuildingRequest artifactBuildingRequest,
            ProjectBuildingRequest pluginBuildingRequest) {
        super(checksumAlgorithm);
        if (!(checksumAlgorithm.equals("sha1") || checksumAlgorithm.equals("md5"))) {
            throw new IllegalArgumentException("Invalid checksum algorithm maven central only supports sha1 or md5");
        }

        this.artifactBuildingRequest = artifactBuildingRequest;
        this.pluginBuildingRequest = pluginBuildingRequest;
    }

    private String calculateChecksumInternal(Artifact artifact, ProjectBuildingRequest buildingRequest) {
        try {
            String groupId = artifact.getGroupId().replace(".", "/");
            String artifactId = artifact.getArtifactId();
            String version = artifact.getVersion();
            String extension = artifact.getType();
            String filename = artifactId + "-" + version + "." + extension;

            for (ArtifactRepository repository : buildingRequest.getRemoteRepositories()) {
                String url = repository.getUrl().replaceAll("/$", "") + "/" + groupId + "/" + artifactId + "/" + version
                        + "/" + filename + "." + checksumAlgorithm;

                LOGGER.debug("Checking: " + url);

                HttpClient client = HttpClient.newBuilder()
                        .followRedirects(HttpClient.Redirect.ALWAYS)
                        .build();
                HttpRequest request =
                        HttpRequest.newBuilder().uri(URI.create(url)).build();
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    return response.body().strip();
                }
            }

            LOGGER.warn("Artifact checksum `" + groupId + "." + artifactId + "." + version + "." + filename + "."
                    + checksumAlgorithm + "` not found among remote repositories.");
            throw new RuntimeException("Artifact checksum `" + groupId + "." + artifactId + "." + version + "."
                    + filename + "." + checksumAlgorithm + "` not found among remote repositories.");
        } catch (Exception e) {
            LOGGER.warn("Could not resolve artifact: " + artifact.getArtifactId(), e);
            throw new RuntimeException("Could not resolve artifact: " + artifact.getArtifactId(), e);
        }
    }

    private ResolvedUrl getResolvedFieldInternal(Artifact artifact, ProjectBuildingRequest buildingRequest) {
        String groupId = artifact.getGroupId().replace(".", "/");
        String artifactId = artifact.getArtifactId();
        String version = artifact.getVersion();
        String extension = artifact.getType();
        String filename = artifactId + "-" + version + "." + extension;
        // return ResolvedUrl.of(CENTRAL_URL + "/" + groupId + "/" + artifactId + "/" + version + "/" + filename);
        return ResolvedUrl.Unresolved();
    }

    @Override
    public String calculateArtifactChecksum(Artifact artifact) {
        return calculateChecksumInternal(artifact, artifactBuildingRequest);
    }

    @Override
    public String calculatePluginChecksum(Artifact artifact) {
        return calculateChecksumInternal(artifact, pluginBuildingRequest);
    }

    @Override
    public String getDefaultChecksumAlgorithm() {
        return "sha1";
    }

    @Override
    public ResolvedUrl getArtifactResolvedField(Artifact artifact) {
        return getResolvedFieldInternal(artifact, artifactBuildingRequest);
    }

    @Override
    public ResolvedUrl getPluginResolvedField(Artifact artifact) {
        return getResolvedFieldInternal(artifact, pluginBuildingRequest);
    }
}
