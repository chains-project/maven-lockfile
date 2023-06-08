package io.github.chains_project.maven_lockfile;

import io.github.chains_project.maven_lockfile.data.LockFile;
import io.github.chains_project.maven_lockfile.graph.DependencyNode;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Dependency;
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
 * For this every version in the pom file will be replaced with the version from the lock file.
 * This also adds all dependencies from the lock file that are not in the pom file.
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

    /**
     * Freezes the dependencies of the project. Every dependency will be locked to a specific version.
     * @throws MojoExecutionException if the lock file is invalid or could not be read.
     */
    public void execute() throws MojoExecutionException {
        File pomFile = project.getFile();
        try {
            LockFile lockFileFromFile = LockFile.readLockFile(LockFileFacade.getLockFilePath(project));
            List<Dependency> filteredDeps = getHighestVersionDependency(lockFileFromFile);
            // read the pom with a pomfile reader:
            MavenXpp3Reader reader = new MavenXpp3Reader();
            Model pom = reader.read(new FileReader(pomFile));
            List<Dependency> pomDeps = pom.getDependencies();
            Map<String, Dependency> pomDepMap = new HashMap<>();
            for (var pomDep : pomDeps) {
                String key = pomDep.getGroupId() + ":" + pomDep.getArtifactId() + ":" + pomDep.getScope();
                pomDepMap.put(key, pomDep);
            }

            for (Dependency dep : filteredDeps) {
                String key = dep.getGroupId() + ":" + dep.getArtifactId() + ":" + dep.getScope();
                var pomDep = pomDepMap.get(key);
                if (pomDep != null) {
                    pomDep.setVersion(dep.getVersion());
                } else {
                    pomDeps.add(dep);
                }
            }
            pom.setDependencies(pomDeps);
            // write the pom back to the pom file:
            MavenXpp3Writer writer = new MavenXpp3Writer();
            writer.write(new FileWriter(pomFile), pom);
        } catch (IOException e) {
            throw new MojoExecutionException("Could not freeze versions", e);
        } catch (XmlPullParserException e) {
            throw new MojoExecutionException("Could not freeze versions", e);
        }
    }

    private List<Dependency> getHighestVersionDependency(LockFile lockFileFromFile) {
        var deps = lockFileFromFile.getDependencies();
        List<Dependency> filteredDeps = new ArrayList<>();
        Queue<DependencyNode> depQueue = new ArrayDeque<>(deps);
        while (!depQueue.isEmpty()) {
            var dep = depQueue.poll();
            filteredDeps.add(toMavenDependency(dep));
            depQueue.addAll(dep.getChildren());
        }
        // Create a map to store the highest version of each dependency
        Map<String, Dependency> highestVersionMap = new HashMap<>();
        // Iterate over the filtered dependencies and update the highest version map
        for (Dependency dep : filteredDeps) {
            String key = dep.getGroupId() + ":" + dep.getArtifactId();
            Dependency highestVersionDep = highestVersionMap.get(key);
            if (highestVersionDep == null || dep.getVersion().compareTo(highestVersionDep.getVersion()) > 0) {
                highestVersionMap.put(key, dep);
            }
        }

        // Replace the filtered dependencies list with the values from the highest version map
        filteredDeps = new ArrayList<>(highestVersionMap.values());
        return filteredDeps;
    }
    /**
     * Converts a DependencyNode to a Maven Model Dependency.
     * @param dep  the DependencyNode to convert
     * @return  the converted Dependency
     */
    private Dependency toMavenDependency(DependencyNode dep) {
        Dependency mavenDep = new Dependency();
        mavenDep.setGroupId(dep.getGroupId().getValue());
        mavenDep.setArtifactId(dep.getArtifactId().getValue());
        mavenDep.setVersion(dep.getVersion().getValue());
        mavenDep.setScope(dep.getScope().getValue());
        return mavenDep;
    }
}
