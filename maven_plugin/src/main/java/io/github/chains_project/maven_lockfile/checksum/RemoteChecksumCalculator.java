package io.github.chains_project.maven_lockfile.checksum;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.apache.log4j.Logger;
import org.apache.maven.artifact.Artifact;

public class RemoteChecksumCalculator extends AbstractChecksumCalculator {

    private static final Logger LOGGER = Logger.getLogger(RemoteChecksumCalculator.class);
    private static final String CENTRAL_URL = "https://repo1.maven.org/maven2";

    public RemoteChecksumCalculator(String checksumAlgorithm) {
        super(checksumAlgorithm);
        if (!(checksumAlgorithm.equals("sha1") || checksumAlgorithm.equals("md5"))) {
            throw new IllegalArgumentException("Invalid checksum algorithm maven central only supports sha1 or md5");
        }
    }

    @Override
    public String calculateChecksum(Artifact artifact) {
        try {

            String groupId = artifact.getGroupId().replace(".", "/");
            String artifactId = artifact.getArtifactId();
            String version = artifact.getVersion();
            String extension = artifact.getType();
            String filename = artifactId + "-" + version + "." + extension;
            String url = CENTRAL_URL + "/" + groupId + "/" + artifactId + "/" + version + "/" + filename + "."
                    + checksumAlgorithm;
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).build();
            return client.send(request, HttpResponse.BodyHandlers.ofString()).body();

        } catch (Exception e) {
            LOGGER.warn("Could not resolve artifact: " + artifact.getArtifactId(), e);
            throw new RuntimeException("Could not resolve artifact: " + artifact.getArtifactId(), e);
        }
    }

    @Override
    public String getDefaultChecksumAlgorithm() {
        return "sha1";
    }
}
