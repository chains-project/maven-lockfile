package io.github.chains_project.maven_lockfile.checksum;

import io.github.chains_project.maven_lockfile.data.RepositoryId;
import io.github.chains_project.maven_lockfile.data.ResolvedUrl;

public class RepositoryInformation {
    private final ResolvedUrl resolvedUrl;
    private final RepositoryId repositoryId;

    public RepositoryInformation(ResolvedUrl resolvedUrl, RepositoryId repositoryId) {
        this.resolvedUrl = resolvedUrl;
        this.repositoryId = repositoryId;
    }

    public static RepositoryInformation Unresolved() {
        return new RepositoryInformation(ResolvedUrl.Unresolved(), RepositoryId.None());
    }

    /**
     * @return the resolved url
     */
    public ResolvedUrl getResolvedUrl() {
        return resolvedUrl;
    }

    /**
     * @return the repository id
     */
    public RepositoryId getRepositoryId() {
        return repositoryId;
    }
}
