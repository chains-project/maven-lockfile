package io.github.chains_project.maven_lockfile.data;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Path;
import java.util.function.Predicate;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/**
 * Checks that lockfiles keep the fields other tools rely on, listed under "Fields other tools can rely on" in the
 * README. A change that moves, renames or retypes one of them fails here instead of in a tool after a release.
 */
class ToolContractTest {

    /** Written by the current release on every dependency update, so it is real {@code generate} output. */
    private static final Path REPOSITORY_LOCKFILE = Path.of("lockfile.json");

    @TestFactory
    Stream<DynamicTest> every_format_generate_writes_keeps_the_fields() throws IOException {
        // generate writes lockFile.toJson(formatVersion), see GenerateLockFileMojo.
        LockFile lockFile = LockFile.readLockFile(REPOSITORY_LOCKFILE);
        return IntStream.rangeClosed(1, LockFile.LATEST_LOCKFILE_VERSION).mapToObj(v -> formatTest(lockFile, v));
    }

    private static DynamicTest formatTest(LockFile lockFile, int version) {
        String json = lockFile.toJson(version);
        return DynamicTest.dynamicTest("lockFileVersion " + version, () -> assertKeepsTheFields(json));
    }

    private static void assertKeepsTheFields(String json) {
        JsonElement root = JsonParser.parseString(json);
        assertField(root, "lockFileVersion", ToolContractTest::isPositiveInteger);
        assertField(root, "metaData.config.mavenLockfileVersion", ToolContractTest::isNonEmptyString);
        assertField(root, "metaData.config.hermetic", ToolContractTest::isBoolean);
    }

    /** Fails unless the field at the dotted path is present and passes the type check. */
    private static void assertField(JsonElement root, String path, Predicate<JsonElement> type) {
        JsonElement value = root;
        for (String key : path.split("\\.")) {
            value = value != null && value.isJsonObject()
                    ? value.getAsJsonObject().get(key)
                    : null;
        }
        assertThat(value).as(path).isNotNull().matches(type);
    }

    private static boolean isPositiveInteger(JsonElement value) {
        return value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber() && value.getAsInt() >= 1;
    }

    private static boolean isNonEmptyString(JsonElement value) {
        return value.isJsonPrimitive()
                && value.getAsJsonPrimitive().isString()
                && !value.getAsString().isEmpty();
    }

    private static boolean isBoolean(JsonElement value) {
        return value.isJsonPrimitive() && value.getAsJsonPrimitive().isBoolean();
    }
}
