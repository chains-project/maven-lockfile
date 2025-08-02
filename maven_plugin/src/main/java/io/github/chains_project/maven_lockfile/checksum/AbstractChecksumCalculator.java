package io.github.chains_project.maven_lockfile.checksum;

import io.github.chains_project.maven_lockfile.data.ResolvedUrl;
import java.nio.file.Path;
import org.apache.maven.artifact.Artifact;

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

    public abstract ResolvedUrl getArtifactResolvedField(Artifact artifact);

    public abstract ResolvedUrl getPluginResolvedField(Artifact artifact);

    public abstract String calculatePomChecksum(Path path);
}
