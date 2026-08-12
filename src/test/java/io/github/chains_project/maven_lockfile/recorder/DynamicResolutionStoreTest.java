package io.github.chains_project.maven_lockfile.recorder;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DynamicResolutionStoreTest {

    @TempDir
    Path tempDir;

    @Test
    void readReturnsEmptyListWhenFileMissing() throws IOException {
        Path missing = tempDir.resolve("does-not-exist.json");
        assertThat(DynamicResolutionStore.read(missing)).isEmpty();
    }

    @Test
    void writeThenReadRoundTripsArtifacts() throws IOException {
        Path path = DynamicResolutionStore.defaultPath(tempDir);
        RecordedArtifact provider = new RecordedArtifact(
                "org.apache.maven.surefire",
                "surefire-junit-platform",
                "3.2.5",
                "jar",
                null,
                "org.apache.maven.plugins",
                "maven-surefire-plugin");
        RecordedArtifact dependency = new RecordedArtifact(
                "org.apache.maven.surefire",
                "common-java5",
                "3.2.5",
                "jar",
                null,
                "org.apache.maven.plugins",
                "maven-surefire-plugin");

        DynamicResolutionStore.write(path, List.of(provider, dependency));
        List<RecordedArtifact> readBack = DynamicResolutionStore.read(path);

        assertThat(readBack).containsExactlyInAnyOrder(provider, dependency);
    }

    @Test
    void defaultPathIsRootedUnderTargetMavenLockfile() {
        Path path = DynamicResolutionStore.defaultPath(tempDir);
        assertThat(path)
                .isEqualTo(tempDir.resolve("target").resolve("maven-lockfile").resolve("dynamic-resolutions.json"));
    }
}
