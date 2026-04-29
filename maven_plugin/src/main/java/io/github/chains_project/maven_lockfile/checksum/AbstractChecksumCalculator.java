package io.github.chains_project.maven_lockfile.checksum;

import com.google.common.io.BaseEncoding;
import io.github.chains_project.maven_lockfile.reporting.PluginLogManager;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Collection;
import java.util.Locale;
import org.apache.maven.artifact.Artifact;
import java.nio.charset.StandardCharsets;

public abstract class AbstractChecksumCalculator {

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

    /**
     * Pre-warm internal caches for the given artifacts. Implementations may use this
     * to fetch checksums and repository information in parallel. The default is a no-op.
     */
    public void prewarmArtifactCache(Collection<Artifact> artifacts) {
        // No-op by default; overridden by RemoteChecksumCalculator
    }

    public String calculatePomChecksum(Path path) {
        try {
            MessageDigest messageDigest = MessageDigest.getInstance(checksumAlgorithm);

            //normalize line endings to prevent false-positives on checksum mismatch
            byte[] normalizedBytes = Files.readString(path)
                                            .replace("\r\n", "\n")
                                            .replace("\r", "\n")
                                            .getBytes(StandardCharsets.UTF_8);
            
            byte[] artifactHash = messageDigest.digest(normalizedBytes);
            BaseEncoding baseEncoding = BaseEncoding.base16();
            return baseEncoding.encode(artifactHash).toLowerCase(Locale.ROOT);
        } catch (Exception e) {
            PluginLogManager.getLog().warn("Could not calculate checksum for pom " + path, e);
            return "";
        }
    }
}
