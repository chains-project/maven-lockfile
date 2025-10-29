package io.github.chains_project.maven_lockfile.data;

import io.github.chains_project.maven_lockfile.checksum.ChecksumModes;
import io.github.chains_project.maven_lockfile.checksum.FileSystemChecksumCalculator;

public class Config {

    private final boolean includeMavenPlugins;
    private final boolean allowValidationFailure;
    private final boolean allowPomValidationFailure;
    private final boolean includeEnvironment;
    private final boolean reduced;
    private final String mavenLockfileVersion;
    private final ChecksumModes checksumMode;
    private final String checksumAlgorithm;

    public Config(
            IncludeMavenPlugins includeMavenPlugins,
            ValidationFailure allowValidationFailure,
            PomValidationFailure allowPomValidationFailure,
            IncludeEnvironment includeEnvironment,
            Reduced reduced,
            String mavenLockfileVersion,
            ChecksumModes checksumMode,
            String checksumAlgorithm) {
        this.includeMavenPlugins = includeMavenPlugins.equals(IncludeMavenPlugins.Include);
        this.allowValidationFailure = allowValidationFailure.equals(ValidationFailure.Warn);
        this.allowPomValidationFailure = allowPomValidationFailure.equals(PomValidationFailure.Warn);
        this.includeEnvironment = includeEnvironment.equals(IncludeEnvironment.Include);
        this.reduced = reduced.equals(Reduced.Reduced);
        this.mavenLockfileVersion = mavenLockfileVersion;
        this.checksumMode = checksumMode;
        this.checksumAlgorithm = checksumAlgorithm;
    }

    public Config() {
        this.includeMavenPlugins = true;
        this.allowValidationFailure = false;
        this.allowPomValidationFailure = false;
        this.includeEnvironment = true;
        this.reduced = false;
        this.mavenLockfileVersion = "1";
        this.checksumMode = ChecksumModes.LOCAL;
        this.checksumAlgorithm = new FileSystemChecksumCalculator(null, null, null, null).getDefaultChecksumAlgorithm();
    }
    /**
     * @return the includeMavenPlugins
     */
    public boolean isIncludeMavenPlugins() {
        return includeMavenPlugins;
    }
    /**
     * @return the includeMavenPlugins enum
     */
    public IncludeMavenPlugins getIncludeMavenPlugins() {
        return includeMavenPlugins ? IncludeMavenPlugins.Include : IncludeMavenPlugins.Exclude;
    }
    /**
     * @return the allowValidationFailure
     */
    public boolean isAllowValidationFailure() {
        return allowValidationFailure;
    }
    /**
     * @return the allowValidationFailure enum
     */
    public ValidationFailure getAllowValidationFailure() {
        return allowValidationFailure ? ValidationFailure.Warn : ValidationFailure.Error;
    }
    /**
     * @return the allowPomValidationFailure
     */
    public boolean isAllowPomValidationFailure() {
        return allowPomValidationFailure;
    }
    /**
     * @return the allowPomValidationFailure enum
     */
    public PomValidationFailure getAllowPomValidationFailure() {
        return allowPomValidationFailure ? PomValidationFailure.Warn : PomValidationFailure.Error;
    }
    /**
     * @return the includeEnvironment
     */
    public boolean isIncludeEnvironment() {
        return includeEnvironment;
    }
    /**
     * @return the includeEnvironment enum
     */
    public IncludeEnvironment getIncludeEnvironment() {
        return includeEnvironment ? IncludeEnvironment.Include : IncludeEnvironment.Exclude;
    }
    /**
     * @return the reduced
     */
    public boolean isReduced() {
        return reduced;
    }
    /**
     * @return the reduced enum
     */
    public Reduced getReduced() {
        return reduced ? Reduced.Reduced : Reduced.NonReduced;
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
    public ChecksumModes getChecksumMode() {
        return checksumMode;
    }
    /**
     * @return the checksumAlgorithm
     */
    public String getChecksumAlgorithm() {
        return checksumAlgorithm;
    }

    public enum IncludeMavenPlugins {
        Include,
        Exclude
    }

    public enum ValidationFailure {
        Warn,
        Error
    }

    public enum PomValidationFailure {
        Warn,
        Error
    }

    public enum IncludeEnvironment {
        Include,
        Exclude
    }

    public enum Reduced {
        Reduced,
        NonReduced;
    }
}
