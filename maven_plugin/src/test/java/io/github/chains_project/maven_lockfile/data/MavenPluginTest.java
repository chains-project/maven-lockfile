package io.github.chains_project.maven_lockfile.data;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

public class MavenPluginTest {

    private MavenPlugin createPlugin(
            String groupId, String artifactId, String version, String checksumAlgorithm, String checksum) {
        return new MavenPlugin(
                GroupId.of(groupId),
                ArtifactId.of(artifactId),
                VersionNumber.of(version),
                null,
                null,
                checksumAlgorithm,
                checksum);
    }

    @Test
    void compareToReturnsZeroForIdenticalPlugins() {
        MavenPlugin plugin1 = createPlugin("org.example", "artifact", "1.0.0", "SHA-256", "abc123");
        MavenPlugin plugin2 = createPlugin("org.example", "artifact", "1.0.0", "SHA-256", "abc123");

        assertThat(plugin1.compareTo(plugin2)).isEqualTo(0);
        assertThat(plugin2.compareTo(plugin1)).isEqualTo(0);
    }

    @Test
    void compareToOrdersByGroupIdFirst() {
        MavenPlugin pluginA = createPlugin("org.aaa", "artifact", "1.0.0", "SHA-256", "abc123");
        MavenPlugin pluginB = createPlugin("org.bbb", "artifact", "1.0.0", "SHA-256", "abc123");

        assertThat(pluginA.compareTo(pluginB)).isLessThan(0);
        assertThat(pluginB.compareTo(pluginA)).isGreaterThan(0);
    }

    @Test
    void compareToFallsBackToArtifactIdWhenGroupIdEqual() {
        MavenPlugin pluginA = createPlugin("org.example", "aaa-artifact", "1.0.0", "SHA-256", "abc123");
        MavenPlugin pluginB = createPlugin("org.example", "bbb-artifact", "1.0.0", "SHA-256", "abc123");

        assertThat(pluginA.compareTo(pluginB)).isLessThan(0);
        assertThat(pluginB.compareTo(pluginA)).isGreaterThan(0);
    }

    @Test
    void compareToFallsBackToVersionWhenGroupIdAndArtifactIdEqual() {
        MavenPlugin pluginA = createPlugin("org.example", "artifact", "1.0.0", "SHA-256", "abc123");
        MavenPlugin pluginB = createPlugin("org.example", "artifact", "2.0.0", "SHA-256", "abc123");

        assertThat(pluginA.compareTo(pluginB)).isLessThan(0);
        assertThat(pluginB.compareTo(pluginA)).isGreaterThan(0);
    }

    @Test
    void compareToFallsBackToChecksumAlgorithmWhenCoordinatesEqual() {
        MavenPlugin pluginA = createPlugin("org.example", "artifact", "1.0.0", "MD5", "abc123");
        MavenPlugin pluginB = createPlugin("org.example", "artifact", "1.0.0", "SHA-256", "abc123");

        assertThat(pluginA.compareTo(pluginB)).isLessThan(0);
        assertThat(pluginB.compareTo(pluginA)).isGreaterThan(0);
    }

    @Test
    void compareToFallsBackToChecksumWhenAllElseEqual() {
        MavenPlugin pluginA = createPlugin("org.example", "artifact", "1.0.0", "SHA-256", "aaa111");
        MavenPlugin pluginB = createPlugin("org.example", "artifact", "1.0.0", "SHA-256", "bbb222");

        assertThat(pluginA.compareTo(pluginB)).isLessThan(0);
        assertThat(pluginB.compareTo(pluginA)).isGreaterThan(0);
    }
}
