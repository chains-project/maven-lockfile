package it;

import static com.soebes.itf.extension.assertj.MavenITAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

import com.soebes.itf.jupiter.extension.MavenJupiterExtension;
import com.soebes.itf.jupiter.extension.MavenTest;
import com.soebes.itf.jupiter.maven.MavenExecutionResult;
import io.github.chains_project.maven_lockfile.data.LockFile;
import io.github.chains_project.maven_lockfile.graph.DependencyNode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.apache.maven.plugin.testing.AbstractMojoTestCase;

@MavenJupiterExtension
public class IntegrationTestsIT extends AbstractMojoTestCase {
    @MavenTest
    public void simpleProject(MavenExecutionResult result) throws Exception {
        // contract: an empty project should generate an empty lock file
        assertThat(result).isSuccessful();
        Path lockFilePath = getLockFile(result);
        assertThat(lockFilePath).exists();
        var lockFile = LockFile.readLockFile(lockFilePath);
        assertThat(lockFile.getDependencies()).isEmpty();
    }

    @MavenTest
    public void singleDependency(MavenExecutionResult result) throws Exception {
        // contract: an empty project should generate an empty lock file
        assertThat(result).isSuccessful();
        Path lockFilePath = getLockFile(result);
        assertThat(lockFilePath).exists();
        var lockFile = LockFile.readLockFile(lockFilePath);
        assertThat(lockFile.getDependencies()).hasSize(1);
        var junitDep = lockFile.getDependencies().get(0);
        assertThat(junitDep.getArtifactId()).extracting(v -> v.getValue()).isEqualTo("spoon-core");
        assertThat(junitDep.getGroupId()).extracting(v -> v.getValue()).isEqualTo("fr.inria.gforge.spoon");
        assertThat(junitDep.getVersion()).extracting(v -> v.getValue()).isEqualTo("10.3.0");
        assertThat(junitDep.getChecksum())
                .isEqualTo("37a43de039cf9a6701777106e3c5921e7131e5417fa707709abf791d3d8d9174");
    }

    @MavenTest
    public void singleDependencyCheckCorrect(MavenExecutionResult result) throws Exception {
        // contract: an empty project should generate an empty lock file
        assertThat(result).isSuccessful();
        Path lockFilePath = getLockFile(result);
        assertThat(lockFilePath).exists();
        var lockFile = LockFile.readLockFile(lockFilePath);
        assertThat(lockFile.getDependencies()).hasSize(1);
        var junitDep = lockFile.getDependencies().get(0);
        assertThat(junitDep.getArtifactId()).extracting(v -> v.getValue()).isEqualTo("junit-jupiter-api");
        assertThat(junitDep.getGroupId()).extracting(v -> v.getValue()).isEqualTo("org.junit.jupiter");
        assertThat(junitDep.getVersion()).extracting(v -> v.getValue()).isEqualTo("5.9.2");
        assertThat(junitDep.getChecksum())
                .isEqualTo("f767a170f97127b0ad3582bf3358eabbbbe981d9f96411853e629d9276926fd5");
    }

    @MavenTest
    public void singleDependencyCheckMustFail(MavenExecutionResult result) throws Exception {
        // contract: a changed dependency should fail the build.
        // we changed the group id of "groupId": "org.opentest4j", to "groupId": "org.opentest4j5",
        assertThat(result).isFailure();
    }

    @MavenTest
    public void pluginProject(MavenExecutionResult result) throws Exception {
        assertThat(result).isSuccessful();
        Path lockFilePath = getLockFile(result);
        assertThat(lockFilePath).exists();
        var lockFile = LockFile.readLockFile(lockFilePath);
        assertThat(lockFile.getMavenPlugins()).isNotEmpty();
    }

    @MavenTest
    public void freezeJunit(MavenExecutionResult result) throws Exception {
        assertThat(result).isSuccessful();
        var path = Files.find(
                        result.getMavenProjectResult().getTargetBaseDirectory(),
                        Integer.MAX_VALUE,
                        (u, v) -> u.getFileName().toString().contains("pom.xml"))
                .findAny()
                .orElseThrow();
        var pom = Files.readString(path);
        assertThat(pom).contains("<groupId>org.junit.jupiter</groupId>");
        assertThat(pom).contains("<artifactId>junit-jupiter-api</artifactId>");
        assertThat(pom).contains("<version>5.9.2</version>");
    }

    @MavenTest
    void reduceLog4jAffected(MavenExecutionResult result) throws Exception {
        assertThat(result).isSuccessful();
        Path lockFilePath = getLockFile(result);
        assertThat(lockFilePath).exists();
        var lockFile = LockFile.readLockFile(lockFilePath);
        assertThat(lockFile.getDependencies().stream().flatMap(v -> flattenDependencies(v).stream()))
                .anyMatch(v -> v.getArtifactId().getValue().equals("log4j-core")
                        && v.getVersion().getValue().equals("2.0"));
    }

    @MavenTest
    void reduceLog4jNotAffected(MavenExecutionResult result) throws Exception {
        assertThat(result).isSuccessful();
        Path lockFilePath = getLockFile(result);
        assertThat(lockFilePath).exists();
        var lockFile = LockFile.readLockFile(lockFilePath);
        assertThat(lockFile.getDependencies().stream().flatMap(v -> flattenDependencies(v).stream()))
                .noneMatch(v -> v.getArtifactId().getValue().equals("log4j-core")
                        && v.getVersion().getValue().equals("2.0"));
    }

    private Path getLockFile(MavenExecutionResult result) throws IOException {
        return Files.find(
                        result.getMavenProjectResult().getTargetBaseDirectory(),
                        Integer.MAX_VALUE,
                        (v, u) -> v.getFileName().toString().contains("lockfile.json"))
                .findFirst()
                .orElseThrow();
    }

    public List<DependencyNode> flattenDependencies(DependencyNode node) {
        List<DependencyNode> dependencies = new ArrayList<>();
        flattenDependencies(node, dependencies);
        return dependencies;
    }

    private void flattenDependencies(DependencyNode node, List<DependencyNode> dependencies) {
        dependencies.add(node);
        for (DependencyNode child : node.getChildren()) {
            flattenDependencies(child, dependencies);
        }
    }
}
