package io.github.chains_project.maven_lockfile.data;

/**
 * Represents the Maven scopes.
 */
public enum MavenScope {
    COMPILE("compile"),
    PROVIDED("provided"),
    RUNTIME("runtime"),
    TEST("test"),
    SYSTEM("system"),
    IMPORT("import");

    private final String value;

    /**
     * Creates a new MavenScope with the specified value.
     *
     * @param value the string representation of the scope
     * @throws IllegalArgumentException if the specified value is null or empty
     */
    MavenScope(String value) {
        if (value == null || value.isEmpty()) {
            throw new IllegalArgumentException("Maven scope value cannot be null or empty");
        }
        this.value = value;
    }

    /**
     * Returns the string representation of the scope.
     *
     * @return the string representation of the scope
     */
    public String getValue() {
        return value;
    }

    /**
     * Returns the MavenScope object that corresponds to the specified string.
     *
     * @param value the string representation of the scope, or null/empty to return the default scope (compile)
     * @return the MavenScope object that corresponds to the specified string, or the default scope if the specified value is null or empty
     * @throws IllegalArgumentException if the specified value does not correspond to a MavenScope object
     */
    public static MavenScope fromString(String value) {
        if (value == null || value.isEmpty()) {
            return MavenScope.COMPILE;
        }
        for (MavenScope scope : MavenScope.values()) {
            if (scope.value.equalsIgnoreCase(value)) {
                return scope;
            }
        }
        throw new IllegalArgumentException("Invalid Maven scope value: " + value);
    }
}
