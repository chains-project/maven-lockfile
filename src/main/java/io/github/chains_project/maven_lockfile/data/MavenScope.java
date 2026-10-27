package io.github.chains_project.maven_lockfile.data;

import java.util.List;
import java.util.Objects;

/**
 * Represents a Maven dependency scope.
 * The standard scopes are available as constants. Any other scope found in a POM (e.g. {@code annotationProcessor})
 * is kept verbatim so that it can be written back unchanged.
 */
public final class MavenScope {
    public static final MavenScope COMPILE = new MavenScope("compile");
    public static final MavenScope PROVIDED = new MavenScope("provided");
    public static final MavenScope RUNTIME = new MavenScope("runtime");
    public static final MavenScope TEST = new MavenScope("test");
    public static final MavenScope SYSTEM = new MavenScope("system");
    public static final MavenScope IMPORT = new MavenScope("import");

    private static final List<MavenScope> STANDARD_SCOPES = List.of(COMPILE, PROVIDED, RUNTIME, TEST, SYSTEM, IMPORT);

    private final String value;

    private MavenScope(String value) {
        this.value = value;
    }

    /**
     * Returns the standard Maven scopes.
     *
     * @return the standard Maven scopes
     */
    public static List<MavenScope> values() {
        return STANDARD_SCOPES;
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
     * Returns whether this scope is one of the standard Maven scopes.
     *
     * @return true if this scope is one of the standard Maven scopes, false otherwise
     */
    public boolean isStandard() {
        return STANDARD_SCOPES.contains(this);
    }

    /**
     * Returns the MavenScope object that corresponds to the specified string.
     *
     * @param value the string representation of the scope, or null/empty to return the default scope (compile)
     * @return the standard MavenScope that matches the specified string, the default scope if the specified value is
     * null or empty, or a non-standard MavenScope holding the specified value otherwise
     */
    public static MavenScope fromString(String value) {
        if (value == null || value.isEmpty()) {
            return MavenScope.COMPILE;
        }
        for (MavenScope scope : STANDARD_SCOPES) {
            if (scope.value.equalsIgnoreCase(value)) {
                return scope;
            }
        }
        return new MavenScope(value);
    }

    @Override
    public String toString() {
        return "{" + " MavenScope='" + getValue() + "'" + "}";
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) return true;
        if (!(o instanceof MavenScope)) {
            return false;
        }
        MavenScope mavenScope = (MavenScope) o;
        return Objects.equals(value, mavenScope.value);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(value);
    }
}
