package se.kth;

/*
 * Copyright 2001-2005 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.eclipse.aether.artifact.Artifact;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.repository.LocalRepositoryManager;

import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Goal which pins the dependencies of a project to a specific version.
 *
 * @goal generate a lock file
 *
 * @phase compile
 */
@Mojo(name = "generate", defaultPhase = LifecyclePhase.GENERATE_RESOURCES)
public class GenerateLockFileMojo
        extends AbstractMojo
{
    /**
     * The Maven project.
     * @parameter defaultvalue = ${project}
     * @readonly
     * @required
     */
    @Parameter( defaultValue = "${project}", readonly = true, required = true )
    private MavenProject project;

    /**
     * The current repository session, used for accessing the local artifact files, among other things
     */
    @Parameter(defaultValue = "${repositorySystemSession}")
    private RepositorySystemSession repoSession;

    private final Gson gson;
    private final LockFile lockFile;
    private final String checksumAlgorithm;


    public GenerateLockFileMojo() throws NoSuchAlgorithmException {
        gson = new GsonBuilder().setPrettyPrinting().create();
        lockFile = new LockFile();
        checksumAlgorithm = "SHA-256";
    }

    /**
     * Returns the local file that an artifact has been resolved to
     * @param artifact the artifact to be resolved
     * @return the file constituting the local artifact
     */
    private Path getLocalArtifactPath(Artifact artifact) {
        LocalRepositoryManager repoManager = repoSession.getLocalRepositoryManager();
        String pathStringRelativeToBaseDirectory = repoManager.getPathForLocalArtifact(artifact);
        File localRepositoryBaseDirectory = repoManager.getRepository().getBasedir();
        File artifactFile = new File(localRepositoryBaseDirectory, pathStringRelativeToBaseDirectory);
        return Path.of(artifactFile.getAbsolutePath());
    }

    public void execute()
            throws MojoExecutionException
    {
        // Get all the artifacts for the dependencies in the project
        Set<org.apache.maven.artifact.Artifact> dependencyArtifacts = project.getDependencyArtifacts();

        for (var a : dependencyArtifacts) {
            String groupId = a.getGroupId();
            String artifactId = a.getArtifactId();
            String version = a.getVersion();
            String coords = groupId + ":" + artifactId + ":" + version;
            Artifact artifact = new DefaultArtifact(coords);
            Path path = getLocalArtifactPath(artifact);
            String checksum;
            try {
                checksum = Utilities.calculateChecksum(path, checksumAlgorithm);
            } catch (IOException | NoSuchAlgorithmException e) {
                getLog().error(e);
                return;
            }

            lockFile.dependencies.add(new LockFileDependency(groupId, artifactId, version, checksumAlgorithm, checksum));
        }

        String json = gson.toJson(lockFile);

        try {
            Path lockFilePath = Utilities.getLockFilePath(project);
            Files.writeString(lockFilePath, json);
            getLog().info("Lockfile written to " + lockFilePath);
        } catch (IOException e) {
            getLog().error(e);
            return;
        }
    }
}
