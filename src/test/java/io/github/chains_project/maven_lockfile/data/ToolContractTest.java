package io.github.chains_project.maven_lockfile.data;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Checks the lockfile fields that tools regenerating lockfiles, such as dependency update bots, rely on. They keep
 * their path and type in every {@code lockFileVersion}:
 * <ul>
 *   <li>{@code lockFileVersion}: integer, at least 1. The format version. A release rejects a version newer than it
 *       can read.</li>
 *   <li>{@code metaData.config.mavenLockfileVersion}: non-empty string. The release that generated the file.</li>
 * </ul>
 */
class ToolContractTest {

    @Test
    void repository_lockfile_has_the_fields_tools_rely_on() throws IOException {
        String json = Files.readString(Path.of("lockfile.json"));
        JsonObject lockFile = JsonParser.parseString(json).getAsJsonObject();
        JsonObject config = lockFile.getAsJsonObject("metaData").getAsJsonObject("config");

        assertThat(lockFile.getAsJsonPrimitive("lockFileVersion").getAsInt()).isPositive();
        assertThat(config.getAsJsonPrimitive("mavenLockfileVersion").getAsString())
                .isNotEmpty();
    }
}
