package io.github.chains_project.maven_lockfile.reporting;

import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugin.logging.SystemStreamLog;

public class PluginLogManager {
    private static Log log;

    private PluginLogManager() {}

    public static void setLog(Log log) {
        PluginLogManager.log = log;
    }

    public static Log getLog() {
        if (log == null) {
            log = new SystemStreamLog();
        }

        return log;
    }
}
