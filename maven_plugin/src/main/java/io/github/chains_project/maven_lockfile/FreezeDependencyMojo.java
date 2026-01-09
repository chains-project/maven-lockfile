package io.github.chains_project.maven_lockfile;

import io.github.chains_project.maven_lockfile.data.LockFile;
import io.github.chains_project.maven_lockfile.data.MavenPlugin;
import io.github.chains_project.maven_lockfile.graph.DependencyNode;
import io.github.chains_project.maven_lockfile.reporting.PluginLogManager;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Build;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.DependencyManagement;
import org.apache.maven.model.Model;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.apache.maven.model.io.xpp3.MavenXpp3Writer;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;

/**
 * Freeze the dependencies of a project. Every dependency will be locked to a specific version.
 * For this, every version of direct dependencies in the pom file will be replaced with the version from the lock file.
 * This also adds all transitive dependencies from the lock file inside the dependencyManagement section.
 * <br>
 * <b> If there exists no lock file, this fails.</b>
 */
@Mojo(
        name = "freeze",
        defaultPhase = LifecyclePhase.GENERATE_RESOURCES,
        requiresDependencyResolution = ResolutionScope.COMPILE,
        requiresOnline = true)
public class FreezeDependencyMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    @Parameter(defaultValue = "${session}", readonly = true, required = true)
    private MavenSession session;

    @Parameter(defaultValue = "pom.lockfile.xml", property = "pomLockfileOutput")
    private String pomLockfileOutput;

    @Parameter(defaultValue = "lockfile.json", property = "lockfileName")
    private String lockfileName;

    @Parameter(defaultValue = "true", property = "exactVersionStrings")
    private String exactVersionStrings;

    /**
     * Freezes the dependencies of the project. Every dependency will be locked to a specific version.
     *
     * @throws MojoExecutionException if the lock file is invalid or could not be read.
     */
    public void execute() throws MojoExecutionException {
        PluginLogManager.setLog(getLog());
        File pomFile = project.getFile();
        File pomLockFile = new File(project.getBasedir(), pomLockfileOutput);
        try {
            LockFile lockFile = LockFile.readLockFile(LockFileFacade.getLockFilePath(project, lockfileName));
            List<Dependency> filteredDependencies = getNearestVersionDependency(lockFile);
            Model pomModel = readPomFile(pomFile);
            updateDependencies(pomModel, filteredDependencies);
            updatePlugins(pomModel, lockFile.getMavenPlugins());
            writePomLockFile(pomModel, pomLockFile);
        } catch (IOException | XmlPullParserException e) {
            throw new MojoExecutionException("Could not freeze versions", e);
        }
    }

    private Model readPomFile(File pomFile) throws IOException, XmlPullParserException {
        try (FileReader fileReader = new FileReader(pomFile)) {
            MavenXpp3Reader reader = new MavenXpp3Reader();
            return reader.read(fileReader);
        }
    }

    private void updateDependencies(Model pomModel, List<Dependency> filteredDependencies) {
        List<Dependency> pomDependencies = pomModel.getDependencies();
        DependencyManagement dependencyManagement = pomModel.getDependencyManagement();
        if (dependencyManagement == null) {
            dependencyManagement = new DependencyManagement();
        }
        List<Dependency> dependencyManagementDependencies = dependencyManagement.getDependencies();
        Map<String, Dependency> pomDependencyMap = createDependencyMap(pomDependencies);
        Map<String, Dependency> dependencyManagementMap = createDependencyMap(dependencyManagementDependencies);
        for (Dependency dep : filteredDependencies) {
            String key = dep.getGroupId() + ":" + dep.getArtifactId();
            Dependency pomDependency = pomDependencyMap.get(key);
            Dependency dependencyManagementDep = dependencyManagementMap.get(key);
            if (pomDependency != null) {
                pomDependency.setVersion(dep.getVersion());
                pomDependency.setScope(dep.getScope());
            } else if (dependencyManagementDep != null) {
                dependencyManagementDep.setVersion(dep.getVersion());
            } else {
                dependencyManagementDependencies.add(dep);
            }
        }
        pomModel.setDependencies(pomDependencies);
        dependencyManagement.setDependencies(dependencyManagementDependencies);
        pomModel.setDependencyManagement(dependencyManagement);
    }

    private Map<String, Dependency> createDependencyMap(List<Dependency> dependencies) {
        Map<String, Dependency> dependencyMap = new HashMap<>();
        for (Dependency dep : dependencies) {
            String key = dep.getGroupId() + ":" + dep.getArtifactId();
            dependencyMap.put(key, dep);
        }
        return dependencyMap;
    }

    private void writePomLockFile(Model pomModel, File pomLockFile) throws IOException {
        try (FileWriter fileWriter = new FileWriter(pomLockFile)) {
            MavenXpp3Writer writer = new MavenXpp3Writer();
            writer.write(fileWriter, pomModel);
        }
    }

    private List<Dependency> getNearestVersionDependency(LockFile lockFileFromFile) {
        var deps = lockFileFromFile.getDependencies();
        Map<String, Dependency> nearestVersionMap = new HashMap<>();
        Queue<DependencyNode> depQueue = new ArrayDeque<>(deps);
        while (!depQueue.isEmpty()) {
            var depNode = depQueue.poll();
            Dependency dep = toMavenDependency(depNode);
            String key = dep.getGroupId() + ":" + dep.getArtifactId();
            if (depNode.isIncluded()) {
                nearestVersionMap.put(key, dep);
            }
            depQueue.addAll(depNode.getChildren());
        }
        return new ArrayList<>(nearestVersionMap.values());
    }

    /**
     * Converts a DependencyNode to a Maven Model Dependency.
     *
     * @param dep the DependencyNode to convert
     * @return the converted Dependency
     */
    private Dependency toMavenDependency(DependencyNode dep) {
        Dependency mavenDep = new Dependency();
        mavenDep.setGroupId(dep.getGroupId().getValue());
        mavenDep.setArtifactId(dep.getArtifactId().getValue());
        String version = dep.getVersion().getValue();
        if (exactVersionStrings.equals("true")) {
            version = convertSoftToExactVersionString(version);
        }
        mavenDep.setVersion(version);
        if (dep.getClassifier() != null) {
            mavenDep.setClassifier(dep.getClassifier().getValue());
        }
        if (dep.getType() != null) {
            mavenDep.setType(dep.getType().getValue());
        }
        mavenDep.setScope(dep.getScope().getValue());
        return mavenDep;
    }

    /**
     * Transform a soft version requirement into an exact one by wrapping it in a range which only includes .
     */
    private String convertSoftToExactVersionString(String version) {
        if (version.startsWith("[") || version.startsWith("(")) {
            getLog().warn("Version is already a range, '" + version + "'. Cannot reliably make exact for freeze pom.");
            return version;
        }
        return "[" + version + "]";
    }

    /**
     * Updates the plugins in the POM model with plugin dependencies from the lock file.
     *
     * @param pomModel the POM model to update
     * @param mavenPlugins the set of Maven plugins from the lock file
     */
    private void updatePlugins(Model pomModel, Set<MavenPlugin> mavenPlugins) {
        if (mavenPlugins == null || mavenPlugins.isEmpty()) {
            return;
        }

        Build build = pomModel.getBuild();
        if (build == null) {
            build = new Build();
            pomModel.setBuild(build);
        }

        List<Plugin> plugins = build.getPlugins();
        if (plugins == null) {
            plugins = new ArrayList<>();
            build.setPlugins(plugins);
        }

        Map<String, Plugin> existingPluginsMap = new HashMap<>();
        for (Plugin plugin : plugins) {
            String key = plugin.getGroupId() + ":" + plugin.getArtifactId();
            existingPluginsMap.put(key, plugin);
        }

        // Process each plugin from the lock file
        for (MavenPlugin mavenPlugin : mavenPlugins) {
            String key = mavenPlugin.getGroupId().getValue() + ":" + mavenPlugin.getArtifactId().getValue();
            Plugin plugin = existingPluginsMap.get(key);

            if (plugin == null) {
                // Plugin doesn't exist in the POM, create it
                plugin = new Plugin();
                plugin.setGroupId(mavenPlugin.getGroupId().getValue());
                plugin.setArtifactId(mavenPlugin.getArtifactId().getValue());
                plugins.add(plugin);
            }

            // Update plugin version
            String version = mavenPlugin.getVersion().getValue();
            if (exactVersionStrings.equals("true")) {
                version = convertSoftToExactVersionString(version);
            }
            plugin.setVersion(version);

            // Add plugin dependencies if they exist
            Set<DependencyNode> pluginDependencies = mavenPlugin.getDependencies();
            if (pluginDependencies != null && !pluginDependencies.isEmpty()) {
                List<Dependency> dependencies = new ArrayList<>();
                Queue<DependencyNode> depQueue = new ArrayDeque<>(pluginDependencies);

                while (!depQueue.isEmpty()) {
                    DependencyNode depNode = depQueue.poll();
                    if (depNode.isIncluded()) {
                        dependencies.add(toMavenDependency(depNode));
                    }
                    depQueue.addAll(depNode.getChildren());
                }

                plugin.setDependencies(dependencies);
            }
        }
    }
}
