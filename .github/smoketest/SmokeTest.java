///usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS org.buildobjects:jproc:2.8.2 com.fasterxml.jackson.core:jackson-databind:2.14.2 org.eclipse.jgit:org.eclipse.jgit:6.5.0.202303070854-r org.junit.jupiter:junit-jupiter-api:5.7.2 org.junit.jupiter:junit-jupiter-engine:5.7.2
//JAVA 17+
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;
import org.buildobjects.process.ProcBuilder;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import static java.lang.System.*;
import static org.junit.jupiter.api.Assertions.*;

public class SmokeTest {
    private static String pluginCommand = "io.github.chains-project:maven-lockfile:%s:generate";
    private static String[] mavenGraph = new String[] { "com.github.ferstl:depgraph-maven-plugin:4.0.2:graph", "-DgraphFormat=json" };
    private static ObjectMapper mapper = new ObjectMapper();
    private static List<CiProject> projects = List.of(
            new CiProject("https://github.com/INRIA/spoon","8e1d4272f58189587279033e3b3ca80c5f78f8ff"),
            new CiProject("https://github.com/stanfordnlp/CoreNLP","139893242878ecacde79b2ba1d0102b855526610"),
            new CiProject("https://github.com/javaparser/javaparser","7e761814c661d67293fb9941287f544ce7fdd14c"),
            new CiProject("https://github.com/checkstyle/checkstyle","dffecfe45722704f54b1727f969445e2aacd4cb3")
    );

    public static void main(String... args) throws Exception {
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        Path mavenPath = Path.of("./maven_plugin/./mvnw");
        String pluginVersion = getProjectVersion(mavenPath);
        new ProcBuilder(mavenPath.toString(), "clean", "install", "-DskipTests", "-q")
            .withNoTimeout()
            .run();
        out.println("your version is: " + getProjectVersion(mavenPath));
        String command = String.format(pluginCommand, pluginVersion);

        File smoketestsDir = new File("smoketests");
        if (!smoketestsDir.exists()) {
            smoketestsDir.mkdirs();
        }

        for(CiProject projectUrl : projects) {
            out.println("Testing project " + projectUrl);
            String projectName = projectUrl.projectUrl.substring(projectUrl.projectUrl.lastIndexOf('/') + 1);
            try (Git result = Git.cloneRepository().setURI(projectUrl.projectUrl)
                    .setDirectory(new File("smoketests/" + projectName))
                    .call()) {
                result.checkout().setName(projectUrl.commitHash).call();
                File workingDir = result.getRepository().getDirectory().getParentFile();
                System.out.println("Cloned " + projectUrl.projectUrl + " to " + workingDir);
                new ProcBuilder("../../maven_plugin/mvnw", command)
                    .withWorkingDirectory(workingDir)
                    .withNoTimeout()
                    .run();
                LockFile lockFile = mapper.readValue(new File(workingDir, "lockfile.json"), LockFile.class);
                new ProcBuilder("../../maven_plugin/mvnw", mavenGraph)
                    .withWorkingDirectory(workingDir)
                    .withNoTimeout()
                    .run();

                JsonFile jsonFile = mapper.readValue(new File(workingDir, "/target/dependency-graph.json"), JsonFile.class);
                List<Node> dependencies = jsonFile.artifacts;
                // the first is the root
                dependencies.remove(0);
                List<DependencyLockFile> errors = new ArrayList<>();
                Queue<DependencyLockFile> workingQueue = new ArrayDeque<>(lockFile.dependencies());
                List<DependencyLockFile> completeList = new ArrayList<>();
                while(!workingQueue.isEmpty()) {
                    DependencyLockFile current = workingQueue.poll();
                    completeList.add(current);
                    workingQueue.addAll(current.children());
                }
                for (Node dependency : dependencies) {
                    DependencyLockFile lockFileDependency = findDependency(
                            completeList, dependency);
                    if(lockFileDependency == null) {
                        errors.add(new DependencyLockFile(dependency.groupId(), dependency.artifactId(), dependency.version(), "", "", "", "", new ArrayList<>()));
                    }
                }
                if (!errors.isEmpty()) {
                    fail("The following dependencies are not in the lockfile: " + errors.stream()
                        .map(d -> d.groupId() + ":" + d.artifactId() + ":" + d.version)
                        .collect(Collectors.joining("\n")));
                }
                if(errors.isEmpty()) {
                    out.println("All dependencies are in the lockfile");
                }
            }
        }
    }

    private static DependencyLockFile findDependency(List<DependencyLockFile> dependencies,
            Node dependency) {
        for (DependencyLockFile dependencyLockFile : dependencies) {
            if (dependencyLockFile.groupId().equals(dependency.groupId()) && dependencyLockFile.artifactId().equals(dependency.artifactId())) {
                return dependencyLockFile;
            }
        }
        return null;
    }

    private static String getProjectVersion(Path path) {
        return new ProcBuilder(path.toAbsolutePath().toString(), new String[]{ "help:evaluate", "-Dexpression=project.version", "-q",
                "-DforceStdout"}).withNoTimeout().withWorkingDirectory(Path.of("./maven_plugin").toFile()).run().getOutputString().trim();
    }

    record Dependency(String groupId, String artifactId, String classifier, String version,
                    String scope, int depth, String submodule) {
    };

    public record JsonFile(String graphName, List<Node> artifacts, List<Edge> dependencies) {
    };

    record Node(String id, int numericId, String groupId, String artifactId, String version,
                    boolean optional, List<String> classifiers, List<String> scopes,
                    List<String> types) {
    }

    record Edge(String from, String to, int numericFrom, int numericTo, String resolution) {
    };

    public record LockFile(String artifactID, String groupID, String version, String lockFileVersion, 
                    List<DependencyLockFile> dependencies) {
    };

    public record DependencyLockFile(String groupId, String artifactId, String version, 
                    String checksumAlgorithm, String checksum, String id,String parent, 
                    List<DependencyLockFile> children) {
    };

    private record CiProject(String projectUrl, String commitHash) {
    };
}
