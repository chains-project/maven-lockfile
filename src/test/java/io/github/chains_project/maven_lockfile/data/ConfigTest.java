package io.github.chains_project.maven_lockfile.data;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.chains_project.maven_lockfile.JsonUtils;
import io.github.chains_project.maven_lockfile.checksum.ChecksumModes;
import org.junit.jupiter.api.Test;

class ConfigTest {

    private static Config config() {
        return new Config(
                Config.MavenPluginsInclusion.Exclude,
                Config.OnValidationFailure.Warn,
                Config.OnPomValidationFailure.Warn,
                Config.OnMavenPluginValidationFailure.Warn,
                Config.OnEnvironmentalValidationFailure.Warn,
                Config.EnvironmentInclusion.Exclude,
                Config.ReductionState.Reduced,
                "5.18.4",
                ChecksumModes.REMOTE,
                "SHA-512",
                Config.BomsInclusion.Include,
                Config.OnBomValidationFailure.Warn,
                Config.ParentPomInclusion.Include,
                Config.OnParentPomValidationFailure.Warn,
                Config.MavenExtensionsInclusion.Include,
                Config.OnMavenExtensionsValidationFailure.Warn,
                Config.HermeticInclusion.Include);
    }

    @Test
    void withMavenLockfileReplacesOnlyVersionAndChecksum() {
        Config original = config();

        Config pinned = original.withMavenLockfile("5.18.5", "abc123");

        assertThat(pinned.getMavenLockfileVersion()).isEqualTo("5.18.5");
        assertThat(pinned.getMavenLockfileChecksum()).isEqualTo("abc123");
        assertThat(pinned)
                .usingRecursiveComparison()
                .ignoringFields("mavenLockfileVersion", "mavenLockfileChecksum")
                .isEqualTo(original);
        assertThat(original.getMavenLockfileVersion()).isEqualTo("5.18.4");
        assertThat(original.getMavenLockfileChecksum()).isNull();
    }

    @Test
    void mavenLockfileChecksumIsSerializedNextToVersion() {
        String json = JsonUtils.toJson(config().withMavenLockfile("5.18.4", "abc123"));

        assertThat(json)
                .containsSubsequence("\"mavenLockfileVersion\": \"5.18.4\"", "\"mavenLockfileChecksum\": \"abc123\"");
        assertThat(JsonUtils.fromJson(json, Config.class).getMavenLockfileChecksum())
                .isEqualTo("abc123");
    }

    @Test
    void lockfileWithoutMavenLockfileChecksumIsStillReadable() {
        String json = JsonUtils.toJson(config());

        assertThat(json).doesNotContain("mavenLockfileChecksum");
        Config read = JsonUtils.fromJson(json, Config.class);
        assertThat(read.getMavenLockfileVersion()).isEqualTo("5.18.4");
        assertThat(read.getMavenLockfileChecksum()).isNull();
    }
}
