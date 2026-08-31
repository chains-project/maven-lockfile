package io.github.chains_project.maven_lockfile.recorder;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import javax.inject.Named;
import javax.inject.Singleton;
import org.apache.maven.eventspy.AbstractEventSpy;
import org.apache.maven.eventspy.EventSpy;
import org.apache.maven.execution.ExecutionEvent;
import org.apache.maven.plugin.MojoExecution;
import org.eclipse.aether.RepositoryEvent;

/**
 * Maven core extension that records every artifact resolved during a session, regardless of
 * which plugin triggered it - including artifacts resolved imperatively at execution time (e.g.
 * Surefire's test-framework provider) that never appear in any POM. Load via {@code
 * .mvn/extensions.xml} or {@code -Dmaven.ext.class.path}; {@code LockFileFacade} merges what it
 * recorded (via {@link DynamicResolutionStore}) into the triggering plugin's own dependencies
 * when {@code hermetic} is enabled.
 *
 * <p>Pauses itself once this plugin's own {@code generate}/{@code validate} Mojo starts: that
 * goal resolves a large, legitimate static graph through the same resolver, which would otherwise
 * be captured as if it were dynamic activity.
 */
@Named
@Singleton
public class DynamicResolutionSpy extends AbstractEventSpy {

    private static final String OWN_GROUP_ID = "io.github.chains-project";
    private static final String OWN_ARTIFACT_ID = "maven-lockfile";
    private static final Set<String> PAUSE_ON_OWN_GOALS = Set.of("generate", "validate");

    private final Map<RecordedArtifact, Boolean> recorded = new ConcurrentHashMap<>();
    private final AtomicBoolean paused = new AtomicBoolean(false);
    private final AtomicReference<MojoExecution> currentMojo = new AtomicReference<>();

    /**
     * Writes an empty recording as soon as the extension loads, before any Mojo runs. That way
     * {@code LockFileFacade} can tell "extension attached but nothing captured yet" (file exists,
     * empty) apart from "extension not attached" (file absent) - the former usually means
     * generate/validate ran before whatever build phase triggers the dynamic resolution (e.g.
     * `test` for Surefire's provider), since this goal's default binding is generate-resources.
     */
    @Override
    public void init(EventSpy.Context context) {
        flushMarker();
    }

    @Override
    public void onEvent(Object event) {
        if (event instanceof ExecutionEvent) {
            onExecutionEvent((ExecutionEvent) event);
            return;
        }
        if (paused.get() || !(event instanceof RepositoryEvent)) {
            return;
        }
        RepositoryEvent repositoryEvent = (RepositoryEvent) event;
        if (repositoryEvent.getType() != RepositoryEvent.EventType.ARTIFACT_RESOLVED
                || repositoryEvent.getException() != null) {
            return;
        }
        org.eclipse.aether.artifact.Artifact artifact = repositoryEvent.getArtifact();
        if (artifact == null) {
            return;
        }
        if (OWN_GROUP_ID.equals(artifact.getGroupId()) && OWN_ARTIFACT_ID.equals(artifact.getArtifactId())) {
            return;
        }
        MojoExecution mojo = currentMojo.get();
        RecordedArtifact newlyRecorded = new RecordedArtifact(
                artifact.getGroupId(),
                artifact.getArtifactId(),
                artifact.getVersion(),
                artifact.getExtension(),
                artifact.getClassifier(),
                mojo != null ? mojo.getGroupId() : null,
                mojo != null ? mojo.getArtifactId() : null);
        if (recorded.put(newlyRecorded, Boolean.TRUE) == null) {
            flush();
        }
    }

    private void onExecutionEvent(ExecutionEvent executionEvent) {
        MojoExecution mojoExecution = executionEvent.getMojoExecution();
        if (mojoExecution == null) {
            return;
        }
        if (executionEvent.getType() == ExecutionEvent.Type.MojoStarted) {
            currentMojo.set(mojoExecution);
            if (OWN_GROUP_ID.equals(mojoExecution.getGroupId())
                    && OWN_ARTIFACT_ID.equals(mojoExecution.getArtifactId())
                    && PAUSE_ON_OWN_GOALS.contains(mojoExecution.getGoal())) {
                paused.set(true);
            }
        } else if (executionEvent.getType() == ExecutionEvent.Type.MojoSucceeded
                || executionEvent.getType() == ExecutionEvent.Type.MojoFailed
                || executionEvent.getType() == ExecutionEvent.Type.MojoSkipped) {
            currentMojo.compareAndSet(mojoExecution, null);
        }
    }

    @Override
    public void close() {
        flush();
    }

    private void flush() {
        if (recorded.isEmpty()) {
            return;
        }
        flushMarker();
    }

    /**
     * Merges this process's in-memory {@code recorded} set with whatever is already on disk before
     * writing, since each {@code mvn} invocation in a job gets its own {@link DynamicResolutionSpy}
     * instance (and thus its own empty {@code recorded} map) - a plain overwrite would lose an
     * earlier invocation's recording every time this one flushes, including at {@link #init}.
     */
    private void flushMarker() {
        String multiModuleProjectDirectory = System.getProperty("maven.multiModuleProjectDirectory");
        if (multiModuleProjectDirectory == null) {
            return;
        }
        Path path = DynamicResolutionStore.defaultPath(Path.of(multiModuleProjectDirectory));
        try {
            Set<RecordedArtifact> merged = new TreeSet<>(recorded.keySet());
            merged.addAll(DynamicResolutionStore.read(path));
            DynamicResolutionStore.write(path, merged);
        } catch (IOException e) {
            System.err.println("[maven-lockfile] Could not write recorded dynamic resolutions: " + e.getMessage());
        }
    }
}
