
# Maven Lockfile

[![SemVersion](https://img.shields.io/badge/semver-2.0.0-blue)](https://img.shields.io/badge/semver-2.0.0-blue)
[![Maven Central](https://img.shields.io/maven-central/v/io.github.chains-project/maven-lockfile.svg)](https://search.maven.org/#search%7Cga%7C1%7Cg%3A%22io.github.chains-project%22%20AND%20a%3A%22maven-lockfile%22)
[![Lockfile](https://github.com/chains-project/maven-lockfile/actions/workflows/Lockfile.yml/badge.svg)](https://github.com/chains-project/maven-lockfile/actions/workflows/Lockfile.yml)
[![CII Best Practices](https://bestpractices.coreinfrastructure.org/projects/7447/badge)](https://bestpractices.coreinfrastructure.org/projects/7447)
[![Reproducible Builds](https://img.shields.io/endpoint?url=https%3A%2F%2Fraw.githubusercontent.com%2Fjvm-repo-rebuild%2Freproducible-central%2Fmaster%2Fcontent%2Fio%2Fgithub%2Fchains-project%2Fmaven-lockfile%2Fbadge.json)](https://github.com/jvm-repo-rebuild/reproducible-central/blob/master/content/io/github/chains-project/maven-lockfile/README.md)

![Mavenlockfile Banner](https://github.com/user-attachments/assets/87b2e254-1c16-4995-8f4a-f80da93bfbc7)

This plugin is a state-of-the-art solution for validating the integrity of a maven build and guarding the build against malicious actors that might tamper with the artifacts. Features:
* generating a lock file that contains the checksums of all the artifacts and dependencies.
* validate the integrity of a build environment prior to building.
* rebuild old versions with the pinned versions from the lockfile 

Reference: [Maven-Lockfile: High Integrity Rebuild of Past Java Releases](https://arxiv.org/abs/2510.00730), Technical report 2510.00730, arXiv, 2025.

<details>
<summary>
  <b>Video Demo</b>
</summary>

Video Demo available in full quality on [YouTube](https://youtu.be/eGgR3toBgxU) or compressed below:

https://github.com/user-attachments/assets/4fac8229-d80b-4832-93c1-8cc8bf83e72b
</details>

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


## Command line Flags

- `reduced` (`-Dreduced=false`) will reduce the lockfile only containing the dependencies after dependency resolution conflicts are resolved. This format is smaller, and easier to review and read. Only use this if you do not need the full dependency tree.
- `includeMavenPlugins` (`-DincludeMavenPlugins=true`) will include the maven plugins in the lockfile. This is useful if you want to validate the Maven plugins as well.
- `allowValidationFailure` (`-DallowValidationFailure=true`, default=false) allow validation failures, printing a warning instead of an error. This is useful if you want to only validate the Maven lockfile, but do not need to fail the build in case the lockfile is not valid. Use with caution, you loose all guarantees.
- `allowPomValidationFailure` (`-DallowPomValidationFailure=true`, default=false) allow validation failure of the pom specifically, dependency validation still occurs (assuming `allowValidationFailure` is `false`). In case of checksum mismatch of pom prints a warning instead of default exception.
- `allowEnvironmentalValidationFailure` (`-DallowEnvironmentalValidationFailure=true`, default=false) allow validation failure of the environment. In case of environment mismatch prints a warning instead of default exception.
- `includeEnvironment` (`-DincludeEnvironment=true`) will include the environment metadata in the lockfile. This is useful if you want to have warnings when the environment changes.
- `checksumAlgorithm` (`-DchecksumAlgorithm=SHA-256`) will set the checksum algorithm used to generate the lockfile. If not explicitly provided it will use SHA-256.
- `checksumMode` will set the checksum mode used to generate the lockfile. See [Checksum Modes](/maven_plugin/src/main/java/io/github/chains_project/maven_lockfile/checksum/ChecksumModes.java) for more information.
- `skip` (`-Dskip=true`) will skip the execution of the plugin. This is useful if you would like to disable the plugin for a specific module.
- `lockfileName` (`-DlockfileName=my-lockfile.json` default="lockfile.json") will set the name of the lockfile file to be generated/read.
- `getConfigFromFile` will read the configuration of maven lockfile from the existing lockfile.

For `:freeze` target:
- `pomLockfileOutput` (`-DpomLockfileOutput=pom.xml`, default=pom.lockfile.xml) sets the name of the generated flattened pom file. Default is to create a new file with the name `pom.lockfile.xml`, but you can also set it to `pom.xml` to overwrite the original pom file.
- `exactVersionStrings` (`-DexactVersionStrings=false`, default=true) provide version string as exact parameter `[1.0.0]`, instead of soft requirement `1.0.0`.

### Flags example

The flags are passed by the maven [`-D` (`--define`)](https://books.sonatype.com/mvnref-book/reference/running-sect-options.html) property. For example, to set the `lockfileName` to `my-lockfile.json` and include maven plugins in the lockfile, you would run the following command:
```bash
mvn io.github.chains-project:maven-lockfile:generate -DincludeMavenPlugins=true -DlockfileName=my-lockfile.json
```

## Format

An example lockfile is shown below:
For a full example, see the [lockfile.json](/maven_plugin/lockfile.json) file in this repository.
```json
{
  "artifactId": "single-dependency",
  "groupId": "com.mycompany.app",
  "version": "1",
  "pom": {
    "path": "pom.xml",
    "checksumAlgorithm": "SHA-256",
    "checksum": "769c9ca78c22cd41728e76bf04377d1362da16063bd225c8ba8593ee28382507"
  },
  "lockFileVersion": 1,
  "dependencies": [
    {
      "groupId": "org.junit.jupiter",
      "artifactId": "junit-jupiter-api",
      "version": "5.9.2",
      "checksumAlgorithm": "SHA-256",
      "checksum": "f767a170f97127b0ad3582bf3358eabbbbe981d9f96411853e629d9276926fd5",
      "scope": "test",
      "resolved": "https://repo.maven.apache.org/maven2/org/junit/jupiter/junit-jupiter-api/5.9.2/junit-jupiter-api-5.9.2.jar",
      "repositoryId": "central",
      "selectedVersion": "5.9.2",
      "included": true,
      "id": "org.junit.jupiter:junit-jupiter-api:5.9.2",
      "children": [
        {
          "groupId": "org.apiguardian",
          "artifactId": "apiguardian-api",
          "version": "1.1.2",
          "checksumAlgorithm": "SHA-256",
          "checksum": "b509448ac506d607319f182537f0b35d71007582ec741832a1f111e5b5b70b38",
          "scope": "test",
          "resolved": "https://repo.maven.apache.org/maven2/org/apiguardian/apiguardian-api/1.1.2/apiguardian-api-1.1.2.jar",
          "repositoryId": "central",
          "selectedVersion": "1.1.2",
          "included": true,
          "id": "org.apiguardian:apiguardian-api:1.1.2",
          "parent": "org.junit.jupiter:junit-jupiter-api:5.9.2",
          "children": []
        },
        {
          "groupId": "org.junit.platform",
          "artifactId": "junit-platform-commons",
          "version": "1.9.2",
          "checksumAlgorithm": "SHA-256",
          "checksum": "624a3d745ef1d28e955a6a67af8edba0fdfc5c9bad680a73f67a70bb950a683d",
          "scope": "test",
          "resolved": "https://repo.maven.apache.org/maven2/org/junit/platform/junit-platform-commons/1.9.2/junit-platform-commons-1.9.2.jar",
          "repositoryId": "central",
          "selectedVersion": "1.9.2",
          "included": true,
          "id": "org.junit.platform:junit-platform-commons:1.9.2",
          "parent": "org.junit.jupiter:junit-jupiter-api:5.9.2",
          "children": [
            {
              "groupId": "org.apiguardian",
              "artifactId": "apiguardian-api",
              "version": "1.1.2",
              "checksumAlgorithm": "SHA-256",
              "checksum": "b509448ac506d607319f182537f0b35d71007582ec741832a1f111e5b5b70b38",
              "scope": "test",
              "resolved": "https://repo.maven.apache.org/maven2/org/apiguardian/apiguardian-api/1.1.2/apiguardian-api-1.1.2.jar",
              "repositoryId": "central",
              "selectedVersion": "1.1.2",
              "included": false,
              "id": "org.apiguardian:apiguardian-api:1.1.2",
              "parent": "org.junit.platform:junit-platform-commons:1.9.2",
              "children": []
            }
          ]
        },
        {
          "groupId": "org.opentest4j",
          "artifactId": "opentest4j",
          "version": "1.2.0",
          "checksumAlgorithm": "SHA-256",
          "checksum": "58812de60898d976fb81ef3b62da05c6604c18fd4a249f5044282479fc286af2",
          "scope": "test",
          "resolved": "https://repo.maven.apache.org/maven2/org/opentest4j/opentest4j/1.2.0/opentest4j-1.2.0.jar",
          "repositoryId": "central",
          "selectedVersion": "1.2.0",
          "included": true,
          "id": "org.opentest4j:opentest4j:1.2.0",
          "parent": "org.junit.jupiter:junit-jupiter-api:5.9.2",
          "children": []
        }
      ]
    }
  ],
  "mavenPlugins": [],
  "metaData": {
    "environment": {
      "osName": "Mac OS X",
      "mavenVersion": "3.9.11",
      "javaVersion": "21.0.8"
    },
    "config": {
      "includeMavenPlugins": false,
      "allowValidationFailure": false,
      "allowPomValidationFailure": false,
      "allowEnvironmentalValidationFailure": false,
      "includeEnvironment": true,
      "reduced": false,
      "mavenLockfileVersion": "5.9.1-SNAPSHOT",
      "checksumMode": "local",
      "checksumAlgorithm": "SHA-256"
    }
  }
}
```
This is close to the format of the lock file in the npm package-lock.json file.
We made some java-specific changes to the format, e.g., we added the `groupId` field.

In case the artifact url cannot be resolved or the checksum cannot be calculated or downloaded (depending on `checksumMode`) an empty string will be recorded in the respective `resolved` or `checksum` field.

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
          uses: chains-project/maven-lockfile@dbd9538eaf1bc297225b74f5e891af7d2faf61a1 # v5.5.2
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

Extended github actions example with all available options:

```yml
- uses: chains-project/maven-lockfile@dbd9538eaf1bc297225b74f5e891af7d2faf61a1 # v5.5.2
  with:
    # Required. The GitHub token used to commit the updated lockfile to the repository.
    - github-token: ${{ secrets.JRELEASER_GITHUB_TOKEN }}

    # Optional. Whether to commit an updated lockfile to the repository. The action can be used 
    #  to update lockfiles automatically in e.g. pull requests (se warning about pull-requests 
    #  from forks). If this is true and the pom.xml or workflow-file has updated it will create 
    #  and commit the new lockfile - the action **will not** fail if the lockfile is outdated 
    #  or invalid and only push the correct version. If this is false or the pom.xml and 
    #  workflow-file remain unchanged, the action be used to verify the lockfile is correct - 
    #  the action **will** fail in case of an outdated or invalid lockfile.
    # Defaults to true.
    - commit-lockfile: true

    # Optional. The commit message for the lockfile if 'commit-lockfile' is true.
    # Defaults to 'chore: update lockfile'
    - commit-message: 'chore: update lockfile'

    # Optional. Wether to include Maven plugins in the lockfile.
    # Defaults to false.
    - include-maven-plugins: false

    # Optional. The name of the lockfile to generate/validate.
    # Defaults to 'lockfile.json'.
    - lockfile-name: 'lockfile.json'

    # Optional. The name of the workflow file, to automatically trigger lockfile generation with 
    #  the workflow is updated.
    # Defaults to 'Lockfile.yml'
    - workflow-filename: 'Lockfile.yml'
```

### Using Action in Release with `-SNAPSHOT`-versions (synchronizing lockfile with release)

If you are updating your POM.xml during your release (e.g. by using `mvn version:set`) to remove `-SNAPSHOT` suffixes or increase the version during the release process the lockfile will need to be regenerated as well or the commit tagged with the release will contain a lockfile with the wrong version. 
If you are setting the `-SNAPSHOT` version in the release action/script as well it is a good idea to update the lockfile to avoid a separate `chore: lockfile` commit. 

As an example, the steps for the CI in maven-lockfile is:
* set the version from `X.Y.Z-SNAPSHOT` to `X.Y.Z` in `pom.xml`
* run maven-lockfile using `mvn io.github.chains-project:maven-lockfile:5.4.1:generate`
* build and release
* create `Releasing version X.Y.Z` commit and tag it with `vX.Y.Z`
* set the version to `X.Y.(Z+1)-SNAPSHOT` in `pom.xml`
* run maven-lockfile using `mvn io.github.chains-project:maven-lockfile:5.4.1:generate`
* create `Setting SNAPSHOT version X.Y.(Z+1)-SNAPSHOT` commit

## Related work

Here we list some related work that we found while researching this topic.

- Maven: https://github.com/vandmo/dependency-lock-maven-plugin
- Gradle: For Gradle, there exists a built-in solution: https://docs.gradle.org/current/userguide/dependency_locking.html. This solution only works for Gradle builds and is deeply connected to the Gradle build system. The Gradle ecosystem is fast changing and so is its dependency resolution. Our lockfile is independent of the build system and can be used to validate the integrity of a maven repository.
- NPM: https://docs.npmjs.com/cli/v9/configuring-npm/package-lock-json
- Ruby: Bundler has built-in checksum verification since 2.6, see [doc](https://mensfeld.pl/2025/01/the-silent-guardian-why-bundler-checksums-are-a-game-changer-for-your-applications/)
