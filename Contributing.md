## How to create a release
mvn -Ppublication deploy -DaltDeploymentRepository=local::file:./target/staging-deploy
jreleaser full-release
Current JSON schema:
```json
{
artifactId : string,
groupId : string,
version : string,
lockFileVersion: number,
dependencies: {
	[dependencyName: Dependency]
	}
},
packagedDependencies: {
	[dependencyName: PackagedDependency]
}
}

type Dependency = {
	artifactId : string,
	groupId : string,
	version : string,
	resolverUrl: string,
	integrity(SHA-256): string,
	scope: string,
	requires: {
		[dependencyName: string]: Dependency
	}
}
type PackagedDependency = {
artifactId : string,
groupId : string,
version : string,
resolverUrl: string,
integrity(SHA-256): string,
}
```
