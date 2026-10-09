package io.github.chains_project.maven_lockfile.recorder;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositoryEvent;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DynamicResolutionSpyTest {

    private static final String PROJECT_DIR_PROPERTY = "maven.multiModuleProjectDirectory";

    @TempDir
    Path tempDir;

    private String previousProjectDir;

    @BeforeEach
    void setProjectDir() {
        previousProjectDir = System.getProperty(PROJECT_DIR_PROPERTY);
        System.setProperty(PROJECT_DIR_PROPERTY, tempDir.toString());
    }

    @AfterEach
    void restoreProjectDir() {
        if (previousProjectDir == null) {
            System.clearProperty(PROJECT_DIR_PROPERTY);
        } else {
            System.setProperty(PROJECT_DIR_PROPERTY, previousProjectDir);
        }
    }

    @Test
    void concurrentResolutionEventsAreAllRecordedAsValidJson() throws Exception {
        int threads = 5;
        int artifactsPerThread = 80;
        DynamicResolutionSpy spy = new DynamicResolutionSpy();
        spy.init(null);
        DefaultRepositorySystemSession session = new DefaultRepositorySystemSession();

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        for (int t = 0; t < threads; t++) {
            int thread = t;
            futures.add(pool.submit(() -> {
                start.await();
                for (int i = 0; i < artifactsPerThread; i++) {
                    spy.onEvent(new RepositoryEvent.Builder(session, RepositoryEvent.EventType.ARTIFACT_RESOLVED)
                            .setArtifact(new DefaultArtifact("g" + thread + ":a" + i + ":1.0"))
                            .build());
                }
                return null;
            }));
        }
        start.countDown();
        for (Future<?> future : futures) {
            future.get();
        }
        pool.shutdown();
        spy.close();

        List<RecordedArtifact> recorded = DynamicResolutionStore.read(DynamicResolutionStore.defaultPath(tempDir));
        assertThat(recorded).hasSize(threads * artifactsPerThread);
    }
}
