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
import io.github.chains_project.maven_lockfile.data.RepositoryId;
import io.github.chains_project.maven_lockfile.data.ResolvedUrl;
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
        // contract: if including maven plugins the lockfile should contain these and be able to calculate checksums for
        // them.
        // Note that remote does not work as the maven-lockfile plugin with SNAPSHOT version is not available on remote.
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

    private boolean fileExists(MavenExecutionResult result, String fileName) throws IOException {
        return Files.find(
                        result.getMavenProjectResult().getTargetBaseDirectory(),
                        Integer.MAX_VALUE,
                        (path, attr) -> path.getFileName().toString().contains(fileName))
                .findAny()
                .isPresent();
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

        String stdout = Files.readString(result.getMavenLog().getStdout());
        assertThat(stdout.contains("[WARNING] Failed verifying lock file. Lock file validation failed."))
                .isTrue();
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
        var fileExists = fileExists(result, "lockfile.json");
        assertThat(fileExists).isFalse();
    }

    @MavenTest
    public void differentLockfileName(MavenExecutionResult result) throws Exception {
        // contract: the lockfile should be generated with a different name
        assertThat(result).isSuccessful();
        var lockfileExists = fileExists(result, "lockfile.json");
        assertThat(lockfileExists).isFalse();
        var differentLockfileNameExists = fileExists(result, "different-lockfile-name.json");
        assertThat(differentLockfileNameExists).isTrue();
    }

    @MavenTest
    public void differentLockfileNameFreezeShouldSucceed(MavenExecutionResult result) throws Exception {
        // contract: if there exists a different-name-lockfile.json and -DlockfileName="different-lockfile-name.json" is
        // provided, freeze should succeed
        assertThat(result).isSuccessful();
    }

    @MavenTest
    public void differentLockfileNameValidateShouldFail(MavenExecutionResult result) throws Exception {
        // contract: if there exists a lockfile.json but -DlockfileName="different-lockfile-name.json" is provided,
        // validate should fail
        assertThat(result).isFailure();
    }

    @MavenTest
    public void differentLockfileNameValidateShouldSucceed(MavenExecutionResult result) throws Exception {
        // contract: if there exists a different-name-lockfile.json and -DlockfileName="different-lockfile-name.json" is
        // provided, validate should succeed
        assertThat(result).isSuccessful();
    }

    @MavenTest
    public void remoteRepositoryShouldResolve(MavenExecutionResult result) throws Exception {
        // contract: if the pom contains a remote repository other that maven_central, the artifact should resolve
        assertThat(result).isSuccessful();
    }

    @MavenTest
    public void checksumModeRemote(MavenExecutionResult result) throws Exception {
        // contract: if checksum mode is remote, maven-lockfile should be able to download and verify SHA-256 from maven
        // central and if SHA-256 is not available, it should be able to .
        assertThat(result).isSuccessful();
        var lockfilePath = findFile(result, "lockfile.json");
        assertThat(lockfilePath).exists();
        var lockfile = LockFile.readLockFile(lockfilePath);

        // Verify: atlassian-bandana:0.2.0 is hosted on packages.atlassian.com which doesn't provide SHA-256, SHA-256
        // has
        // to be calculated
        var dep1Checksum = lockfile.getDependencies().stream()
                .filter(dependency -> dependency
                        .getChecksum()
                        .equals("12357e6d5c5eb6b5ed80bbb98f4ef7b70fcb08520a9f306c4af086c37d6ebc11"))
                .findAny();
        assertThat(dep1Checksum).isNotNull();
        result.getMavenLog();

        // Verify: jsap:2.1 is hosted on repo.maven.apache.org which doesn't provide SHA-256, and who's SHA-1 has a
        // different format (providing `checksum path` instead of `checksum`). SHA-1 should still succeed as the
        // `checksum` is verified aganist up until the first space, thus excluding the path of the file when the
        // SHA-1 was generated. SHA-256 has to be calculated.
        var dep2Checksum = lockfile.getDependencies().stream()
                .filter(dependency -> dependency
                        .getChecksum()
                        .equals("331746fa62cfbc3368260c5a2e660936ad11be612308c120a044e120361d474e"))
                .findAny();
        assertThat(dep2Checksum).isNotNull();

        // Verify: spoon-core:11.1.0 is hosted on maven central and directly provides SHA-256 checksums
        var dep3Checksum = lockfile.getDependencies().stream()
                .filter(dependency -> dependency
                        .getChecksum()
                        .equals("a8ae41ae0a1578a7ef9ce4f8d562813a99e6cc015e8cb3b0482b5470d53f1c6b"))
                .findAny();
        assertThat(dep3Checksum).isNotNull();
    }

    @MavenTest
    public void resolvedFieldShouldResolve(MavenExecutionResult result) throws Exception {
        // contract: resolved field should find correctly url for projects with multiple repositories
        assertThat(result).isSuccessful();
        Path lockFilePath = findFile(result, "lockfile.json");
        assertThat(lockFilePath).exists();
        var lockFile = LockFile.readLockFile(lockFilePath);
        var atlassianResolved = lockFile.getDependencies().stream()
                .filter(
                        dependency -> dependency
                                .getResolved()
                                .equals(
                                        ResolvedUrl.of(
                                                "https://packages.atlassian.com/maven-public/atlassian-bandana/atlassian-bandana/0.2.0/atlassian-bandana-0.2.0.jar")))
                .findAny();
        assertThat(atlassianResolved).isNotNull();
        var mavenCentralResolved = lockFile.getDependencies().stream()
                .filter(
                        dependency -> dependency
                                .getResolved()
                                .equals(
                                        ResolvedUrl.of(
                                                "https://repo.maven.apache.org/maven2/org/sonatype/sisu/sisu-inject-bean/1.4.2/sisu-inject-bean-1.4.2.jar")))
                .findAny();
        assertThat(mavenCentralResolved).isNotNull();
        // Ensure dependencies with classifiers have correctly resolved urls.
        // sisu-guice with classifier noaop is a direct dependency of org.sonatype.sisu:sisu-inject-bean.
        var dependencyWithClassifierResolved = lockFile.getDependencies().stream()
                .filter(
                        dependency -> dependency
                                .getResolved()
                                .equals(
                                        ResolvedUrl.of(
                                                "https://repo.maven.apache.org/maven2/org/sonatype/sisu/sisu-guice/2.1.7/sisu-guice-2.1.7-noaop.jar")))
                .findAny();
        assertThat(dependencyWithClassifierResolved).isNotNull();
        // Ensure repository ids are resolved.
        var atlassianRepositoryId = lockFile.getDependencies().stream()
                .filter(dependency -> dependency.getRepositoryId().equals(RepositoryId.of("maven-atlassian-all")))
                .findAny();
        assertThat(atlassianRepositoryId).isNotNull();
        var mavenCentralRepositoryId = lockFile.getDependencies().stream()
                .filter(dependency -> dependency.getRepositoryId().equals(RepositoryId.of("central")))
                .findAny();
        assertThat(mavenCentralRepositoryId).isNotNull();
    }

    @MavenTest
    public void pomCheckShouldFail(MavenExecutionResult result) throws Exception {
        // contract: if the pom checksum does not match is should fail with reason being pom didn't match.
        assertThat(result).isFailure();
        String stdout = Files.readString(result.getMavenLog().getStdout());
        assertThat(stdout.contains("Pom checksum mismatch.")).isTrue();
    }

    @MavenTest
    public void environmentalCheckShouldFail(MavenExecutionResult result) throws Exception {
        // contract: if the pom checksum does not match is should fail with reason being pom didn't match.
        assertThat(result).isFailure();
        String stdout = Files.readString(result.getMavenLog().getStdout());
        assertThat(stdout.contains("Failed verifying environment.")).isTrue();
    }
}
