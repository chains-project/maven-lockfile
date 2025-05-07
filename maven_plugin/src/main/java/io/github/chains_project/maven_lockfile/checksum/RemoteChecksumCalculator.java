package io.github.chains_project.maven_lockfile.checksum;

import com.google.common.io.BaseEncoding;
import io.github.chains_project.maven_lockfile.data.ResolvedUrl;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.MessageDigest;
import java.util.Locale;
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
        if (!(checksumAlgorithm.equals("md5")
                || checksumAlgorithm.equals("sha1")
                || checksumAlgorithm.equals("sha256")
                || checksumAlgorithm.equals("sha512"))) {
            throw new IllegalArgumentException(
                    "Invalid checksum algorithm maven central only supports md5, sha1, sha256 or sha512.");
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

            BaseEncoding baseEncoding = BaseEncoding.base16();
            HttpClient client = HttpClient.newBuilder()
                    .followRedirects(HttpClient.Redirect.ALWAYS)
                    .build();

            for (ArtifactRepository repository : buildingRequest.getRemoteRepositories()) {
                String artifactUrl = repository.getUrl().replaceAll("/$", "") + "/" + groupId + "/" + artifactId + "/"
                        + version + "/" + filename;
                String checksumUrl = artifactUrl + "." + checksumAlgorithm;

                LOGGER.debug("Checking: " + checksumUrl);

                HttpRequest checksumRequest =
                        HttpRequest.newBuilder().uri(URI.create(checksumUrl)).build();
                HttpResponse<String> checksumResponse =
                        client.send(checksumRequest, HttpResponse.BodyHandlers.ofString());

                if (checksumResponse.statusCode() >= 200 && checksumResponse.statusCode() < 300) {
                    return Optional.of(checksumResponse.body().strip());
                }

                if (checksumResponse.statusCode() == 404) {
                    HttpRequest artifactRequest = HttpRequest.newBuilder()
                            .uri(URI.create(artifactUrl))
                            .build();
                    HttpResponse<byte[]> artifactResponse =
                            client.send(artifactRequest, HttpResponse.BodyHandlers.ofByteArray());

                    if (artifactResponse.statusCode() < 200 || artifactResponse.statusCode() >= 300) {
                        continue;
                    }

                    LOGGER.info("Unable to find " + checksumAlgorithm + " checksum for " + artifact.getGroupId() + ":"
                            + artifactId + ":" + version + " on remote. Downloading and calculating locally.");

                    // Fallback to and verify downloaded artifact with sha1
                    HttpRequest artifactVerificationRequest = HttpRequest.newBuilder()
                            .uri(URI.create(artifactUrl + ".sha1"))
                            .build();
                    HttpResponse<String> artifactVerificationResponse =
                            client.send(artifactVerificationRequest, HttpResponse.BodyHandlers.ofString());

                    if (artifactVerificationResponse.statusCode() >= 200
                            && artifactVerificationResponse.statusCode() < 300) {
                        MessageDigest verificationMessageDigest = MessageDigest.getInstance("sha1");
                        String sha1 = baseEncoding
                                .encode(verificationMessageDigest.digest(artifactResponse.body()))
                                .toLowerCase(Locale.ROOT);

                        if (!sha1.equals(artifactVerificationResponse.body().strip())) {
                            LOGGER.error("Invalid sha1 checksum for: " + artifactUrl);
                        }
                    } else {
                        LOGGER.warn("Unable to find sha1 to verify download of: " + artifactUrl);
                    }

                    MessageDigest messageDigest = MessageDigest.getInstance(checksumAlgorithm);
                    String checksum = baseEncoding
                            .encode(messageDigest.digest(artifactResponse.body()))
                            .toLowerCase(Locale.ROOT);
                    return Optional.of(checksum);
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

            HttpClient client = HttpClient.newBuilder()
                    .followRedirects(HttpClient.Redirect.ALWAYS)
                    .build();

            for (ArtifactRepository repository : buildingRequest.getRemoteRepositories()) {
                String url = repository.getUrl().replaceAll("/$", "") + "/" + groupId + "/" + artifactId + "/" + version
                        + "/" + filename;

                LOGGER.debug("Checking: " + url);

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
