package io.github.chains_project.maven_lockfile.checksum;

import com.google.common.io.BaseEncoding;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Locale;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.maven.artifact.Artifact;

public abstract class AbstractChecksumCalculator {

    private static final Logger LOGGER = LogManager.getLogger(AbstractChecksumCalculator.class);

    protected String checksumAlgorithm;

    AbstractChecksumCalculator(String checksumAlgorithm) {
        if (checksumAlgorithm == null || checksumAlgorithm.isEmpty()) {
            this.checksumAlgorithm = getDefaultChecksumAlgorithm();
        } else {
            this.checksumAlgorithm = checksumAlgorithm;
        }
    }

    /**
     * @return the checksumAlgorithm
     */
    public String getChecksumAlgorithm() {
        return checksumAlgorithm;
    }

    public abstract String calculateArtifactChecksum(Artifact artifact);

    public abstract String calculatePluginChecksum(Artifact artifact);

    public abstract String getDefaultChecksumAlgorithm();

    public abstract RepositoryInformation getArtifactResolvedField(Artifact artifact);

    public abstract RepositoryInformation getPluginResolvedField(Artifact artifact);

    public String calculatePomChecksum(Path path) {
        try {
            MessageDigest messageDigest = MessageDigest.getInstance(checksumAlgorithm);
            byte[] fileBuffer = Files.readAllBytes(path);
            byte[] artifactHash = messageDigest.digest(fileBuffer);
            BaseEncoding baseEncoding = BaseEncoding.base16();
            return baseEncoding.encode(artifactHash).toLowerCase(Locale.ROOT);
        } catch (Exception e) {
            LOGGER.warn("Could not calculate checksum for pom {}", path, e);
            return "";
        }
    }
}
