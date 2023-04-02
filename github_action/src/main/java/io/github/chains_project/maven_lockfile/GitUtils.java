package io.github.chains_project.maven_lockfile;

import io.quarkus.logging.Log;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import javax.enterprise.context.ApplicationScoped;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.AbstractTreeIterator;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.filter.PathSuffixFilter;

@ApplicationScoped
public class GitUtils {
    /**
     * Checks if the pom.xml file has changed in the PR.
     * @param repoRoot  The root of the repository.
     * @param baseRef  The base ref of the PR.
     * @param headRef  The head ref of the PR.
     * @return  True if a pom.xml file has changed, false otherwise.
     */
    public boolean isPomChanged(Path repoRoot, String baseRef, String headRef) {
        Log.info("Checking if pom.xml has changed" + baseRef + " " + headRef);
        try (Git git = Git.open(repoRoot.toFile())) {
            AbstractTreeIterator oldTreeParser = prepareTreeParser(git.getRepository(), baseRef);
            AbstractTreeIterator newTreeParser = prepareTreeParser(git.getRepository(), headRef);
            List<DiffEntry> diff = git.diff()
                    .setOldTree(oldTreeParser)
                    .setNewTree(newTreeParser)
                    .setPathFilter(PathSuffixFilter.create("pom.xml"))
                    .call();
            return !diff.isEmpty();
        } catch (Exception e) {
            Log.error("Error while checking if pom.xml has changed", e);
            // error while filtering lets keep all results
            return false;
        }
    }

    private static AbstractTreeIterator prepareTreeParser(Repository repository, String objectId) throws IOException {
        // from the commit we can build the tree which allows us to construct the TreeParser
        try (RevWalk walk = new RevWalk(repository)) {
            RevCommit commit = walk.parseCommit(repository.resolve(objectId));
            RevTree tree = walk.parseTree(commit.getTree().getId());

            CanonicalTreeParser treeParser = new CanonicalTreeParser();
            try (ObjectReader reader = repository.newObjectReader()) {
                treeParser.reset(reader, tree.getId());
            }

            walk.dispose();

            return treeParser;
        }
    }
}
