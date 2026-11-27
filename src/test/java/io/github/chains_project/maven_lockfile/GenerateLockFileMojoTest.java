package io.github.chains_project.maven_lockfile;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.chains_project.maven_lockfile.checksum.ChecksumModes;
import io.github.chains_project.maven_lockfile.data.Config;
import io.github.chains_project.maven_lockfile.data.FeatureConfig;
import io.github.chains_project.maven_lockfile.data.ValidationPolicy;
import org.apache.maven.model.Plugin;
import org.apache.maven.plugin.MojoExecution;
import org.junit.jupiter.api.Test;

class GenerateLockFileMojoTest {

    private static GenerateLockFileMojo mojo() {
        var m = new GenerateLockFileMojo();
        var plugin = new Plugin();
        plugin.setVersion("6.0.0");
        m.mojo = new MojoExecution(plugin, "generate", "default");
        return m;
    }

    private static Config storedConfig() {
        return new Config(
                new FeatureConfig(false, ValidationPolicy.Warn),
                ValidationPolicy.Error,
                new FeatureConfig(true, ValidationPolicy.Error),
                ValidationPolicy.Warn,
                Config.ReductionState.NonReduced,
                "5.19.0",
                ChecksumModes.LOCAL,
                "SHA-256",
                new FeatureConfig(true, ValidationPolicy.Error),
                new FeatureConfig(true, ValidationPolicy.Error),
                Config.HermeticInclusion.Exclude);
    }

    @Test
    void newLockfileDefaults() {
        // contract: a new lockfile includes every feature, fails on its mismatches and uses remote checksums.
        Config config = mojo().withParameters(GenerateLockFileMojo.newLockfileConfig());

        assertThat(config.mavenPlugins().isIncluded()).isTrue();
        assertThat(config.isReduced()).isFalse();
        assertThat(config.environment().isIncluded()).isTrue();
        assertThat(config.environment().getOnFailure()).isEqualTo(ValidationPolicy.Error);
        assertThat(config.boms().isIncluded()).isTrue();
        assertThat(config.parentPom().isIncluded()).isTrue();
        assertThat(config.getChecksumMode()).isEqualTo(ChecksumModes.REMOTE);
        assertThat(config.getMavenLockfileVersion()).isEqualTo("6.0.0");
    }

    @Test
    void setParametersOverrideStoredConfig() {
        var m = mojo();
        m.includeMavenPlugins = true;
        m.reduced = true;
        m.checksumMode = "remote";
        m.checksumAlgorithm = "SHA-512";

        Config merged = m.withParameters(storedConfig());

        assertThat(merged.mavenPlugins().isIncluded()).isTrue();
        // contract: an include flag keeps the failure policy the lockfile records.
        assertThat(merged.mavenPlugins().getOnFailure()).isEqualTo(ValidationPolicy.Warn);
        assertThat(merged.isReduced()).isTrue();
        assertThat(merged.getChecksumMode()).isEqualTo(ChecksumModes.REMOTE);
        assertThat(merged.getChecksumAlgorithm()).isEqualTo("SHA-512");
    }

    @Test
    void storedConfigUsedWhenParametersNotSet() {
        Config merged = mojo().withParameters(storedConfig());

        assertThat(merged.mavenPlugins().isIncluded()).isFalse();
        assertThat(merged.isReduced()).isFalse();
        assertThat(merged.getChecksumMode()).isEqualTo(ChecksumModes.LOCAL);
        assertThat(merged.getChecksumAlgorithm()).isEqualTo("SHA-256");
        assertThat(merged.getOnPomValidationFailure()).isEqualTo(ValidationPolicy.Warn);
    }
}
