package io.github.chains_project.maven_lockfile.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.chains_project.maven_lockfile.JsonUtils;
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

    /**
     * Tests that standard scopes are matched case-insensitively and map to the shared constants.
     */
    @Test
    @DisplayName("fromString matches standard scopes case-insensitively")
    void fromStringStandardScopeIgnoresCase() {
        MavenScope scope = MavenScope.fromString("Provided");
        assertSame(MavenScope.PROVIDED, scope);
        assertTrue(scope.isStandard());
    }

    /**
     * Tests that a non-standard scope, such as {@code annotationProcessor} found in some published POMs,
     * is accepted and its original value is kept.
     */
    @Test
    @DisplayName("fromString keeps the value of a non-standard scope")
    void fromStringWithNonStandardScope() {
        MavenScope scope = MavenScope.fromString("annotationProcessor");
        assertEquals("annotationProcessor", scope.getValue());
        assertFalse(scope.isStandard());
        assertEquals(MavenScope.fromString("annotationProcessor"), scope);
        assertNotEquals(MavenScope.fromString("kapt"), scope);
        assertNotEquals(MavenScope.COMPILE, scope);
    }

    /**
     * Tests that a non-standard scope survives a JSON round trip unchanged.
     */
    @Test
    @DisplayName("Non-standard scope survives a JSON round trip")
    void nonStandardScopeJsonRoundTrip() {
        MavenScope scope = MavenScope.fromString("annotationProcessor");
        String json = JsonUtils.toJson(scope);
        assertEquals("\"annotationProcessor\"", json);
        assertEquals(scope, JsonUtils.fromJson(json, MavenScope.class));
    }
}
