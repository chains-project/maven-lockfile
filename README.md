# Maven Integrity Plugin

This plugin is a proof of concept for a maven plugin that can be used to validate the integrity of a maven repository. It does this by generating a lock file that contains the checksums of all the artifacts in the repository. The lock file can then be used to validate the integrity of the repository.
This guards the supply chain against malicious actors that might tamper with the artifacts in the repository.

## Installation:

We have not yet published this plugin to maven central, so you will have to build it yourself.:
For now, you can build it by running the following command in the root of the repository:

```
mvn clean install
```

## Usage
First, generate a lock file by running the following command in the repository that you want to validate:

```
mvn integrity-maven-plugin:generate
```

Then run the following command to validate the repository:

```
mvn integrity-maven-plugin:validate
```

## Format

The lockfile consists of an object containing an array of objects that looks like the following:

```json
{
"name": "my-app",
"version": "1",
"lockFileVersion": 1,
"dependencies": [
	{
	"artifactId": "junit-jupiter-api",
	"groupId": "org.junit.jupiter",
	"version": "5.9.2",
	"checksumAlgorithm": "SHA-256",
	"checksum": "f767a170f97127b0ad3582bf3358eabbbbe981d9f96411853e629d9276926fd5",
	"repoUrl": "https://repo.maven.apache.org/maven2",
	"requires": [
		{
		"artifactId": "opentest4j",
		"groupId": "org.opentest4j",
		"version": "1.2.0",
		"checksumAlgorithm": "SHA-256",
		"checksum": "58812de60898d976fb81ef3b62da05c6604c18fd4a249f5044282479fc286af2",
		"repoUrl": "https://repo.maven.apache.org/maven2"
		},
		{
		"artifactId": "junit-platform-commons",
		"groupId": "org.junit.platform",
		"version": "1.9.2",
		"checksumAlgorithm": "SHA-256",
		"checksum": "624a3d745ef1d28e955a6a67af8edba0fdfc5c9bad680a73f67a70bb950a683d",
		"repoUrl": "https://repo.maven.apache.org/maven2"
		},
		{
		"artifactId": "apiguardian-api",
		"groupId": "org.apiguardian",
		"version": "1.1.2",
		"checksumAlgorithm": "SHA-256",
		"checksum": "b509448ac506d607319f182537f0b35d71007582ec741832a1f111e5b5b70b38",
		"repoUrl": "https://repo.maven.apache.org/maven2"
		}
	]
	}
]
}%
```
This is close to the format of the lock file in the npm package-lock.json file.
We made some java-specific changes to the format, e.g., we added the groupId field.
For each artifact, we store the hashes of all transitive dependencies in the `requires` field.
This allows us to validate the integrity of the transitive dependencies as well.
The `repoUrl` field is used to validate that the artifact is downloaded from the correct repository.
Different from JS, all build tools download almost everything from maven-central instead of multiple different repositories.
This means changes in the `repoUrl` field are not as common as in JS.

## TODO

- Make the integrity conformant to https://w3c.github.io/webappsec-subresource-integrity/
- Make the validation failure message more human-readable
- Add functionality for modifying lock files, e.g., adding a new dependency without generating the whole file from scratch.

## Related work
Here we list some related work that we found while researching this topic.
## Maven
- https://github.com/vandmo/dependency-lock-maven-plugin
##  Gradle
- https://docs.gradle.org/current/userguide/dependency_locking.html
## NPM
- https://docs.npmjs.com/cli/v9/configuring-npm/package-lock-json
