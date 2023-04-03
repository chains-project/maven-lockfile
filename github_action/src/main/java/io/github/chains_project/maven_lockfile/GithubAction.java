package io.github.chains_project.maven_lockfile;

import io.quarkiverse.githubaction.Action;
import io.quarkiverse.githubaction.Commands;
import io.quarkiverse.githubaction.Context;
import io.quarkiverse.githubaction.Inputs;
import org.buildobjects.process.ProcBuilder;
import org.kohsuke.github.GitHub;

public class GithubAction {

    private static final String COMMAND_GENERATE = "io.github.chains-project:maven-lockfile:1.0.9-SNAPSHOT-SNAPSHOT:generate";
    private static final String COMMAND_VALIDATE = "io.github.chains-project:maven-lockfile:1.0.9-SNAPSHOT-SNAPSHOT:validate";


    @Action("generate")
    void runLockFile(Inputs inputs, Commands commands, Context context, GitHub gitHub) {
        new ProcBuilder("mvn").withNoTimeout().withArg(COMMAND_GENERATE).run();
    }

    @Action("validate")
    void validateLockFile(Inputs inputs, Commands commands, Context context, GitHub gitHub) {
        validateLockFile(commands);
    }

    private void validateLockFile(Commands commands) {
        try {
            if (new ProcBuilder("mvn")
                            .withNoTimeout()
                            .withArg(COMMAND_VALIDATE)
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
    }
}
