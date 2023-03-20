package io.github.chains_project.maven_lockfile.data;

import com.google.common.base.Strings;
import java.util.Objects;

public class GroupId implements Comparable<GroupId> {
    public static GroupId of(String groupId) {
        String checked = Objects.requireNonNull(groupId);
        if (Strings.isNullOrEmpty(checked)) {
            throw new IllegalArgumentException("groupId cannot be empty");
        }
        return new GroupId(groupId);
    }

    private final String value;

    private GroupId(String groupId) {
        this.value = groupId;
    }

    public String getValue() {
        return value;
    }

    @Override
    public String toString() {
        return "{" + " GroupId='" + getValue() + "'" + "}";
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) return true;
        if (!(o instanceof GroupId)) {
            return false;
        }
        GroupId groupId = (GroupId) o;
        return Objects.equals(value, groupId.value);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(value);
    }

    @Override
    public int compareTo(GroupId o) {
        return this.value.compareTo(o.value);
    }
}
