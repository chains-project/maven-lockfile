package io.github.chains_project.maven_lockfile.data;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

public class ArtifactIdTest {

    @Test
    void chainsArtifactID() {
        ArtifactId artifactId = ArtifactId.of("chains");
        assertThat(artifactId.getValue()).isEqualTo("chains");
    }

    @Test
    void nullNotAllowed() {
        assertThatThrownBy(() -> ArtifactId.of(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void emptyNotAllowed() {
        assertThatThrownBy(() -> ArtifactId.of("")).isInstanceOf(IllegalArgumentException.class);
    }
}
