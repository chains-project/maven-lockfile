package io.github.chains_project.maven_lockfile.data;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

/**
 * Checks that lockfiles keep the fields other tools rely on, as described by
 * {@code schema/lockfile-tool-contract.schema.json}. A change that moves, renames or retypes one of them fails here
 * instead of in a dependency update bot after a release.
 *
 * <p>The schema uses only a few JSON Schema keywords, which this test checks itself so the contract adds no
 * dependency. Any other keyword in the schema fails the test, so the schema cannot ask for a check that is skipped.
 */
class ToolContractTest {

    private static final Path SCHEMA = Path.of("schema/lockfile-tool-contract.schema.json");

    /** Written by the current release on every dependency update, so it is real {@code generate} output. */
    private static final Path REPOSITORY_LOCKFILE = Path.of("lockfile.json");

    private static final Set<String> ANNOTATIONS = Set.of("$schema", "$id", "title", "description");
    private static final Set<String> CHECKS = Set.of("type", "required", "properties", "minimum", "minLength");

    @TestFactory
    Stream<DynamicTest> every_format_generate_writes_meets_the_contract() throws IOException {
        // generate writes lockFile.toJson(formatVersion), see GenerateLockFileMojo.
        LockFile lockFile = LockFile.readLockFile(REPOSITORY_LOCKFILE);
        return IntStream.rangeClosed(1, LockFile.LATEST_LOCKFILE_VERSION).mapToObj(v -> formatTest(lockFile, v));
    }

    private static DynamicTest formatTest(LockFile lockFile, int version) {
        String json = lockFile.toJson(version);
        return DynamicTest.dynamicTest("lockFileVersion " + version, () -> assertThat(violations(json)).isEmpty());
    }

    @Test
    void repository_lockfile_meets_the_contract() throws IOException {
        assertThat(violations(Files.readString(REPOSITORY_LOCKFILE))).isEmpty();
    }

    @Test
    void moved_config_is_reported() throws IOException {
        String json = "{\"lockFileVersion\": 2, \"config\": {\"mavenLockfileVersion\": \"6.0.0\"}}";

        assertThat(violations(json)).containsExactly("$: missing required property metaData");
    }

    @Test
    void missing_contract_fields_are_reported() throws IOException {
        String json = "{\"lockFileVersion\": 1, \"metaData\": {\"config\": {}}}";

        assertThat(violations(json))
                .containsExactlyInAnyOrder(
                        "$.metaData.config: missing required property mavenLockfileVersion",
                        "$.metaData.config: missing required property hermetic");
    }

    @Test
    void retyped_contract_fields_are_reported() throws IOException {
        String json = "{\"lockFileVersion\": \"2\", \"metaData\": {\"config\":"
                + " {\"mavenLockfileVersion\": \"\", \"hermetic\": {\"enabled\": true}}}}";

        assertThat(violations(json))
                .containsExactlyInAnyOrder(
                        "$.lockFileVersion: expected integer",
                        "$.metaData.config.mavenLockfileVersion: shorter than 1",
                        "$.metaData.config.hermetic: expected boolean");
    }

    @Test
    void null_and_out_of_range_values_are_reported() throws IOException {
        String json = "{\"lockFileVersion\": 0, \"metaData\": {\"config\":"
                + " {\"mavenLockfileVersion\": null, \"hermetic\": false}}}";

        assertThat(violations(json))
                .containsExactlyInAnyOrder(
                        "$.lockFileVersion: below minimum 1",
                        "$.metaData.config.mavenLockfileVersion: expected string");
    }

    @Test
    void unchecked_schema_keyword_is_rejected() {
        String json = "{\"type\": \"object\", \"additionalProperties\": false}";
        JsonObject schema = JsonParser.parseString(json).getAsJsonObject();

        assertThatThrownBy(() -> check(schema, new JsonObject(), "$", new ArrayList<>()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("additionalProperties");
    }

    private static List<String> violations(String json) throws IOException {
        JsonObject schema = JsonParser.parseString(Files.readString(SCHEMA)).getAsJsonObject();
        List<String> violations = new ArrayList<>();
        check(schema, JsonParser.parseString(json), "$", violations);
        return violations;
    }

    private static void check(JsonObject schema, JsonElement value, String path, List<String> violations) {
        for (String keyword : schema.keySet()) {
            if (!ANNOTATIONS.contains(keyword) && !CHECKS.contains(keyword)) {
                throw new IllegalArgumentException("The schema uses " + keyword + " at " + path
                        + ", which ToolContractTest does not check. Add support for it here.");
            }
        }
        if (schema.has("type")) {
            String type = schema.get("type").getAsString();
            if (!hasType(value, type)) {
                violations.add(path + ": expected " + type);
                return;
            }
        }
        if (schema.has("minimum") && isNumber(value)) {
            BigDecimal minimum = schema.get("minimum").getAsBigDecimal();
            if (value.getAsBigDecimal().compareTo(minimum) < 0) {
                violations.add(path + ": below minimum " + minimum);
            }
        }
        if (schema.has("minLength") && isString(value)) {
            String string = value.getAsString();
            int minLength = schema.get("minLength").getAsInt();
            if (string.codePointCount(0, string.length()) < minLength) {
                violations.add(path + ": shorter than " + minLength);
            }
        }
        if (!value.isJsonObject()) {
            return;
        }
        JsonObject object = value.getAsJsonObject();
        if (schema.has("required")) {
            for (JsonElement name : schema.getAsJsonArray("required")) {
                if (!object.has(name.getAsString())) {
                    violations.add(path + ": missing required property " + name.getAsString());
                }
            }
        }
        if (schema.has("properties")) {
            for (Map.Entry<String, JsonElement> property : schema.getAsJsonObject("properties").entrySet()) {
                JsonElement child = object.get(property.getKey());
                if (child != null) {
                    check(property.getValue().getAsJsonObject(), child, path + "." + property.getKey(), violations);
                }
            }
        }
    }

    private static boolean hasType(JsonElement value, String type) {
        switch (type) {
            case "object":
                return value.isJsonObject();
            case "string":
                return isString(value);
            case "boolean":
                return value.isJsonPrimitive() && value.getAsJsonPrimitive().isBoolean();
            case "integer":
                return isNumber(value) && isIntegral(value.getAsBigDecimal());
            default:
                throw new IllegalArgumentException("ToolContractTest does not check type " + type);
        }
    }

    private static boolean isString(JsonElement value) {
        return value.isJsonPrimitive() && value.getAsJsonPrimitive().isString();
    }

    private static boolean isNumber(JsonElement value) {
        return value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber();
    }

    private static boolean isIntegral(BigDecimal number) {
        return number.signum() == 0 || number.stripTrailingZeros().scale() <= 0;
    }
}
