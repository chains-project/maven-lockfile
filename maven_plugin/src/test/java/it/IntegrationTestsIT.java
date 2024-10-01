package it;

import static com.soebes.itf.extension.assertj.MavenITAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.common.collect.Ordering;
import com.soebes.itf.jupiter.extension.MavenJupiterExtension;
import com.soebes.itf.jupiter.extension.MavenTest;
import com.soebes.itf.jupiter.maven.MavenExecutionResult;
import io.github.chains_project.maven_lockfile.data.ArtifactId;
import io.github.chains_project.maven_lockfile.data.Classifier;
import io.github.chains_project.maven_lockfile.data.GroupId;
import io.github.chains_project.maven_lockfile.data.LockFile;
import io.github.chains_project.maven_lockfile.data.VersionNumber;
import io.github.chains_project.maven_lockfile.graph.DependencyNode;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.commons.io.FileUtils;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;

@MavenJupiterExtension
public class IntegrationTestsIT {
    @MavenTest
    public void simpleProject(MavenExecutionResult result) throws Exception {
        // contract: an empty project should generate an empty lock file
        assertThat(result).isSuccessful();
        Path lockFilePath = findFile(result, "lockfile.json");
        assertThat(lockFilePath).exists();
        var lockFile = LockFile.readLockFile(lockFilePath);
        assertThat(lockFile.getDependencies()).isEmpty();
    }

    @MavenTest
    public void singleDependency(MavenExecutionResult result) throws Exception {
        // contract: an empty project should generate an empty lock file
        assertThat(result).isSuccessful();
        Path lockFilePath = findFile(result, "lockfile.json");
        assertThat(lockFilePath).exists();
        var lockFile = LockFile.readLockFile(lockFilePath);
        assertThat(lockFile.getEnvironment()).isNotNull();
        assertThat(lockFile.getDependencies()).hasSize(1);
        var junitDep = lockFile.getDependencies().toArray(DependencyNode[]::new)[0];
        assertThat(junitDep.getArtifactId()).extracting(ArtifactId::getValue).isEqualTo("spoon-core");
        assertThat(junitDep.getGroupId()).extracting(GroupId::getValue).isEqualTo("fr.inria.gforge.spoon");
        assertThat(junitDep.getVersion()).extracting(VersionNumber::getValue).isEqualTo("10.3.0");
        assertThat(junitDep.getChecksum())
                .isEqualTo("37a43de039cf9a6701777106e3c5921e7131e5417fa707709abf791d3d8d9174");
    }

    @MavenTest
    public void singleDependencyCheckCorrect(MavenExecutionResult result) throws Exception {
        // contract: an empty project should generate an empty lock file
        assertThat(result).isSuccessful();
        Path lockFilePath = findFile(result, "lockfile.json");
        assertThat(lockFilePath).exists();
        var lockFile = LockFile.readLockFile(lockFilePath);
        assertThat(lockFile.getDependencies()).hasSize(1);
        var junitDep = lockFile.getDependencies().toArray(DependencyNode[]::new)[0];
        assertThat(junitDep.getArtifactId()).extracting(ArtifactId::getValue).isEqualTo("junit-jupiter-api");
        assertThat(junitDep.getGroupId()).extracting(GroupId::getValue).isEqualTo("org.junit.jupiter");
        assertThat(junitDep.getVersion()).extracting(VersionNumber::getValue).isEqualTo("5.9.2");
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
        Path lockFilePath = findFile(result, "lockfile.json");
        assertThat(lockFilePath).exists();
        var lockFile = LockFile.readLockFile(lockFilePath);
        assertThat(lockFile.getMavenPlugins()).isNotEmpty();
        assertThat(lockFile.getMavenPlugins())
                .allMatch(v -> !v.getChecksum().isBlank()
                        && v.getChecksumAlgorithm().equals(lockFile.getConfig().getChecksumAlgorithm()));
    }

    @MavenTest
    public void freezeJunit(MavenExecutionResult result) throws Exception {
        assertThat(result).isSuccessful();
        Path path = findFile(result, "pom.xml");
        var pom = Files.readString(path);
        assertThat(pom).contains("<groupId>org.junit.jupiter</groupId>");
        assertThat(pom).contains("<artifactId>junit-jupiter-api</artifactId>");
        assertThat(pom).contains("<version>5.9.2</version>");
    }

    @MavenTest
    public void freezeWithoutDepManagement(MavenExecutionResult result) throws Exception {
        checkFreeze(result);
    }

    @MavenTest
    public void freezeWithDepManagement(MavenExecutionResult result) throws Exception {
        checkFreeze(result);
    }

    private void checkFreeze(MavenExecutionResult result) throws Exception {
        assertThat(result).isSuccessful();

        Path actualPomPath = findFile(result, "pom.xml");
        Path expectedPomPath = findFile(result, "pom.original.xml");
        Path actualLockfilePomPath = findFile(result, "pom.lockfile.xml");
        Path expectedLockfilePomPath = findFile(result, "pom.lockfile.expected.xml");

        Model expectedLockfilePom = readPom(expectedLockfilePomPath);
        Model actualLockfilePom = readPom(actualLockfilePomPath);

        // ensure pom.xml is similar to the lockfile pom after applying freeze
        List<String> expectedLockfileDepKeys = getDependencyKeys(expectedLockfilePom.getDependencies());
        List<String> actualLockfileDepKeys = getDependencyKeys(actualLockfilePom.getDependencies());
        assertThat(actualLockfileDepKeys)
                .hasSameSizeAs(expectedLockfileDepKeys)
                .containsExactlyInAnyOrderElementsOf(expectedLockfileDepKeys);

        List<String> expectedLockfileDepManKeys =
                getDependencyKeys(expectedLockfilePom.getDependencyManagement().getDependencies());
        List<String> actualPomDepManKeys =
                getDependencyKeys(actualLockfilePom.getDependencyManagement().getDependencies());
        assertThat(actualPomDepManKeys)
                .hasSameSizeAs(expectedLockfileDepManKeys)
                .containsExactlyInAnyOrderElementsOf(expectedLockfileDepManKeys);

        // assert that the original pom file has not changed
        assertTrue(
                FileUtils.contentEquals(actualPomPath.toFile(), expectedPomPath.toFile()),
                "The original pom file has been changed.");
    }

    private Path findFile(MavenExecutionResult result, String fileName) throws IOException {
        return Files.find(
                        result.getMavenProjectResult().getTargetBaseDirectory(),
                        Integer.MAX_VALUE,
                        (path, attr) -> path.getFileName().toString().contains(fileName))
                .findAny()
                .orElseThrow(FileNotFoundException::new);
    }

    private Model readPom(Path pomPath) throws IOException, XmlPullParserException {
        try (Reader reader = Files.newBufferedReader(pomPath)) {
            MavenXpp3Reader pomReader = new MavenXpp3Reader();
            return pomReader.read(reader);
        }
    }

    private List<String> getDependencyKeys(List<Dependency> dependencies) {
        List<String> keys = new ArrayList<>();
        for (Dependency dependency : dependencies) {
            String key = dependency.getGroupId() + ":" + dependency.getArtifactId() + ":" + dependency.getScope() + ":"
                    + dependency.getVersion();
            keys.add(key);
        }
        return keys;
    }

    @MavenTest
    void reduceLog4jAffected(MavenExecutionResult result) throws Exception {
        assertThat(result).isSuccessful();
        Path lockFilePath = findFile(result, "lockfile.json");
        assertThat(lockFilePath).exists();
        var lockFile = LockFile.readLockFile(lockFilePath);
        assertThat(lockFile.getDependencies().stream().flatMap(v -> flattenDependencies(v).stream()))
                .anyMatch(v -> v.getArtifactId().getValue().equals("log4j-core")
                        && v.getVersion().getValue().equals("2.0"));
    }

    @MavenTest
    void reduceLog4jNotAffected(MavenExecutionResult result) throws Exception {
        assertThat(result).isSuccessful();
        Path lockFilePath = findFile(result, "lockfile.json");
        assertThat(lockFilePath).exists();
        var lockFile = LockFile.readLockFile(lockFilePath);
        assertThat(lockFile.getDependencies().stream().flatMap(v -> flattenDependencies(v).stream()))
                .noneMatch(v -> v.getArtifactId().getValue().equals("log4j-core")
                        && v.getVersion().getValue().equals("2.0"));
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

    @MavenTest
    void classifierDependency(MavenExecutionResult result) throws Exception {
        classifier(result);
    }

    @MavenTest
    void classifierDependencyCheckCorrect(MavenExecutionResult result) throws Exception {
        classifier(result);
    }

    private void classifier(MavenExecutionResult result) throws Exception {
        // contract: an empty project should generate an empty lock file
        assertThat(result).isSuccessful();
        Path lockFilePath = findFile(result, "lockfile.json");
        assertThat(lockFilePath).exists();
        var lockFile = LockFile.readLockFile(lockFilePath);
        assertThat(lockFile.getDependencies()).hasSize(3);

        var junitSourceDep = lockFile.getDependencies().toArray(DependencyNode[]::new)[0];
        assertThat(junitSourceDep.getArtifactId())
                .extracting(ArtifactId::getValue)
                .isEqualTo("junit-jupiter-api");
        assertThat(junitSourceDep.getGroupId()).extracting(GroupId::getValue).isEqualTo("org.junit.jupiter");
        assertThat(junitSourceDep.getVersion())
                .extracting(VersionNumber::getValue)
                .isEqualTo("5.9.2");
        assertThat(junitSourceDep.getClassifier())
                .extracting(Classifier::getValue)
                .isEqualTo("sources");
        assertThat(junitSourceDep.getChecksum())
                .isEqualTo("2b04279c000da27679100d5854d3045a09c2a9a4cda942777f0b0519bb9f295d");

        var junitJavaDocsDep = lockFile.getDependencies().toArray(DependencyNode[]::new)[1];
        assertThat(junitJavaDocsDep.getArtifactId())
                .extracting(ArtifactId::getValue)
                .isEqualTo("junit-jupiter-api");
        assertThat(junitJavaDocsDep.getGroupId()).extracting(GroupId::getValue).isEqualTo("org.junit.jupiter");
        assertThat(junitJavaDocsDep.getVersion())
                .extracting(VersionNumber::getValue)
                .isEqualTo("5.9.2");
        assertThat(junitJavaDocsDep.getClassifier())
                .extracting(Classifier::getValue)
                .isEqualTo("javadoc");
        assertThat(junitJavaDocsDep.getChecksum())
                .isEqualTo("789224a3a7bff190858307399f64ee7d7e4ab810c7eab12ee107e27765acd8d9");

        var junitDep = lockFile.getDependencies().toArray(DependencyNode[]::new)[2];
        assertThat(junitDep.getArtifactId()).extracting(ArtifactId::getValue).isEqualTo("junit-jupiter-api");
        assertThat(junitDep.getGroupId()).extracting(GroupId::getValue).isEqualTo("org.junit.jupiter");
        assertThat(junitDep.getVersion()).extracting(VersionNumber::getValue).isEqualTo("5.9.2");
        assertThat(junitDep.getClassifier()).isNull();
        assertThat(junitDep.getChecksum())
                .isEqualTo("f767a170f97127b0ad3582bf3358eabbbbe981d9f96411853e629d9276926fd5");
    }

    @MavenTest
    public void classifierDependencyCheckMustFail(MavenExecutionResult result) throws Exception {
        // contract: a changed dependency should fail the build.
        // we changed the classifier id of "classifier": "sources", to "classifier": "42",
        assertThat(result).isFailure();
    }

    @MavenTest
    public void singleDependencyCheckMustWarn(MavenExecutionResult result) throws Exception {
        // contract: a changed dependency should generate a warning on the build.
        // if the allowValidationFailure parameter is true
        // we changed the group id of "groupId": "org.opentest4j", to "groupId": "org.opentest4j5",
        assertThat(result).isSuccessful();
    }

    @MavenTest
    public void withEnvironment(MavenExecutionResult result) throws Exception {
        // contract: a null environment should be returned if include environment is false
        assertThat(result).isSuccessful();
        Path lockFilePath = findFile(result, "lockfile.json");
        assertThat(lockFilePath).exists();
        var lockFile = LockFile.readLockFile(lockFilePath);
        assertThat(lockFile.getConfig().isIncludeEnvironment()).isTrue();
        assertThat(lockFile.getEnvironment()).isNotNull();
    }

    @MavenTest
    public void withoutEnvironment(MavenExecutionResult result) throws Exception {
        // contract: a not null environment should be returned if include environment is true
        assertThat(result).isSuccessful();
        Path lockFilePath = findFile(result, "lockfile.json");
        assertThat(lockFilePath).exists();
        var lockFile = LockFile.readLockFile(lockFilePath);
        assertThat(lockFile.getConfig().isIncludeEnvironment()).isFalse();
        assertThat(lockFile.getEnvironment()).isNull();
    }

    @MavenTest
    public void withEnvironmentFromLockfile(MavenExecutionResult result) throws Exception {
        // contract: a not null environment should be returned if include environment is true
        assertThat(result).isSuccessful();
        Path lockFilePath = findFile(result, "lockfile.json");
        assertThat(lockFilePath).exists();
        var lockFile = LockFile.readLockFile(lockFilePath);
        assertThat(lockFile.getConfig().isIncludeEnvironment()).isTrue();
        assertThat(lockFile.getEnvironment()).isNotNull();
    }

    @MavenTest
    public void withoutEnvironmentFromLockfile(MavenExecutionResult result) throws Exception {
        // contract: a null environment should be returned if include environment is false
        assertThat(result).isSuccessful();
        Path lockFilePath = findFile(result, "lockfile.json");
        assertThat(lockFilePath).exists();
        var lockFile = LockFile.readLockFile(lockFilePath);
        assertThat(lockFile.getConfig().isIncludeEnvironment()).isFalse();
        assertThat(lockFile.getEnvironment()).isNull();
    }

    @MavenTest
    public void orderedLockfile(MavenExecutionResult result) throws Exception {
        // contract: the dependency list should be ordered
        assertThat(result).isSuccessful();
        Path lockFilePath = findFile(result, "lockfile.json");
        assertThat(lockFilePath).exists();
        var lockFile = LockFile.readLockFile(lockFilePath);
        var dependencyList = lockFile.getDependencies().stream()
                .map(it -> it.getComparatorString())
                .collect(Collectors.toList());
        boolean sorted = Ordering.natural().isOrdered(dependencyList);
        assertThat(sorted).isTrue();
    }

    @MavenTest
    public void skipLockfile(MavenExecutionResult result) throws Exception {
        // contract: the lockfile should not be generated if skip option is true
        assertThat(result).isSuccessful();
        var fileExists = Files.find(
                        result.getMavenProjectResult().getTargetBaseDirectory(),
                        Integer.MAX_VALUE,
                        (path, attr) -> path.getFileName().toString().contains("lockfile.json"))
                .findAny()
                .isPresent();
        assertThat(fileExists).isFalse();
    }
}
