package io.github.chains_project.maven_lockfile.data;

import com.google.common.base.Strings;
import java.util.Objects;

public class VersionNumber implements Comparable<VersionNumber> {
    public static VersionNumber of(String versionNumber) {
        String checked = Objects.requireNonNull(versionNumber);
        if (Strings.isNullOrEmpty(checked)) {
            throw new IllegalArgumentException("versionNumber cannot be empty");
        }
        return new VersionNumber(versionNumber);
    }

    private final String value;

    private VersionNumber(String versionNumber) {
        this.value = Objects.requireNonNull(versionNumber, "versionNumber is marked non-null but is null");
    }

    public String getValue() {
        return value;
    }

    @Override
    public String toString() {
        return "{" + " VersionNumber='" + getValue() + "'" + "}";
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) return true;
        if (!(o instanceof VersionNumber)) {
            return false;
        }
        VersionNumber versionNumber = (VersionNumber) o;
        return Objects.equals(value, versionNumber.value);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(value);
    }

    @Override
    public int compareTo(VersionNumber o) {
        return this.value.compareTo(o.value);
    }
}
