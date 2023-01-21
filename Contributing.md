## How to create a release
mvn -Ppublication deploy -DaltDeploymentRepository=local::file:./target/staging-deploy
jreleaser full-release
