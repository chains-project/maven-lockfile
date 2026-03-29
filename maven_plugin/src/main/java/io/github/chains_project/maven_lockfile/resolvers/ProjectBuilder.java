package io.github.chains_project.maven_lockfile.resolvers;

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
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.*;
import org.apache.maven.shared.transfer.artifact.resolve.ArtifactResolver;
import org.apache.maven.shared.transfer.artifact.resolve.ArtifactResolverException;
import org.apache.maven.shared.transfer.artifact.resolve.ArtifactResult;
import org.codehaus.plexus.component.repository.exception.ComponentLookupException;

public class ProjectBuilder {
    private final org.apache.maven.project.ProjectBuilder injectedProjectBuilder;
    private final MavenSession session;
    private final Log log;

    @SuppressWarnings("deprecation")
    private final List<ArtifactRepository> repositories;

    @SuppressWarnings("deprecation")
    public ProjectBuilder(
            MavenSession session,
            List<ArtifactRepository> repositories,
            org.apache.maven.project.ProjectBuilder projectBuilder) {
        this.session = session;
        this.log = PluginLogManager.getLog();
        this.repositories = repositories;
        this.injectedProjectBuilder = projectBuilder;
    }

    /**
     * Builds a MavenProject from a GAV specification.
     *
     * @return an Optional that contains the MavenProject in case it was successfully built.
     */
    public Optional<MavenProject> buildFromGav(String groupId, String artifactId, String version) {
        log.debug(String.format("Resolving dependencies for%s:%s:%s", groupId, artifactId, version));

        var pomFileOptional = lookForPomFileInLocalRepository(groupId, artifactId, version);

        if (pomFileOptional.isEmpty()) {
            pomFileOptional = resolvePomFile(groupId, artifactId, version);
        }

        if (pomFileOptional.isPresent()) {
            return buildProjectFromPomFile(pomFileOptional.get());
        }

        return Optional.empty();
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

    private Optional<File> resolvePomFile(String groupId, String artifactId, String version) {
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
                return Optional.of(result.getArtifact().getFile());
            }

            log.warn(String.format(
                    "Couldn't resolve POM file for %s:%s:%s: resolver returned an empty result",
                    groupId, artifactId, version));
        } catch (ComponentLookupException | ArtifactResolverException e) {
            log.warn(String.format(
                    "Couldn't resolve POM file for %s:%s:%s: %s", groupId, artifactId, version, e.getMessage()));
        }

        return Optional.empty();
    }

    private Optional<MavenProject> buildProjectFromPomFile(File pomFile) {
        // Build MavenProject from plugin POM
        ProjectBuildingRequest buildingRequest = new DefaultProjectBuildingRequest(session.getProjectBuildingRequest());
        buildingRequest.setRemoteRepositories(repositories);
        buildingRequest.setProcessPlugins(false);
        buildingRequest.setResolveDependencies(false);

        try {
            ProjectBuildingResult result = injectedProjectBuilder.build(pomFile, buildingRequest);

            if (result.getProject() == null) {
                log.warn(String.format(
                        "Problems building plugin project for %s: %s",
                        pomFile.getAbsoluteFile(), result.getProblems()));
                return Optional.empty();
            }

            return Optional.of(result.getProject());
        } catch (ProjectBuildingException e) {
            log.warn(String.format("Couldn't build project for %s: %s", pomFile.getAbsoluteFile(), e.getMessage()));
        }

        return Optional.empty();
    }
}
