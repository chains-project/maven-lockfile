package io.github.chains_project;

import org.eclipse.aether.RepositoryEvent;
import org.eclipse.aether.RepositoryListener;
import org.eclipse.aether.repository.RemoteRepository;

public class LoggingRepositoryListener implements RepositoryListener {

    @Override
    public void artifactDownloaded(RepositoryEvent event) {
        if (event.getRepository() instanceof RemoteRepository) {
            RemoteRepository remoteRepo = (RemoteRepository)event.getRepository();
            System.out.println("Downloaded artifact: " + event.getArtifact() + " from " + remoteRepo.getUrl());
        } else if (event.getRepository() != null) {
            System.out.println("Downloaded artifact: " + event.getArtifact() + " from repository: " + event.getRepository().toString());
        } else {
            System.out.println("Downloaded artifact: " + event.getArtifact() + " from unknown repository");
        }
    }

    @Override
    public void artifactResolved(RepositoryEvent event) {}
    @Override
    public void artifactResolving(RepositoryEvent event) {}
    @Override
    public void artifactDownloading(RepositoryEvent event) {}
    @Override
    public void metadataResolved(RepositoryEvent event) {}
    @Override
    public void metadataResolving(RepositoryEvent event) {}
    @Override
    public void metadataDownloaded(RepositoryEvent event) {}
    @Override
    public void metadataDownloading(RepositoryEvent event) {}
    @Override
    public void artifactDescriptorInvalid(RepositoryEvent event) {}
    @Override
    public void artifactDescriptorMissing(RepositoryEvent event) {}
    @Override
    public void artifactInstalling(RepositoryEvent event) {}
    @Override
    public void metadataDeploying(RepositoryEvent event) {}
    @Override
    public void metadataInstalling(RepositoryEvent event) {}
    @Override
    public void metadataInstalled(RepositoryEvent event) {}
    @Override
    public void metadataInvalid(RepositoryEvent event) {}
    @Override
    public void artifactInstalled(RepositoryEvent event) {}
    @Override
    public void artifactDeploying(RepositoryEvent event) {}
    @Override
    public void artifactDeployed(RepositoryEvent event) {}
    @Override
    public void metadataDeployed(RepositoryEvent event) {}
}
