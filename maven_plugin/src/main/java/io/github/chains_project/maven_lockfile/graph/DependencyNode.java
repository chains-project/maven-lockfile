package io.github.chains_project.maven_lockfile.graph;

import com.google.gson.annotations.Expose;
import io.github.chains_project.maven_lockfile.data.ArtifactId;
import io.github.chains_project.maven_lockfile.data.Classifier;
import io.github.chains_project.maven_lockfile.data.GroupId;
import io.github.chains_project.maven_lockfile.data.MavenScope;
import io.github.chains_project.maven_lockfile.data.RepositoryId;
import io.github.chains_project.maven_lockfile.data.ResolvedUrl;
import io.github.chains_project.maven_lockfile.data.VersionNumber;
import java.util.*;

/**
 * This class represents a node in the dependency graph. It contains the artifactId, groupId and version  of the dependency.
 * It also contains a reference to the parent node.
 */
public class DependencyNode implements Comparable<DependencyNode> {

    private final GroupId groupId;
    private final ArtifactId artifactId;
    private final VersionNumber version;
    private final Classifier classifier;
    private final String checksumAlgorithm;
    private final String checksum;
    private final MavenScope scope;
    private final ResolvedUrl resolved;
    private final RepositoryId repositoryId;

    private String selectedVersion;

    private boolean included;

    NodeId id;

    @Expose(serialize = false, deserialize = false)
    private NodeId parent;

    private final Set<DependencyNode> children;

    DependencyNode(
            ArtifactId artifactId,
            GroupId groupId,
            VersionNumber version,
            Classifier classifier,
            MavenScope scope,
            ResolvedUrl resolved,
            RepositoryId repositoryId,
            String checksumAlgorithm,
            String checksum) {
        this.artifactId = artifactId;
        this.groupId = groupId;
        this.version = version;
        this.classifier = classifier;
        this.checksumAlgorithm = checksumAlgorithm;
        this.checksum = checksum;
        this.children = new TreeSet<>(Comparator.comparing(DependencyNode::getComparatorString));
        this.id = new NodeId(groupId, artifactId, version);
        this.scope = scope;
        this.resolved = resolved;
        this.repositoryId = repositoryId;
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
     * @return the classifier or null if not present
     */
    public Classifier getClassifier() {
        return classifier;
    }
    /**
     * @return the scope
     */
    public MavenScope getScope() {
        return scope;
    }
    /**
     * @return the resolved url.
     */
    public ResolvedUrl getResolved() {
        return resolved;
    }
    /**
     * @return the repository id.
     */
    public RepositoryId getRepositoryId() {
        return repositoryId;
    }

    void addChild(DependencyNode child) {
        children.add(child);
        child.setParent(id);
        if (!child.parent.equals(id)) {
            throw new IllegalStateException("Child node has wrong parent");
        }
    }

    void setParent(NodeId parent) {
        this.parent = parent;
    }

    /**
     * @return the children
     */
    public Set<DependencyNode> getChildren() {
        return Collections.unmodifiableSet(children);
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
                classifier,
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
                && Objects.equals(classifier, other.classifier)
                && Objects.equals(checksumAlgorithm, other.checksumAlgorithm)
                && Objects.equals(checksum, other.checksum)
                && Objects.equals(scope, other.scope)
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
        int versionCompare = version.compareTo(o.version);
        if (versionCompare != 0) {
            return versionCompare;
        }
        if (classifier == null) {
            if (o.classifier == null) {
                return 0;
            }
            return -1;
        }
        if (o.classifier == null) {
            return 1;
        }
        return classifier.compareTo(o.classifier);
    }

    @Override
    public String toString() {
        return "DependencyNode [groupId=" + groupId + ", artifactId=" + artifactId + ", version=" + version
                + ", classifier=" + classifier + ", checksumAlgorithm=" + checksumAlgorithm + ", checksum=" + checksum
                + ", scope=" + scope + ", resolved=" + resolved + ", repositoryId=" + repositoryId
                + ", selectedVersion=" + selectedVersion + ", id=" + id + ", parent=" + parent + ", children="
                + children + "]";
    }

    public String getComparatorString() {
        return this.getGroupId().getValue() + "#" + this.getArtifactId().getValue() + "#"
                + this.getVersion().getValue() + "#" + this.getChecksum();
    }
}
