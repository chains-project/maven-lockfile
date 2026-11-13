package io.github.chains_project.maven_lockfile.data;

import java.io.IOException;

/** A lockfile that this release cannot read: an unsupported {@code lockFileVersion}, or a malformed file. */
public class LockFileFormatException extends IOException {

    public LockFileFormatException(String message) {
        super(message);
    }
}
