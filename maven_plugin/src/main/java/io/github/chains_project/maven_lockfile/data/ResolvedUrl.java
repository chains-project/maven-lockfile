package io.github.chains_project.maven_lockfile.data;

import java.util.Objects;

/**
 * This class represents a resolved url for an artifact.
 * The resolved url points to where the artifact was downloaded from.
 * It is a string that is not null or empty. It is immutable.
 */
public record ResolvedUrl(String resolvedUrl) {
    public ResolvedUrl {
        Objects.requireNonNull(resolvedUrl, "resolvedUrl cannot be null");
    }

    public static ResolvedUrl of(String resolvedUrl) {
        return new ResolvedUrl(resolvedUrl);
    }

    public static ResolvedUrl Unresolved() {
        return new ResolvedUrl("");
    }

    public String toString() {
        return "{" + " ResolvedUrl='" + resolvedUrl + "'" + "}";
    }
}