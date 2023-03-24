package io.github.chains_project.maven_lockfile;

import java.io.IOException;
import java.nio.file.Path;
import javax.inject.Inject;
import org.buildobjects.process.ProcBuilder;
import org.eclipse.jgit.api.Git;
import org.kohsuke.github.GHEventPayload;
import org.kohsuke.github.GitHub;
import io.quarkiverse.githubaction.Action;
import io.quarkiverse.githubaction.Commands;
import io.quarkiverse.githubaction.Context;
import io.quarkiverse.githubaction.Inputs;

public class GithubAction {

  private final GitUtils gitUtils;
  private static final String command = "io.github.chains-project:integrity-maven-plugin:0.0.4-SNAPSHOT:generate";
  public GithubAction(GitUtils gitUtils) {
    this.gitUtils = gitUtils;
  }
  @Action
    void runLockFile(Inputs inputs, Commands commands, Context context, GitHub gitHub)
        throws IOException {
          String baseRef = context.getGitHubBaseRef();
          String headRef = context.getGitHubHeadRef();
          Path repo = Path.of(context.getGitHubWorkspace());
      try (Git git = Git.open(repo.toFile())) {
        if(gitUtils.isPomChanged(repo, baseRef, headRef)) {
          ProcBuilder.run("mvn", command);
          gitHub.getRepository(context.getGitHubRepository());
      } catch (Exception e) {
      }
        }
}
