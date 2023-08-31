package io.github.chains_project.maven_lockfile.reporting;

import com.google.common.collect.Sets;
import io.github.chains_project.maven_lockfile.data.LockFile;
import io.github.chains_project.maven_lockfile.data.MavenPlugin;
import io.github.chains_project.maven_lockfile.graph.DependencyNode;
import java.util.HashSet;
import java.util.Set;

public class LockFileDifference {

    private final Set<DependencyNode> missingDependenciesInProject;
    private final Set<DependencyNode> missingDependenciesInFile;

    private final Set<MavenPlugin> missingPluginsInProject;
    private final Set<MavenPlugin> missingPluginsInFile;

    private LockFileDifference(
            Set<DependencyNode> missingDependenciesInProject,
            Set<DependencyNode> missingDependenciesInFile,
            Set<MavenPlugin> missingPluginsInProject,
            Set<MavenPlugin> missingPluginsInFile) {
        this.missingDependenciesInProject = missingDependenciesInProject;
        this.missingDependenciesInFile = missingDependenciesInFile;
        this.missingPluginsInProject = missingPluginsInProject;
        this.missingPluginsInFile = missingPluginsInFile;
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

        return new LockFileDifference(
                missingDependenciesInProject, missingDependenciesInFile, missingPluginsInProject, missingPluginsInFile);
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
}
