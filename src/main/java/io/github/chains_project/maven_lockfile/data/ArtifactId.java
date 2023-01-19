package io.github.chains_project.maven_lockfile.data;

import com.google.common.base.Strings;
import java.util.Objects;

public class ArtifactId {
    public static ArtifactId of(String artifactId) {
        // Artifact ID must be non-null and non-empty.
        String checked = Objects.requireNonNull(artifactId);
        if (Strings.isNullOrEmpty(checked)) {
            throw new IllegalArgumentException("artifactId cannot be empty");
        }
        return new ArtifactId(artifactId);
    }

    private final String value;

    public ArtifactId(String artifactId) {
        this.value = Objects.requireNonNull(artifactId);
    }

    public String getValue() {
        return value;
    }

    @Override
    public String toString() {
        return "{" + " ArtifactId='" + getValue() + "'" + "}";
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) return true;
        if (!(o instanceof ArtifactId)) {
            return false;
        }
        ArtifactId artifactId = (ArtifactId) o;
        return Objects.equals(value, artifactId.value);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(value);
    }
}
