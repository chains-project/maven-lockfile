package io.github.chains_project.maven_lockfile;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.chains_project.maven_lockfile.data.LockFile;
import io.github.chains_project.maven_lockfile.data.LockFileDependency;
import org.instancio.Instancio;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

public class JsonUtilsTest {

    @Test
    void toJsonAndBack() {
        var dep = Instancio.create(LockFileDependency.class);
        var cloned = JsonUtils.fromJson(JsonUtils.toJson(dep), LockFileDependency.class);
        assertThat(cloned).isEqualTo(dep);
    }

    @Test
    @Disabled
    void toJsonAndBack2() {
        var dep = Instancio.create(LockFile.class);
        var cloned = JsonUtils.fromJson(JsonUtils.toJson(dep), LockFile.class);
        assertThat(cloned).isEqualTo(dep);
    }
}
