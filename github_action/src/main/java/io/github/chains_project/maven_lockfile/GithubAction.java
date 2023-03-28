package io.github.chains_project.maven_lockfile;

import io.quarkiverse.githubaction.Action;
import io.quarkiverse.githubaction.Commands;
import io.quarkiverse.githubaction.Context;
import io.quarkiverse.githubaction.Inputs;
import java.io.IOException;
import java.nio.file.Path;
import org.buildobjects.process.ProcBuilder;
import org.eclipse.jgit.api.Git;
import org.kohsuke.github.GitHub;

public class GithubAction {

    private final GitUtils gitUtils;
    private static final String COMMAND_GENERATE = "io.github.chains-project:integrity-maven-plugin:0.3.2:generate";
    private static final String COMMAND_VALIDATE = "io.github.chains-project:integrity-maven-plugin:0.3.2:generate";

    public GithubAction(GitUtils gitUtils) {
        this.gitUtils = gitUtils;
    }

    @Action
    void runLockFile(Inputs inputs, Commands commands, Context context, GitHub gitHub) throws IOException {
        String baseRef = context.getGitHubBaseRef();
        String headRef = context.getGitHubHeadRef();
        Path repo = Path.of(context.getGitHubWorkspace());
        try (Git git = Git.open(repo.toFile())) {
            if (gitUtils.isPomChanged(repo, baseRef, headRef)) {
                new ProcBuilder("mvn").withNoTimeout().withArg(COMMAND_GENERATE).run();
            } else {
                validateLockFile(commands);
            }
        } catch (Exception e) {
            commands.error(e.getMessage());
        }
    }

    private void validateLockFile(Commands commands) {
        try {
            new ProcBuilder("mvn").withNoTimeout().withArg(COMMAND_VALIDATE).run();
        } catch (Exception e) {
            commands.error(
                    "Integrity check failed\n Please run `mvn io.github.chains-project:integrity-maven-plugin:0.3.2:generate` and commit the changes."
                            + e.getMessage());
        }
    }
}
