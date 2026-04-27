package io.github.chains_project.maven_lockfile.checksum;

import com.google.common.base.Strings;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import io.github.chains_project.maven_lockfile.data.AbstractMavenComponent;
import io.github.chains_project.maven_lockfile.data.LockFile;
import io.github.chains_project.maven_lockfile.data.Pom;
import io.github.chains_project.maven_lockfile.graph.DependencyNode;
import io.github.chains_project.maven_lockfile.reporting.PluginLogManager;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.project.MavenProject;

public class CachingChecksumCalculator extends AbstractChecksumCalculator {

    private static final String CHECKSUM_CACHE_SESSION_KEY = "generate-lock-file-checksum-cache";
    private static final String RESOLVE_CACHE_SESSION_KEY = "generate-lock-file-resolve-cache";
    private static final int CACHE_SIZE = 2000;

    private final AbstractChecksumCalculator delegate;
    private final Cache<String, String> checksumCache;
    private final Cache<String, RepositoryInformation> resolveCache;

    public CachingChecksumCalculator(
            final AbstractChecksumCalculator delegate,
            final Cache<String, String> checksumCache,
            Cache<String, RepositoryInformation> resolveCache) {
        super(delegate.getChecksumAlgorithm());
        this.delegate = delegate;
        this.checksumCache = checksumCache;
        this.resolveCache = resolveCache;
    }

    @Override
    public String getChecksumAlgorithm() {
        return delegate.getChecksumAlgorithm();
    }

    @Override
    public void prewarmArtifactCache(Collection<Artifact> artifacts) {
        if (artifacts.isEmpty()) {
            return;
        }
        int poolSize = Math.min(16, Math.max(4, Runtime.getRuntime().availableProcessors() * 2));
        PluginLogManager.getLog()
                .info(String.format(
                        "Pre-warming checksum cache for %d unique artifacts with %d threads",
                        artifacts.size(), poolSize));
        ExecutorService executor = Executors.newFixedThreadPool(poolSize);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (var artifact : artifacts) {
                futures.add(executor.submit(() -> {
                    calculateArtifactChecksum(artifact);
                    getArtifactResolvedField(artifact);
                }));
            }
            for (var future : futures) {
                try {
                    future.get();
                } catch (Exception e) {
                    PluginLogManager.getLog().debug("Pre-warm task failed: " + e.getMessage());
                }
            }
        } finally {
            executor.shutdown();
        }
    }

    private static String cacheKey(Artifact artifact) {
        var classifier = Strings.nullToEmpty(artifact.getClassifier());
        return artifact.getGroupId() + ":" + artifact.getArtifactId() + ":" + artifact.getVersion() + ":" + classifier
                + ":" + artifact.getType();
    }

    private String checksumCacheKey(Artifact artifact) {
        return checksumCacheKey(cacheKey(artifact));
    }

    private String checksumCacheKey(String cacheKey) {
        return cacheKey + ":" + getChecksumAlgorithm();
    }

    @Override
    public void prepopulateCache(LockFile lockFile) {
        prepopulateDeps(lockFile.getDependencies());
        prepopulatePlugins(lockFile.getMavenPlugins());
        prepopulatePlugins(lockFile.getMavenExtensions());
        prepopulatePom(lockFile.getPom());
        lockFile.getBoms().forEach(this::prepopulatePom);
    }

    private void prepopulate(Artifact artifact, String checksum, RepositoryInformation repositoryInformation) {
        checksumCache.put(checksumCacheKey(artifact), checksum);
        resolveCache.put(cacheKey(artifact), repositoryInformation);
    }

    private void prepopulatePom(Pom pom) {
        prepopulate(pom.toArtifact(), pom.getChecksum(), pom.getRepositoryInformation());
        if (pom.getParent() != null) {
            prepopulatePom(pom.getParent());
        }
    }

    private void prepopulatePlugins(Collection<? extends AbstractMavenComponent> plugins) {
        for (AbstractMavenComponent plugin : plugins) {
            prepopulate(plugin.toArtifact(), plugin.getChecksum(), plugin.getRepositoryInformation());
            prepopulateDeps(plugin.getDependencies());
        }
    }

    private void prepopulateDeps(Collection<DependencyNode> dependencies) {
        for (DependencyNode node : dependencies) {
            prepopulate(node.toArtifact(), node.getChecksum(), node.getRepositoryInformation());
            prepopulateDeps(node.getChildren());
            node.getBoms().forEach(this::prepopulatePom);
        }
    }

    @Override
    public void report() {
        var checksumStats = checksumCache.stats();
        var resolveStats = resolveCache.stats();
        PluginLogManager.getLog()
                .debug("Checksum cache stats: " + checksumStats + ", average load = "
                        + TimeUnit.NANOSECONDS.toMicros((long) checksumStats.averageLoadPenalty()) + "us");
        PluginLogManager.getLog()
                .debug("Resolve cache stats: " + resolveStats + ", average load = "
                        + TimeUnit.NANOSECONDS.toMicros((long) resolveStats.averageLoadPenalty()) + "us");
    }

    @Override
    public String calculateArtifactChecksum(final Artifact artifact) {
        try {
            return checksumCache.get(checksumCacheKey(artifact), () -> delegate.calculateArtifactChecksum(artifact));
        } catch (ExecutionException e) {
            throw new RuntimeException(e.getCause());
        }
    }

    @Override
    public String calculatePluginChecksum(final Artifact artifact) {
        try {
            return checksumCache.get(checksumCacheKey(artifact), () -> delegate.calculatePluginChecksum(artifact));
        } catch (ExecutionException e) {
            throw new RuntimeException(e.getCause());
        }
    }

    @Override
    public String calculatePomChecksum(Path path) {
        try {
            return checksumCache.get(checksumCacheKey(path.toString()), () -> delegate.calculatePomChecksum(path));
        } catch (ExecutionException e) {
            throw new RuntimeException(e.getCause());
        }
    }

    @Override
    public String getDefaultChecksumAlgorithm() {
        return delegate.getDefaultChecksumAlgorithm();
    }

    @Override
    public RepositoryInformation getArtifactResolvedField(final Artifact artifact) {
        try {
            return resolveCache.get(cacheKey(artifact), () -> delegate.getArtifactResolvedField(artifact));
        } catch (ExecutionException e) {
            throw new RuntimeException(e.getCause());
        }
    }

    @Override
    public RepositoryInformation getPluginResolvedField(final Artifact artifact) {
        try {
            return resolveCache.get(cacheKey(artifact), () -> delegate.getPluginResolvedField(artifact));
        } catch (ExecutionException e) {
            throw new RuntimeException(e.getCause());
        }
    }

    /**
     * Constructs a caching decorator over an existing checksum calculator.
     * The internal details of the cache will be stored in the Maven session, allowing the cache to be used across
     * multiple project builds in a reactor (there are limitations to this, as not all projects in a reactor will
     * share a classloader, so the caching will fall back in those instances to a project scoped cache).
     *
     * @param checksumCalculator the calculator to addd caching to.
     * @param project the Maven project being built.
     * @param session the Maven session for cross-project builds.
     */
    public static CachingChecksumCalculator getCachingChecksumCalculator(
            AbstractChecksumCalculator checksumCalculator, MavenProject project, MavenSession session) {
        Cache<String, String> sessionChecksumCache = null;
        Cache<String, RepositoryInformation> sessionResolveCache = null;
        try {
            sessionChecksumCache =
                    (Cache<String, String>) session.getSystemProperties().get(CHECKSUM_CACHE_SESSION_KEY);
        } catch (ClassCastException ignored) {
            PluginLogManager.getLog()
                    .warn("Could not obtain session checksum cache. "
                            + project.getParent().getId() + " / " + System.identityHashCode(project.getParent()));
            sessionChecksumCache =
                    CacheBuilder.newBuilder().maximumSize(CACHE_SIZE).build();
        }
        try {
            sessionResolveCache = (Cache<String, RepositoryInformation>)
                    session.getSystemProperties().get(RESOLVE_CACHE_SESSION_KEY);
        } catch (ClassCastException ignored) {
            PluginLogManager.getLog()
                    .warn("Could not obtain session resolve cache. "
                            + project.getParent().getId() + " / " + System.identityHashCode(project.getParent()));
            sessionResolveCache =
                    CacheBuilder.newBuilder().maximumSize(CACHE_SIZE).build();
        }
        if (sessionChecksumCache == null) {
            sessionChecksumCache = CacheBuilder.newBuilder()
                    .recordStats()
                    .maximumSize(CACHE_SIZE)
                    .build();
            PluginLogManager.getLog()
                    .debug("Setting checksum cache in " + project.getName() + " / " + project.getId() + " / "
                            + System.identityHashCode(project.getParent()));
            session.getSystemProperties().put(CHECKSUM_CACHE_SESSION_KEY, sessionChecksumCache);
        }
        if (sessionResolveCache == null) {
            sessionResolveCache = CacheBuilder.newBuilder()
                    .recordStats()
                    .maximumSize(CACHE_SIZE)
                    .build();
            PluginLogManager.getLog()
                    .debug("Setting resolve cache in " + project.getName() + " / " + project.getId() + " / "
                            + System.identityHashCode(project.getParent()));
            session.getSystemProperties().put(RESOLVE_CACHE_SESSION_KEY, sessionResolveCache);
        }
        return new CachingChecksumCalculator(checksumCalculator, sessionChecksumCache, sessionResolveCache);
    }
}
