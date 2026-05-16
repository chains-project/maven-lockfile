package io.github.chains_project.maven_lockfile.reporting;

import io.github.chains_project.maven_lockfile.data.Config;
import io.github.chains_project.maven_lockfile.data.LockFile;
import java.util.Optional;

/**
 * Represents one validation concern checked during {@code mvn maven-lockfile:validate}.
 *
 * <p>Each implementation is responsible for deciding whether it applies to the current
 * configuration ({@link #isEnabled}), how to compare the on-disk lockfile against the
 * freshly-generated one ({@link #validate}), and what to do on mismatch ({@link #isWarn}).
 *
 * <p>To add a new field to lockfile validation, implement this interface and register the
 * implementation in {@link ValidationPhases#all}.
 */
public interface ValidationPhase {

    /** Whether this phase should run given the current config. */
    boolean isEnabled(Config config);

    /**
     * Compare the two lockfiles for this concern.
     *
     * @return {@link Optional#empty()} if they agree, or a non-empty failure message.
     */
    Optional<String> validate(LockFile fromFile, LockFile fromProject, Config config);

    /** {@code true} → log a warning on failure; {@code false} → throw a build error. */
    boolean isWarn(Config config);
}
