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
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.eclipse.aether.RepositorySystemSession;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.NoSuchAlgorithmException;

import static se.kth.Utilities.generateLockFileFromProject;

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

    public void execute()
            throws MojoExecutionException
    {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        try {
            LockFile lockFile = generateLockFileFromProject(project, repoSession);
            String json = gson.toJson(lockFile);

            Path lockFilePath = Utilities.getLockFilePath(project);
            Files.writeString(lockFilePath, json);
            getLog().info("Lockfile written to " + lockFilePath);
        } catch (IOException | NoSuchAlgorithmException e) {
            getLog().error(e);
        }
    }
}
