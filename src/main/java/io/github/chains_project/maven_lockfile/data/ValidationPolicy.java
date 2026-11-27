package io.github.chains_project.maven_lockfile.data;

/** What a validation mismatch does. */
public enum ValidationPolicy {
    /** Log a warning. */
    Warn,
    /** Fail the build. */
    Error;

    public static ValidationPolicy of(boolean allowFailure) {
        return allowFailure ? Warn : Error;
    }
}
