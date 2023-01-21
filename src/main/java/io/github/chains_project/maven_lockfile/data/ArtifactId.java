package io.github.chains_project.maven_lockfile.data;

import com.google.common.base.Strings;
import java.util.Objects;

/**
 * This class represents an artifact id.
 * An artifact id is an identifier for a maven artifact, and is unique within a group id.
 * It is a string that is not null or empty. It is immutable.
 * It is used to identify a maven artifact in a lock file.
 * It is also used to identify a maven artifact in a dependency graph.
 * For example, the artifact id of the artifact "org.apache.commons:commons-lang3:3.9" is "commons-lang3".
 */
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
