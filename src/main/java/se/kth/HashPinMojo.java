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
import java.util.Set;

/**
 * Goal which pins the dependencies of a project to a specific version.
 *
 * @goal pin dependencies to hash values
 * 
 * @phase compile
 */
@Mojo(name = "validate", defaultPhase = LifecyclePhase.VALIDATE)
public class HashPinMojo
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

    /**
     * Returns the local file that an artifact has been resolved to
     * @param artifact the artifact to be resolved
     * @return the file constituting the local artifactt
     */
    private Path getLocalArtifactPath(Artifact artifact) {
        LocalRepositoryManager repoManager = repoSession.getLocalRepositoryManager();
        String pathStringRelativeToBaseDirectory = repoManager.getPathForLocalArtifact(artifact);
        File localRepositoryBaseDirectory = repoManager.getRepository().getBasedir();
        File artifactFile = new File(localRepositoryBaseDirectory, pathStringRelativeToBaseDirectory);
        return Path.of(artifactFile.getAbsolutePath());
    }

    private boolean check(Path artifactPath, String expectedMD5Checksum) {
        try {
            // TODO: maybe use https://maven.apache.org/resolver/apidocs/org/eclipse/aether/spi/checksums/package-summary.html
            // instead? Should be equivalent, but maybe best to stay inside the Maven ecosystem if possible
            byte[] fileBuffer = Files.readAllBytes(artifactPath);
            byte[] artifactHash = MessageDigest.getInstance("MD5").digest(fileBuffer);
            String checksum = new BigInteger(1, artifactHash).toString(16);

            return expectedMD5Checksum.equals(checksum);
        } catch (NoSuchAlgorithmException | IOException e) {
            getLog().error(e);
        }
        return false;
    }

    public void execute()
        throws MojoExecutionException
    {
        // Get all the artifacts for the dependencies in the project
        Set<org.apache.maven.artifact.Artifact> dependencyArtifacts = project.getDependencyArtifacts();

        for (var a : dependencyArtifacts) {
            String coords = a.getGroupId() + ":" + a.getArtifactId() + ":" + a.getVersion();
            Artifact artifact = new DefaultArtifact(coords);
            Path path = getLocalArtifactPath(artifact);

            // TODO: get checksums from pom.xml instead of hardcoded example
            if (check(path, "ee3c981f43a1d5b1578d146a935010f6")) {
                getLog().info("Artifact " + artifact.getArtifactId() + " matches expected checksum of ee3c981f43a1d5b1578d146a935010f6");
            } else {
                getLog().info("Artifact " + artifact.getArtifactId() + "does not match expected checksum.");
            }
        }

        // getLog().info("Properties: " + project.getProperties());
    }
}
