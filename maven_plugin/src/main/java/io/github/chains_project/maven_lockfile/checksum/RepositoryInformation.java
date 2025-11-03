package io.github.chains_project.maven_lockfile.checksum;

import io.github.chains_project.maven_lockfile.data.RepositoryId;
import io.github.chains_project.maven_lockfile.data.ResolvedUrl;

public class RepositoryInformation {
    public final ResolvedUrl resolvedUrl;
    public final RepositoryId repositoryId;

    public RepositoryInformation(ResolvedUrl resolvedUrl, RepositoryId repositoryId) {
        this.resolvedUrl = resolvedUrl;
        this.repositoryId = repositoryId;
    }
}
