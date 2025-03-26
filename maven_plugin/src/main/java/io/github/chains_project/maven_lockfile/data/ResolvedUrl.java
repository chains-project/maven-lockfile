package io.github.chains_project.maven_lockfile.data;

import java.util.Objects;

/**
 * This class represents a resolved url for an artifact.
 * The resolved url points to where the artifact was downloaded from.
 * It is a string that is not null or empty. It is immutable.
 */
public class ResolvedUrl implements Comparable<ResolvedUrl> {
    public static ResolvedUrl of(String resolvedUrl) {
        // ResolvedUrl must be non-null.
        String checked = Objects.requireNonNull(resolvedUrl, "resolvedUrl cannot be null");
        return new ResolvedUrl(checked);
    }

    public static ResolvedUrl Unresolved() {
        return new ResolvedUrl("");
    }

    private final String value;

    public ResolvedUrl(String resolvedUrl) {
        this.value = Objects.requireNonNull(resolvedUrl);
    }

    public String getValue() {
        return value;
    }

    @Override
    public String toString() {
        return "{" + " ResolvedUrl='" + getValue() + "'" + "}";
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) return true;
        if (!(o instanceof ResolvedUrl)) {
            return false;
        }
        ResolvedUrl resolvedUrl = (ResolvedUrl) o;
        return Objects.equals(value, resolvedUrl.value);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(value);
    }

    @Override
    public int compareTo(ResolvedUrl o) {
        return this.value.compareTo(o.value);
    }
}
