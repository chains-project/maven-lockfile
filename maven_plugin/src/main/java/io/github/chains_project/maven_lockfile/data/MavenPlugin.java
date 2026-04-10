package io.github.chains_project.maven_lockfile.data;

import io.github.chains_project.maven_lockfile.graph.DependencyNode;
import java.util.Set;

/**
 * This class represents a maven plugin. It contains a group id, an artifact id, and a version number.
 * A plugin is uniquely identified by its group id, artifact id, and version number.
 * A maven plugin is a dependency that is used to build a project. It is not a dependency of the project itself.
 */
public class MavenPlugin extends AbstractMavenComponent {

    public MavenPlugin(
            GroupId groupId,
            ArtifactId artifactId,
            VersionNumber version,
            ResolvedUrl resolvedUrl,
            RepositoryId repositoryId,
            String checksumAlgorithm,
            String checksum) {
        this(groupId, artifactId, version, resolvedUrl, repositoryId, checksumAlgorithm, checksum, null,null);
    }

    public MavenPlugin(
            GroupId groupId,
            ArtifactId artifactId,
            VersionNumber version,
            ResolvedUrl resolvedUrl,
            RepositoryId repositoryId,
            String checksumAlgorithm,
            String checksum,
            Set<DependencyNode> dependencies,
            Pom parent) {
        super(groupId, artifactId, version, checksum, checksumAlgorithm, resolvedUrl, repositoryId, dependencies, parent);
    }

    @Override
    public String toString() {
        // only show the group id, artifact id, and version - this is sufficient for debugging purposes
        return "MavenPlugin [groupId=" + groupId + ", artifactId=" + artifactId + ", version=" + version + "]";
    }
}
