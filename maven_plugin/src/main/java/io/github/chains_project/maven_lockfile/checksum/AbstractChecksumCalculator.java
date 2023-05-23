package io.github.chains_project.maven_lockfile.checksum;

import org.apache.maven.artifact.Artifact;

public abstract class AbstractChecksumCalculator {

    protected String checksumAlgorithm;

    AbstractChecksumCalculator(String checksumAlgorithm) {
        this.checksumAlgorithm = checksumAlgorithm;
    }

    /**
     * @return the checksumAlgorithm
     */
    public String getChecksumAlgorithm() {
        return checksumAlgorithm;
    }

    public abstract String calculateChecksum(Artifact artifact);
}
