package io.github.chains_project.maven_lockfile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import com.google.common.collect.Sets;
import io.github.chains_project.maven_lockfile.graph.DependencyNode;
import java.util.HashSet;
import java.util.Set;
import org.instancio.Instancio;
import org.junit.jupiter.api.Test;

public class JsonUtilsTest {

    @Test
    void set_view_to_json_does_not_return_null() {

        Instancio.create(DependencyNode.class);
        Set<DependencyNode> set = new HashSet<>();
        set.add(Instancio.create(DependencyNode.class));
        Set<DependencyNode> set2 = new HashSet<>();
        set2.add(Instancio.create(DependencyNode.class));
        var result = new HashSet<>(Sets.difference(set, set2));
        assertEquals(result.size(), 1);
        assertNotEquals(JsonUtils.toJson(result), "null");
    }
}
