# Maven Integrity Plugin

Does work currently!

## Installation:

Stand in this repository and run

```
mvn clean install
```

## Usage

Stand in the repository that you want to validate and run

```
mvn hash-pin:validate
```

## Format

The lockfile consists of an object containing an array of objects that looks like the following:

```
{
"artifactId": "com.google.code.gson",
"groupId": "gson",
"version": "2.10",
"checksumAlgorithm": "SHA-256",
"checksum": "cdd163ce3598a20fc04eee71b140b24f6f2a3b35f0a499dbbdd9852e83fbfaf"
}
```

## TODO

- Make the integrity conformant to https://w3c.github.io/webappsec-subresource-integrity/
- Make the validation fail message more human readable
- Add functionality for modifying lock files, e.g., adding a new dependency without generating the whole file from scratch.

## Related work

- Java
- https://github.com/vandmo/dependency-lock-maven-plugin
- Gradle
-
