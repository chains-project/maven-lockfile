package io.github.chains_project.maven_lockfile;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.chains_project.maven_lockfile.checksum.ChecksumModes;
import io.github.chains_project.maven_lockfile.data.Config;
import org.junit.jupiter.api.Test;

class ValidateMojoTest {

    private static ValidateMojo mojo() {
        return new ValidateMojo();
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
                Config.OnMavenExtensionsValidationFailure.Error,
                Config.HermeticInclusion.Exclude);
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
    void warnsAboutGenerateParameters() {
        var log = new CapturingLog();
        var m = mojo();
        m.setLog(log);
        m.includeMavenPlugins = true;
        m.reduced = false;
        m.checksumMode = "local";

        m.warnAboutGenerateParameters();

        assertThat(log.warnings)
                .containsExactly(
                        "Parameter 'includeMavenPlugins' belongs to the 'generate' goal. 'validate' should use the"
                                + " value recorded in the lockfile. Passing it to 'validate' is deprecated and will be"
                                + " an error in 6.0.0. See https://github.com/chains-project/maven-lockfile/issues/1575",
                        "Parameter 'reduced' belongs to the 'generate' goal. 'validate' should use the value recorded"
                                + " in the lockfile. Passing it to 'validate' is deprecated and will be an error in"
                                + " 6.0.0. See https://github.com/chains-project/maven-lockfile/issues/1575",
                        "Parameter 'checksumMode' belongs to the 'generate' goal. 'validate' should use the value"
                                + " recorded in the lockfile. Passing it to 'validate' is deprecated and will be an"
                                + " error in 6.0.0. See https://github.com/chains-project/maven-lockfile/issues/1575");
    }

    @Test
    void doesNotWarnAboutUnsetOrValidateParameters() {
        var log = new CapturingLog();
        var m = mojo();
        m.setLog(log);
        m.allowEnvironmentalValidationFailure = true;
        m.allowValidationFailure = false;
        // -DchecksumAlgorithm= with no value counts as not set, as in mergeConfigWithCliArgs
        m.checksumAlgorithm = "";

        m.warnAboutGenerateParameters();

        assertThat(log.warnings).isEmpty();
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
