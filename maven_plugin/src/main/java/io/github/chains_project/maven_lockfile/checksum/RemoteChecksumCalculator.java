package io.github.chains_project.maven_lockfile.checksum;

import io.github.chains_project.maven_lockfile.data.ResolvedUrl;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Optional;

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

    private Optional<String> calculateChecksumInternal(Artifact artifact, ProjectBuildingRequest buildingRequest) {
        try {
            String groupId = artifact.getGroupId().replace(".", "/");
            String artifactId = artifact.getArtifactId();
            String version = artifact.getVersion();
            String extension = artifact.getType();
            if (extension.equals("maven-plugin")) {
                extension = "jar";
            }
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
                    return Optional.of(response.body().strip());
                }
            }

            LOGGER.warn("Artifact checksum `" + groupId + "." + artifactId + "." + version + "." + filename + "."
                    + checksumAlgorithm + "` not found among remote repositories.");
            return Optional.empty();
        } catch (Exception e) {
            LOGGER.warn("Could not resolve artifact: " + artifact.getArtifactId(), e);
            return Optional.empty();
        }
    }

    private Optional<ResolvedUrl> getResolvedFieldInternal(Artifact artifact, ProjectBuildingRequest buildingRequest) {
        try {
            String groupId = artifact.getGroupId().replace(".", "/");
            String artifactId = artifact.getArtifactId();
            String version = artifact.getVersion();
            String extension = artifact.getType();
            if (extension.equals("maven-plugin")) {
                extension = "jar";
            }
            String filename = artifactId + "-" + version + "." + extension;

            for (ArtifactRepository repository : buildingRequest.getRemoteRepositories()) {
                String url = repository.getUrl().replaceAll("/$", "") + "/" + groupId + "/" + artifactId + "/" + version
                        + "/" + filename;

                LOGGER.debug("Checking: " + url);

                HttpClient client = HttpClient.newBuilder()
                        .followRedirects(HttpClient.Redirect.ALWAYS)
                        .build();
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .method("HEAD", HttpRequest.BodyPublishers.noBody())
                        .build();
                HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());

                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    return Optional.of(ResolvedUrl.of(url));
                }
            }

            LOGGER.warn("Artifact checksum `" + groupId + "." + artifactId + "." + version + "." + filename
                    + "` not found among remote repositories.");
            return Optional.empty();
        } catch (Exception e) {
            LOGGER.warn("Could not resolve url for artifact: " + artifact.getArtifactId(), e);
            return Optional.empty();
        }
    }

    @Override
    public String calculateArtifactChecksum(Artifact artifact) {
        return calculateChecksumInternal(artifact, artifactBuildingRequest).orElse("");
    }

    @Override
    public String calculatePluginChecksum(Artifact artifact) {
        return calculateChecksumInternal(artifact, pluginBuildingRequest).orElse("");
    }

    @Override
    public String getDefaultChecksumAlgorithm() {
        return "sha1";
    }

    @Override
    public ResolvedUrl getArtifactResolvedField(Artifact artifact) {
        return getResolvedFieldInternal(artifact, artifactBuildingRequest).orElse(ResolvedUrl.Unresolved());
    }

    @Override
    public ResolvedUrl getPluginResolvedField(Artifact artifact) {
        return getResolvedFieldInternal(artifact, pluginBuildingRequest).orElse(ResolvedUrl.Unresolved());
    }
}
