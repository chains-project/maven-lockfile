package io.github.chains_project.maven_lockfile.checksum;

import com.google.gson.annotations.SerializedName;

/**
 * The checksum modes that are supported by the plugin.
 * A checksum mode is a way to calculate the checksum of a dependency.
 */
public enum ChecksumModes {
    /**
     * Downloads the checksum from the maven repository. Supports MD5, SHA-1, SHA-256 and SHA-512. If the requested
     * checksum is not found in remote repository, the artifact will be downloaded and checksum will be calculated
     * on the downloaded artifact. The download will be verified with the SHA-1 checksum if it available in the remote
     * repository.
     */
    @SerializedName("remote")
    REMOTE("remote"),
    /**
     * Calculates the checksum from the downloaded artifact in the local m2 folder.
     */
    @SerializedName("local")
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
