package io.github.chains_project.maven_lockfile.resolvers;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Plugin;
import org.apache.maven.project.MavenProject;

/**
 * Base class for resolvers that discover artifacts dynamically loaded by specific Maven build
 * plugins — artifacts that are not declared in the project's {@code pom.xml} but are required
 * for a hermetic offline build.
 *
 * <p>Implement this class to add support for a new plugin. Discovered artifacts are returned as
 * a {@link DiscoveryResult} containing plugin dependencies keyed by the target plugin's
 * {@code groupId:artifactId}.
 *
 * <p>Register new implementations in {@code LockFileFacade#PLUGIN_RESOLVERS}.
 */
public abstract class SpecialPluginResolver {

    /**
     * Returns {@code true} if this resolver applies to the given project (i.e. the plugin it
     * handles is present in the project's build plugins).
     */
    public abstract boolean isApplicable(MavenProject project);

    /**
     * A short human-readable name for log messages (e.g. {@code "Surefire"}).
     */
    public abstract String getDisplayName();

    /**
     * Discovers artifacts that the plugin loads dynamically and returns them as a
     * {@link DiscoveryResult}.
     *
     * @param project the Maven project
     * @param session the Maven session
     * @return discovery result (may be {@link DiscoveryResult#empty()})
     */
    public abstract DiscoveryResult discover(MavenProject project, MavenSession session);

    /**
     * Returns the first build plugin matching the given {@code artifactId}, or empty.
     */
    protected static Optional<Plugin> findPlugin(MavenProject project, String artifactId) {
        return project.getBuildPlugins().stream()
                .filter(p -> artifactId.equals(p.getArtifactId()))
                .findFirst();
    }

    /**
     * Holds the artifacts discovered by a {@link SpecialPluginResolver}.
     *
     * <p>{@link #pluginDependencies} are mapped by plugin key ({@code groupId:artifactId});
     * they are injected as user-declared dependencies for the target plugin's resolution in
     * {@code LockFileFacade}.
     */
    public static final class DiscoveryResult {

        private final Map<String, List<Dependency>> pluginDependencies;

        private DiscoveryResult(Map<String, List<Dependency>> pluginDependencies) {
            this.pluginDependencies = pluginDependencies;
        }

        /** Result with no discovered artifacts. */
        public static DiscoveryResult empty() {
            return new DiscoveryResult(Collections.emptyMap());
        }

        /**
         * Result carrying dependencies to inject into a specific plugin's resolution.
         *
         * @param pluginKey  {@code groupId:artifactId} of the target plugin
         * @param deps       dependencies to inject
         */
        public static DiscoveryResult ofPluginDependencies(
                String pluginKey, List<Dependency> deps) {
            return new DiscoveryResult(Map.of(pluginKey, deps));
        }

        /**
         * Returns dependencies to inject, keyed by plugin {@code groupId:artifactId}.
         */
        public Map<String, List<Dependency>> getPluginDependencies() {
            return pluginDependencies;
        }

        public boolean isEmpty() {
            return pluginDependencies.isEmpty();
        }
    }
}
