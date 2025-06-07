package io.github.chains_project.maven_lockfile;

import io.github.chains_project.maven_lockfile.data.LockFile;
import io.github.chains_project.maven_lockfile.graph.DependencyNode;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.DependencyManagement;
import org.apache.maven.model.Model;
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
        File pomFile = project.getFile();
        File pomLockFile = new File(project.getBasedir(), pomLockfileOutput);
        try {
            LockFile lockFile = LockFile.readLockFile(LockFileFacade.getLockFilePath(project, lockfileName));
            List<Dependency> filteredDependencies = getNearestVersionDependency(lockFile);
            Model pomModel = readPomFile(pomFile);
            updateDependencies(pomModel, filteredDependencies);
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
}
