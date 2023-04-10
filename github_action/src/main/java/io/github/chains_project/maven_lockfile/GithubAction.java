package io.github.chains_project.maven_lockfile;

import io.quarkiverse.githubaction.Action;
import io.quarkiverse.githubaction.Commands;
import io.quarkiverse.githubaction.Context;
import io.quarkiverse.githubaction.Inputs;
import javax.enterprise.context.ApplicationScoped;
import org.buildobjects.process.ProcBuilder;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class GithubAction {

    @ConfigProperty(name = "quarkus.application.version")
    public String version;

    private static final String COMMAND_GENERATE = "io.github.chains-project:maven-lockfile:%s:generate";
    private static final String COMMAND_VALIDATE = "io.github.chains-project:maven-lockfile:%s:validate";

    @Action
    void run(Inputs inputs, Commands commands, Context context) {
        if (Boolean.parseBoolean(System.getenv("POM_CHANGED"))) {
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
            var result = new ProcBuilder("mvn")
                    .withOutputStream(System.out)
                    .withErrorStream(System.err)
                    .withNoTimeout()
                    .withArgs(String.format(COMMAND_GENERATE, version), "-q")
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
        commands.group("maven-lockfile-validation");
        commands.notice("Validating lockfile");
        try {
            if (new ProcBuilder("mvn")
                            .withNoTimeout()
                            .withArgs(String.format(COMMAND_VALIDATE, version), "-q")
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
            commands.endGroup();
            System.exit(1);
        }
        commands.notice("Integrity check passed");
        commands.endGroup();
    }
}
