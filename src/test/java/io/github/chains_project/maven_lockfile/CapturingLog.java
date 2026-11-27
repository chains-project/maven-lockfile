package io.github.chains_project.maven_lockfile;

import java.util.ArrayList;
import java.util.List;
import org.apache.maven.plugin.logging.SystemStreamLog;

/** A Maven log that keeps the warnings it gets, so tests can check them. */
final class CapturingLog extends SystemStreamLog {

    final List<String> warnings = new ArrayList<>();

    @Override
    public void warn(CharSequence content) {
        warnings.add(content.toString());
    }
}
