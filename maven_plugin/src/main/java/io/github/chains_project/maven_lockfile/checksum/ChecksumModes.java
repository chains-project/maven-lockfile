package io.github.chains_project.maven_lockfile.checksum;

/**
 * The checksum modes that are supported by the plugin.
 * A checksum mode is a way to calculate the checksum of a dependency.
 */
public enum ChecksumModes {
    /**
     * Downloads the checksum from the maven repository.
     */
    REMOTE("remote"),
    /**
     * Calculates the checksum from the downloaded artifact.
     */
    LOCAL("local");

    /**
     * The name of the checksum mode.
     */
    private final String name;

    ChecksumModes(String name) {
        this.name = name;
    }

    /**
     * Gets a checksum mode from its name. Throws an exception if no checksum mode with the given name exists.
     * @param name  The name of the checksum mode.
     * @return  The checksum mode.
     */
    public static ChecksumModes fromName(String name) {
        for (ChecksumModes mode : ChecksumModes.values()) {
            if (mode.name.equals(name)) {
                return mode;
            }
        }
        throw new IllegalArgumentException("No checksum mode with name " + name + " found.");
    }
}
