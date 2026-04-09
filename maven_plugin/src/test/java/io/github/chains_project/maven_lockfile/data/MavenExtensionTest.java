package io.github.chains_project.maven_lockfile.data;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

public class MavenExtensionTest {

    private MavenExtension createExtension(
            String groupId, String artifactId, String version, String checksumAlgorithm, String checksum) {
        return new MavenExtension(
                GroupId.of(groupId),
                ArtifactId.of(artifactId),
                VersionNumber.of(version),
                checksum,
                checksumAlgorithm,
                null,
                null,
                null);
    }

    @Test
    void compareToReturnsZeroForIdenticalExtensions() {
        MavenExtension extension1 = createExtension("org.example", "artifact", "1.0.0", "SHA-256", "abc123");
        MavenExtension extension2 = createExtension("org.example", "artifact", "1.0.0", "SHA-256", "abc123");

        assertThat(extension1.compareTo(extension2)).isEqualTo(0);
        assertThat(extension2.compareTo(extension1)).isEqualTo(0);
    }

    @Test
    void compareToOrdersByGroupIdFirst() {
        MavenExtension extensionA = createExtension("org.aaa", "artifact", "1.0.0", "SHA-256", "abc123");
        MavenExtension extensionB = createExtension("org.bbb", "artifact", "1.0.0", "SHA-256", "abc123");

        assertThat(extensionA.compareTo(extensionB)).isLessThan(0);
        assertThat(extensionB.compareTo(extensionA)).isGreaterThan(0);
    }

    @Test
    void compareToFallsBackToArtifactIdWhenGroupIdEqual() {
        MavenExtension extensionA = createExtension("org.example", "aaa-artifact", "1.0.0", "SHA-256", "abc123");
        MavenExtension extensionB = createExtension("org.example", "bbb-artifact", "1.0.0", "SHA-256", "abc123");

        assertThat(extensionA.compareTo(extensionB)).isLessThan(0);
        assertThat(extensionB.compareTo(extensionA)).isGreaterThan(0);
    }

    @Test
    void compareToFallsBackToVersionWhenGroupIdAndArtifactIdEqual() {
        MavenExtension extensionA = createExtension("org.example", "artifact", "1.0.0", "SHA-256", "abc123");
        MavenExtension extensionB = createExtension("org.example", "artifact", "2.0.0", "SHA-256", "abc123");

        assertThat(extensionA.compareTo(extensionB)).isLessThan(0);
        assertThat(extensionB.compareTo(extensionA)).isGreaterThan(0);
    }

    @Test
    void compareToFallsBackToChecksumAlgorithmWhenCoordinatesEqual() {
        MavenExtension extensionA = createExtension("org.example", "artifact", "1.0.0", "MD5", "abc123");
        MavenExtension extensionB = createExtension("org.example", "artifact", "1.0.0", "SHA-256", "abc123");

        assertThat(extensionA.compareTo(extensionB)).isLessThan(0);
        assertThat(extensionB.compareTo(extensionA)).isGreaterThan(0);
    }

    @Test
    void compareToFallsBackToChecksumWhenAllElseEqual() {
        MavenExtension extensionA = createExtension("org.example", "artifact", "1.0.0", "SHA-256", "aaa111");
        MavenExtension extensionB = createExtension("org.example", "artifact", "1.0.0", "SHA-256", "bbb222");

        assertThat(extensionA.compareTo(extensionB)).isLessThan(0);
        assertThat(extensionB.compareTo(extensionA)).isGreaterThan(0);
    }

    @Test
    void equalsReturnsTrueForIdenticalExtensions() {
        MavenExtension extension1 = createExtension("org.example", "artifact", "1.0.0", "SHA-256", "abc123");
        MavenExtension extension2 = createExtension("org.example", "artifact", "1.0.0", "SHA-256", "abc123");

        assertThat(extension1).isEqualTo(extension2);
        assertThat(extension2).isEqualTo(extension1);
        assertThat(extension1.hashCode()).isEqualTo(extension2.hashCode());
    }

    @Test
    void equalsReturnsFalseForExtensionAndPluginWithSameGAV() {
        MavenExtension extension = createExtension("org.example", "artifact", "1.0.0", "SHA-256", "abc123");
        MavenPlugin plugin = new MavenPlugin(
                GroupId.of("org.example"),
                ArtifactId.of("artifact"),
                VersionNumber.of("1.0.0"),
                null,
                null,
                "SHA-256",
                "abc123");

        assertThat(extension).isNotEqualTo(plugin);
        assertThat(plugin).isNotEqualTo(extension);
    }

    @Test
    void compareToOrdersByTypeFirst() {
        MavenExtension extension = createExtension("org.example", "artifact", "1.0.0", "SHA-256", "abc123");
        MavenPlugin plugin = new MavenPlugin(
                GroupId.of("org.example"),
                ArtifactId.of("artifact"),
                VersionNumber.of("1.0.0"),
                null,
                null,
                "SHA-256",
                "abc123");

        int result = extension.compareTo(plugin);
        assertThat(result).isNotEqualTo(0);
        assertThat(plugin.compareTo(extension)).isEqualTo(-result);
    }
}
