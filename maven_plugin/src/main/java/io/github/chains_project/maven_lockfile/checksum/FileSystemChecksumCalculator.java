package io.github.chains_project.maven_lockfile.checksum;

import com.google.common.io.BaseEncoding;
import io.github.chains_project.maven_lockfile.data.RepositoryId;
import io.github.chains_project.maven_lockfile.data.ResolvedUrl;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.*;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.model.Dependency;
import org.apache.maven.project.ProjectBuildingRequest;
import org.apache.maven.shared.transfer.dependencies.resolve.DependencyResolver;

public class FileSystemChecksumCalculator extends AbstractChecksumCalculator {

    private static final Logger LOGGER = LogManager.getLogger(FileSystemChecksumCalculator.class);

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
            LOGGER.warn("Could not resolve artifact: {}", artifact.getArtifactId(), e);
            return artifact;
        }
    }

    private Optional<String> calculateChecksumInternal(Artifact artifact) {
        if (artifact.getFile() == null) {
            LOGGER.warn("Artifact {} has no file", artifact);
            LOGGER.error("Artifact has no file");
            return Optional.empty();
        }
        try {
            MessageDigest messageDigest = MessageDigest.getInstance(checksumAlgorithm);
            byte[] fileBuffer = Files.readAllBytes(artifact.getFile().toPath());
            byte[] artifactHash = messageDigest.digest(fileBuffer);
            BaseEncoding baseEncoding = BaseEncoding.base16();
            return Optional.of(baseEncoding.encode(artifactHash).toLowerCase(Locale.ROOT));
        } catch (Exception e) {
            LOGGER.warn("Could not calculate checksum for artifact {}", artifact, e);
            return Optional.empty();
        }
    }

    private Optional<RepositoryInformation> getResolvedFieldInternal(
            Artifact artifact, ProjectBuildingRequest buildingRequest) {
        if (artifact.getFile() == null) {
            LOGGER.warn("Artifact {} has no file", artifact);
            LOGGER.error("Artifact has no file");
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

            String type = artifact.getType();
            if (type.equals("maven-plugin")) {
                type = "jar";
            }

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
                    LOGGER.warn("Possible unknown _remote.repositories format");
                    continue;
                }

                repository = remoteRepository.substring(start + 1, end);
                if (!remoteRepositoriesSet.contains(repository)) {
                    continue;
                }

                break;
            }

            if (repository == null) {
                // No repository found, possible locally installed artifact or unknown _remote.repositories format
                return Optional.empty();
            }

            // Convert repository to url
            final String finalRepository = repository;
            Optional<ArtifactRepository> remoteRepository = buildingRequest.getRemoteRepositories().stream()
                    .filter(repo -> (repo.getId().equals(finalRepository)))
                    .findFirst();

            if (remoteRepository.isEmpty()) {
                LOGGER.warn("Could not find repository '{}' in building request.", finalRepository);
                return Optional.empty();
            }

            String groupId = artifact.getGroupId().replace(".", "/");
            String artifactId = artifact.getArtifactId();
            String version = artifact.getVersion();

            String url = remoteRepository.get().getUrl().replaceAll("/$", "") + "/" + groupId + "/" + artifactId + "/"
                    + version + "/" + target;

            return Optional.of(new RepositoryInformation(ResolvedUrl.of(url), RepositoryId.of(repository)));
        } catch (Exception e) {
            LOGGER.warn("Could not fetch remote repository for artifact {}", artifact, e);
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
        return getResolvedFieldInternal(resolveDependency(artifact, artifactBuildingRequest), artifactBuildingRequest)
                .orElse(RepositoryInformation.Unresolved());
    }

    @Override
    public RepositoryInformation getPluginResolvedField(Artifact artifact) {
        return getResolvedFieldInternal(resolveDependency(artifact, pluginBuildingRequest), pluginBuildingRequest)
                .orElse(RepositoryInformation.Unresolved());
    }
}
