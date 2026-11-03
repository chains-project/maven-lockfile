package io.github.chains_project.maven_lockfile.checksum;

import com.google.common.io.BaseEncoding;
import io.github.chains_project.maven_lockfile.data.RepositoryId;
import io.github.chains_project.maven_lockfile.data.ResolvedUrl;
import io.github.chains_project.maven_lockfile.reporting.PluginLogManager;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.*;
import org.apache.maven.RepositoryUtils;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.project.ProjectBuildingRequest;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.repository.LocalArtifactRequest;
import org.eclipse.aether.repository.LocalArtifactResult;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.LocalRepositoryManager;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactRequest;

public class FileSystemChecksumCalculator extends AbstractChecksumCalculator {

    private final RepositorySystem repositorySystem;
    private final ProjectBuildingRequest artifactBuildingRequest;
    private final ProjectBuildingRequest pluginBuildingRequest;
    private final Path userLocalRepository;
    private LocalRepositoryManager userLocalRepositoryManager;

    public FileSystemChecksumCalculator(
            RepositorySystem repositorySystem,
            ProjectBuildingRequest artifactBuildingRequest,
            ProjectBuildingRequest pluginBuildingRequest,
            String checksumAlgorithm) {
        this(
                repositorySystem,
                artifactBuildingRequest,
                pluginBuildingRequest,
                checksumAlgorithm,
                Paths.get(System.getProperty("user.home"), ".m2", "repository"));
    }

    /**
     * @param userLocalRepository where to look up the origin of an artifact that the build's local
     *     repository records as locally installed, typically the user's {@code ~/.m2/repository}
     */
    FileSystemChecksumCalculator(
            RepositorySystem repositorySystem,
            ProjectBuildingRequest artifactBuildingRequest,
            ProjectBuildingRequest pluginBuildingRequest,
            String checksumAlgorithm,
            Path userLocalRepository) {
        super(checksumAlgorithm);
        this.repositorySystem = repositorySystem;
        this.artifactBuildingRequest = artifactBuildingRequest;
        this.pluginBuildingRequest = pluginBuildingRequest;
        this.userLocalRepository = userLocalRepository;
    }

    private Artifact resolveDependency(Artifact artifact, ProjectBuildingRequest buildingRequest) {
        try {
            ArtifactRequest request = new ArtifactRequest(
                    RepositoryUtils.toArtifact(artifact),
                    RepositoryUtils.toRepos(buildingRequest.getRemoteRepositories()),
                    null);
            return RepositoryUtils.toArtifact(repositorySystem
                    .resolveArtifact(buildingRequest.getRepositorySession(), request)
                    .getArtifact());
        } catch (Exception e) {
            PluginLogManager.getLog()
                    .warn(String.format("Could not resolve artifact: %s", artifact.getArtifactId()), e);
            return artifact;
        }
    }

    private Optional<String> calculateChecksumInternal(Artifact artifact) {
        if (artifact.getFile() == null) {
            PluginLogManager.getLog().warn(String.format("Artifact %s has no file", artifact));
            PluginLogManager.getLog().error("Artifact has no file");
            return Optional.empty();
        }
        try {
            MessageDigest messageDigest = MessageDigest.getInstance(checksumAlgorithm);
            byte[] fileBuffer = Files.readAllBytes(artifact.getFile().toPath());
            byte[] artifactHash = messageDigest.digest(fileBuffer);
            BaseEncoding baseEncoding = BaseEncoding.base16();
            return Optional.of(baseEncoding.encode(artifactHash).toLowerCase(Locale.ROOT));
        } catch (Exception e) {
            PluginLogManager.getLog().warn(String.format("Could not calculate checksum for artifact %s", artifact), e);
            return Optional.empty();
        }
    }

    private Optional<RepositoryInformation> getResolvedFieldInternal(
            Artifact artifact, ProjectBuildingRequest buildingRequest) {
        if (artifact.getFile() == null) {
            PluginLogManager.getLog().warn(String.format("Artifact %s has no file", artifact));
            PluginLogManager.getLog().error("Artifact has no file");
            return Optional.empty();
        }
        try {
            Optional<RemoteRepository> remoteRepository = findOrigin(artifact, buildingRequest);
            if (remoteRepository.isEmpty()) {
                // Locally installed, or not downloaded from any of the build's repositories
                return Optional.empty();
            }

            String groupId = artifact.getGroupId().replace(".", "/");
            String artifactId = artifact.getArtifactId();
            String baseVersion = artifact.getBaseVersion();
            String classifier = artifact.hasClassifier() ? "-" + artifact.getClassifier() : "";
            String artifactFileName = artifactId + "-" + artifact.getVersion() + classifier + "."
                    + artifact.getArtifactHandler().getExtension();

            String url = remoteRepository.get().getUrl().replaceAll("/$", "") + "/" + groupId + "/" + artifactId + "/"
                    + baseVersion + "/" + artifactFileName;

            return Optional.of(new RepositoryInformation(
                    ResolvedUrl.of(url), RepositoryId.of(remoteRepository.get().getId())));
        } catch (Exception e) {
            PluginLogManager.getLog()
                    .warn(String.format("Could not fetch remote repository for artifact %s", artifact), e);
            return Optional.empty();
        }
    }

    /**
     * Asks the local repository manager which of the build's remote repositories the artifact was
     * downloaded from. The manager tracks this with the running Maven's own repository key function, so
     * the answer does not depend on how {@code _remote.repositories} spells a repository.
     */
    @SuppressWarnings("deprecation") // getFile() instead of getPath(): Maven 3.9 ships Resolver 1
    private Optional<RemoteRepository> findOrigin(Artifact artifact, ProjectBuildingRequest buildingRequest) {
        RepositorySystemSession session = buildingRequest.getRepositorySession();
        LocalArtifactRequest request = new LocalArtifactRequest(
                RepositoryUtils.toArtifact(artifact),
                RepositoryUtils.toRepos(buildingRequest.getRemoteRepositories()),
                null);
        LocalArtifactResult result = session.getLocalRepositoryManager().find(session, request);
        // The origin must be that of the checksummed file, not of a namesake (e.g. a reactor module's jar)
        if (!result.isAvailable() || !inSameDirectory(result.getFile(), artifact.getFile())) {
            return Optional.empty();
        }
        if (result.getRepository() != null) {
            return Optional.of(result.getRepository());
        }
        // Installed locally, e.g. copied into an isolated repository for integration tests. The user's
        // ~/.m2 may still record which repository it was originally downloaded from.
        return findOriginInUserLocalRepository(session, request);
    }

    @SuppressWarnings("deprecation") // File-based API: Maven 3.9 ships Resolver 1
    private Optional<RemoteRepository> findOriginInUserLocalRepository(
            RepositorySystemSession session, LocalArtifactRequest request) {
        Path localRepository = session.getLocalRepository()
                .getBasedir()
                .toPath()
                .toAbsolutePath()
                .normalize();
        if (repositorySystem == null
                || localRepository.equals(userLocalRepository.toAbsolutePath().normalize())) {
            return Optional.empty();
        }
        if (userLocalRepositoryManager == null) {
            userLocalRepositoryManager = repositorySystem.newLocalRepositoryManager(
                    session, new LocalRepository(userLocalRepository.toFile()));
        }
        return Optional.ofNullable(
                userLocalRepositoryManager.find(session, request).getRepository());
    }

    private static boolean inSameDirectory(File first, File second) {
        try {
            return Files.isSameFile(
                    first.toPath().toAbsolutePath().getParent(),
                    second.toPath().toAbsolutePath().getParent());
        } catch (IOException e) {
            return false;
        }
    }

    @Override
    public String calculateArtifactChecksum(Artifact artifact) {
        return calculateChecksumInternal(resolveDependency(artifact, artifactBuildingRequest))
                .orElse("");
    }

    @Override
    public String calculatePluginChecksum(Artifact artifact) {
        return calculateChecksumInternal(resolveDependency(artifact, pluginBuildingRequest))
                .orElse("");
    }

    @Override
    public String getDefaultChecksumAlgorithm() {
        return "SHA-256";
    }

    @Override
    public RepositoryInformation getArtifactResolvedField(Artifact artifact) {
        // If the artifact already has its file set (e.g. pre-resolved from local .m2),
        // skip re-resolution — the resolver may return a new Artifact without the file.
        Artifact toCheck = artifact.getFile() != null ? artifact : resolveDependency(artifact, artifactBuildingRequest);
        return getResolvedFieldInternal(toCheck, artifactBuildingRequest).orElse(RepositoryInformation.Unresolved());
    }

    @Override
    public RepositoryInformation getPluginResolvedField(Artifact artifact) {
        Artifact toCheck = artifact.getFile() != null ? artifact : resolveDependency(artifact, pluginBuildingRequest);
        return getResolvedFieldInternal(toCheck, pluginBuildingRequest).orElse(RepositoryInformation.Unresolved());
    }
}
