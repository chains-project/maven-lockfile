package io.github.chains_project.maven_lockfile.reporting;

import io.github.chains_project.maven_lockfile.JsonUtils;
import io.github.chains_project.maven_lockfile.LockFileEquality;
import io.github.chains_project.maven_lockfile.data.Config;
import io.github.chains_project.maven_lockfile.data.LockFile;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * All built-in {@link ValidationPhase} implementations, in the order they are run during
 * {@code mvn maven-lockfile:validate}.
 */
public final class ValidationPhases {

    private ValidationPhases() {}

    public static List<ValidationPhase> all() {
        return List.of(
                new EnvironmentPhase(),
                new PomPhase(),
                new CorePhase(),
                new MavenPluginsPhase(),
                new BomsPhase(),
                new ParentPomPhase(),
                new ExtensionsPhase());
    }

    private static final class EnvironmentPhase implements ValidationPhase {

        @Override
        public boolean isEnabled(Config config) {
            return config.isIncludeEnvironment();
        }

        @Override
        public Optional<String> validate(LockFile fromFile, LockFile fromProject, Config config) {
            if (Objects.equals(fromFile.getEnvironment(), fromProject.getEnvironment())) {
                return Optional.empty();
            }
            String msg = "Lock file environment does not match project environment.\n"
                    + "Lockfile environment: " + fromFile.getEnvironment() + "\n"
                    + "Project environment:  " + fromProject.getEnvironment() + "\n";
            return Optional.of(msg);
        }

        @Override
        public boolean isWarn(Config config) {
            return config.getOnEnvironmentalValidationFailure() == Config.OnEnvironmentalValidationFailure.Warn;
        }
    }

    private static final class PomPhase implements ValidationPhase {

        @Override
        public boolean isEnabled(Config config) {
            return true;
        }

        @Override
        public Optional<String> validate(LockFile fromFile, LockFile fromProject, Config config) {
            if (LockFileEquality.pomEqual(fromFile.getPom(), fromProject.getPom(), false)) {
                return Optional.empty();
            }
            String msg = "Pom checksum mismatch. Differences:\nYour lockfile pom:\n"
                    + JsonUtils.toJson(fromFile.getPom())
                    + "\nYour project pom:\n"
                    + JsonUtils.toJson(fromProject.getPom())
                    + "\n";
            return Optional.of(msg);
        }

        @Override
        public boolean isWarn(Config config) {
            return config.getOnPomValidationFailure() == Config.OnPomValidationFailure.Warn;
        }
    }

    private static final class CorePhase implements ValidationPhase {

        @Override
        public boolean isEnabled(Config config) {
            return true;
        }

        @Override
        public Optional<String> validate(LockFile fromFile, LockFile fromProject, Config config) {
            if (LockFileEquality.coreEqual(fromFile, fromProject)) {
                return Optional.empty();
            }
            var diff = LockFileDifference.diff(fromFile, fromProject);
            String msg = "Failed verifying lock file. Lock file validation failed. Differences:\n"
                    + "Your lockfile from file is for: "
                    + fromFile.getGroupId().getValue() + ":"
                    + fromFile.getName().getValue() + ":"
                    + fromFile.getVersion().getValue() + "\n"
                    + "Your generated lockfile is for: "
                    + fromProject.getGroupId().getValue() + ":"
                    + fromProject.getName().getValue() + ":"
                    + fromProject.getVersion().getValue() + "\n"
                    + "Missing dependencies in lock file:\n "
                    + JsonUtils.toJson(diff.getMissingDependenciesInFile()) + "\n"
                    + "Missing dependencies in project:\n "
                    + JsonUtils.toJson(diff.getMissingDependenciesInProject()) + "\n";
            return Optional.of(msg);
        }

        @Override
        public boolean isWarn(Config config) {
            return config.getOnValidationFailure() == Config.OnValidationFailure.Warn;
        }
    }

    private static final class MavenPluginsPhase implements ValidationPhase {

        @Override
        public boolean isEnabled(Config config) {
            return config.isIncludeMavenPlugins();
        }

        @Override
        public Optional<String> validate(LockFile fromFile, LockFile fromProject, Config config) {
            if (LockFileEquality.mavenPluginsEqual(fromFile, fromProject)) {
                return Optional.empty();
            }
            var diff = LockFileDifference.diff(fromFile, fromProject);
            String msg = "Maven plugin validation failed. Differences:\n"
                    + "Missing plugins in lockfile:\n "
                    + JsonUtils.toJson(diff.getMissingPluginsInFile()) + "\n"
                    + "Missing plugins in project:\n "
                    + JsonUtils.toJson(diff.getMissingPluginsInProject()) + "\n";
            return Optional.of(msg);
        }

        @Override
        public boolean isWarn(Config config) {
            return config.getOnMavenPluginValidationFailure() == Config.OnMavenPluginValidationFailure.Warn;
        }
    }

    private static final class BomsPhase implements ValidationPhase {

        @Override
        public boolean isEnabled(Config config) {
            return config.isIncludeBoms();
        }

        @Override
        public Optional<String> validate(LockFile fromFile, LockFile fromProject, Config config) {
            if (LockFileEquality.bomsEqualForAll(fromFile, fromProject, config.isIncludeParentPom())) {
                return Optional.empty();
            }
            return Optional.of("Bom validation failed."
                    + " The BOM membership or BOM POM data of dependencies differs"
                    + " between the lockfile and the project.");
        }

        @Override
        public boolean isWarn(Config config) {
            return config.getOnBomValidationFailure() == Config.OnBomValidationFailure.Warn;
        }
    }

    private static final class ParentPomPhase implements ValidationPhase {

        @Override
        public boolean isEnabled(Config config) {
            return config.isIncludeParentPom();
        }

        @Override
        public Optional<String> validate(LockFile fromFile, LockFile fromProject, Config config) {
            if (LockFileEquality.parentPomsEqualForAll(fromFile, fromProject)) {
                return Optional.empty();
            }
            return Optional.of("Parent POM validation failed."
                    + " The parentPom of one or more dependencies or plugins differs"
                    + " between the lockfile and the project.");
        }

        @Override
        public boolean isWarn(Config config) {
            return config.getOnParentPomValidationFailure() == Config.OnParentPomValidationFailure.Warn;
        }
    }

    private static final class ExtensionsPhase implements ValidationPhase {

        @Override
        public boolean isEnabled(Config config) {
            return config.isIncludeMavenExtensions();
        }

        @Override
        public Optional<String> validate(LockFile fromFile, LockFile fromProject, Config config) {
            if (Objects.equals(fromFile.getMavenExtensions(), fromProject.getMavenExtensions())) {
                return Optional.empty();
            }
            var diff = LockFileDifference.diff(fromFile, fromProject);
            String msg = "Maven extensions validation failed.\n"
                    + "Missing extensions in lockfile:\n "
                    + JsonUtils.toJson(diff.getMissingExtensionsInFile()) + "\n"
                    + "Missing extensions in project:\n "
                    + JsonUtils.toJson(diff.getMissingExtensionsInProject()) + "\n";
            return Optional.of(msg);
        }

        @Override
        public boolean isWarn(Config config) {
            return config.getOnMavenExtensionsValidationFailure() == Config.OnMavenExtensionsValidationFailure.Warn;
        }
    }
}
