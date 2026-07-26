package io.github.chains_project.maven_lockfile.data;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests for the {@link MavenScope} class.
 */
class MavenScopeTest {
    /**
     * Tests that all {@link MavenScope} values can be parsed correctly.
     * For each scope value, this test calls the {@link MavenScope#fromString(String)} method
     * with the scope value and checks that the returned {@link MavenScope} object is equal to the expected value.
     */
    @Test
    @DisplayName("All MavenScope values can be parsed correctly")
    void allScopes() {
        for (MavenScope scope : MavenScope.values()) {
            String value = scope.getValue();
            MavenScope parsedScope = MavenScope.fromString(value);
            assertEquals(scope, parsedScope);
        }
    }

    /**
     * Tests that the {@link MavenScope#fromString(String)} method returns {@link MavenScope#COMPILE}
     * when called with a null value.
     * This test calls the {@link MavenScope#fromString(String)} method with a null value
     * and checks that the returned {@link MavenScope} object is equal to {@link MavenScope#COMPILE}.
     */
    @Test
    @DisplayName("fromString returns COMPILE when called with null")
    void fromStringWithNull() {
        MavenScope scope = MavenScope.fromString(null);
        assertEquals(MavenScope.COMPILE, scope);
    }

    /**
     * Tests that the {@link MavenScope#fromString(String)} method returns {@link MavenScope#COMPILE}
     * when called with an empty value.
     * This test calls the {@link MavenScope#fromString(String)} method with an empty value
     * and checks that the returned {@link MavenScope} object is equal to {@link MavenScope#COMPILE}.
     */
    @Test
    @DisplayName("fromString returns COMPILE when called with empty string")
    void fromStringWithEmpty() {
        MavenScope scope = MavenScope.fromString("");
        assertEquals(MavenScope.COMPILE, scope);
    }
}
