package io.github.chains_project.maven_lockfile.data;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class VersionNumberTest {
    @Test
    void simpleVersion() {
        VersionNumber versionNumber = VersionNumber.of("1.0.0");
        assertThat(versionNumber.getValue()).isEqualTo("1.0.0");
    }

    @Test
    void snapShotVersion() {
        VersionNumber versionNumber = VersionNumber.of("3.0.0-SNAPSHOT");
        assertThat(versionNumber.getValue()).isEqualTo("3.0.0-SNAPSHOT");
    }

    @Test
    void versionWithBuildNumber() {
        VersionNumber versionNumber = VersionNumber.of("3.0.0-20201203.123456-1");
        assertThat(versionNumber.getValue()).isEqualTo("3.0.0-20201203.123456-1");
    }

    @Test
    void versionWithBuildNumberAndSnapshot() {
        VersionNumber versionNumber = VersionNumber.of("3.0.0-20201203.123456-1-SNAPSHOT");
        assertThat(versionNumber.getValue()).isEqualTo("3.0.0-20201203.123456-1-SNAPSHOT");
    }

    @Test
    void versionWithBuildNumberAndSnapshotAndBuildNumber() {
        VersionNumber versionNumber = VersionNumber.of("3.0.0-20201203.123456-1-SNAPSHOT-20201203.123456-1");
        assertThat(versionNumber.getValue()).isEqualTo("3.0.0-20201203.123456-1-SNAPSHOT-20201203.123456-1");
    }

    @Test
    void nullNotAllowed() {
        assertThatThrownBy(() -> VersionNumber.of(null)).isInstanceOf(NullPointerException.class);
    }
}
