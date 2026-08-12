package io.github.chains_project.maven_lockfile.recorder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import org.apache.maven.execution.ExecutionEvent;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.repository.internal.MavenRepositorySystemUtils;
import org.eclipse.aether.RepositoryEvent;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.repository.RemoteRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

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
    void recordsPomResolutions() throws IOException {
        DynamicResolutionSpy spy = new DynamicResolutionSpy();
        spy.onEvent(resolvedEvent("org.apache.maven.surefire", "surefire-providers", "3.2.5", "pom"));

        assertThat(currentlyRecorded(spy))
                .extracting(RecordedArtifact::getArtifactId)
                .containsExactly("surefire-providers");
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
    void tagsArtifactsWithTheCurrentlyExecutingPlugin() throws IOException {
        DynamicResolutionSpy spy = new DynamicResolutionSpy();
        spy.onEvent(mojoStartedEvent("org.apache.maven.plugins", "maven-surefire-plugin"));
        spy.onEvent(resolvedEvent("org.apache.maven.surefire", "surefire-junit-platform", "3.2.5", "jar"));

        List<RecordedArtifact> recorded = currentlyRecorded(spy);
        assertThat(recorded).hasSize(1);
        assertThat(recorded.get(0).getTriggeringPluginGroupId()).isEqualTo("org.apache.maven.plugins");
        assertThat(recorded.get(0).getTriggeringPluginArtifactId()).isEqualTo("maven-surefire-plugin");
    }

    @Test
    void noLongerTagsArtifactsOnceTheMojoFinishes() throws IOException {
        DynamicResolutionSpy spy = new DynamicResolutionSpy();
        MojoExecution surefireExecution = mojoExecution("org.apache.maven.plugins", "maven-surefire-plugin");
        spy.onEvent(mojoStartedEvent(surefireExecution));
        spy.onEvent(mojoFinishedEvent(surefireExecution, ExecutionEvent.Type.MojoSucceeded));
        spy.onEvent(resolvedEvent("org.apache.maven.surefire", "surefire-junit-platform", "3.2.5", "jar"));

        assertThat(currentlyRecorded(spy).get(0).getTriggeringPluginGroupId()).isNull();
    }

    @Test
    void deduplicatesRepeatedResolutionsOfTheSameArtifact() throws IOException {
        DynamicResolutionSpy spy = new DynamicResolutionSpy();
        spy.onEvent(resolvedEvent("org.apache.maven.surefire", "surefire-junit-platform", "3.2.5", "jar"));
        spy.onEvent(resolvedEvent("org.apache.maven.surefire", "surefire-junit-platform", "3.2.5", "jar"));

        assertThat(currentlyRecorded(spy)).hasSize(1);
    }

    private static MojoExecution mojoExecution(String groupId, String artifactId) {
        MojoExecution mojoExecution = mock(MojoExecution.class);
        when(mojoExecution.getGroupId()).thenReturn(groupId);
        when(mojoExecution.getArtifactId()).thenReturn(artifactId);
        return mojoExecution;
    }

    private static ExecutionEvent mojoStartedEvent(String groupId, String artifactId) {
        return mojoStartedEvent(mojoExecution(groupId, artifactId));
    }

    private static ExecutionEvent mojoStartedEvent(MojoExecution mojoExecution) {
        return mojoFinishedEvent(mojoExecution, ExecutionEvent.Type.MojoStarted);
    }

    private static ExecutionEvent mojoFinishedEvent(MojoExecution mojoExecution, ExecutionEvent.Type type) {
        ExecutionEvent event = mock(ExecutionEvent.class);
        when(event.getType()).thenReturn(type);
        when(event.getMojoExecution()).thenReturn(mojoExecution);
        return event;
    }

    private static RepositoryEvent resolvedEvent(String groupId, String artifactId, String version, String extension) {
        return new RepositoryEvent.Builder(SESSION, RepositoryEvent.EventType.ARTIFACT_RESOLVED)
                .setArtifact(new DefaultArtifact(groupId, artifactId, extension, version))
                .setRepository(CENTRAL)
                .build();
    }

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
