package io.github.chains_project.maven_lockfile.data;

import io.github.chains_project.maven_lockfile.checksum.ChecksumModes;
import io.github.chains_project.maven_lockfile.checksum.FileSystemChecksumCalculator;

public class Config {

    private final boolean includeMavenPlugins;
    private final boolean allowValidationFailure;
    private final boolean allowPomValidationFailure;
    private final boolean allowEnvironmentalValidationFailure;
    private final boolean includeEnvironment;
    private final boolean reduced;
    private final String mavenLockfileVersion;
    private final ChecksumModes checksumMode;
    private final String checksumAlgorithm;

    public Config(
            MavenPluginsInclusion includeMavenPlugins,
            OnValidationFailure allowValidationFailure,
            OnPomValidationFailure allowPomValidationFailure,
            OnEnvironmentalValidationFailure allowEnvironmentalValidationFailure,
            EnvironmentInclusion includeEnvironment,
            ReductionState reduced,
            String mavenLockfileVersion,
            ChecksumModes checksumMode,
            String checksumAlgorithm) {
        this.includeMavenPlugins = includeMavenPlugins.equals(MavenPluginsInclusion.Include);
        this.allowValidationFailure = allowValidationFailure.equals(OnValidationFailure.Warn);
        this.allowPomValidationFailure = allowPomValidationFailure.equals(OnPomValidationFailure.Warn);
        this.allowEnvironmentalValidationFailure =
                allowEnvironmentalValidationFailure.equals(OnEnvironmentalValidationFailure.Warn);
        this.includeEnvironment = includeEnvironment.equals(EnvironmentInclusion.Include);
        this.reduced = reduced.equals(ReductionState.Reduced);
        this.mavenLockfileVersion = mavenLockfileVersion;
        this.checksumMode = checksumMode;
        this.checksumAlgorithm = checksumAlgorithm;
    }

    public Config() {
        this.includeMavenPlugins = false;
        this.allowValidationFailure = false;
        this.allowPomValidationFailure = false;
        this.allowEnvironmentalValidationFailure = false;
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
     * @return the mavenPluginsInclusion enum
     */
    public MavenPluginsInclusion getMavenPluginsInclusion() {
        return includeMavenPlugins ? MavenPluginsInclusion.Include : MavenPluginsInclusion.Exclude;
    }
    /**
     * @return the allowValidationFailure
     */
    public boolean isAllowValidationFailure() {
        return allowValidationFailure;
    }
    /**
     * @return the onValidationFailure enum
     */
    public OnValidationFailure getOnValidationFailure() {
        return allowValidationFailure ? OnValidationFailure.Warn : OnValidationFailure.Error;
    }
    /**
     * @return the allowPomValidationFailure
     */
    public boolean isAllowPomValidationFailure() {
        return allowPomValidationFailure;
    }
    /**
     * @return the onPomValidationFailure enum
     */
    public OnPomValidationFailure getOnPomValidationFailure() {
        return allowPomValidationFailure ? OnPomValidationFailure.Warn : OnPomValidationFailure.Error;
    }
    /**
     * @return the allowEnvironmentalValidationFailure
     */
    public boolean isAllowEnvironmentalValidationFailure() {
        return allowEnvironmentalValidationFailure;
    }
    /**
     * @return the onEnvironmentalValidationFailure enum
     */
    public OnEnvironmentalValidationFailure getOnEnvironmentalValidationFailure() {
        return allowEnvironmentalValidationFailure
                ? OnEnvironmentalValidationFailure.Warn
                : OnEnvironmentalValidationFailure.Error;
    }
    /**
     * @return the includeEnvironment
     */
    public boolean isIncludeEnvironment() {
        return includeEnvironment;
    }
    /**
     * @return the environmentInclusion enum
     */
    public EnvironmentInclusion getEnvironmentInclusion() {
        return includeEnvironment ? EnvironmentInclusion.Include : EnvironmentInclusion.Exclude;
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
    public ReductionState getReductionState() {
        return reduced ? ReductionState.Reduced : ReductionState.NonReduced;
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

    public enum MavenPluginsInclusion {
        Include,
        Exclude
    }

    public enum OnValidationFailure {
        Warn,
        Error
    }

    public enum OnPomValidationFailure {
        Warn,
        Error
    }

    public enum OnEnvironmentalValidationFailure {
        Warn,
        Error
    }

    public enum EnvironmentInclusion {
        Include,
        Exclude
    }

    public enum ReductionState {
        Reduced,
        NonReduced
    }
}
