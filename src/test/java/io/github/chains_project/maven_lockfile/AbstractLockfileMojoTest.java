package io.github.chains_project.maven_lockfile;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.chains_project.maven_lockfile.checksum.ChecksumModes;
import io.github.chains_project.maven_lockfile.data.Config;
import io.github.chains_project.maven_lockfile.data.FeatureConfig;
import io.github.chains_project.maven_lockfile.data.ValidationPolicy;
import org.apache.maven.model.Plugin;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.plugin.MojoExecutionException;
import org.junit.jupiter.api.Test;

class AbstractLockfileMojoTest {

    private static AbstractLockfileMojo mojo() {
        return new AbstractLockfileMojo() {
            @Override
            public void execute() throws MojoExecutionException {}
        };
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
                new FeatureConfig(true, ValidationPolicy.Error),
                new FeatureConfig(true, ValidationPolicy.Error),
                new FeatureConfig(true, ValidationPolicy.Error),
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

    @Test
    void newLockfileDefaultsToRemoteChecksumMode() {
        var m = mojo();
        var plugin = new Plugin();
        plugin.setVersion("5.18.9");
        m.mojo = new MojoExecution(plugin, "generate", "default");

        Config config = m.getConfig();

        assertThat(config.getChecksumMode()).isEqualTo(ChecksumModes.REMOTE);
    }

    @Test
    void cliChecksumModeOverridesStoredConfig() {
        // storedConfig() is already ChecksumModes.LOCAL, so exercise the override with REMOTE
        // to prove the CLI value is actually picked up rather than the stored one re-emitted.
        var m = mojo();
        m.checksumMode = "remote";
        m.checksumAlgorithm = "SHA-512";

        Config merged = m.mergeConfigWithCliArgs(storedConfig());

        assertThat(merged.getChecksumMode()).isEqualTo(ChecksumModes.REMOTE);
        assertThat(merged.getChecksumAlgorithm()).isEqualTo("SHA-512");
    }

    @Test
    void storedChecksumModeUsedWhenCliArgNotSet() {
        var m = mojo();
        assertThat(m.checksumMode).isNull();
        assertThat(m.checksumAlgorithm).isNull();

        Config merged = m.mergeConfigWithCliArgs(storedConfig());

        assertThat(merged.getChecksumMode()).isEqualTo(ChecksumModes.LOCAL);
        assertThat(merged.getChecksumAlgorithm()).isEqualTo("SHA-256");
    }
}
