package io.github.chains_project.maven_lockfile;

import io.quarkiverse.githubaction.Action;
import io.quarkiverse.githubaction.Commands;
import io.quarkiverse.githubaction.Context;
import io.quarkiverse.githubaction.Inputs;
import java.util.ArrayList;
import java.util.List;
import jakarta.enterprise.context.ApplicationScoped;
import org.buildobjects.process.ProcBuilder;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class GithubAction {

    // Injects the version of the extension from the pom.xml
    // this field is intentionally set to package private instead of private to reduce reflection usage
    @ConfigProperty(name = "quarkus.application.version")
    String version;

    private static final String COMMAND_GENERATE = "io.github.chains-project:maven-lockfile:%s:generate";
    private static final String COMMAND_VALIDATE = "io.github.chains-project:maven-lockfile:%s:validate";

    @Action
    void run(Inputs inputs, Commands commands, Context context) {

        boolean includeMavenPlugins = inputs.getBoolean("include-maven-plugins").orElse(false);
        if (Boolean.parseBoolean(System.getenv("POM_CHANGED"))) {
            commands.group("maven-lockfile");
            commands.notice("Pom file changed, running lockfile generation");
            commands.endGroup();
            generateLockFile(commands, includeMavenPlugins);
        } else {
            commands.group("maven-lockfile");
            commands.notice("Pom file not changed, running lockfile validation");
            commands.endGroup();
            validateLockFile(commands, includeMavenPlugins);
        }
    }

    void generateLockFile(Commands commands, boolean includeMavenPlugins) {
        commands.group("maven-lockfile");
        try {
            List<String> arguments = new ArrayList<>();
            arguments.add(String.format(COMMAND_GENERATE, version));
            if (includeMavenPlugins) {
                arguments.add("-DincludeMavenPlugins=true");
            }
            arguments.add("-q");
            var result = new ProcBuilder("mvn")
                    .withOutputStream(System.out)
                    .withErrorStream(System.err)
                    .withNoTimeout()
                    .withArgs(arguments.toArray(new String[0]))
                    .run();
            if (result.getExitValue() != 0) {
                commands.error("Lockfile generation failed\n");
                commands.notice(result.getOutputString());
                commands.notice(result.getErrorString());
                commands.endGroup();
                System.exit(1);
            }
            commands.jobSummary("# Maven Lockfile");
            commands.appendJobSummary("✅**Success** Lockfile generation succeeded");
        } catch (Exception e) {
            commands.error("Lockfile generation failed\n" + e.getMessage());
            commands.jobSummary("# Maven Lockfile");
            commands.appendJobSummary("⚠️**Warning** Lockfile generation failed");
            commands.appendJobSummary("The error message is:\n " + e.getMessage());
            commands.endGroup();
            System.exit(1);
        }
        commands.notice("Lockfile generated");
        commands.endGroup();
    }

    void validateLockFile(Commands commands, boolean includeMavenPlugins) {
        commands.group("maven-lockfile-validation");
        try {
            List<String> arguments = new ArrayList<>();
            arguments.add(String.format(COMMAND_VALIDATE, version));
            if (includeMavenPlugins) {
                arguments.add("-DincludeMavenPlugins=true");
            }
            arguments.add("-q");
            if (new ProcBuilder("mvn")
                            .withNoTimeout()
                            .withArgs(arguments.toArray(new String[0]))
                            .withOutputStream(System.out)
                            .withErrorStream(System.err)
                            .run()
                            .getExitValue()
                    != 0) {
                commands.error("Integrity check failed\n");
                commands.endGroup();
                System.exit(1);
            }
        } catch (Exception e) {
            commands.error("Integrity check failed\n." + e.getMessage());
            commands.jobSummary("# Maven Lockfile");
            commands.appendJobSummary("⚠️**Warning** Integrity check failed");
            commands.appendJobSummary(String.format(
                    "The lockfile is not up to date with the pom file. Please run %s to update the lockfile. For your convenience, you can also download the generated lockfile from the artifacts of this check run.",
                    String.format(COMMAND_GENERATE, version)));
            commands.endGroup();
            System.exit(1);
        }
        commands.jobSummary("# Maven Lockfile");
        commands.appendJobSummary("✅**Success** Integrity check passed");
        commands.appendJobSummary("The lockfile is up to date with the pom files.");
        commands.notice("Integrity check passed");
        commands.endGroup();
    }
}
