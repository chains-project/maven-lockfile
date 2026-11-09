package io.github.chains_project.maven_lockfile.checksum;

import com.google.common.io.BaseEncoding;
import io.github.chains_project.maven_lockfile.data.RepositoryId;
import io.github.chains_project.maven_lockfile.data.ResolvedUrl;
import io.github.chains_project.maven_lockfile.reporting.PluginLogManager;
import java.io.File;
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

    public FileSystemChecksumCalculator(
            RepositorySystem repositorySystem,
            ProjectBuildingRequest artifactBuildingRequest,
            ProjectBuildingRequest pluginBuildingRequest,
            String checksumAlgorithm) {
        super(checksumAlgorithm);
        this.repositorySystem = repositorySystem;
        this.artifactBuildingRequest = artifactBuildingRequest;
        this.pluginBuildingRequest = pluginBuildingRequest;
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
            // The local repository manager knows how the running Maven records an artifact's origin
            // (_remote.repositories keys differ between Resolver 1 and 2 and are configurable), so
            // ask it instead of parsing that resolver-internal file.
            RepositorySystemSession session = buildingRequest.getRepositorySession();
            LocalArtifactRequest request = new LocalArtifactRequest(
                    RepositoryUtils.toArtifact(artifact),
                    RepositoryUtils.toRepos(buildingRequest.getRemoteRepositories()),
                    null);
            // File-based accessors are deprecated in Resolver 2 but the Path ones do not exist in
            // Resolver 1, which Maven 3.9 provides at runtime.
            LocalArtifactResult local = session.getLocalRepositoryManager().find(session, request);
            if (local.getFile() == null || !directoryOf(local.getFile()).equals(directoryOf(artifact.getFile()))) {
                // The file was not taken from the local repository (e.g. a reactor module's output).
                return Optional.empty();
            }

            RemoteRepository repository = local.getRepository();
            if (repository == null && local.isAvailable()) {
                repository = findInUserLocalRepository(session, request);
            }
            if (repository == null) {
                // Locally installed artifact, or downloaded from a repository this build does not use.
                return Optional.empty();
            }

            String type = artifact.getArtifactHandler().getExtension();
            String classifier = artifact.getClassifier() == null ? "" : "-" + artifact.getClassifier();
            String artifactFileName = artifact.getArtifactId() + "-" + artifact.getVersion() + classifier + "." + type;

            String groupId = artifact.getGroupId().replace(".", "/");
            String artifactId = artifact.getArtifactId();
            String baseVersion = artifact.getBaseVersion();

            String url = repository.getUrl().replaceAll("/$", "") + "/" + groupId + "/" + artifactId + "/" + baseVersion
                    + "/" + artifactFileName;

            return Optional.of(new RepositoryInformation(ResolvedUrl.of(url), RepositoryId.of(repository.getId())));
        } catch (Exception e) {
            PluginLogManager.getLog()
                    .warn(String.format("Could not fetch remote repository for artifact %s", artifact), e);
            return Optional.empty();
        }
    }

    /**
     * An artifact copied into an isolated local repository (e.g. by an integration test harness) is
     * recorded there as locally installed. The user's default local repository may still record
     * where it was downloaded from.
     */
    private RemoteRepository findInUserLocalRepository(RepositorySystemSession session, LocalArtifactRequest request) {
        Path userLocalRepository = Paths.get(System.getProperty("user.home"), ".m2", "repository")
                .toAbsolutePath()
                .normalize();
        Path sessionLocalRepository = session.getLocalRepository()
                .getBasedir()
                .toPath()
                .toAbsolutePath()
                .normalize();
        if (userLocalRepository.equals(sessionLocalRepository) || !Files.isDirectory(userLocalRepository)) {
            return null;
        }
        LocalRepositoryManager userManager =
                repositorySystem.newLocalRepositoryManager(session, new LocalRepository(userLocalRepository.toFile()));
        return userManager.find(session, request).getRepository();
    }

    private static Path directoryOf(File file) {
        return file.toPath().toAbsolutePath().normalize().getParent();
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
