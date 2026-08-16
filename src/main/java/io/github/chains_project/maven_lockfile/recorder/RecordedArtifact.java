package io.github.chains_project.maven_lockfile.recorder;

import java.util.Objects;

/** An artifact coordinate observed being resolved during a session, captured by {@link DynamicResolutionSpy}. */
public final class RecordedArtifact implements Comparable<RecordedArtifact> {

    private final String groupId;
    private final String artifactId;
    private final String version;
    private final String extension;
    private final String classifier;
    private final String triggeringPluginGroupId;
    private final String triggeringPluginArtifactId;

    public RecordedArtifact(
            String groupId,
            String artifactId,
            String version,
            String extension,
            String classifier,
            String triggeringPluginGroupId,
            String triggeringPluginArtifactId) {
        this.groupId = groupId;
        this.artifactId = artifactId;
        this.version = version;
        this.extension = extension;
        this.classifier = classifier == null ? "" : classifier;
        this.triggeringPluginGroupId = triggeringPluginGroupId;
        this.triggeringPluginArtifactId = triggeringPluginArtifactId;
    }

    public String getGroupId() {
        return groupId;
    }

    public String getArtifactId() {
        return artifactId;
    }

    public String getVersion() {
        return version;
    }

    public String getExtension() {
        return extension;
    }

    public String getClassifier() {
        return classifier;
    }

    /** GroupId of the plugin whose Mojo was executing when this artifact was resolved, or null if none was. */
    public String getTriggeringPluginGroupId() {
        return triggeringPluginGroupId;
    }

    public String getTriggeringPluginArtifactId() {
        return triggeringPluginArtifactId;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof RecordedArtifact)) {
            return false;
        }
        RecordedArtifact other = (RecordedArtifact) obj;
        return Objects.equals(groupId, other.groupId)
                && Objects.equals(artifactId, other.artifactId)
                && Objects.equals(version, other.version)
                && Objects.equals(extension, other.extension)
                && Objects.equals(classifier, other.classifier);
    }

    @Override
    public int hashCode() {
        return Objects.hash(groupId, artifactId, version, extension, classifier);
    }

    @Override
    public int compareTo(RecordedArtifact o) {
        int c = groupId.compareTo(o.groupId);
        if (c != 0) return c;
        c = artifactId.compareTo(o.artifactId);
        if (c != 0) return c;
        c = version.compareTo(o.version);
        if (c != 0) return c;
        c = extension.compareTo(o.extension);
        if (c != 0) return c;
        return classifier.compareTo(o.classifier);
    }

    @Override
    public String toString() {
        return groupId + ":" + artifactId + ":" + version
                + (extension == null ? "" : ":" + extension)
                + (classifier == null || classifier.isEmpty() ? "" : ":" + classifier);
    }
}
