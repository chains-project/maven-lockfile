package io.github.chains_project.maven_lockfile.reporting;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.chains_project.maven_lockfile.checksum.ChecksumModes;
import io.github.chains_project.maven_lockfile.data.ArtifactId;
import io.github.chains_project.maven_lockfile.data.Config;
import io.github.chains_project.maven_lockfile.data.GroupId;
import io.github.chains_project.maven_lockfile.data.LockFile;
import io.github.chains_project.maven_lockfile.data.MavenPlugin;
import io.github.chains_project.maven_lockfile.data.MetaData;
import io.github.chains_project.maven_lockfile.data.Pom;
import io.github.chains_project.maven_lockfile.data.RepositoryId;
import io.github.chains_project.maven_lockfile.data.ResolvedUrl;
import io.github.chains_project.maven_lockfile.data.VersionNumber;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class ValidationPhasesTest {

    private static final GroupId GROUP_ID = GroupId.of("com.example");
    private static final ArtifactId ARTIFACT_ID = ArtifactId.of("demo");
    private static final VersionNumber VERSION = VersionNumber.of("1.0");

    @Test
    void mavenPluginValidationFailureCanWarnWithoutAllowingDependencyValidationFailures() {
        Config config = config(Config.OnValidationFailure.Error, Config.OnMavenPluginValidationFailure.Warn);
        LockFile fromFile = lockFile(Set.of(plugin("maven-compiler-plugin", "from-file")), config);
        LockFile fromProject = lockFile(Set.of(plugin("maven-compiler-plugin", "from-project")), config);

        List<Boolean> warningFailures = ValidationPhases.all().stream()
                .filter(phase -> phase.isEnabled(config))
                .filter(phase -> phase.validate(fromFile, fromProject, config).isPresent())
                .map(phase -> phase.isWarn(config))
                .collect(Collectors.toList());

        assertThat(warningFailures).containsExactly(true);
    }

    private static Config config(
            Config.OnValidationFailure onValidationFailure,
            Config.OnMavenPluginValidationFailure onMavenPluginValidationFailure) {
        return new Config(
                Config.MavenPluginsInclusion.Include,
                onValidationFailure,
                Config.OnPomValidationFailure.Error,
                onMavenPluginValidationFailure,
                Config.OnEnvironmentalValidationFailure.Error,
                Config.EnvironmentInclusion.Exclude,
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

    private static LockFile lockFile(Set<MavenPlugin> plugins, Config config) {
        return new LockFile(
                GROUP_ID,
                ARTIFACT_ID,
                VERSION,
                new Pom(GROUP_ID, ARTIFACT_ID, VERSION, "pom.xml", null, null, "SHA-256", "pom-checksum", null),
                Set.of(),
                plugins,
                Set.of(),
                new MetaData(null, config),
                Set.of());
    }

    private static MavenPlugin plugin(String artifactId, String checksum) {
        return new MavenPlugin(
                GroupId.of("org.apache.maven.plugins"),
                ArtifactId.of(artifactId),
                VersionNumber.of("3.13.0"),
                ResolvedUrl.Unresolved(),
                RepositoryId.None(),
                "SHA-256",
                checksum);
    }
}
