package io.github.chains_project.maven_lockfile.resolvers;

import io.github.chains_project.maven_lockfile.exceptions.ProjectResolutionException;
import io.github.chains_project.maven_lockfile.reporting.PluginLogManager;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.factory.ArtifactFactory;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.building.ModelBuildingRequest;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.*;
import org.apache.maven.shared.transfer.artifact.resolve.ArtifactResolver;
import org.apache.maven.shared.transfer.artifact.resolve.ArtifactResolverException;
import org.apache.maven.shared.transfer.artifact.resolve.ArtifactResult;
import org.codehaus.plexus.component.repository.exception.ComponentLookupException;

public class ProjectBuilder {
    private final MavenSession session;
    private final Log log;

    @SuppressWarnings("deprecation")
    private final List<ArtifactRepository> repositories;

    @SuppressWarnings("deprecation")
    public ProjectBuilder(MavenSession session, List<ArtifactRepository> repositories) {
        this.session = session;
        this.log = PluginLogManager.getLog();
        this.repositories = repositories;
    }

    /**
     * Builds a MavenProject from a GAV specification.
     *
     * @return the built MavenProject.
     */
    public MavenProject buildFromGav(String groupId, String artifactId, String version) {
        log.debug(String.format("Resolving dependencies for%s:%s:%s", groupId, artifactId, version));

        var pomFileOptional = lookForPomFileInLocalRepository(groupId, artifactId, version);
        if (pomFileOptional.isPresent()) {
            return buildProjectFromPomFile(groupId, artifactId, version, pomFileOptional.get());
        }

        var pomFile = resolvePomFile(groupId, artifactId, version);
        return buildProjectFromPomFile(groupId, artifactId, version, pomFile);
    }

    private Optional<File> lookForPomFileInLocalRepository(String groupId, String artifactId, String version) {
        var repositorySession = session.getRepositorySession();

        // getBasedir is deprecated, but is compatible with Maven 3.9.x.
        @SuppressWarnings("deprecation")
        File localRepoBase = repositorySession.getLocalRepository().getBasedir();

        String groupPath = groupId.replace(".", "/");
        String pomFileName = artifactId + "-" + version + ".pom";

        Path localPomPath = Paths.get(localRepoBase.getAbsolutePath(), groupPath, artifactId, version, pomFileName);
        if (Files.exists(localPomPath)) {
            return Optional.of(localPomPath.toFile());
        }

        return Optional.empty();
    }

    private File resolvePomFile(String groupId, String artifactId, String version) {
        try {
            @SuppressWarnings("deprecation")
            ArtifactFactory artifactFactory = session.getContainer().lookup(ArtifactFactory.class);
            Artifact pomArtifact = artifactFactory.createArtifact(groupId, artifactId, version, null, "pom");

            ProjectBuildingRequest pomBuildingRequest =
                    new DefaultProjectBuildingRequest(session.getProjectBuildingRequest());
            pomBuildingRequest.setRemoteRepositories(repositories);

            @SuppressWarnings("deprecation")
            ArtifactResolver artifactResolver = session.getContainer().lookup(ArtifactResolver.class);
            ArtifactResult result = artifactResolver.resolveArtifact(pomBuildingRequest, pomArtifact);

            if (result != null
                    && result.getArtifact() != null
                    && result.getArtifact().getFile() != null) {
                return result.getArtifact().getFile();
            }

            throw new ProjectResolutionException(
                    groupId,
                    artifactId,
                    version,
                    "Couldn't resolve POM file for artifact, resolver returned an empty result");
        } catch (ComponentLookupException | ArtifactResolverException e) {
            throw new ProjectResolutionException(groupId, artifactId, version, e.getMessage());
        }
    }

    private MavenProject buildProjectFromPomFile(String groupId, String artifactId, String version, File pomFile)
            throws ProjectResolutionException {
        // Build MavenProject from plugin POM
        ProjectBuildingRequest buildingRequest = new DefaultProjectBuildingRequest(session.getProjectBuildingRequest());
        buildingRequest.setRemoteRepositories(repositories);
        buildingRequest.setProcessPlugins(false);
        buildingRequest.setResolveDependencies(true);

        try {
            // Note: getContainer() is deprecated but there's no clear replacement in the current Maven API
            @SuppressWarnings("deprecation")
            org.apache.maven.project.ProjectBuilder projectBuilder =
                    session.getContainer().lookup(org.apache.maven.project.ProjectBuilder.class);
            buildingRequest.setValidationLevel(ModelBuildingRequest.VALIDATION_LEVEL_MINIMAL);
            ProjectBuildingResult result = projectBuilder.build(pomFile, buildingRequest);

            if (result.getProject() == null) {
                throw new ProjectResolutionException(
                        groupId, artifactId, version, result.getProblems().toString());
            }

            return result.getProject();
        } catch (ComponentLookupException | ProjectBuildingException e) {
            throw new ProjectResolutionException(groupId, artifactId, version, e.getMessage());
        }
    }
}
