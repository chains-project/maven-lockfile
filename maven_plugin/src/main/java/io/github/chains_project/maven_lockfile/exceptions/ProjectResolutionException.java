package io.github.chains_project.maven_lockfile.exceptions;

/**
 * Thrown when a Maven project cannot be resolved from GAV coordinates.
 * This exception signals that lockfile generation cannot proceed due to
 * missing or inaccessible project artifacts.
 */
public class ProjectResolutionException extends RuntimeException {
    private final String groupId;
    private final String artifactId;
    private final String version;

    /**
     * Constructs a new ProjectResolutionException with the specified GAV coordinates, message, and cause.
     *
     * @param groupId the group ID of the unresolvable project
     * @param artifactId the artifact ID of the unresolvable project
     * @param version the version of the unresolvable project
     * @param message additional context about the failure
     * @param cause the underlying exception that caused the resolution failure
     */
    public ProjectResolutionException(
            String groupId, String artifactId, String version, String message, Throwable cause) {
        super(String.format("Failed to resolve project %s:%s:%s: %s", groupId, artifactId, version, message), cause);
        this.groupId = groupId;
        this.artifactId = artifactId;
        this.version = version;
    }

    /**
     * Constructs a new ProjectResolutionException with the specified GAV coordinates and message.
     *
     * @param groupId the group ID of the unresolvable project
     * @param artifactId the artifact ID of the unresolvable project
     * @param version the version of the unresolvable project
     * @param message additional context about the failure
     */
    public ProjectResolutionException(String groupId, String artifactId, String version, String message) {
        this(groupId, artifactId, version, message, null);
    }

    public String getGroupId() {
        return groupId;
    }

    public String getArtifactId() {
        return artifactId;
    }

    public String getVersion() {
        return version;
    }
}
