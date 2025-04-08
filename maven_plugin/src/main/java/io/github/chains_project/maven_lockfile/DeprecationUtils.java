package io.github.chains_project.maven_lockfile;

import org.apache.maven.plugin.logging.Log;

public class DeprecationUtils {
    public static String ChecksumModeDeprecation(String checksumMode, Log logger) {
        if (checksumMode.equals("maven_local")) {
            if (logger != null) {
                logger.warn("Option 'checksumMode=maven_local' is deprecated. Use 'checksumMode=local' instead.");
            }
            return "local";
        }
        if (checksumMode.equals("maven_central")) {
            if (logger != null) {
                logger.warn("Option 'checksumMode=maven_central' is deprecated. Use 'checksumMode=remote' instead.");
            }
            return "remote";
        }

        return checksumMode;
    }

    public static String ChecksumModeDeprecation(String checksumMode) {
        return ChecksumModeDeprecation(checksumMode, null);
    }
}
