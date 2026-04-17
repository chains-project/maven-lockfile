package io.github.chains_project.maven_lockfile.checksum;

import com.google.common.io.BaseEncoding;
import io.github.chains_project.maven_lockfile.data.RepositoryId;
import io.github.chains_project.maven_lockfile.data.ResolvedUrl;
import io.github.chains_project.maven_lockfile.reporting.PluginLogManager;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.*;
import java.util.stream.Collectors;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.model.Dependency;
import org.apache.maven.project.ProjectBuildingRequest;
import org.apache.maven.shared.transfer.dependencies.resolve.DependencyResolver;

public class FileSystemChecksumCalculator extends AbstractChecksumCalculator {

    private final DependencyResolver resolver;
    private final ProjectBuildingRequest artifactBuildingRequest;
    private final ProjectBuildingRequest pluginBuildingRequest;

    public FileSystemChecksumCalculator(
            DependencyResolver resolver,
            ProjectBuildingRequest artifactBuildingRequest,
            ProjectBuildingRequest pluginBuildingRequest,
            String checksumAlgorithm) {
        super(checksumAlgorithm);
        this.resolver = resolver;
        this.artifactBuildingRequest = artifactBuildingRequest;
        this.pluginBuildingRequest = pluginBuildingRequest;
    }

    /**
     * Create a dependency from an artifact. This is necessary because the API of the dependency resolver expects a dependency.
     * @param node  The artifact to create a dependency from.
     * @return  The dependency
     */
    private Dependency createDependency(Artifact node) {
        Dependency dependency = new Dependency();
        dependency.setGroupId(node.getGroupId());
        dependency.setArtifactId(node.getArtifactId());
        dependency.setVersion(node.getVersion());
        dependency.setScope(node.getScope());
        dependency.setType(node.getType());
        dependency.setClassifier(node.getClassifier());
        dependency.setOptional(node.isOptional());
        return dependency;
    }

    private Artifact resolveDependency(Artifact artifact, ProjectBuildingRequest buildingRequest) {
        try {
            return resolver.resolveDependencies(buildingRequest, List.of(createDependency(artifact)), null, null)
                    .iterator()
                    .next()
                    .getArtifact();
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
            Path artifactFolderPath = artifact.getFile().toPath().getParent();
            Path remoteRepositoriesPath = artifactFolderPath.resolve("_remote.repositories");
            List<String> locallySavedRemoteRepositories = Files.readAllLines(remoteRepositoriesPath);

            Set<String> remoteRepositoriesSet = buildingRequest.getRemoteRepositories().stream()
                    .map(ArtifactRepository::getId)
                    .collect(Collectors.toSet());

            String repository = null;
            boolean foundWithEmptyRepoId = false;

            String type = artifact.getArtifactHandler().getExtension();

            String classifier = artifact.getClassifier();
            if (classifier == null) {
                classifier = "";
            } else {
                classifier = "-" + classifier;
            }

            String target = artifact.getArtifactId() + "-" + artifact.getVersion() + classifier + "." + type;

            for (String remoteRepository : locallySavedRemoteRepositories) {
                if (!remoteRepository.startsWith(target)) {
                    continue;
                }

                // Parsing 'repository' from 'artifactId-version.type>repository='
                int start = remoteRepository.indexOf(">");
                int end = remoteRepository.indexOf("=");

                if (start == -1 || end == -1) {
                    PluginLogManager.getLog().warn("Possible unknown _remote.repositories format");
                    continue;
                }

                repository = remoteRepository.substring(start + 1, end);
                if (repository.isEmpty()) {
                    // Empty repo ID means the artifact was resolved locally (e.g. copied from user's
                    // .m2 to an isolated repo). Record this and keep looking for a non-empty entry.
                    foundWithEmptyRepoId = true;
                    repository = null;
                    continue;
                }
                if (!remoteRepositoriesSet.contains(repository)) {
                    continue;
                }

                break;
            }

            if (repository == null) {
                if (foundWithEmptyRepoId) {
                    // Artifact was locally installed/copied (no remote attribution in _remote.repositories).
                    // Attempt to find the correct repo by checking the user's default .m2 repository,
                    // which retains the original remote attribution from when the artifact was downloaded.
                    Path isoRepoBase = java.nio.file.Paths.get(
                            buildingRequest.getLocalRepository().getBasedir());
                    if (artifactFolderPath.startsWith(isoRepoBase)) {
                        Path relPath = isoRepoBase.relativize(artifactFolderPath);
                        Path userM2 = java.nio.file.Paths.get(System.getProperty("user.home"), ".m2", "repository");
                        Path fallbackFolder = userM2.resolve(relPath);
                        Path fallbackRemoteRepo = fallbackFolder.resolve("_remote.repositories");
                        if (Files.exists(fallbackRemoteRepo)) {
                            for (String line : Files.readAllLines(fallbackRemoteRepo)) {
                                if (!line.startsWith(target)) continue;
                                int s = line.indexOf(">");
                                int e = line.indexOf("=");
                                if (s == -1 || e == -1) continue;
                                String fallbackId = line.substring(s + 1, e);
                                if (!fallbackId.isEmpty() && remoteRepositoriesSet.contains(fallbackId)) {
                                    repository = fallbackId;
                                    break;
                                }
                            }
                        }
                    }
                }
                if (repository == null) {
                    // No repository found, possible locally installed artifact or unknown format
                    return Optional.empty();
                }
            }

            // Convert repository to url
            final String finalRepository = repository;
            Optional<ArtifactRepository> remoteRepository = buildingRequest.getRemoteRepositories().stream()
                    .filter(repo -> (repo.getId().equals(finalRepository)))
                    .findFirst();

            if (remoteRepository.isEmpty()) {
                PluginLogManager.getLog()
                        .warn(String.format("Could not find repository '%s' in building request.", finalRepository));
                return Optional.empty();
            }

            String groupId = artifact.getGroupId().replace(".", "/");
            String artifactId = artifact.getArtifactId();
            String baseVersion = artifact.getBaseVersion();

            String url = remoteRepository.get().getUrl().replaceAll("/$", "") + "/" + groupId + "/" + artifactId + "/"
                    + baseVersion + "/" + target;

            return Optional.of(new RepositoryInformation(ResolvedUrl.of(url), RepositoryId.of(repository)));
        } catch (Exception e) {
            PluginLogManager.getLog()
                    .warn(String.format("Could not fetch remote repository for artifact %s", artifact), e);
            return Optional.empty();
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
        Artifact toCheck =
                artifact.getFile() != null ? artifact : resolveDependency(artifact, artifactBuildingRequest);
        return getResolvedFieldInternal(toCheck, artifactBuildingRequest)
                .orElse(RepositoryInformation.Unresolved());
    }

    @Override
    public RepositoryInformation getPluginResolvedField(Artifact artifact) {
        Artifact toCheck =
                artifact.getFile() != null ? artifact : resolveDependency(artifact, pluginBuildingRequest);
        return getResolvedFieldInternal(toCheck, pluginBuildingRequest)
                .orElse(RepositoryInformation.Unresolved());
    }
}
