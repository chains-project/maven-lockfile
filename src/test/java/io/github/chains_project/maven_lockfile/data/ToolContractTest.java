package io.github.chains_project.maven_lockfile.data;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Checks the fields listed under "Fields other tools can rely on" in the README. */
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
