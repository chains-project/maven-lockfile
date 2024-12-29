
# Maven Lockfile
<p align="left">
    <a href="https://img.shields.io/badge/semver-2.0.0-blue" alt="SemVersion">
        <img src="https://img.shields.io/badge/semver-2.0.0-blue" /></a>
    <a href="https://maven-badges.herokuapp.com/maven-central/io.github.chains-project/maven-lockfile/badge.png?gav=true" alt="Maven-Central">
        <img src="https://maven-badges.herokuapp.com/maven-central/io.github.chains-project/maven-lockfile/badge.png?gav=true" /></a>
    <a href="https://github.com/chains-project/maven-lockfile/actions/workflows/Lockfile.yml" alt="Lockfile">
        <img src="https://github.com/chains-project/maven-lockfile/actions/workflows/Lockfile.yml/badge.svg" /></a>
    <a href="https://bestpractices.coreinfrastructure.org/projects/7447"><img src="https://bestpractices.coreinfrastructure.org/projects/7447/badge"></a>
    <a href="https://github.com/jvm-repo-rebuild/reproducible-central/blob/master/content/io/github/chains-project/maven-lockfile/README.md" alt="Reproducible Builds">
        <img src="https://img.shields.io/endpoint?url=https%3A%2F%2Fraw.githubusercontent.com%2Fjvm-repo-rebuild%2Freproducible-central%2Fmaster%2Fcontent%2Fio%2Fgithub%2Fchains-project%2Fmaven-lockfile%2Fbadge.json" /></a>
</p>

![My new creation-min](https://user-images.githubusercontent.com/25300639/229370974-7071d818-e094-4959-8b2f-e2050368ee1c.png)

This plugin is a state-of-the-art solution that validates the integrity of a maven build.
It does this by generating a lock file that contains the checksums of all the artifacts in the repository.
The lock file can then be used to validate the integrity prior to building.
This guards the supply chain against malicious actors that might tamper with the artifacts in the repository.
We also allow you to rebuild your old versions with the pinned versions from the lockfile with `freeze`.

## Installation:

This plugin is available on maven central. See https://search.maven.org/artifact/io.github.chains-project/maven-lockfile for the latest version.

## Usage

### Generate a lockfile

To generate a lock file, run the following command:

```
mvn io.github.chains-project:maven-lockfile:generate
```
This generates a lockfile.json file in each module of the repository, in readable JSON.
This file contains the checksums of all the artifacts in the repository.
The complete dependency tree, with transitive dependencies, is stored in the lockfile (akin a sbom).
For multi-module projects, there is one lockfile per module.

### Checking the local dependencies against Maven lockfile.

Run the following command to validate the repository:

```
mvn io.github.chains-project:maven-lockfile:validate
```
If this runs successfully, the repository is valid. All dependencies defined are still the same as when the lock file was generated.
If the command fails, this means a dependency has changed.

###  Rebuild old versions with the pinned versions from the lockfile.

First create `pom.lockfile.xml`
```
mvn io.github.chains-project:maven-lockfile:freeze
```
This creates a new pom file with the default name `pom.lockfile.xml`. A custom name can be passed with the flag `pomLockfileOutput`.
In the new pom file, every version of direct dependencies in the original pom will be replaced with the versions from the lockfile. Also, every transitive dependency is added to the pom inside the `dependencyManagement` section with the version and scope from the lockfile.

Then, invoke maven with the -f flag

```
mvn -f pom.lockfile.xml
```


## Flags

- `reduced` will reduce the lockfile only containing the dependencies after dependency resolution conflicts are resolved. This format is smaller, and easier to review and read. Only use this if you do not need the full dependency tree.
- `includeMavenPlugins` will include the maven plugins in the lockfile. This is useful if you want to validate the Maven plugins as well.
- `allowValidationFailure` (default=false) allow validation failures, printing a warning instead of an error. This is useful if you want to only validate the Maven lockfile, but do not need to fail the build in case the lockfile is not valid. Use with caution, you loose all guarantees.
- `includeEnvironment` will include the environment metadata in the lockfile. This is useful if you want to have warnings when the environment changes.
- `checksumAlgorithm` will set the checksum algorithm used to generate the lockfile. The default depends on your checksum mode.
- `checksumMode` will set the checksum mode used to generate the lockfile. See [Checksum Modes](/maven_plugin/src/main/java/io/github/chains_project/maven_lockfile/checksum/ChecksumModes.java) for more information.
- `skip` will skip the execution of the plugin. This is useful if you would like to disable the plugin for a specific module.
- `lockfileName` (default="lockfile.json") will set the name of the lockfile file to be generated/read.
- `getConfigFromFile` will read the configuration of maven lockfile from the existing lockfile.
## Format

An example lockfile is shown below:
For a full example, see the [lockfile.json](/maven_plugin/lockfile.json) file in this repository.
```json
{
   "artifactID":"my-app",
   "groupID":"com.mycompany.app",
   "version":"1",
   "lockFileVersion":1,
   "dependencies":[
      {
         "groupId":"org.junit.platform",
         "artifactId":"junit-platform-engine",
         "version":"1.9.2",
         "checksumAlgorithm":"SHA-256",
         "checksum":"25f23dc535a091e9dc80c008faf29dcb92be902e6911f77a736fbaf019908367",
         "id":"org.junit.platform:junit-platform-engine:1.9.2",
         "parent":"org.junit.jupiter:junit-jupiter-engine:5.9.2",
         "children":[
            {
               "groupId":"org.apiguardian",
               "artifactId":"apiguardian-api",
               "version":"1.1.2",
               "checksumAlgorithm":"SHA-256",
               "checksum":"b509448ac506d607319f182537f0b35d71007582ec741832a1f111e5b5b70b38",
               "id":"org.apiguardian:apiguardian-api:1.1.2",
               "parent":"org.junit.platform:junit-platform-engine:1.9.2",
               "children":[

               ]
            },
            {
               "groupId":"org.junit.platform",
               "artifactId":"junit-platform-commons",
               "version":"1.9.2",
               "checksumAlgorithm":"SHA-256",
               "checksum":"624a3d745ef1d28e955a6a67af8edba0fdfc5c9bad680a73f67a70bb950a683d",
               "id":"org.junit.platform:junit-platform-commons:1.9.2",
               "parent":"org.junit.platform:junit-platform-engine:1.9.2",
               "children":[
                  {
                     "groupId":"org.apiguardian",
                     "artifactId":"apiguardian-api",
                     "version":"1.1.2",
                     "checksumAlgorithm":"SHA-256",
                     "checksum":"b509448ac506d607319f182537f0b35d71007582ec741832a1f111e5b5b70b38",
                     "id":"org.apiguardian:apiguardian-api:1.1.2",
                     "parent":"org.junit.platform:junit-platform-commons:1.9.2",
                     "children":[

                     ]
                  }
               ]
            },
            {
               "groupId":"org.opentest4j",
               "artifactId":"opentest4j",
               "version":"1.2.0",
               "checksumAlgorithm":"SHA-256",
               "checksum":"58812de60898d976fb81ef3b62da05c6604c18fd4a249f5044282479fc286af2",
               "id":"org.opentest4j:opentest4j:1.2.0",
               "parent":"org.junit.platform:junit-platform-engine:1.9.2",
               "children":[

               ]
            }
         ]
      }
   ]
}
```
This is close to the format of the lock file in the npm package-lock.json file.
We made some java-specific changes to the format, e.g., we added the `groupId` field.
For each artifact, we store the hashes of all transitive dependencies in the `children` field.
This allows us to validate the integrity of the transitive dependencies as well.
## GithubAction

We have created a GithubAction that can be used to validate the integrity of your `maven` repository.
A sample workflow is shown below:
Usage:
```yml
name: Lockfile
on:
  pull_request:

permissions:
  contents: read
jobs:
  check-lockfile:
        permissions:
          contents: write
        runs-on: ubuntu-latest
        steps:
        - name: run maven-lockfile
          uses: chains-project/maven-lockfile@bdabb56b82feb242cd543af007b333bd8276e44e # v5.3.5
          with:
            github-token: ${{ secrets.JRELEASER_GITHUB_TOKEN }}
            include-maven-plugins: true
```
If a pom.xml or lockfile.json file is changed, this action will add a commit with the updated lockfile to the pull request.
Otherwise, it will validate the lockfile and fail if the lockfile is incorrect.
A lockfile is incorrect if any dependency has changed since the lockfile was generated. This includes versions and checksums.

⚠️**Warning**: The action result of your lockfile could be platform-dependent. Some artifacts are platform-dependent and the checksums will differ between platforms.

⚠️**Warning**: This action will only retrigger CI if you use a personal access token. If you use the default token, the action will not retrigger CI. See https://github.com/EndBug/add-and-commit#the-commit-from-the-action-is-not-triggering-ci for more information.

⚠️**Warning**: Commiting the changed lockfile does not work for pull requests from forks. See https://github.com/EndBug/add-and-commit#working-with-prs. You can add a personal access token to your repository to resolve this issue.
It still works for pull requests from the same repository. Renovate also works with this action because these PRs are created from the same repository.

### Arguments

- `github-token` (required): The GitHub token used to commit the lockfile to the repository.
- `commit-lockfile` (optional, default=true): Whether to commit an updated lockfile to the repository. The action can be used to update lockfiles automatically in e.g. pull requests (se warning about pull-requests from forks). If this is true and the pom.xml or workflow-file has updated it will create and commit the new lockfile - the action **will not** fail if the lockfile is outdated or invalid and only push the correct version. If this is false or the pom.xml and workflow-file remain unchanged, the action be used to verify the lockfile is correct - the action **will** fail in case of an outdated or invalid lockfile.
- `commit-message` (optional, default='chore: update lockfile'): The commit message for the lockfile if `commit-lockfile` is true.
- `commit-author` (optional, default='github\_actions'): The author for the lockfile commit if `commit-lockfile` is true. GitHub provides three values for this field.
  - github\_actor -> `UserName <UserName@users.noreply.github.com>`
  - user\_info -> `Your Display Name <your-actual@email.com>`
  - github\_actions -> `github-actions <email associated with the github logo>`
- `include-maven-plugins` (optional, default='false'): Whether to include Maven plugins in the lockfile.
- `lockfile-name` (optional, default="lockfile.json"): The name of the lockfile to generate/validate.
- `workflow-filename` (optional, default='Lockfile.yml'): The name of the workflow file, to automatically trigger lockfile generation when the workflow is updated.

## Related work

Here we list some related work that we found while researching this topic.

- Maven: https://github.com/vandmo/dependency-lock-maven-plugin
- Gradle: For Gradle, there exists a built-in solution: https://docs.gradle.org/current/userguide/dependency_locking.html. This solution only works for Gradle builds and is deeply connected to the Gradle build system. The Gradle ecosystem is fast changing and so is its dependency resolution. Our lockfile is independent of the build system and can be used to validate the integrity of a maven repository.
- NPM: https://docs.npmjs.com/cli/v9/configuring-npm/package-lock-json
