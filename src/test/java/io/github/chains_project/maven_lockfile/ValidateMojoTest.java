package io.github.chains_project.maven_lockfile;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.chains_project.maven_lockfile.checksum.ChecksumModes;
import io.github.chains_project.maven_lockfile.data.Config;
import org.apache.maven.plugin.MojoExecutionException;
import org.junit.jupiter.api.Test;

class ValidateMojoTest {

    private static AbstractLockfileMojo mojo() {
        return new AbstractLockfileMojo() {
            @Override
            public void execute() throws MojoExecutionException {}
        };
    }

    private static Config storedConfig() {
        return new Config(
                Config.MavenPluginsInclusion.Exclude,
                Config.OnValidationFailure.Error,
                Config.OnPomValidationFailure.Warn,
                Config.OnMavenPluginValidationFailure.Error,
                Config.OnEnvironmentalValidationFailure.Error,
                Config.EnvironmentInclusion.Include,
                Config.ReductionState.NonReduced,
                "5.16.0",
                ChecksumModes.LOCAL,
                "SHA-256",
                Config.BomsInclusion.Exclude,
                Config.OnBomValidationFailure.Error,
                Config.ParentPomInclusion.Exclude,
                Config.OnParentPomValidationFailure.Error,
                Config.MavenExtensionsInclusion.Exclude,
                Config.OnMavenExtensionsValidationFailure.Error);
    }

    @Test
    void cliArgOverridesStoredConfig() {
        var m = mojo();
        m.allowEnvironmentalValidationFailure = true;

        Config merged = m.mergeConfigWithCliArgs(storedConfig());

        assertThat(merged.getOnEnvironmentalValidationFailure())
                .isEqualTo(Config.OnEnvironmentalValidationFailure.Warn);
        // Unset CLI args preserve stored values
        assertThat(merged.getOnValidationFailure()).isEqualTo(Config.OnValidationFailure.Error);
        assertThat(merged.getOnPomValidationFailure()).isEqualTo(Config.OnPomValidationFailure.Warn);
    }

    @Test
    void storedConfigUsedWhenCliArgNotSet() {
        var m = mojo();
        assertThat(m.allowEnvironmentalValidationFailure).isNull();

        Config merged = m.mergeConfigWithCliArgs(storedConfig());
        assertThat(merged.getOnEnvironmentalValidationFailure())
                .isEqualTo(Config.OnEnvironmentalValidationFailure.Error);
        assertThat(merged.getOnMavenPluginValidationFailure()).isEqualTo(Config.OnMavenPluginValidationFailure.Error);
    }
}
