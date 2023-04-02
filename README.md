
# Maven Lockfile
<p align="left">
	<a href="https://img.shields.io/badge/semver-2.0.0-blue" alt=SemVersion">
		<img src="https://img.shields.io/badge/semver-2.0.0-blue" /></a>
	<a href="https://maven-badges.herokuapp.com/maven-central/io.github.chains-project/maven-lockfile/badge.png?gav=true" alt=Maven-Central">
		<img src="https://maven-badges.herokuapp.com/maven-central/io.github.chains-project/maven-lockfile/badge.png?gav=true" /></a>
</p>


This plugin is a state-of-the-art solution that can be used to validate the integrity of a maven repository. It does this by generating a lock file that contains the checksums of all the artifacts in the repository. The lock file can then be used to validate the integrity of the repository.
This guards the supply chain against malicious actors that might tamper with the artifacts in the repository.

## Installation:

This plugin is available on maven central. To use it, add the following to your pom.xml:
See https://search.maven.org/artifact/io.github.chains-project/maven-lockfile for the latest version.
All versions below 1.0.0 are considered unstable and should not be used in production.
We will release a stable version once we have implemented all the features we want to have.


## Usage
First, generate a lock file by running the following command in the repository that you want to validate:

```
mvn io.github.chains-project:maven-lockfile:${CurrentVersion}:generate
```

Then run the following command to validate the repository:

```
mvn io.github.chains-project:maven-lockfile:${CurrentVersion}:validate
```

## Format

An example lockfile is shown below:

```json
{
"artifactID": "my-app",
"groupID": "com.mycompany.app",
"version": "1",
"lockFileVersion": 1,
"dependencies": [
			{
			"groupId": "org.junit.platform",
			"artifactId": "junit-platform-engine",
			"version": "1.9.2",
			"checksumAlgorithm": "SHA-256",
			"checksum": "25f23dc535a091e9dc80c008faf29dcb92be902e6911f77a736fbaf019908367",
			"id": "org.junit.platform:junit-platform-engine:1.9.2",
			"parent": "org.junit.jupiter:junit-jupiter-engine:5.9.2",
			"children": [
				{
				"groupId": "org.apiguardian",
				"artifactId": "apiguardian-api",
				"version": "1.1.2",
				"checksumAlgorithm": "SHA-256",
				"checksum": "b509448ac506d607319f182537f0b35d71007582ec741832a1f111e5b5b70b38",
				"id": "org.apiguardian:apiguardian-api:1.1.2",
				"parent": "org.junit.platform:junit-platform-engine:1.9.2",
				"children": []
				},
				{
				"groupId": "org.junit.platform",
				"artifactId": "junit-platform-commons",
				"version": "1.9.2",
				"checksumAlgorithm": "SHA-256",
				"checksum": "624a3d745ef1d28e955a6a67af8edba0fdfc5c9bad680a73f67a70bb950a683d",
				"id": "org.junit.platform:junit-platform-commons:1.9.2",
				"parent": "org.junit.platform:junit-platform-engine:1.9.2",
				"children": [
					{
					"groupId": "org.apiguardian",
					"artifactId": "apiguardian-api",
					"version": "1.1.2",
					"checksumAlgorithm": "SHA-256",
					"checksum": "b509448ac506d607319f182537f0b35d71007582ec741832a1f111e5b5b70b38",
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
				"id": "org.opentest4j:opentest4j:1.2.0",
				"parent": "org.junit.platform:junit-platform-engine:1.9.2",
				"children": []
				}
			]
			}
]
}
```
This is close to the format of the lock file in the npm package-lock.json file.
We made some java-specific changes to the format, e.g., we added the groupId field.
For each artifact, we store the hashes of all transitive dependencies in the `children` field.
This allows us to validate the integrity of the transitive dependencies as well.
## GithubAction

We have created a GithubAction that can be used to validate the integrity of a maven repository.
Usage:
```yml
on:
  workflow_dispatch:
  pull_request:


jobs:
  lockfile:
    runs-on: ubuntu-latest
        runs-on: ubuntu-latest
        needs: build-and-publish
        steps:
        - name: run maven-lockfile
          uses: chains-project/maven-lockfile@main
          with:
            github-token: ${{ secrets.GITHUB_TOKEN }}
```


## Related work
Here we list some related work that we found while researching this topic.

- Maven: https://github.com/vandmo/dependency-lock-maven-plugin
## Gradle:
	For Gradle exists a built-in solution: https://docs.gradle.org/current/userguide/dependency_locking.html
	This solution only works for Gradle builds and is deeply connected to the Gradle build system.
	The Gradle ecosystem is fast changing and so is there dependency resolution.
	Our lockfile is independent of the build system and can be used to validate the integrity of a maven repository.


- NPM: https://docs.npmjs.com/cli/v9/configuring-npm/package-lock-json
