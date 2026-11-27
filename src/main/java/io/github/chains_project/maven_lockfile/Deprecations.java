package io.github.chains_project.maven_lockfile;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecution;

/** Warnings about what changes in 6.0.0 (#1732). Remove them in 6.0.0. */
final class Deprecations {

    private static final String MAJOR_RELEASE_ISSUE = "https://github.com/chains-project/maven-lockfile/issues/1732";

    private Deprecations() {}

    /** Warns if the lockfile that {@code mojo} reads or writes has {@code lockFileVersion} 1. */
    static void warnAboutLockfile(
            AbstractMojo mojo, MojoExecution execution, String lockfileName, int lockfileVersion) {
        if (lockfileVersion == 1) {
            mojo.getLog()
                    .warn(lockfileVersion1(lockfileName, execution.getPlugin().getVersion()));
        }
    }

    private static String lockfileVersion1(String lockfileName, String pluginVersion) {
        return String.format(
                "%1$s uses lockFileVersion 1, which 6.0.0 will not read. Switch with 'mvn"
                        + " io.github.chains-project:maven-lockfile:%2$s:generate -DlockfileVersion=2'; later runs of"
                        + " 'generate' keep version 2, which needs maven-lockfile %2$s or later wherever %1$s is"
                        + " read. See %3$s",
                lockfileName, pluginVersion, MAJOR_RELEASE_ISSUE);
    }
}
