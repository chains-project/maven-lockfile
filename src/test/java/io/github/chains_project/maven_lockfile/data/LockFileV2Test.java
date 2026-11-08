package io.github.chains_project.maven_lockfile.data;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.chains_project.maven_lockfile.JsonUtils;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

class LockFileV2Test {

    /** This repository's lockfile and every lockfile fixture of the integration tests. */
    static List<Path> v1LockFiles() throws IOException {
        try (Stream<Path> fixtures = Files.walk(Path.of("src/test/resources-its"))) {
            return Stream.concat(Stream.of(Path.of("lockfile.json")), fixtures)
                    .filter(path -> path.toString().endsWith(".json"))
                    .filter(path -> read(path).contains("\"lockFileVersion\": 1"))
                    .sorted()
                    .collect(Collectors.toList());
        }
    }

    @TestFactory
    Stream<DynamicTest> v2_holds_the_same_content_as_v1() throws IOException {
        return v1LockFiles().stream()
                .map(path -> DynamicTest.dynamicTest(path.toString(), () -> assertRoundTrips(path)));
    }

    private static void assertRoundTrips(Path path) throws IOException {
        JsonObject v1 = JsonParser.parseString(read(path)).getAsJsonObject();

        JsonObject v2 = LockFileV2.encode(v1);
        JsonObject decoded = LockFileV2.decode(v2);
        decoded.addProperty("lockFileVersion", 1);
        assertThat(decoded).isEqualTo(v1);

        // Through the model, including fields that equals() ignores, such as resolved.
        LockFile fromV1 = LockFile.readLockFile(path);
        LockFile fromV2 = LockFile.fromJson(fromV1.toJson(2), path.toString());
        assertThat(fromV2.getLockfileVersion()).isEqualTo(2);
        assertThat(fromV2.toJson(1)).isEqualTo(fromV1.toJson(1));
    }

    @TestFactory
    Stream<DynamicTest> v1_output_is_unchanged() throws IOException {
        return v1LockFiles().stream()
                .map(path -> DynamicTest.dynamicTest(path.toString(), () -> {
                    LockFile lockFile = LockFile.readLockFile(path);
                    assertThat(lockFile.toJson(1)).isEqualTo(JsonUtils.toJson(lockFile));
                }));
    }

    @Test
    void v2_stores_each_artifact_once() throws IOException {
        JsonObject v2 = LockFileV2.encode(
                JsonParser.parseString(read(Path.of("lockfile.json"))).getAsJsonObject());
        String json = JsonUtils.toJsonKeepingNulls(v2);

        assertThat(v2.getAsJsonObject("artifacts").keySet())
                .contains("pkg:maven/com.google.code.gson/gson@2.14.0", "pkg:maven/org.apache/apache@35?type=pom");
        // Each checksum appears once, in the artifacts table.
        assertThat(json.split("\"checksum\":", -1))
                .hasSize(v2.getAsJsonObject("artifacts").size() + 1);
        assertThat(json).doesNotContain("\"id\":", "\"parent\": \"");
    }

    @Test
    void occurrence_with_different_metadata_keeps_it_inline() throws IOException {
        JsonObject v1 = JsonParser.parseString(read(Path.of("lockfile.json"))).getAsJsonObject();
        JsonObject first = v1.getAsJsonArray("dependencies").get(0).getAsJsonObject();
        JsonObject copy = first.deepCopy();
        copy.remove("children");
        copy.addProperty("repositoryId", "mirror");
        copy.remove("resolved");
        first.getAsJsonArray("children").add(copy);

        JsonObject v2 = LockFileV2.encode(v1);
        JsonObject decoded = LockFileV2.decode(v2);
        decoded.addProperty("lockFileVersion", 1);

        assertThat(decoded).isEqualTo(v1);
        assertThat(JsonUtils.toJsonKeepingNulls(v2)).contains("\"repositoryId\": \"mirror\"", "\"resolved\": null");
    }

    @Test
    void newer_lockfile_version_is_rejected() {
        String json = "{\"lockFileVersion\": 3, \"metaData\": {\"config\": {\"mavenLockfileVersion\": \"7.0.0\"}}}";

        assertThatThrownBy(() -> LockFile.fromJson(json, "lockfile.json"))
                .isInstanceOf(LockFileFormatException.class)
                .hasMessage("lockfile.json has lockFileVersion 3, but this release of maven-lockfile only reads"
                        + " lockFileVersion 1 to 2. Upgrade maven-lockfile to at least 7.0.0, the release that"
                        + " generated it.");
    }

    @Test
    void dangling_artifact_reference_is_rejected() {
        String json = "{\"lockFileVersion\": 2, \"dependencies\": [{\"artifact\": \"pkg:maven/a/b@1\"}],"
                + " \"artifacts\": {}}";

        assertThatThrownBy(() -> LockFile.fromJson(json, "lockfile.json"))
                .isInstanceOf(LockFileFormatException.class)
                .hasMessageContaining("pkg:maven/a/b@1");
    }

    @Test
    void purl_encodes_qualifiers_and_reserved_characters() {
        JsonObject coordinates = new JsonObject();
        coordinates.addProperty("groupId", "org.example");
        coordinates.addProperty("artifactId", "lib");
        coordinates.addProperty("version", "1.0+build");
        coordinates.addProperty("classifier", "sources");
        coordinates.addProperty("type", "test-jar");

        assertThat(LockFileV2.purlOf(coordinates))
                .isEqualTo("pkg:maven/org.example/lib@1.0%2Bbuild?classifier=sources&type=test-jar");
    }

    private static String read(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
