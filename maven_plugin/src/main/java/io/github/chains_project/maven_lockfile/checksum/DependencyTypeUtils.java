package io.github.chains_project.maven_lockfile.checksum;

import java.util.Map;

/**
 * Maps Maven dependency types to their file extensions.
 *
 * @see <a href="https://maven.apache.org/ref/3.9.9/maven-core/artifact-handlers.html">Maven Default Artifact Handlers</a>
 */
public final class DependencyTypeUtils {
    private static final Map<String, String> TYPE_TO_EXTENSION = Map.ofEntries(
            Map.entry("jar", "jar"),
            Map.entry("pom", "pom"),
            Map.entry("maven-plugin", "jar"),
            Map.entry("ear", "ear"),
            Map.entry("ejb", "jar"),
            Map.entry("ejb-client", "jar"),
            Map.entry("javadoc", "jar"),
            Map.entry("java-source", "jar"),
            Map.entry("rar", "rar"),
            Map.entry("test-jar", "jar"),
            Map.entry("war", "war"));

    private DependencyTypeUtils() {}

    public static String getExtension(String type) {
        if (type == null || type.isEmpty()) {
            return "jar";
        }
        return TYPE_TO_EXTENSION.getOrDefault(type, type);
    }
}
