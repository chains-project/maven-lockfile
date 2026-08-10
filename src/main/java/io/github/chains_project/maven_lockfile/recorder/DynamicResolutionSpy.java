package io.github.chains_project.maven_lockfile.recorder;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.inject.Named;
import javax.inject.Singleton;
import org.apache.maven.eventspy.AbstractEventSpy;
import org.apache.maven.execution.ExecutionEvent;
import org.apache.maven.plugin.MojoExecution;
import org.eclipse.aether.RepositoryEvent;

/**
 * Maven core extension that observes every artifact resolution performed during the session,
 * regardless of which plugin triggered it.
 *
 * <p>Some build plugins (Surefire/Failsafe test-framework providers, Quarkus deployment JARs,
 * protobuf's OS-specific {@code protoc}, ...) resolve artifacts imperatively at execution time,
 * via their own Mojo code calling straight into the resolver - never declaring them in any POM.
 * Nothing that only walks the declared dependency graph (including this plugin's own {@code
 * generate} goal by default) can see those. This spy instead taps Maven's {@link
 * org.apache.maven.eventspy.EventSpy} extension point, which receives a {@link RepositoryEvent}
 * for every artifact resolution in the session - static or dynamic - so it needs no per-plugin
 * knowledge.
 *
 * <p>Must be loaded before the session starts (it's an {@code EventSpy}, not a Mojo), via {@code
 * .mvn/extensions.xml} or {@code -Dmaven.ext.class.path} pointing at this plugin's jar. When
 * {@code includeDynamicallyResolvedArtifacts} is enabled, {@code LockFileFacade} merges what it
 * recorded (via {@link DynamicResolutionStore}) into the generated lockfile.
 *
 * <p>The recording is flushed to disk after every newly-seen artifact, not only at session end:
 * this plugin's own {@code generate} goal defaults to the early {@code generate-resources} phase,
 * which runs before Surefire/Failsafe execute. A {@code generate} execution bound to a later
 * phase (e.g. {@code verify}, after tests run) can then see artifacts resolved earlier in the
 * very same session, not just ones left over from a previous build.
 *
 * <p><b>Self-exclusion:</b> {@code generate}/{@code validate} themselves resolve a large,
 * legitimate static graph (every plugin's full dependency tree, including candidate versions
 * evaluated and discarded during conflict mediation) through this exact same resolver - so once
 * this plugin's own goal starts running, those resolutions are indistinguishable, event-for-event,
 * from genuine dynamic activity. The spy pauses itself the moment it observes its own {@code
 * generate}/{@code validate} Mojo start, so its own bookkeeping is never mistaken for the thing it
 * exists to detect.
 */
@Named
@Singleton
public class DynamicResolutionSpy extends AbstractEventSpy {

    private static final String OWN_GROUP_ID = "io.github.chains-project";
    private static final String OWN_ARTIFACT_ID = "maven-lockfile";
    private static final Set<String> PAUSE_ON_OWN_GOALS = Set.of("generate", "validate");

    private final Map<RecordedArtifact, Boolean> recorded = new ConcurrentHashMap<>();
    private final AtomicBoolean paused = new AtomicBoolean(false);

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
        if (artifact == null || "pom".equals(artifact.getExtension())) {
            // POM resolution is already visible through the declared parent/BOM chain; only the
            // binary artifact matters for what a hermetic build needs to fetch.
            return;
        }
        String repositoryId = repositoryEvent.getRepository() != null
                ? repositoryEvent.getRepository().getId()
                : null;
        RecordedArtifact newlyRecorded = new RecordedArtifact(
                artifact.getGroupId(),
                artifact.getArtifactId(),
                artifact.getVersion(),
                artifact.getExtension(),
                artifact.getClassifier(),
                repositoryId);
        if (recorded.put(newlyRecorded, Boolean.TRUE) == null) {
            flush();
        }
    }

    private void onExecutionEvent(ExecutionEvent executionEvent) {
        if (executionEvent.getType() != ExecutionEvent.Type.MojoStarted) {
            return;
        }
        MojoExecution mojoExecution = executionEvent.getMojoExecution();
        if (mojoExecution == null) {
            return;
        }
        if (OWN_GROUP_ID.equals(mojoExecution.getGroupId())
                && OWN_ARTIFACT_ID.equals(mojoExecution.getArtifactId())
                && PAUSE_ON_OWN_GOALS.contains(mojoExecution.getGoal())) {
            paused.set(true);
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
        String multiModuleProjectDirectory = System.getProperty("maven.multiModuleProjectDirectory");
        if (multiModuleProjectDirectory == null) {
            return;
        }
        Path path = DynamicResolutionStore.defaultPath(Path.of(multiModuleProjectDirectory));
        try {
            DynamicResolutionStore.write(path, recorded.keySet());
        } catch (IOException e) {
            // An EventSpy failure must never break the build it's observing.
            System.err.println("[maven-lockfile] Could not write recorded dynamic resolutions: " + e.getMessage());
        }
    }
}
