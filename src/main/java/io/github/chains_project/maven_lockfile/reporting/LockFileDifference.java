package io.github.chains_project.maven_lockfile.reporting;

import com.google.common.collect.Sets;
import io.github.chains_project.maven_lockfile.data.LockFile;
import io.github.chains_project.maven_lockfile.data.MavenExtension;
import io.github.chains_project.maven_lockfile.data.MavenPlugin;
import io.github.chains_project.maven_lockfile.graph.DependencyNode;
import java.util.HashSet;
import java.util.Set;

public class LockFileDifference {

    private final Set<DependencyNode> missingDependenciesInProject;
    private final Set<DependencyNode> missingDependenciesInFile;

    private final Set<MavenPlugin> missingPluginsInProject;
    private final Set<MavenPlugin> missingPluginsInFile;

    private final Set<MavenExtension> missingExtensionsInProject;
    private final Set<MavenExtension> missingExtensionsInFile;

    private LockFileDifference(
            Set<DependencyNode> missingDependenciesInProject,
            Set<DependencyNode> missingDependenciesInFile,
            Set<MavenPlugin> missingPluginsInProject,
            Set<MavenPlugin> missingPluginsInFile,
            Set<MavenExtension> missingExtensionsInProject,
            Set<MavenExtension> missingExtensionsInFile) {
        this.missingDependenciesInProject = missingDependenciesInProject;
        this.missingDependenciesInFile = missingDependenciesInFile;
        this.missingPluginsInProject = missingPluginsInProject;
        this.missingPluginsInFile = missingPluginsInFile;
        this.missingExtensionsInProject = missingExtensionsInProject;
        this.missingExtensionsInFile = missingExtensionsInFile;
    }

    public static LockFileDifference diff(LockFile lockFileFromFile, LockFile lockFileFromProject) {
        Set<DependencyNode> dependenciesFromFile = new HashSet<>(lockFileFromFile.getDependencies());
        Set<DependencyNode> dependenciesFromProject = new HashSet<>(lockFileFromProject.getDependencies());
        Set<DependencyNode> missingDependenciesInProject =
                Sets.difference(dependenciesFromFile, dependenciesFromProject);
        Set<DependencyNode> missingDependenciesInFile = Sets.difference(dependenciesFromProject, dependenciesFromFile);
        Set<MavenPlugin> pluginsFromFile = new HashSet<>(lockFileFromFile.getMavenPlugins());
        Set<MavenPlugin> pluginsFromProject = new HashSet<>(lockFileFromProject.getMavenPlugins());
        Set<MavenPlugin> missingPluginsInProject = Sets.difference(pluginsFromFile, pluginsFromProject);
        Set<MavenPlugin> missingPluginsInFile = Sets.difference(pluginsFromProject, pluginsFromFile);
        Set<MavenExtension> extensionsFromFile = new HashSet<>(lockFileFromFile.getMavenExtensions());
        Set<MavenExtension> extensionsFromProject = new HashSet<>(lockFileFromProject.getMavenExtensions());
        Set<MavenExtension> missingExtensionsInProject = Sets.difference(extensionsFromFile, extensionsFromProject);
        Set<MavenExtension> missingExtensionsInFile = Sets.difference(extensionsFromProject, extensionsFromFile);

        return new LockFileDifference(
                missingDependenciesInProject,
                missingDependenciesInFile,
                missingPluginsInProject,
                missingPluginsInFile,
                missingExtensionsInProject,
                missingExtensionsInFile);
    }

    /**
     * @return the missingDependenciesInFile
     */
    public Set<DependencyNode> getMissingDependenciesInFile() {
        return new HashSet<>(missingDependenciesInFile);
    }

    /**
     * @return the missingDependenciesInProject
     */
    public Set<DependencyNode> getMissingDependenciesInProject() {
        return new HashSet<>(missingDependenciesInProject);
    }
    /**
     * @return the missingPluginsInFile
     */
    public Set<MavenPlugin> getMissingPluginsInFile() {
        return new HashSet<>(missingPluginsInFile);
    }
    /**
     * @return the missingPluginsInProject
     */
    public Set<MavenPlugin> getMissingPluginsInProject() {
        return new HashSet<>(missingPluginsInProject);
    }
    /**
     * @return the missingExtensionsInFile
     */
    public Set<MavenExtension> getMissingExtensionsInFile() {
        return new HashSet<>(missingExtensionsInFile);
    }
    /**
     * @return the missingExtensionsInProject
     */
    public Set<MavenExtension> getMissingExtensionsInProject() {
        return new HashSet<>(missingExtensionsInProject);
    }
}
