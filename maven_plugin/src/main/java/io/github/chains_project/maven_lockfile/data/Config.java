package io.github.chains_project.maven_lockfile.data;

import io.github.chains_project.maven_lockfile.checksum.ChecksumModes;
import io.github.chains_project.maven_lockfile.checksum.FileSystemChecksumCalculator;

public class Config {

    private final boolean includeMavenPlugins;
    private final boolean allowValidationFailure;
    private final boolean includeEnvironment;
    private final boolean reduced;
    private final String mavenLockfileVersion;
    private final String checksumMode;
    private final String checksumAlgorithm;

    public Config(
            boolean includeMavenPlugins,
            boolean allowValidationFailure,
            boolean includeEnvironment,
            boolean reduced,
            String mavenLockfileVersion,
            String checksumMode,
            String checksumAlgorithm) {
        this.includeMavenPlugins = includeMavenPlugins;
        this.allowValidationFailure = allowValidationFailure;
        this.includeEnvironment = includeEnvironment;
        this.reduced = reduced;
        this.mavenLockfileVersion = mavenLockfileVersion;
        this.checksumMode = checksumMode;
        this.checksumAlgorithm = checksumAlgorithm;
    }

    public Config() {
        this.includeMavenPlugins = false;
        this.allowValidationFailure = false;
        this.includeEnvironment = true;
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
     * @return the allowValidationFailure
     */
    public boolean isAllowValidationFailure() {
        return allowValidationFailure;
    }
    /**
     * @return the includeEnvironment
     */
    public boolean isIncludeEnvironment() {
        return includeEnvironment;
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
