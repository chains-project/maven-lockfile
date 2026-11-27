package io.github.chains_project.maven_lockfile;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.chains_project.maven_lockfile.checksum.ChecksumModes;
import io.github.chains_project.maven_lockfile.data.Config;
import io.github.chains_project.maven_lockfile.data.FeatureConfig;
import io.github.chains_project.maven_lockfile.data.ValidationPolicy;
import org.junit.jupiter.api.Test;

class ValidateMojoTest {

    private static ValidateMojo mojo() {
        return new ValidateMojo();
    }

    private static Config storedConfig() {
        return new Config(
                new FeatureConfig(false, ValidationPolicy.Error),
                ValidationPolicy.Error,
                new FeatureConfig(true, ValidationPolicy.Error),
                ValidationPolicy.Warn,
                Config.ReductionState.NonReduced,
                "5.16.0",
                ChecksumModes.LOCAL,
                "SHA-256",
                new FeatureConfig(false, ValidationPolicy.Error),
                new FeatureConfig(false, ValidationPolicy.Error),
                Config.HermeticInclusion.Exclude);
    }

    @Test
    void cliArgOverridesStoredConfig() {
        var m = mojo();
        m.allowEnvironmentalValidationFailure = true;

        Config merged = m.mergeConfigWithCliArgs(storedConfig());

        assertThat(merged.environment().getOnFailure()).isEqualTo(ValidationPolicy.Warn);
        // Unset CLI args preserve stored values
        assertThat(merged.getOnDependencyValidationFailure()).isEqualTo(ValidationPolicy.Error);
        assertThat(merged.getOnPomValidationFailure()).isEqualTo(ValidationPolicy.Warn);
    }

    @Test
    void storedConfigUsedWhenCliArgNotSet() {
        var m = mojo();
        assertThat(m.allowEnvironmentalValidationFailure).isNull();

        Config merged = m.mergeConfigWithCliArgs(storedConfig());
        assertThat(merged.environment().getOnFailure()).isEqualTo(ValidationPolicy.Error);
        assertThat(merged.mavenPlugins().getOnFailure()).isEqualTo(ValidationPolicy.Error);
    }
}
