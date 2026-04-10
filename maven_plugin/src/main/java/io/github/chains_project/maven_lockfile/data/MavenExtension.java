package io.github.chains_project.maven_lockfile.data;

import io.github.chains_project.maven_lockfile.graph.DependencyNode;
import java.util.Set;

/**
 * A Maven build extension with its dependencies and metadata.
 * Build extensions extend Maven's core capabilities and are loaded before the build starts.
 */
public class MavenExtension extends AbstractMavenComponent {

    /**
     * Create a Maven extension.
     *
     * @param groupId           the group ID
     * @param artifactId        the artifact ID
     * @param version           the version
     * @param checksum          the checksum
     * @param checksumAlgorithm the checksum algorithm
     * @param resolved          the resolved URL
     * @param repositoryId      the repository ID
     * @param dependencies      the extension's dependencies
     */
    public MavenExtension(
            GroupId groupId,
            ArtifactId artifactId,
            VersionNumber version,
            String checksum,
            String checksumAlgorithm,
            ResolvedUrl resolved,
            RepositoryId repositoryId,
            Set<DependencyNode> dependencies) {
        super(groupId, artifactId, version, checksum, checksumAlgorithm, resolved, repositoryId, dependencies, null);
    }

    @Override
    public String toString() {
        return "MavenExtension [groupId=" + groupId + ", artifactId=" + artifactId + ", version=" + version + "]";
    }
}
