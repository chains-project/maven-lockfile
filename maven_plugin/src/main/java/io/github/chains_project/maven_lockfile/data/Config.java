package io.github.chains_project.maven_lockfile.data;

import io.github.chains_project.maven_lockfile.checksum.ChecksumModes;
import io.github.chains_project.maven_lockfile.checksum.FileSystemChecksumCalculator;

public class Config {

    private final boolean includeMavenPlugins;
    private final boolean reduced;
    private final String mavenLockfileVersion;
    private final String checksumMode;
    private final String checksumAlgorithm;

    public Config(
            boolean includeMavenPlugins,
            boolean reduced,
            String mavenLockfileVersion,
            String checksumMode,
            String checksumAlgorithm) {
        this.includeMavenPlugins = includeMavenPlugins;
        this.reduced = reduced;
        this.mavenLockfileVersion = mavenLockfileVersion;
        this.checksumMode = checksumMode;
        this.checksumAlgorithm = checksumAlgorithm;
    }

    public Config() {
        this.includeMavenPlugins = false;
        this.reduced = false;
        this.mavenLockfileVersion = "1";
        this.checksumMode = ChecksumModes.MAVEN_LOCAL.name();
        this.checksumAlgorithm = new FileSystemChecksumCalculator(null, null, null).getDefaultChecksumAlgorithm();
    }
    /**
     * @return the includeMavenPlugins
     */
    public boolean isIncludeMavenPlugins() {
        return includeMavenPlugins;
    }
    /**
     * @return the reduced
     */
    public boolean isReduced() {
        return reduced;
    }
    /**
     * @return the mavenLockfileVersion
     */
    public String getMavenLockfileVersion() {
        return mavenLockfileVersion;
    }
    /**
     * @return the checksumMode
     */
    public String getChecksumMode() {
        return checksumMode;
    }
    /**
     * @return the checksumAlgorithm
     */
    public String getChecksumAlgorithm() {
        return checksumAlgorithm;
    }
}
