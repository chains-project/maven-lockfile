///usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS org.buildobjects:jproc:2.8.2 com.fasterxml.jackson.core:jackson-databind:2.14.2 org.eclipse.jgit:org.eclipse.jgit:6.5.0.202303070854-r org.junit.jupiter:junit-jupiter-api:5.7.2 org.junit.jupiter:junit-jupiter-engine:5.7.2
//JAVA 17+
import static java.lang.System.*;
import org.buildobjects.process.ProcBuilder;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import java.util.stream.Collectors;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

public class SmokeTest {
    private static String pluginCommand =
            "io.github.chains-project:integrity-maven-plugin:%s:generate";
    private static String[] mavenGraph = new String[] { "com.github.ferstl:depgraph-maven-plugin:4.0.2:graph", "-DgraphFormat=json" };
        private static ObjectMapper mapper = new ObjectMapper();
        private static List<String> projects = List.of("https://github.com/INRIA/spoon");

        public static void main(String... args) throws Exception {
            Files.list(Path.of(".")).forEach(out::println);
        String pluginVersion = getProjectVersion();
        new ProcBuilder("./mvnw", "clean", "install", "-DskipTests").withNoTimeout().run();
        out.println("your version is:" + getProjectVersion());
        String command = String.format(pluginCommand, pluginVersion);
        for(String projectUrl : projects) {
            out.println("Testing project " + projectUrl);

        try (Git result = Git.cloneRepository().setURI(projectUrl)
                         .call()) {
                File workingDir = result.getRepository().getDirectory().getParentFile();
                new ProcBuilder("../mvnw", command)
                .withWorkingDirectory(workingDir)
                .withNoTimeout()
                .run();
            LockFile lockFile = mapper.readValue(new File(workingDir, "lockfile.json"), LockFile.class);
                new ProcBuilder("../mvnw", mavenGraph)
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

    private static String getProjectVersion() {
        return ProcBuilder.run("../mvnw", "help:evaluate", "-Dexpression=project.version", "-q",
                "-DforceStdout");
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

    public record LockFile(String artifactID, String groupID, String version, String lockFileVersion, List<DependencyLockFile> dependencies) {
    };

    public record DependencyLockFile(String groupId, String artifactId, String version, String checksumAlgorithm, String checksum,
                    String id,String parent, List<DependencyLockFile> children) {
    };
    
}
