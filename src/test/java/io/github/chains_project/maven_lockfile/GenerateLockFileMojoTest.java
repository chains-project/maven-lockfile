package io.github.chains_project.maven_lockfile;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class GenerateLockFileMojoTest {

    private static GenerateLockFileMojo mojo(CapturingLog log) {
        var m = new GenerateLockFileMojo();
        m.setLog(log);
        m.lockfileName = "lockfile.json";
        return m;
    }

    @Test
    void warnsAboutValidateParameters() {
        var log = new CapturingLog();
        var m = mojo(log);
        m.allowEnvironmentalValidationFailure = true;

        m.warnAboutValidateParameters();

        assertThat(log.warnings)
                .containsExactly("Parameter 'allowEnvironmentalValidationFailure' belongs to the 'validate' goal."
                        + " Passing it to 'generate' is deprecated and will be an error in 6.0.0. To change the"
                        + " value recorded in the lockfile, edit 'allowEnvironmentalValidationFailure' under"
                        + " 'metaData.config' in lockfile.json. See"
                        + " https://github.com/chains-project/maven-lockfile/issues/1575");
    }

    @Test
    void warnsEvenWhenValidateParameterIsFalse() {
        // contract: passing the parameter at all is deprecated, whatever its value
        var log = new CapturingLog();
        var m = mojo(log);
        m.allowValidationFailure = false;
        m.allowMavenExtensionsValidationFailure = false;

        m.warnAboutValidateParameters();

        assertThat(log.warnings).hasSize(2);
        assertThat(log.warnings.get(0)).startsWith("Parameter 'allowValidationFailure' belongs to the 'validate'");
        assertThat(log.warnings.get(1))
                .startsWith("Parameter 'allowMavenExtensionsValidationFailure' belongs to the 'validate'");
    }

    @Test
    void doesNotWarnAboutGenerateParameters() {
        var log = new CapturingLog();
        var m = mojo(log);
        m.includeMavenPlugins = false;
        m.hermetic = true;
        m.reduced = true;
        m.checksumMode = "local";

        m.warnAboutValidateParameters();

        assertThat(log.warnings).isEmpty();
    }
}
