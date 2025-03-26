package io.github.chains_project.maven_lockfile.checksum;

import com.google.common.io.BaseEncoding;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import io.github.chains_project.maven_lockfile.data.ResolvedUrl;
import org.apache.log4j.Logger;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.model.Dependency;
import org.apache.maven.project.ProjectBuildingRequest;
import org.apache.maven.shared.transfer.dependencies.resolve.DependencyResolver;

public class FileSystemChecksumCalculator extends AbstractChecksumCalculator {

    private static final Logger LOGGER = Logger.getLogger(FileSystemChecksumCalculator.class);

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
            LOGGER.warn("Could not resolve artifact: " + artifact.getArtifactId(), e);
            return artifact;
        }
    }

    private Optional<String> calculateChecksumInternal(Artifact artifact) {
        if (artifact.getFile() == null) {
            LOGGER.error("Artifact " + artifact + " has no file");
            return Optional.empty();
        }
        try {
            MessageDigest messageDigest = MessageDigest.getInstance(checksumAlgorithm);
            byte[] fileBuffer = Files.readAllBytes(artifact.getFile().toPath());
            byte[] artifactHash = messageDigest.digest(fileBuffer);
            BaseEncoding baseEncoding = BaseEncoding.base16();
            return Optional.of(baseEncoding.encode(artifactHash).toLowerCase(Locale.ROOT));
        } catch (Exception e) {
            LOGGER.warn("Could not calculate checksum for artifact " + artifact, e);
            return Optional.empty();
        }
    }

    private Optional<ResolvedUrl> getResolvedFieldInternal(Artifact artifact) {
        if (artifact.getFile() == null) {
            LOGGER.error("Artifact " + artifact + " has no file");
            return Optional.empty();
        }
        try {
            Path artifactFolderPath = artifact.getFile().toPath().getParent();
            Path remoteRepositoriesPath = artifactFolderPath.resolve("_remote.repositories");
            List<String> remoteRepositories = Files.readAllLines(remoteRepositoriesPath);

            String repository = null;
            String target = artifact.getArtifactId() + "-" + artifact.getVersion() + "." + artifact.getType();

            for (String remoteRepository : remoteRepositories) {
                if (!remoteRepository.startsWith(target)) {
                    continue;
                }

                if (!remoteRepository.contains(">") || !remoteRepository.contains("=")) {
                    LOGGER.warn("Possible unknown _remote.repositories format");
                    continue;
                }

                // Parsing 'repository' from 'artifactId-version.type>repository='
                int start = remoteRepository.indexOf(">");
                int end = remoteRepository.indexOf("=");
                repository = remoteRepository.substring(start + 1, end);
                break;
            }

            if (repository == null) {
                // No repository found, possible locally installed artifact or unknown _remote.repositories format
                return Optional.empty();
            }

            // convert repository to url
            var a = buildingRequest.getRemoteRepositories();
            for (var remoteRepository : a) {
                System.out.println(remoteRepository);
            }

            return Optional.of(ResolvedUrl.of(repository));
        } catch (Exception e) {
            LOGGER.warn("Could not fetch remote repository for artifact " + artifact, e);
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
    public ResolvedUrl getResolvedField(Artifact artifact) {
        return getResolvedFieldInternal(resolveDependency(artifact)).orElse(ResolvedUrl.of(""));
    }
}
