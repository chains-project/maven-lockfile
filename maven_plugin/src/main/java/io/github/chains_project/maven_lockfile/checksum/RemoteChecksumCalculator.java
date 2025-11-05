package io.github.chains_project.maven_lockfile.checksum;

import com.google.common.io.BaseEncoding;
import io.github.chains_project.maven_lockfile.data.RepositoryId;
import io.github.chains_project.maven_lockfile.data.ResolvedUrl;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.project.ProjectBuildingRequest;

public class RemoteChecksumCalculator extends AbstractChecksumCalculator {

    private static final Logger LOGGER = LogManager.getLogger(RemoteChecksumCalculator.class);

    private final ProjectBuildingRequest artifactBuildingRequest;
    private final ProjectBuildingRequest pluginBuildingRequest;

    public RemoteChecksumCalculator(
            String checksumAlgorithm,
            ProjectBuildingRequest artifactBuildingRequest,
            ProjectBuildingRequest pluginBuildingRequest) {
        super(checksumAlgorithm);
        if (!(checksumAlgorithm.equals("MD5")
                || checksumAlgorithm.equals("SHA-1")
                || checksumAlgorithm.equals("SHA-256")
                || checksumAlgorithm.equals("SHA-512"))) {
            throw new IllegalArgumentException("Invalid checksum algorithm '" + checksumAlgorithm
                    + "', remote repositories only supports MD5, SHA-1, SHA-256 or SHA-512.");
        }

        this.artifactBuildingRequest = artifactBuildingRequest;
        this.pluginBuildingRequest = pluginBuildingRequest;
    }

    private Optional<String> calculateChecksumInternal(Artifact artifact, ProjectBuildingRequest buildingRequest) {
        try {
            String groupId = artifact.getGroupId().replace(".", "/");
            String artifactId = artifact.getArtifactId();
            String version = artifact.getVersion();
            String classifier = artifact.getClassifier();
            if (classifier == null) {
                classifier = "";
            } else {
                classifier = "-" + classifier;
            }
            String extension = artifact.getType();
            if (extension.equals("maven-plugin")) {
                extension = "jar";
            }
            String filename = artifactId + "-" + version + classifier + "." + extension;

            BaseEncoding baseEncoding = BaseEncoding.base16();
            HttpClient client = HttpClient.newBuilder()
                    .followRedirects(HttpClient.Redirect.ALWAYS)
                    .build();

            for (ArtifactRepository repository : buildingRequest.getRemoteRepositories()) {
                String artifactUrl = repository.getUrl().replaceAll("/$", "") + "/" + groupId + "/" + artifactId + "/"
                        + version + "/" + filename;
                String checksumUrl =
                        artifactUrl + "." + checksumAlgorithm.toLowerCase().replace("-", "");

                LOGGER.debug("Checking: {}", checksumUrl);

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

                    LOGGER.info(
                            "Unable to find {} checksum for {} on remote. Downloading and calculating locally.",
                            checksumAlgorithm,
                            artifact);

                    // Fallback to and verify downloaded artifact with SHA-1
                    HttpRequest artifactVerificationRequest = HttpRequest.newBuilder()
                            .uri(URI.create(artifactUrl + ".sha1"))
                            .build();
                    HttpResponse<String> artifactVerificationResponse =
                            client.send(artifactVerificationRequest, HttpResponse.BodyHandlers.ofString());

                    // Extract first part of string to handle sha1sum format, `hash_in_hex /path/to/file`.
                    // For example provided by:
                    //     https://repo.maven.apache.org/maven2/com/martiansoftware/jsap/2.1/jsap-2.1.jar.sha1
                    //     https://repo.maven.apache.org/maven2/javax/inject/javax.inject/1/javax.inject-1.jar.sha1
                    String artifactVerification =
                            artifactVerificationResponse.body().strip();
                    int spaceIndex = artifactVerification.indexOf(" ");
                    artifactVerification =
                            spaceIndex == -1 ? artifactVerification : artifactVerification.substring(0, spaceIndex);

                    if (artifactVerificationResponse.statusCode() >= 200
                            && artifactVerificationResponse.statusCode() < 300) {
                        MessageDigest verificationMessageDigest = MessageDigest.getInstance("SHA-1");
                        String sha1 = baseEncoding
                                .encode(verificationMessageDigest.digest(artifactResponse.body()))
                                .toLowerCase(Locale.ROOT);

                        if (!sha1.equals(artifactVerification)) {
                            LOGGER.error("Invalid SHA-1 checksum for: {}", artifactUrl);
                            throw new RuntimeException("Invalid SHA-1 checksum for '" + artifact
                                    + "'. Checksum found at '" + artifactUrl
                                    + ".sha1' does not match calculated checksum of downloaded file. Remote checksum = '"
                                    + artifactVerification + "'. Locally calculated checksum = '" + sha1 + "'.");
                        }
                    } else {
                        LOGGER.warn("Unable to find SHA-1 to verify download of: {}", artifactUrl);
                    }

                    MessageDigest messageDigest = MessageDigest.getInstance(checksumAlgorithm);
                    String checksum = baseEncoding
                            .encode(messageDigest.digest(artifactResponse.body()))
                            .toLowerCase(Locale.ROOT);
                    return Optional.of(checksum);
                }
            }

            LOGGER.warn("Artifact checksum `{}.{}` not found among remote repositories.", artifact, checksumAlgorithm);
            return Optional.empty();
        } catch (Exception e) {
            LOGGER.warn("Could not resolve artifact: {}", artifact.getArtifactId(), e);
            return Optional.empty();
        }
    }

    private Optional<RepositoryInformation> getResolvedFieldInternal(
            Artifact artifact, ProjectBuildingRequest buildingRequest) {
        try {
            String groupId = artifact.getGroupId().replace(".", "/");
            String artifactId = artifact.getArtifactId();
            String version = artifact.getVersion();
            String classifier = artifact.getClassifier();
            if (classifier == null) {
                classifier = "";
            } else {
                classifier = "-" + classifier;
            }
            String extension = artifact.getType();
            if (extension.equals("maven-plugin")) {
                extension = "jar";
            }
            String filename = artifactId + "-" + version + classifier + "." + extension;

            HttpClient client = HttpClient.newBuilder()
                    .followRedirects(HttpClient.Redirect.ALWAYS)
                    .build();

            for (ArtifactRepository repository : buildingRequest.getRemoteRepositories()) {
                String url = repository.getUrl().replaceAll("/$", "") + "/" + groupId + "/" + artifactId + "/" + version
                        + "/" + filename;

                LOGGER.debug("Checking: {}", url);

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .method("HEAD", HttpRequest.BodyPublishers.noBody())
                        .build();
                HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());

                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    return Optional.of(
                            new RepositoryInformation(ResolvedUrl.of(url), RepositoryId.of(repository.getId())));
                }
            }

            LOGGER.warn("Artifact resolved url `{}` not found.", artifact);
            return Optional.empty();
        } catch (Exception e) {
            LOGGER.warn("Could not resolve url for artifact: {}", artifact.getArtifactId(), e);
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
        return "SHA-256";
    }

    @Override
    public RepositoryInformation getArtifactResolvedField(Artifact artifact) {
        return getResolvedFieldInternal(artifact, artifactBuildingRequest).orElse(RepositoryInformation.Unresolved());
    }

    @Override
    public RepositoryInformation getPluginResolvedField(Artifact artifact) {
        return getResolvedFieldInternal(artifact, pluginBuildingRequest).orElse(RepositoryInformation.Unresolved());
    }
}
