package io.github.chains_project.maven_lockfile.data;

import io.github.chains_project.maven_lockfile.checksum.ChecksumModes;

public class Config {

    private final boolean includeMavenPlugins;
    private final boolean allowValidationFailure;
    private final boolean allowPomValidationFailure;
    private final boolean allowMavenPluginValidationFailure;
    private final boolean allowEnvironmentalValidationFailure;
    private final boolean includeEnvironment;
    private final boolean reduced;
    private String mavenLockfileVersion;
    private String mavenLockfileChecksum;
    private final ChecksumModes checksumMode;
    private final String checksumAlgorithm;
    private final boolean includeBoms;
    private final boolean allowBomValidationFailure;
    private final boolean includeParentPom;
    private final boolean allowParentPomValidationFailure;
    private final boolean includeMavenExtensions;
    private final boolean allowMavenExtensionsValidationFailure;
    private final boolean hermetic;

    public Config(
            FeatureConfig mavenPlugins,
            ValidationPolicy onValidationFailure,
            FeatureConfig environment,
            ValidationPolicy onPomValidationFailure,
            ReductionState reduced,
            String mavenLockfileVersion,
            ChecksumModes checksumMode,
            String checksumAlgorithm,
            FeatureConfig boms,
            FeatureConfig parentPom,
            FeatureConfig mavenExtensions,
            HermeticInclusion hermetic) {
        this.includeMavenPlugins = mavenPlugins.isIncluded();
        this.allowMavenPluginValidationFailure = mavenPlugins.getOnFailure() == ValidationPolicy.Warn;
        this.allowValidationFailure = onValidationFailure == ValidationPolicy.Warn;
        this.allowPomValidationFailure = onPomValidationFailure == ValidationPolicy.Warn;
        this.includeEnvironment = environment.isIncluded();
        this.allowEnvironmentalValidationFailure = environment.getOnFailure() == ValidationPolicy.Warn;
        this.reduced = reduced.equals(ReductionState.Reduced);
        this.mavenLockfileVersion = mavenLockfileVersion;
        this.checksumMode = checksumMode;
        this.checksumAlgorithm = checksumAlgorithm;
        this.includeBoms = boms.isIncluded();
        this.allowBomValidationFailure = boms.getOnFailure() == ValidationPolicy.Warn;
        this.includeParentPom = parentPom.isIncluded();
        this.allowParentPomValidationFailure = parentPom.getOnFailure() == ValidationPolicy.Warn;
        this.includeMavenExtensions = mavenExtensions.isIncluded();
        this.allowMavenExtensionsValidationFailure = mavenExtensions.getOnFailure() == ValidationPolicy.Warn;
        this.hermetic = hermetic.equals(HermeticInclusion.Include);
    }

    public Config() {
        this.includeMavenPlugins = true;
        this.allowValidationFailure = false;
        this.allowPomValidationFailure = false;
        this.allowMavenPluginValidationFailure = false;
        this.allowEnvironmentalValidationFailure = false;
        this.includeEnvironment = true;
        this.reduced = false;
        this.mavenLockfileVersion = "1";
        this.checksumMode = ChecksumModes.LOCAL;
        this.checksumAlgorithm = "SHA-256";
        this.includeBoms = false;
        this.allowBomValidationFailure = false;
        this.includeParentPom = false;
        this.allowParentPomValidationFailure = false;
        this.includeMavenExtensions = false;
        this.allowMavenExtensionsValidationFailure = false;
        this.hermetic = false;
    }

    private static FeatureConfig feature(boolean include, boolean allowFailure) {
        return new FeatureConfig(include, ValidationPolicy.of(allowFailure));
    }

    public FeatureConfig mavenPlugins() {
        return feature(includeMavenPlugins, allowMavenPluginValidationFailure);
    }

    public FeatureConfig environment() {
        return feature(includeEnvironment, allowEnvironmentalValidationFailure);
    }

    public FeatureConfig boms() {
        return feature(includeBoms, allowBomValidationFailure);
    }

    public FeatureConfig parentPom() {
        return feature(includeParentPom, allowParentPomValidationFailure);
    }

    public FeatureConfig mavenExtensions() {
        return feature(includeMavenExtensions, allowMavenExtensionsValidationFailure);
    }

    public ValidationPolicy getOnValidationFailure() {
        return ValidationPolicy.of(allowValidationFailure);
    }

    public ValidationPolicy getOnPomValidationFailure() {
        return ValidationPolicy.of(allowPomValidationFailure);
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

    public void setMavenLockfileVersion(String mavenLockfileVersion) {
        this.mavenLockfileVersion = mavenLockfileVersion;
    }
    /**
     * @return the checksum of the maven-lockfile plugin artifact that generated the lockfile
     */
    public String getMavenLockfileChecksum() {
        return mavenLockfileChecksum;
    }

    public void setMavenLockfileChecksum(String mavenLockfileChecksum) {
        this.mavenLockfileChecksum = mavenLockfileChecksum;
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

    /**
     * @return whether artifacts dynamically resolved during a real build (e.g. Surefire's
     *     test-framework provider), and captured by a {@code DynamicResolutionSpy} extension
     *     attached to that build, are merged into the lockfile.
     */
    public boolean isHermetic() {
        return hermetic;
    }

    public HermeticInclusion getHermeticInclusion() {
        return hermetic ? HermeticInclusion.Include : HermeticInclusion.Exclude;
    }

    public enum ReductionState {
        Reduced,
        NonReduced
    }

    public enum HermeticInclusion {
        Include,
        Exclude
    }
}
