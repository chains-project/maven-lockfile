package it;

import static com.soebes.itf.extension.assertj.MavenITAssertions.assertThat;

import com.soebes.itf.jupiter.extension.MavenJupiterExtension;
import com.soebes.itf.jupiter.extension.MavenTest;
import com.soebes.itf.jupiter.maven.MavenExecutionResult;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.maven.plugin.testing.AbstractMojoTestCase;
import se.kth.LockFile;

@MavenJupiterExtension
public class IntegrationTestsIT extends AbstractMojoTestCase {
    @MavenTest
    public void simpleProject(MavenExecutionResult result) throws Exception {
        // contract: an empty project should generate an empty lock file
        assertThat(result).isSuccessful();
        Path lockFilePath = getLockFile(result);
        assertThat(lockFilePath).exists();
        var lockFile = LockFile.readLockFile(lockFilePath);
        assertThat(lockFile.dependencies).isEmpty();
    }

    private Path getLockFile(MavenExecutionResult result) throws IOException {
        return Files.find(
                        result.getMavenProjectResult().getTargetBaseDirectory(),
                        Integer.MAX_VALUE,
                        (v, u) -> v.getFileName().toString().contains("lockfile.json"))
                .findFirst()
                .orElseThrow();
    }
}
