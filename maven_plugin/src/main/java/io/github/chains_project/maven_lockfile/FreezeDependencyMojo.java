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
     *
     * @throws MojoExecutionException if the lock file is invalid or could not be read.
     */
    public void execute() throws MojoExecutionException {
        File pomFile = project.getFile();
        try {
            LockFile lockFileFromFile = LockFile.readLockFile(LockFileFacade.getLockFilePath(project));
            List<Dependency> filteredDeps = getNearestVersionDependency(lockFileFromFile);
            // read the pom with a pomfile reader:
            MavenXpp3Reader reader = new MavenXpp3Reader();
            Model pom = reader.read(new FileReader(pomFile));
            List<Dependency> pomDeps = pom.getDependencies();
            Map<String, Dependency> pomDepMap = new HashMap<>();
            for (var pomDep : pomDeps) {
                String key = pomDep.getGroupId() + ":" + pomDep.getArtifactId();
                pomDepMap.put(key, pomDep);
            }

            for (Dependency dep : filteredDeps) {
                String key = dep.getGroupId() + ":" + dep.getArtifactId();
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
        } catch (IOException | XmlPullParserException e) {
            throw new MojoExecutionException("Could not freeze versions", e);
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
        mavenDep.setVersion(dep.getVersion().getValue());
        mavenDep.setScope(dep.getScope().getValue());
        return mavenDep;
    }
}
