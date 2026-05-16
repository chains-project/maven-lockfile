package io.github.chains_project.maven_lockfile;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

public class AbstractLockfileMojoTest {

    @Test
    void mojo_boolean_parameters_use_boolean_fields() throws NoSuchFieldException {
        assertThat(AbstractLockfileMojo.class
                        .getDeclaredField("includeMavenPlugins")
                        .getType())
                .isEqualTo(boolean.class);
        assertThat(AbstractLockfileMojo.class
                        .getDeclaredField("allowValidationFailure")
                        .getType())
                .isEqualTo(boolean.class);
        assertThat(AbstractLockfileMojo.class
                        .getDeclaredField("allowPomValidationFailure")
                        .getType())
                .isEqualTo(boolean.class);
        assertThat(AbstractLockfileMojo.class
                        .getDeclaredField("allowEnvironmentalValidationFailure")
                        .getType())
                .isEqualTo(boolean.class);
        assertThat(AbstractLockfileMojo.class
                        .getDeclaredField("includeEnvironment")
                        .getType())
                .isEqualTo(boolean.class);
        assertThat(AbstractLockfileMojo.class.getDeclaredField("reduced").getType())
                .isEqualTo(boolean.class);
        assertThat(AbstractLockfileMojo.class.getDeclaredField("skip").getType())
                .isEqualTo(boolean.class);
        assertThat(GenerateLockFileMojo.class
                        .getDeclaredField("getConfigFromFile")
                        .getType())
                .isEqualTo(boolean.class);
    }
}
