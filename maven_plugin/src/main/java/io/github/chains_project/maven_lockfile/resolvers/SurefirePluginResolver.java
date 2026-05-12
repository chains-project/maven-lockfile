package io.github.chains_project.maven_lockfile.resolvers;

import io.github.chains_project.maven_lockfile.reporting.PluginLogManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Dependency;
import org.apache.maven.project.MavenProject;

/**
 * Discovers the test framework provider that {@code maven-surefire-plugin} loads dynamically at
 * runtime based on which testing library is declared in the project's test dependencies.
 *
 * <p>Surefire selects a provider jar at test-execution time by inspecting the test classpath for
 * known framework marker groupIds, then resolves the matching provider at its own version. None of
 * these provider jars appear in the project's declared dependencies, so they would be missing from
 * the lockfile without this resolver.
 *
 * <p>The mapping from test-framework marker groupId to provider artifactId is defined in
 * {@link #FRAMEWORK_PROVIDER_TABLE}. Adding support for a new provider requires only a new entry
 * in that table.
 */
public class SurefirePluginResolver extends SpecialPluginResolver {

    private static final String SUREFIRE_GROUP_ID = "org.apache.maven.surefire";
    private static final String SUREFIRE_ARTIFACT_ID = "maven-surefire-plugin";
    private static final String SUREFIRE_PLUGIN_KEY =
            "org.apache.maven.plugins:" + SUREFIRE_ARTIFACT_ID;

    /**
     * Maps test-framework marker groupIds to the Surefire provider artifactId that handles them.
     * Entries are evaluated in order; the first match wins.
     */
    private static final List<Map.Entry<List<String>, String>> FRAMEWORK_PROVIDER_TABLE = List.of(
            Map.entry(List.of("org.junit.jupiter", "org.junit.platform"), "surefire-junit-platform"),
            Map.entry(List.of("org.testng"), "surefire-testng"),
            Map.entry(List.of("junit"), "surefire-junit47"),
            Map.entry(List.of("junit-addons"), "surefire-junit3"));

    public SurefirePluginResolver() {}

    @Override
    public boolean isApplicable(MavenProject project) {
        return findPlugin(project, SUREFIRE_ARTIFACT_ID).isPresent()
                && detectProvider(project) != null;
    }

    @Override
    public String getDisplayName() {
        return "maven-surefire-plugin (provider auto-detection)";
    }

    @Override
    public DiscoveryResult discover(MavenProject project, MavenSession session) {
        String surefireVersion = findPlugin(project, SUREFIRE_ARTIFACT_ID)
                .map(p -> p.getVersion())
                .orElse(null);

        if (surefireVersion == null || surefireVersion.startsWith("${")) {
            PluginLogManager.getLog().warn(
                    "SurefirePluginResolver: could not resolve surefire version — skipping");
            return DiscoveryResult.empty();
        }

        String providerArtifactId = detectProvider(project);
        if (providerArtifactId == null) {
            return DiscoveryResult.empty();
        }

        Dependency provider = new Dependency();
        provider.setGroupId(SUREFIRE_GROUP_ID);
        provider.setArtifactId(providerArtifactId);
        provider.setVersion(surefireVersion);

        PluginLogManager.getLog().info(String.format(
                "SurefirePluginResolver: injecting %s:%s:%s into %s",
                SUREFIRE_GROUP_ID, providerArtifactId, surefireVersion, SUREFIRE_PLUGIN_KEY));

        List<Dependency> deps = new ArrayList<>();
        deps.add(provider);
        return DiscoveryResult.ofPluginDependencies(SUREFIRE_PLUGIN_KEY, deps);
    }

    private static String detectProvider(MavenProject project) {
        List<String> testGroupIds = project.getDependencies().stream()
                .filter(d -> "test".equals(d.getScope()))
                .map(d -> d.getGroupId())
                .collect(Collectors.toList());

        for (Map.Entry<List<String>, String> entry : FRAMEWORK_PROVIDER_TABLE) {
            if (testGroupIds.stream().anyMatch(entry.getKey()::contains)) {
                return entry.getValue();
            }
        }
        return null;
    }
}
