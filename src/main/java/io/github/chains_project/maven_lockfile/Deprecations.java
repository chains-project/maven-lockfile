package io.github.chains_project.maven_lockfile;

import io.github.chains_project.maven_lockfile.data.Config;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginExecution;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.xml.Xpp3Dom;

/** Warnings about what changes in 6.0.0 (#1732). Remove them in 6.0.0. */
final class Deprecations {

    private static final String GOAL_PARAMETERS_ISSUE = "https://github.com/chains-project/maven-lockfile/issues/1575";
    private static final String EXTENSIONS_ISSUE = "https://github.com/chains-project/maven-lockfile/issues/1598";
    private static final String MAJOR_RELEASE_ISSUE = "https://github.com/chains-project/maven-lockfile/issues/1732";

    /**
     * The goals, each with the parameters that only it uses. In 6.0.0 these parameters move to fields of the goal's own
     * mojo, so this list goes away.
     */
    private enum Goal {
        GENERATE(
                "includeMavenPlugins",
                "includeEnvironment",
                "includeBoms",
                "includeParentPom",
                "includeMavenExtensions",
                "hermetic",
                "reduced",
                "checksumMode",
                "checksumAlgorithm",
                "getConfigFromFile",
                "lockfileVersion"),
        VALIDATE(
                "allowValidationFailure",
                "allowPomValidationFailure",
                "allowMavenPluginValidationFailure",
                "allowEnvironmentalValidationFailure",
                "allowBomValidationFailure",
                "allowParentPomValidationFailure",
                "allowMavenExtensionsValidationFailure"),
        FREEZE("pomLockfileOutput", "exactVersionStrings");

        private final List<String> parameters;

        Goal(String... parameters) {
            this.parameters = List.of(parameters);
        }

        @Override
        public String toString() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /** The parameters that 6.0.0 removes, with the parameter that takes over. */
    private static final Map<String, String> REMOVED_PARAMETERS = Map.of(
            "includeMavenExtensions", "includeMavenPlugins",
            "allowMavenExtensionsValidationFailure", "allowMavenPluginValidationFailure");

    private Deprecations() {}

    /** Warns about each set parameter that belongs to another goal or that 6.0.0 removes. */
    static void warnAboutParameters(
            AbstractMojo mojo,
            Properties userProperties,
            MavenProject project,
            MojoExecution execution,
            String lockfileName) {
        String goal = execution.getGoal();
        List<Object> configurations = pomConfigurations(project, execution);
        for (Goal ownGoal : Goal.values()) {
            for (String name : ownGoal.parameters) {
                if (!isSet(mojo, name, userProperties, configurations)) {
                    continue;
                }
                if (!ownGoal.toString().equals(goal)) {
                    mojo.getLog().warn(wrongGoal(name, ownGoal.toString(), goal, lockfileName));
                } else if (REMOVED_PARAMETERS.containsKey(name)) {
                    mojo.getLog()
                            .warn(String.format(
                                    "Parameter '%s' is deprecated and will be removed in 6.0.0, where '%s' also"
                                            + " applies to build extensions. See %s",
                                    name, REMOVED_PARAMETERS.get(name), EXTENSIONS_ISSUE));
                }
            }
        }
    }

    /**
     * A parameter the goal declares is set if its field is. Maven ignores any other parameter, so it is set if it is a
     * user property or in this plugin's POM configuration.
     */
    private static boolean isSet(Object mojo, String name, Properties userProperties, List<Object> configurations) {
        for (Class<?> type = mojo.getClass(); type != null; type = type.getSuperclass()) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                Object value = field.get(mojo);
                return value != null && !"".equals(value);
            } catch (NoSuchFieldException e) {
                // declared on a superclass, or not at all
            } catch (IllegalAccessException e) {
                throw new IllegalStateException(e);
            }
        }
        return userProperties.containsKey(name)
                || configurations.stream().anyMatch(c -> c instanceof Xpp3Dom && ((Xpp3Dom) c).getChild(name) != null);
    }

    /** The plugin's and the running execution's configuration in the POM. */
    private static List<Object> pomConfigurations(MavenProject project, MojoExecution execution) {
        List<Object> configurations = new ArrayList<>();
        Plugin plugin = project.getPlugin(execution.getPlugin().getKey());
        if (plugin != null) {
            configurations.add(plugin.getConfiguration());
            PluginExecution pluginExecution = plugin.getExecutionsAsMap().get(execution.getExecutionId());
            if (pluginExecution != null) {
                configurations.add(pluginExecution.getConfiguration());
            }
        }
        return configurations;
    }

    private static String wrongGoal(String name, String ownGoal, String goal, String lockfileName) {
        String hint = "";
        if (ownGoal.equals("validate") && goal.equals("generate")) {
            hint = String.format(
                    " To change the value recorded in the lockfile, edit '%s' under 'metaData.config' in %s.",
                    name, lockfileName);
        } else if (ownGoal.equals("generate") && goal.equals("validate")) {
            hint = " 'validate' should use the value recorded in the lockfile.";
        }
        return String.format(
                "Parameter '%s' belongs to the '%s' goal. Passing it to '%s' is deprecated and will be an error in"
                        + " 6.0.0.%s See %s",
                name, ownGoal, goal, hint, GOAL_PARAMETERS_ISSUE);
    }

    /**
     * Warns if the lockfile that {@code mojo} reads or writes has {@code lockFileVersion} 1, and about each extension
     * flag in {@code config} that differs from its plugin flag. {@code config} is {@code null} for {@code freeze}.
     */
    static void warnAboutLockfile(
            AbstractMojo mojo, MojoExecution execution, String lockfileName, int lockfileVersion, Config config) {
        if (lockfileVersion == 1) {
            mojo.getLog()
                    .warn(lockfileVersion1(lockfileName, execution.getPlugin().getVersion()));
        }
        if (config != null) {
            extensionFlagMismatches(config, lockfileName).forEach(mojo.getLog()::warn);
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

    /** 6.0.0 applies the plugin flags to build extensions, so an extension flag that differs changes behaviour. */
    private static List<String> extensionFlagMismatches(Config config, String lockfileName) {
        List<String> warnings = new ArrayList<>();
        if (config.isIncludeMavenExtensions() != config.isIncludeMavenPlugins()) {
            warnings.add(mismatch(
                    "includeMavenExtensions", "includeMavenPlugins", config.isIncludeMavenPlugins(), lockfileName));
        }
        if (config.isAllowMavenExtensionsValidationFailure() != config.isAllowMavenPluginValidationFailure()) {
            warnings.add(mismatch(
                    "allowMavenExtensionsValidationFailure",
                    "allowMavenPluginValidationFailure",
                    config.isAllowMavenPluginValidationFailure(),
                    lockfileName));
        }
        return warnings;
    }

    private static String mismatch(String extensionFlag, String pluginFlag, boolean pluginValue, String lockfileName) {
        return String.format(
                "'%1$s' differs from '%2$s'. 6.0.0 removes '%1$s' and applies '%2$s' to build extensions too."
                        + " To switch now, set '%1$s' to %3$s under 'metaData.config' in %4$s and run 'generate'."
                        + " See %5$s",
                extensionFlag, pluginFlag, pluginValue, lockfileName, EXTENSIONS_ISSUE);
    }
}
