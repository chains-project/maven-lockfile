package io.github.chains_project.maven_lockfile.recorder;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import org.apache.maven.repository.internal.MavenRepositorySystemUtils;
import org.eclipse.aether.RepositoryEvent;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.repository.RemoteRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Verifies DynamicResolutionSpy's event-filtering and recording logic directly, without going
 * through a full Maven session. The end-to-end capture behavior against a real build (the thing
 * this class is meant to observe) is covered by the dynamicResolutionCapture integration test.
 */
class DynamicResolutionSpyTest {

    @TempDir
    Path tempDir;

    private static final RepositorySystemSession SESSION = MavenRepositorySystemUtils.newSession();
    private static final RemoteRepository CENTRAL =
            new RemoteRepository.Builder("central", "default", "https://repo.maven.apache.org/maven2").build();

    @Test
    void recordsArtifactResolvedEvents() throws IOException {
        DynamicResolutionSpy spy = new DynamicResolutionSpy();
        spy.onEvent(resolvedEvent("org.apache.maven.surefire", "surefire-junit-platform", "3.2.5", "jar"));

        assertThat(currentlyRecorded(spy))
                .extracting(RecordedArtifact::getArtifactId)
                .containsExactly("surefire-junit-platform");
    }

    @Test
    void ignoresNonRepositoryEvents() throws IOException {
        DynamicResolutionSpy spy = new DynamicResolutionSpy();
        spy.onEvent("not a repository event");

        assertThat(currentlyRecorded(spy)).isEmpty();
    }

    @Test
    void ignoresPomResolutions() throws IOException {
        DynamicResolutionSpy spy = new DynamicResolutionSpy();
        spy.onEvent(resolvedEvent("org.apache.maven.surefire", "surefire-junit-platform", "3.2.5", "pom"));

        assertThat(currentlyRecorded(spy)).isEmpty();
    }

    @Test
    void ignoresFailedResolutions() throws IOException {
        DynamicResolutionSpy spy = new DynamicResolutionSpy();
        RepositoryEvent failed = new RepositoryEvent.Builder(SESSION, RepositoryEvent.EventType.ARTIFACT_RESOLVED)
                .setArtifact(new DefaultArtifact("g", "a", "jar", "1.0"))
                .setException(new RuntimeException("resolution failed"))
                .build();
        spy.onEvent(failed);

        assertThat(currentlyRecorded(spy)).isEmpty();
    }

    @Test
    void deduplicatesRepeatedResolutionsOfTheSameArtifact() throws IOException {
        DynamicResolutionSpy spy = new DynamicResolutionSpy();
        spy.onEvent(resolvedEvent("org.apache.maven.surefire", "surefire-junit-platform", "3.2.5", "jar"));
        spy.onEvent(resolvedEvent("org.apache.maven.surefire", "surefire-junit-platform", "3.2.5", "jar"));

        assertThat(currentlyRecorded(spy)).hasSize(1);
    }

    private static RepositoryEvent resolvedEvent(String groupId, String artifactId, String version, String extension) {
        return new RepositoryEvent.Builder(SESSION, RepositoryEvent.EventType.ARTIFACT_RESOLVED)
                .setArtifact(new DefaultArtifact(groupId, artifactId, extension, version))
                .setRepository(CENTRAL)
                .build();
    }

    /**
     * Points the spy's close() at a temp directory (the same mechanism it uses to find the real
     * reactor root) and reads back what it wrote, exercising the actual production code path
     * rather than a test-only accessor.
     */
    private List<RecordedArtifact> currentlyRecorded(DynamicResolutionSpy spy) throws IOException {
        String previous = System.getProperty("maven.multiModuleProjectDirectory");
        System.setProperty("maven.multiModuleProjectDirectory", tempDir.toString());
        try {
            spy.close();
        } finally {
            if (previous == null) {
                System.clearProperty("maven.multiModuleProjectDirectory");
            } else {
                System.setProperty("maven.multiModuleProjectDirectory", previous);
            }
        }
        return DynamicResolutionStore.read(DynamicResolutionStore.defaultPath(tempDir));
    }
}
