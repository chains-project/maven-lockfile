package io.github.chains_project.maven_lockfile.resolvers;

import io.github.chains_project.maven_lockfile.checksum.AbstractChecksumCalculator;
import io.github.chains_project.maven_lockfile.data.ArtifactId;
import io.github.chains_project.maven_lockfile.data.GroupId;
import io.github.chains_project.maven_lockfile.data.Pom;
import io.github.chains_project.maven_lockfile.data.VersionNumber;
import io.github.chains_project.maven_lockfile.graph.DependencyGraph;
import io.github.chains_project.maven_lockfile.reporting.PluginLogManager;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Dependency;
import org.apache.maven.project.MavenProject;

public class BomResolver {
    private final MavenSession session;

    @SuppressWarnings("deprecation")
    private final List<ArtifactRepository> repositories;

    private final AbstractChecksumCalculator checksumCalculator;

    @SuppressWarnings("deprecation")
    public BomResolver(
            MavenSession session,
            List<ArtifactRepository> repositories,
            AbstractChecksumCalculator checksumCalculator) {
        this.session = session;
        this.repositories = repositories;
        this.checksumCalculator = checksumCalculator;
    }

    /**
     * Resolve all the BOM POMs for a project.
     *
     * @param project The project to resolve the BOM POMs from.
     * @return A set containing all the resolved BOM POMs.
     */
    public Set<Pom> resolveForProject(MavenProject project) {
        var model = project.getOriginalModel();
        var dependencyManagement = model.getDependencyManagement();
        var projectBuilder = new ProjectBuilder(session, repositories);
        var boms = new TreeSet<Pom>();

        if (dependencyManagement == null
                || dependencyManagement.getDependencies().isEmpty()) {
            return Collections.emptySet();
        }

        for (Dependency dependency : dependencyManagement.getDependencies()) {
            // A BOM POM always has type=pom and scope=import
            if ("pom".equals(dependency.getType()) && "import".equals(dependency.getScope())) {
                var resolvedVersion = resolveVersionFromPlaceholder(dependency.getVersion(), project);
                var bomProjectOptional = projectBuilder.buildFromGav(
                        dependency.getGroupId(), dependency.getArtifactId(), resolvedVersion);

                if (bomProjectOptional.isEmpty()) {
                    PluginLogManager.getLog().warn(String.format("Could not resolve BOM for %s", dependency));
                    continue;
                }

                var bomProject = bomProjectOptional.get();
                var bomBoms = resolveForProject(bomProject);
                var bomTree = resolveBomParents(bomProject);
                if (!bomBoms.isEmpty() && bomTree != null) {
                    bomTree.setBoms(bomBoms);
                }
                boms.add(bomTree);
            }
        }

        return boms;
    }

    /**
     * Resolve the BOM POMs for all the dependencies in a DependencyGraph.
     *
     * @param graph The dependency graph
     */
    @SuppressWarnings("deprecation")
    public void resolveBomsForDependencies(DependencyGraph graph) {
        ProjectBuilder projectBuilder = new ProjectBuilder(session, repositories);
        BomResolver bomResolver = new BomResolver(session, repositories, checksumCalculator);

        graph.getDependencySet().forEach(node -> {
            var projectOptional = projectBuilder.buildFromGav(
                    node.getGroupId().getValue(),
                    node.getArtifactId().getValue(),
                    node.getVersion().getValue());

            if (projectOptional.isEmpty()) {
                PluginLogManager.getLog().warn(String.format("Skipping BOM resolution for %s", node));
                return;
            }

            Set<Pom> boms = bomResolver.resolveForProject(projectOptional.get());

            if (!boms.isEmpty()) {
                // TODO: Avoid the mutation of the graph within this function
                node.setBoms(boms);
            }
        });
    }

    private String resolveVersionFromPlaceholder(String version, MavenProject project) {
        if (version != null && version.startsWith("${") && version.endsWith("}")) {
            String propertyName = version.substring(2, version.length() - 1);

            // Check project properties (interpolated model has all properties resolved)
            var resolvedVersion = project.getModel().getProperties().getProperty(propertyName);

            if (resolvedVersion != null) {
                return resolvedVersion;
            }
        }

        return version;
    }

    private Pom resolveBomParents(MavenProject start) {
        List<MavenProject> projects = new ArrayList<>();
        Pom current = null;

        if (!start.hasParent()) {
            return mavenProjectToBom(start, checksumCalculator, null);
        }

        while (start.hasParent()) {
            projects.add(start);
            start = start.getParent();
        }
        projects.add(start);

        Collections.reverse(projects);

        for (MavenProject project : projects) {
            if (current == null) {
                current = mavenProjectToBom(project, checksumCalculator, null);
            } else {
                var bom = mavenProjectToBom(project, checksumCalculator, current);
                current = bom;
            }
            var bomBoms = resolveForProject(project);
            if (!bomBoms.isEmpty()) {
                current.setBoms(bomBoms);
            }
        }

        return current;
    }

    private static Pom mavenProjectToBom(
            MavenProject project, AbstractChecksumCalculator checksumCalculator, Pom parent) {
        var dependency = project.getModel();

        var repoInfo = checksumCalculator.getArtifactResolvedField(project.getArtifact());
        var checksum = checksumCalculator.calculateArtifactChecksum(project.getArtifact());
        var checksumAlgorithm = checksumCalculator.getChecksumAlgorithm();

        return new Pom(
                GroupId.of(dependency.getGroupId()),
                ArtifactId.of(dependency.getArtifactId()),
                VersionNumber.of(dependency.getVersion()),
                null,
                repoInfo.getResolvedUrl(),
                repoInfo.getRepositoryId(),
                checksumAlgorithm,
                checksum,
                parent);
    }
}
