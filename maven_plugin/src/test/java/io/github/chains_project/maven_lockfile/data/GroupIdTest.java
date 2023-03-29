package io.github.chains_project.maven_lockfile.data;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

public class GroupIdTest {

    @Test
    void chainsGroupID() {
        GroupId groupId = GroupId.of("io.github.chains-project");
        assertThat(groupId.getValue()).isEqualTo("io.github.chains-project");
    }

    @Test
    void nullNotAllowed() {
        assertThatThrownBy(() -> GroupId.of(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void emptyNotAllowed() {
        assertThatThrownBy(() -> GroupId.of("")).isInstanceOf(IllegalArgumentException.class);
    }
}
