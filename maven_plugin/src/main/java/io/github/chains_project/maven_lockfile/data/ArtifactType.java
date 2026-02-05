package io.github.chains_project.maven_lockfile.data;

import com.google.common.base.Strings;
import java.util.Objects;

/**
 * A Maven artifact type specifies the packaging type of the artifact (e.g., jar, pom, war).
 * The default type is "jar", so we return null for "jar" or null/empty input to avoid redundant data.
 */
public class ArtifactType implements Comparable<ArtifactType> {
    public static ArtifactType of(String type) {
        if (Strings.isNullOrEmpty(type) || "jar".equals(type)) {
            return null;
        }
        return new ArtifactType(type);
    }

    private final String value;

    private ArtifactType(String type) {
        this.value = Objects.requireNonNull(type, "type is marked non-null but is null");
    }

    public String getValue() {
        return value;
    }

    @Override
    public String toString() {
        return "{" + " ArtifactType='" + getValue() + "'" + "}";
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) return true;
        if (!(o instanceof ArtifactType)) {
            return false;
        }
        ArtifactType artifactType = (ArtifactType) o;
        return Objects.equals(value, artifactType.value);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(value);
    }

    @Override
    public int compareTo(ArtifactType o) {
        return this.value.compareTo(o.value);
    }
}
