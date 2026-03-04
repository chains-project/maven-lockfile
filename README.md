
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

An example lockfile is shown below. Note that large parts of it has been minimzed to `{...}` for readability.
For a full example, see the [lockfile.json](/maven_plugin/lockfile.json) file in this repository.
```json
{
  "artifactId": "single-dependency",
  "groupId": "com.mycompany.app",
  "version": "1",
  "pom": {
    "groupId": "com.mycompany.app",
    "artifactId": "single-dependency",
    "version": "1",
    "relativePath": "pom.xml",
    "checksumAlgorithm": "SHA-256",
    "checksum": "2152cc00c16d72fbf9430e6a95a56e9edf0180a500155490bf33a7349df75a1b"
  },
  "lockFileVersion": 1,
  "dependencies": [
    {
      "groupId": "fr.inria.gforge.spoon",
      "artifactId": "spoon-core",
      "version": "10.3.0",
      "checksumAlgorithm": "SHA-256",
      "checksum": "37a43de039cf9a6701777106e3c5921e7131e5417fa707709abf791d3d8d9174",
      "scope": "compile",
      "resolved": "https://repo.maven.apache.org/maven2/fr/inria/gforge/spoon/spoon-core/10.3.0/spoon-core-10.3.0.jar",
      "repositoryId": "central",
      "selectedVersion": "10.3.0",
      "included": true,
      "id": "fr.inria.gforge.spoon:spoon-core:10.3.0",
      "children": [
        {
          "groupId": "com.fasterxml.jackson.core",
          "artifactId": "jackson-databind",
          "version": "2.14.2",
          "checksumAlgorithm": "SHA-256",
          "checksum": "501d3abce4d18dcc381058ec593c5b94477906bba6efbac14dae40a642f77424",
          "scope": "compile",
          "resolved": "https://repo.maven.apache.org/maven2/com/fasterxml/jackson/core/jackson-databind/2.14.2/jackson-databind-2.14.2.jar",
          "repositoryId": "central",
          "selectedVersion": "2.14.2",
          "included": true,
          "id": "com.fasterxml.jackson.core:jackson-databind:2.14.2",
          "parent": "fr.inria.gforge.spoon:spoon-core:10.3.0",
          "children": [
            {
              "groupId": "com.fasterxml.jackson.core",
              "artifactId": "jackson-annotations",
              "version": "2.14.2",
              "checksumAlgorithm": "SHA-256",
              "checksum": "2c6869d505cf60dc066734b7d50339f975bd3adc635e26a78abb71acb4473c0d",
              "scope": "compile",
              "resolved": "https://repo.maven.apache.org/maven2/com/fasterxml/jackson/core/jackson-annotations/2.14.2/jackson-annotations-2.14.2.jar",
              "repositoryId": "central",
              "selectedVersion": "2.14.2",
              "included": true,
              "id": "com.fasterxml.jackson.core:jackson-annotations:2.14.2",
              "parent": "com.fasterxml.jackson.core:jackson-databind:2.14.2",
              "children": []
            },
            {
              "groupId": "com.fasterxml.jackson.core",
              "artifactId": "jackson-core",
              "version": "2.14.2",
              "checksumAlgorithm": "SHA-256",
              "checksum": "b5d37a77c88277b97e3593c8740925216c06df8e4172bbde058528df04ad3e7a",
              "scope": "compile",
              "resolved": "https://repo.maven.apache.org/maven2/com/fasterxml/jackson/core/jackson-core/2.14.2/jackson-core-2.14.2.jar",
              "repositoryId": "central",
              "selectedVersion": "2.14.2",
              "included": true,
              "id": "com.fasterxml.jackson.core:jackson-core:2.14.2",
              "parent": "com.fasterxml.jackson.core:jackson-databind:2.14.2",
              "children": []
            }
          ]
        },
        {
          "groupId": "com.martiansoftware",
          "artifactId": "jsap",
          "version": "2.1",
          "checksumAlgorithm": "SHA-256",
          "checksum": "331746fa62cfbc3368260c5a2e660936ad11be612308c120a044e120361d474e",
          "scope": "compile",
          "resolved": "https://repo.maven.apache.org/maven2/com/martiansoftware/jsap/2.1/jsap-2.1.jar",
          "repositoryId": "central",
          "selectedVersion": "2.1",
          "included": true,
          "id": "com.martiansoftware:jsap:2.1",
          "parent": "fr.inria.gforge.spoon:spoon-core:10.3.0",
          "children": []
        },
        {...}
      ]
    }
  ],
  "mavenPlugins": [
    {
      "groupId": "org.apache.maven.plugins",
      "artifactId": "maven-clean-plugin",
      "version": "3.2.0",
      "checksumAlgorithm": "SHA-256",
      "checksum": "b657bef2e1eb11e029a70cd688bde6adad29e4e99dacb18516bf651ecca32435",
      "resolved": "https://repo.maven.apache.org/maven2/org/apache/maven/plugins/maven-clean-plugin/3.2.0/maven-clean-plugin-3.2.0.jar",
      "repositoryId": "central",
      "dependencies": [
        {
          "groupId": "org.apache.maven",
          "artifactId": "maven-core",
          "version": "3.2.5",
          "checksumAlgorithm": "SHA-256",
          "checksum": "4f1a0af8997e1daf778b91c5ae9e973f92df699439d909fdec7fc6055c09de12",
          "scope": "provided",
          "resolved": "https://repo.maven.apache.org/maven2/org/apache/maven/maven-core/3.2.5/maven-core-3.2.5.jar",
          "repositoryId": "central",
          "selectedVersion": "3.2.5",
          "included": true,
          "id": "org.apache.maven:maven-core:3.2.5",
          "children": [
            {
              "groupId": "org.apache.maven",
              "artifactId": "maven-aether-provider",
              "version": "3.2.5",
              "checksumAlgorithm": "SHA-256",
              "checksum": "703944b922d5351aad53b842f7dd38439b7213425f13c6c7f034b8b699b7d578",
              "scope": "provided",
              "resolved": "https://repo.maven.apache.org/maven2/org/apache/maven/maven-aether-provider/3.2.5/maven-aether-provider-3.2.5.jar",
              "repositoryId": "central",
              "selectedVersion": "3.2.5",
              "included": true,
              "id": "org.apache.maven:maven-aether-provider:3.2.5",
              "parent": "org.apache.maven:maven-core:3.2.5",
              "children": [
                {...}
              ]
            },
            {...}
          ]
        },
        {...}
      ]
    },
    {...}
  ],
  "metaData": {
    "environment": {
      "osName": "Linux",
      "mavenVersion": "3.9.12",
      "javaVersion": "21.0.8"
    },
    "config": {
      "includeMavenPlugins": true,
      "allowValidationFailure": false,
      "allowPomValidationFailure": false,
      "allowEnvironmentalValidationFailure": false,
      "includeEnvironment": true,
      "reduced": false,
      "mavenLockfileVersion": "5.14.1-beta-1",
      "checksumMode": "remote",
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
          uses: chains-project/maven-lockfile@2d2ed1462246005ae3aafaf2d0bc619f521eadf6 # 5.14.0
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
- uses: chains-project/maven-lockfile@2d2ed1462246005ae3aafaf2d0bc619f521eadf6 # 5.14.0
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
