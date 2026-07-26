package io.github.chains_project.maven_lockfile.data;

import java.util.Objects;

/**
 * This class represents a repository id for an artifact.
 * The repository id contains the id for the repository object in the pom.
 * It is a string that is not null or empty. It is immutable.
 */
public class RepositoryId implements Comparable<RepositoryId> {
    public static RepositoryId of(String repositoryId) {
        // RepositoryId must be non-null.
        String checked = Objects.requireNonNull(repositoryId, "repositoryId cannot be null");
        return new RepositoryId(checked);
    }

    public static RepositoryId None() {
        return new RepositoryId("");
    }

    private final String value;

    public RepositoryId(String repositoryId) {
        this.value = Objects.requireNonNull(repositoryId);
    }

    public String getValue() {
        return value;
    }

    @Override
    public String toString() {
        return "{" + " RepositoryId='" + getValue() + "'" + "}";
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) return true;
        if (!(o instanceof RepositoryId)) {
            return false;
        }
        RepositoryId resolvedUrl = (RepositoryId) o;
        return Objects.equals(value, resolvedUrl.value);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(value);
    }

    @Override
    public int compareTo(RepositoryId o) {
        return this.value.compareTo(o.value);
    }
}
