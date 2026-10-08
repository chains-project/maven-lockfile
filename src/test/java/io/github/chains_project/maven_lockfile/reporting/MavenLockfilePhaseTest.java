package io.github.chains_project.maven_lockfile.reporting;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.chains_project.maven_lockfile.checksum.ChecksumModes;
import io.github.chains_project.maven_lockfile.data.ArtifactId;
import io.github.chains_project.maven_lockfile.data.Config;
import io.github.chains_project.maven_lockfile.data.GroupId;
import io.github.chains_project.maven_lockfile.data.LockFile;
import io.github.chains_project.maven_lockfile.data.MetaData;
import io.github.chains_project.maven_lockfile.data.VersionNumber;
import java.util.Set;
import org.junit.jupiter.api.Test;

class MavenLockfilePhaseTest {

    private final ValidationPhase phase = new ValidationPhases.MavenLockfilePhase();

    private static Config config(Config.OnMavenPluginValidationFailure onMavenPluginValidationFailure) {
        return new Config(
                Config.MavenPluginsInclusion.Include,
                Config.OnValidationFailure.Error,
                Config.OnPomValidationFailure.Error,
                onMavenPluginValidationFailure,
                Config.OnEnvironmentalValidationFailure.Error,
                Config.EnvironmentInclusion.Exclude,
                Config.ReductionState.NonReduced,
                "5.18.4",
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

    private static LockFile lockFile(String mavenLockfileVersion, String mavenLockfileChecksum) {
        Config config = config(Config.OnMavenPluginValidationFailure.Error)
                .withMavenLockfile(mavenLockfileVersion, mavenLockfileChecksum);
        return new LockFile(
                GroupId.of("com.example"),
                ArtifactId.of("artifact"),
                VersionNumber.of("1.0"),
                null,
                Set.of(),
                Set.of(),
                Set.of(),
                new MetaData(null, config),
                Set.of());
    }

    @Test
    void passesWhenRunningPluginMatchesRecordedChecksum() {
        assertThat(phase.validate(lockFile("5.18.4", "abc"), lockFile("5.18.4", "abc"), null))
                .isEmpty();
    }

    @Test
    void failsWhenRunningPluginDiffersFromRecordedChecksum() {
        assertThat(phase.validate(lockFile("5.18.4", "abc"), lockFile("5.18.4", "tampered"), null))
                .hasValueSatisfying(msg -> assertThat(msg)
                        .contains("maven-lockfile checksum mismatch")
                        .contains("5.18.4 (SHA-256: abc)")
                        .contains("5.18.4 (SHA-256: tampered)"));
    }

    @Test
    void failsWhenRunningPluginChecksumCouldNotBeCalculated() {
        assertThat(phase.validate(lockFile("5.18.4", "abc"), lockFile("5.18.4", ""), null))
                .isPresent();
    }

    @Test
    void skipsLockfilesWithoutRecordedChecksum() {
        assertThat(phase.validate(lockFile("5.18.4", null), lockFile("5.18.4", "abc"), null))
                .isEmpty();
        assertThat(phase.validate(lockFile("5.18.4", ""), lockFile("5.18.4", "abc"), null))
                .isEmpty();
    }

    @Test
    void skipsLockfilesGeneratedByAnotherVersion() {
        assertThat(phase.validate(lockFile("5.18.3", "abc"), lockFile("5.18.4", "def"), null))
                .isEmpty();
    }

    @Test
    void warnsInsteadOfFailingWhenMavenPluginValidationFailuresAreAllowed() {
        assertThat(phase.isEnabled(config(Config.OnMavenPluginValidationFailure.Error)))
                .isTrue();
        assertThat(phase.isWarn(config(Config.OnMavenPluginValidationFailure.Warn)))
                .isTrue();
        assertThat(phase.isWarn(config(Config.OnMavenPluginValidationFailure.Error)))
                .isFalse();
    }
}
