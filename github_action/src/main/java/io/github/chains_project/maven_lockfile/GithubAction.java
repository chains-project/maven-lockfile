package io.github.chains_project.maven_lockfile;

import io.quarkiverse.githubaction.Action;
import io.quarkiverse.githubaction.Commands;
import io.quarkiverse.githubaction.Context;
import io.quarkiverse.githubaction.Inputs;
import io.quarkus.logging.Log;
import javax.enterprise.context.ApplicationScoped;
import org.buildobjects.process.ProcBuilder;
import org.kohsuke.github.GitHub;

@ApplicationScoped
public class GithubAction {

    private static final String COMMAND_GENERATE = "io.github.chains-project:maven-lockfile:1.0.13:generate";
    private static final String COMMAND_VALIDATE =
            "io.github.chains-project:maven-lockfile:1.0.13:validate";


    @Action
    void run(Inputs inputs, Commands commands, Context context) {
        if(inputs.getBoolean("pom-changed").orElse(false)) {
            commands.group("maven-lockfile");
            commands.notice("Pom file changed, running lockfile generation");
            commands.endGroup();
            generateLockFile(commands);
        } else {
            commands.group("maven-lockfile");
            commands.notice("Pom file not changed, running lockfile validation");
            commands.endGroup();
            validateLockFile(commands);
        }
    }
    void generateLockFile(Commands commands) {
        commands.group("maven-lockfile");
        commands.notice("Generating lockfile");
        try {
            var result = new ProcBuilder("./mvnw")
                    .withOutputStream(System.out)
                    .withErrorStream(System.err)
                    .withNoTimeout()
                    .withArg(COMMAND_GENERATE)
                    .run();
            if (result.getExitValue() != 0) {
                commands.error("Lockfile generation failed\n");
                commands.notice(result.getOutputString());
                commands.notice(result.getErrorString());
                commands.endGroup();
                System.exit(1);
            }
        } catch (Exception e) {
            commands.error("Lockfile generation failed\n" + e.getMessage());
            commands.endGroup();
            System.exit(1);
        }
        commands.notice("Lockfile generated");
        commands.endGroup();
    }

    void validateLockFile(Commands commands) {
        Log.info("Validating lockfile");
        try {
            if (new ProcBuilder("./mvnw")
                            .withNoTimeout()
                            .withArg(COMMAND_VALIDATE)
                            .withOutputStream(System.out)
                            .withErrorStream(System.err)
                            .run()
                            .getExitValue()
                    != 0) {
                commands.error("Integrity check failed\n");
                System.exit(1);
            }
        } catch (Exception e) {
            commands.error(
                    "Integrity check failed\n Please run `mvn io.github.chains-project:maven-lockfile:0.3.2:generate` and commit the changes."
                            + e.getMessage());
            System.exit(1);
        }
        Log.info("Lockfile validated");
    }
}
