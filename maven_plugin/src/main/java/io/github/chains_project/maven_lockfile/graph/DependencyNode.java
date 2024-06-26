package io.github.chains_project.maven_lockfile.graph;

import com.google.gson.annotations.Expose;
import io.github.chains_project.maven_lockfile.data.ArtifactId;
import io.github.chains_project.maven_lockfile.data.GroupId;
import io.github.chains_project.maven_lockfile.data.MavenScope;
import io.github.chains_project.maven_lockfile.data.VersionNumber;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import javax.annotation.Nullable;

/**
 * This class represents a node in the dependency graph. It contains the artifactId, groupId and version  of the dependency.
 * It also contains a reference to the parent node.
 */
public class DependencyNode implements Comparable<DependencyNode> {

    private final GroupId groupId;
    private final ArtifactId artifactId;
    private final VersionNumber version;
    private final String checksumAlgorithm;
    private final String checksum;
    private final MavenScope scope;

    @Nullable
    private String selectedVersion;

    @Nullable
    private boolean included;

    NodeId id;

    @Expose(serialize = false, deserialize = false)
    private NodeId parent;

    private final List<DependencyNode> children;

    DependencyNode(
            ArtifactId artifactId,
            GroupId groupId,
            VersionNumber version,
            MavenScope scope,
            String checksumAlgorithm,
            String checksum) {
        this.artifactId = artifactId;
        this.groupId = groupId;
        this.version = version;
        this.checksumAlgorithm = checksumAlgorithm;
        this.checksum = checksum;
        this.children = new ArrayList<>();
        this.id = new NodeId(groupId, artifactId, version);
        this.scope = scope;
    }
    /**
     * @return the artifactId
     */
    public ArtifactId getArtifactId() {
        return artifactId;
    }
    /**
     * @return the groupId
     */
    public GroupId getGroupId() {
        return groupId;
    }
    /**
     * @return the parent
     */
    public NodeId getParent() {
        return parent;
    }
    /**
     * @return the version
     */
    public VersionNumber getVersion() {
        return version;
    }
    /**
     * @return the scope
     */
    public MavenScope getScope() {
        return scope;
    }

    void addChild(DependencyNode child) {
        children.add(child);
        child.setParent(id);
        if (!child.parent.equals(id)) {
            throw new IllegalStateException("Child node has wrong parent");
        }
        Collections.sort(children);
    }

    void setParent(NodeId parent) {
        this.parent = parent;
    }

    /**
     * @return the children
     */
    public List<DependencyNode> getChildren() {
        return Collections.unmodifiableList(children);
    }

    /**
     * @return the checksum
     */
    public String getChecksum() {
        return checksum;
    }
    /**
     * @return the checksumAlgorithm
     */
    public String getChecksumAlgorithm() {
        return checksumAlgorithm;
    }
    /**
     * @param baseVersion the baseVersion to set
     */
    public void setSelectedVersion(String baseVersion) {
        this.selectedVersion = baseVersion;
    }
    /**
     * @return the baseVersion
     */
    public String getSelectedVersion() {
        return selectedVersion;
    }

    /**
     * @param included the state of inclusion
     */
    public void setIncluded(boolean included) {
        this.included = included;
    }

    /**
     * @return the state of inclusion
     */
    public boolean isIncluded() {
        return included;
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                groupId,
                artifactId,
                version,
                checksumAlgorithm,
                checksum,
                scope,
                selectedVersion,
                id,
                parent,
                children);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof DependencyNode)) {
            return false;
        }
        DependencyNode other = (DependencyNode) obj;
        return Objects.equals(groupId, other.groupId)
                && Objects.equals(artifactId, other.artifactId)
                && Objects.equals(version, other.version)
                && Objects.equals(checksumAlgorithm, other.checksumAlgorithm)
                && Objects.equals(checksum, other.checksum)
                && scope == other.scope
                && Objects.equals(selectedVersion, other.selectedVersion)
                && Objects.equals(id, other.id)
                && Objects.equals(parent, other.parent)
                && Objects.equals(children, other.children);
    }

    @Override
    public int compareTo(DependencyNode o) {
        int groupIdCompare = groupId.compareTo(o.groupId);
        if (groupIdCompare != 0) {
            return groupIdCompare;
        }
        int artifactIdCompare = artifactId.compareTo(o.artifactId);
        if (artifactIdCompare != 0) {
            return artifactIdCompare;
        }
        return version.compareTo(o.version);
    }

    @Override
    public String toString() {
        return "DependencyNode [groupId=" + groupId + ", artifactId=" + artifactId + ", version="
                + version + ", checksumAlgorithm=" + checksumAlgorithm + ", checksum=" + checksum
                + ", scope=" + scope + ", selectedVersion=" + selectedVersion + ", id=" + id + ", parent="
                + parent + ", children=" + children + "]";
    }
}
