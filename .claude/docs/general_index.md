# General Index

## Root

- `action.yml` - Composite GitHub Action that runs the Maven lockfile plugin to generate or validate a lockfile. **Generated from template/action.yml** by the gmavenplus-plugin during the `generate-resources` Maven phase — do not edit directly. Key: `github-token`, `commit-lockfile`, `include-maven-plugins`, `lockfile-name`, `workflow-filename` [CONFIG]
- `jreleaser.yml` - Release and signing configuration for JReleaser [CONFIG]
- `lockfile.json` - The project's own lockfile recording its resolved dependencies and checksums (not primarily an example). Also serves as a dogfooding example of a generated lockfile. [GENERATED]
- `pom.xml` - Parent POM that centralizes build configuration, plugins, and reproducibility settings for the project. Key: `project.build.outputTimestamp`, `sigstore.skip`, `modules`, `cyclonedx-maven-plugin`, `gmavenplus-plugin` [BUILD]
- `qodana.yml` - Qodana static analysis profile for CI [CONFIG]
- `renovate.json` - Dependency automation rules for Renovate bot [CONFIG]
- `security.md` - Security policy and vulnerability reporting instructions [DOCS]

## maven_plugin/

- `lockfile.json` - Project lockfile recording resolved dependencies and checksums [DATA]
- `mvnw` - Unix shell Maven Wrapper that downloads and runs Maven [CLI]
- `mvnw.cmd` - Windows/PowerShell Maven Wrapper that downloads and runs Maven [CLI]
- `pom.xml` - Maven module POM configuring the maven-lockfile plugin build, dependencies, and publishing profile. Key: `artifactId`, `packaging`, `properties`, `dependencies`, `maven-plugin-plugin` [BUILD]

## maven_plugin/.mvn/wrapper/

- `maven-wrapper.properties` - Maven Wrapper configuration specifying the distribution URL [CONFIG]

## maven_plugin/src/main/java/io/github/chains_project/maven_lockfile/

- `AbstractLockfileMojo.java` - Abstract base Mojo centralizing injected Maven context and checksum/config factories. Key: `AbstractLockfileMojo`, `generateMetaInformation`, `getChecksumCalculator`, `getChecksumCalculator`, `getConfig` [SOURCE_CODE]
- `FreezeDependencyMojo.java` - Maven Mojo that reads a lockfile and writes a frozen POM with pinned dependency and plugin versions.. Key: `FreezeDependencyMojo`, `execute`, `readPomFile`, `updateDependencies`, `createDependencyMap` [SOURCE_CODE]
- `GenerateLockFileMojo.java` - Maven Mojo that generates the project's lockfile.json by collecting dependencies and checksums. Key: `GenerateLockFileMojo`, `execute`, `getConfig` [SOURCE_CODE]
- `JsonUtils.java` - Central Gson configuration for (de)serializing lockfile domain value objects to compact JSON. Key: `JsonUtils`, `getGson`, `toJson`, `fromJson` [SOURCE_CODE]
- `LockFileFacade.java` - High-level orchestration to build a LockFile: dependency graph, plugin metadata, POM chain and BOMs.. Key: `LockFileFacade`, `GraphBuildingNodeVisitor`, `getLockFilePath`, `generateLockFileFromProject`, `getAllPlugins` [SOURCE_CODE]
- `ValidateChecksumMojo.java` - Maven Mojo that validates the project's resolved dependencies against an existing lockfile.. Key: `ValidateChecksumMojo`, `execute` [SOURCE_CODE]

## maven_plugin/src/main/java/io/github/chains_project/maven_lockfile/checksum/

- `AbstractChecksumCalculator.java` - Abstract base class defining checksum calculation API and common pom checksum implementation. Key: `AbstractChecksumCalculator`, `getChecksumAlgorithm`, `prewarmArtifactCache`, `calculatePomChecksum` [SOURCE_CODE]
- `ChecksumModes.java` - Enum of supported checksum calculation modes with string parsing and Gson serialization names. Key: `ChecksumModes`, `fromName` [SOURCE_CODE]
- `FileSystemChecksumCalculator.java` - Calculates checksums and resolves repository/URL info for Maven artifacts using local filesystem state. Key: `FileSystemChecksumCalculator`, `createDependency`, `resolveDependency`, `calculateChecksumInternal`, `getResolvedFieldInternal` [SOURCE_CODE]
- `RemoteChecksumCalculator.java` - Checksum resolver that fetches artifact checksums and resolved URLs from remote repositories over HTTP with caching and parallel pre-warm.. Key: `RemoteChecksumCalculator`, `getCacheKey`, `calculateChecksumInternal`, `getResolvedFieldInternal`, `prewarmArtifactCache` [SOURCE_CODE]
- `RepositoryInformation.java` - Value object combining a resolved repository URL and repository id with equality semantics.. Key: `RepositoryInformation`, `Unresolved`, `getResolvedUrl`, `getRepositoryId`, `equals` [SOURCE_CODE]

## maven_plugin/src/main/java/io/github/chains_project/maven_lockfile/data/

- `ArtifactId.java` - Immutable value object wrapping a non-null, non-empty Maven artifactId with comparison and equality semantics. Key: `ArtifactId` [SOURCE_CODE]
- `ArtifactType.java` - Value object representing a Maven artifact's packaging type, treating 'jar' as the implicit default (null).. Key: `ArtifactType`, `of`, `getValue`, `compareTo` [SOURCE_CODE]
- `Classifier.java` - Value object representing an optional Maven artifact classifier with comparison and equality semantics. Key: `Classifier`, `of`, `getValue`, `compareTo` [SOURCE_CODE]
- `Config.java` - Serializable configuration model capturing generation/validation options persisted into the lockfile.. Key: `Config`, `MavenPluginsInclusion`, `OnValidationFailure`, `OnPomValidationFailure`, `OnEnvironmentalValidationFailure` [SOURCE_CODE]
- `Environment.java` - Immutable data holder for environment metadata (OS, Maven and Java) used in generated lockfiles. Key: `Environment`, `Environment`, `getJavaVersion`, `getMavenVersion`, `getOsName` [SOURCE_CODE]
- `GroupId.java` - Immutable value object representing a Maven groupId with ordering and basic validation. Key: `GroupId`, `of`, `compareTo`, `equals`, `hashCode` [SOURCE_CODE]
- `LockFile.java` - Immutable data model for the JSON lockfile (project identity, POM, deps, plugins, metadata, BOMs).. Key: `LockFile`, `readLockFile`, `getDependencies`, `getMavenPlugins`, `getBoms` [SOURCE_CODE]
- `MavenPlugin.java` - Domain model representing a Maven build plugin, with identity, resolved location, checksum and dependency nodes.. Key: `MavenPlugin`, `compareTo`, `getDependencies`, `equals`, `hashCode` [SOURCE_CODE]
- `MavenScope.java` - Enum representing Maven dependency scopes with parsing and defaulting behavior. Key: `MavenScope`, `getValue`, `fromString` [SOURCE_CODE]
- `MetaData.java` - Immutable wrapper holding Environment and Config metadata for lockfile operations. Key: `MetaData`, `getConfig`, `getEnvironment` [DATA]
- `Pom.java` - Immutable model representing a POM with GAV, resolution metadata, checksum and parent chain. Key: `Pom`, `compareTo`, `equals`, `hashCode`, `getResolved` [SOURCE_CODE]
- `RepositoryId.java` - Immutable value object representing a Maven repository identifier (with a None sentinel).. Key: `RepositoryId`, `of`, `None`, `getValue`, `compareTo` [SOURCE_CODE]
- `ResolvedUrl.java` - Typed value object representing the resolved URL where an artifact was downloaded from.. Key: `ResolvedUrl`, `of`, `Unresolved`, `getValue`, `ResolvedUrl` [SOURCE_CODE]
- `VersionNumber.java` - Immutable wrapper for a version string that implements Comparable and basic validation.. Key: `VersionNumber` [SOURCE_CODE]

## maven_plugin/src/main/java/io/github/chains_project/maven_lockfile/graph/

- `DependencyGraph.java` - Builds an ordered DependencyNode graph from Maven's dependency graph and prepares checksums/resolution metadata.. Key: `DependencyGraph`, `getRoots`, `getDependencySet`, `of`, `createDependencyNode` [SOURCE_CODE]
- `DependencyNode.java` - Model of a single dependency node used in the lockfile dependency graph.. Key: `DependencyNode`, `addChild`, `compareTo`, `getComparatorString`, `getChildren` [SOURCE_CODE]
- `NodeId.java` - Immutable value object representing a dependency node identifier (group:artifact:version).. Key: `NodeId` [SOURCE_CODE]

## maven_plugin/src/main/java/io/github/chains_project/maven_lockfile/reporting/

- `LockFileDifference.java` - Represents structured differences between two lockfiles (deps and plugins).. Key: `LockFileDifference`, `diff`, `getMissingDependenciesInFile`, `getMissingDependenciesInProject`, `getMissingPluginsInFile` [SOURCE_CODE]
- `PluginLogManager.java` - Static holder providing Maven's Log to non-Mojo classes with a SystemStreamLog fallback. Key: `PluginLogManager`, `setLog`, `getLog` [SOURCE_CODE]

## maven_plugin/src/main/java/io/github/chains_project/maven_lockfile/resolvers/

- `BomResolver.java` - Resolves BOM (type=pom, scope=import) POMs and attaches Pom chain metadata (including checksums) to projects and dependency nodes.. Key: `BomResolver`, `resolveForProject`, `resolveBomsForDependencies`, `resolveVersionFromPlaceholder`, `resolveBomParents` [SOURCE_CODE]
- `ProjectBuilder.java` - Helper that locates a POM for a given GAV and builds a MavenProject from it (local or remote).. Key: `ProjectBuilder`, `buildFromGav`, `lookForPomFileInLocalRepository`, `resolvePomFile`, `buildProjectFromPomFile` [SOURCE_CODE]

## maven_plugin/src/main/java/io/github/chains_project/maven_lockfile/typeadapters/

- `EmptyListToNullFactory.java` - Gson TypeAdapterFactory that serializes empty lists as JSON null to work around a Gson bug.. Key: `EmptyListToNullFactory`, `INSTANCE`, `create` [SOURCE_CODE]

## maven_plugin/src/main/java/org/apache/maven/shared/dependency/graph/internal/

- `SpyingDependencyNodeUtils.java` - Reflection helper to extract the conflict 'winner' version from VerboseDependencyNode.. Key: `SpyingDependencyNodeUtils`, `getWinnerVersion` [SOURCE_CODE]

## maven_plugin/src/test/java/io/github/chains_project/maven_lockfile/

- `JsonUtilsTest.java` - Unit test ensuring JsonUtils serializes a Set of DependencyNode without producing "null". Key: `JsonUtilsTest`, `set_view_to_json_does_not_return_null` [TEST]

## maven_plugin/src/test/java/io/github/chains_project/maven_lockfile/checksum/

- `FileSystemChecksumCalculatorTest.java` - Unit tests verifying resolved URL construction for SNAPSHOT and non-SNAPSHOT artifacts in local repo layout.. Key: `FileSystemChecksumCalculatorTest`, `snapshotArtifactResolvedUrlUsesBaseVersion`, `nonSnapshotArtifactResolvedUrlUsesVersion`, `versionSuffixArtifactResolvedUrlUsesVersion` [TEST]

## maven_plugin/src/test/java/io/github/chains_project/maven_lockfile/data/

- `ArtifactIdTest.java` - Unit tests for ArtifactId value object. Key: `ArtifactIdTest`, `chainsArtifactID`, `nullNotAllowed`, `emptyNotAllowed` [TEST]
- `GroupIdTest.java` - Unit tests for GroupId value object. Key: `GroupIdTest`, `chainsGroupID`, `nullNotAllowed`, `emptyNotAllowed` [TEST]
- `MavenPluginTest.java` - Unit tests verifying MavenPlugin.compareTo ordering semantics across identity and checksum fields.. Key: `MavenPluginTest`, `compareToReturnsZeroForIdenticalPlugins`, `compareToOrdersByGroupIdFirst`, `compareToFallsBackToArtifactIdWhenGroupIdEqual`, `compareToFallsBackToVersionWhenGroupIdAndArtifactIdEqual` [TEST]
- `MavenScopeTest.java` - Unit tests for MavenScope parsing and default behavior. Key: `MavenScopeTest`, `allScopes`, `fromStringWithNull`, `fromStringWithEmpty` [TEST]
- `VersionNumberTest.java` - Unit tests for VersionNumber value object. Key: `VersionNumberTest`, `simpleVersion`, `snapShotVersion`, `versionWithBuildNumber`, `nullNotAllowed` [TEST]

## maven_plugin/src/test/java/io/github/chains_project/maven_lockfile/graph/

- `LockfileTest.java` - Unit test asserting LockFile equality is order-insensitive and building sample dependency/plugin fixtures.. Key: `LockfileTest`, `shouldLockFilesEqualWhenOrderIsChanged`, `dependencyNodeA`, `dependencyNodeB`, `dependencyNodeAChild1` [TEST]

## maven_plugin/src/test/java/it/

- `IntegrationTestsIT.java` - Integration test suite that runs Maven projects and asserts produced lockfile/pom outcomes. Key: `IntegrationTestsIT`, `simpleProject`, `singleDependency`, `pluginProject`, `freezeJunit` [TEST]

## maven_plugin/src/test/resources-its/it/IntegrationTestsIT/environmentalCheckShouldFail/

- `pom.xml` - Integration-test fixture: project expecting environment validation failure. Key: `pom.xml (environmentalCheckShouldFail)` [TEST]

## maven_plugin/src/test/resources-its/it/IntegrationTestsIT/externalParentPom/

- `pom.xml` - Integration-test fixture: project with external parent POM. Key: `pom.xml (externalParentPom)` [TEST]

## maven_plugin/src/test/resources-its/it/IntegrationTestsIT/freezeWithDepManagement/

- `lockfile.json` - Integration test lockfile fixture for freeze with dependencyManagement. Key: `org.eclipse.sisu:org.eclipse.sisu.plexus:0.9.0.M2`, `pom` [TEST]
- `pom.original.xml` - Original POM for freeze-with-dep-management integration test. Key: `freeze-with-dep-management`, `dependencyManagement`, `maven-lockfile plugin (freeze)` [TEST]
- `pom.xml` - Integration-test fixture: freeze goal with dependencyManagement present. Key: `pom.xml (freezeWithDepManagement)` [TEST]

## maven_plugin/src/test/resources-its/it/IntegrationTestsIT/freezeWithoutDepManagement/

- `pom.lockfile.expected.xml` - Expected frozen POM produced by freeze goal for a project without depManagement. Key: `dependencyManagement`, `maven-lockfile plugin (freeze)` [TEST]
- `pom.original.xml` - Original POM fixture for freeze-without-dep-management integration test. Key: `freeze-without-dep-management`, `exactVersionStrings` [TEST]
- `pom.xml` - Integration-test fixture: freeze goal without dependencyManagement. Key: `pom.xml (freezeWithoutDepManagement)` [TEST]

## maven_plugin/src/test/resources-its/it/IntegrationTestsIT/orderedLockfile/

- `pom.xml` - Integration-test fixture: verifies deterministic ordering in lockfile. Key: `pom.xml (orderedLockfile)` [TEST]

## maven_plugin/src/test/resources-its/it/IntegrationTestsIT/pluginProject/

- `pom.xml` - Integration-test fixture: include maven plugins in lockfile generation. Key: `pom.xml (pluginProject)` [TEST]

## maven_plugin/src/test/resources-its/it/IntegrationTestsIT/pluginUserDependency/

- `pom.xml` - Integration-test fixture: plugin with user-declared dependency. Key: `pom.xml (pluginUserDependency)` [TEST]

## maven_plugin/src/test/resources-its/it/IntegrationTestsIT/pomCheckShouldFail/

- `pom.xml` - Integration-test fixture: project expecting POM validation failure. Key: `pom.xml (pomCheckShouldFail)` [TEST]

## maven_plugin/src/test/resources-its/it/IntegrationTestsIT/pomParentCheckShouldFail/

- `pom.xml` - Integration-test fixture: project expecting parent POM validation failure. Key: `pom.xml (pomParentCheckShouldFail)` [TEST]

## maven_plugin/src/test/resources-its/it/IntegrationTestsIT/reduceLog4jAffected/

- `pom.xml` - Integration test POM exercising reduced lockfile with specific dependency order. Key: `io.github.chains-project:maven-lockfile`, `dependencies`, `maven-shade-plugin` [TEST]

## maven_plugin/src/test/resources-its/it/IntegrationTestsIT/reduceLog4jNotAffected/

- `pom.xml` - Integration test POM for reduced lockfile not affected by dependency order. Key: `io.github.chains-project:maven-lockfile`, `dependencies`, `maven-shade-plugin` [TEST]

## maven_plugin/src/test/resources-its/it/IntegrationTestsIT/relativeParentPom/

- `pom.xml` - Aggregator parent POM with a relative child module for parent resolution tests. Key: `modules`, `packaging` [TEST]

## maven_plugin/src/test/resources-its/it/IntegrationTestsIT/relativeParentPom/child-module/

- `pom.xml` - Test child-module POM that uses a relative parent and runs the generate goal. Key: `relative-parent-pom-child-module`, `relativePath`, `maven-lockfile plugin (generate)` [TEST]

## maven_plugin/src/test/resources-its/it/IntegrationTestsIT/remoteRepositoryShouldResolve/

- `lockfile.json` - Test lockfile checking resolution from remote repositories. Key: `atlassian-bandana:atlassian-bandana:0.2.0`, `fr.inria.gforge.spoon:spoon-core:10.3.0` [TEST]
- `pom.xml` - Integration POM verifying artifact resolution from a remote repository. Key: `repositories`, `io.github.chains-project:maven-lockfile`, `dependencies` [TEST]

## maven_plugin/src/test/resources-its/it/IntegrationTestsIT/resolvedFieldShouldResolve/

- `pom.xml` - Integration POM testing resolved field calculation for artifacts from a remote repo. Key: `repositories`, `io.github.chains-project:maven-lockfile`, `dependencies` [TEST]

## maven_plugin/src/test/resources-its/it/IntegrationTestsIT/simpleProject/

- `effective-pom.xml` - Generated effective POM for the simple-project integration test [GENERATED]
- `pom.xml` - Minimal integration test POM to generate a basic lockfile. Key: `io.github.chains-project:maven-lockfile`, `dependencies` [TEST]

## maven_plugin/src/test/resources-its/it/IntegrationTestsIT/singleDependency/

- `pom.xml` - Integration test POM with a single heavy dependency (spoon-core). Key: `dependencies`, `io.github.chains-project:maven-lockfile` [TEST]

## maven_plugin/src/test/resources-its/it/IntegrationTestsIT/singleDependencyCheckCorrect/

- `lockfile.json` - Lockfile fixture for a single dependency with correct checksum. Key: `org.junit.jupiter:junit-jupiter-api:5.9.2`, `boms` [TEST]
- `pom.xml` - Integration test POM to validate lockfile correctness (expected to pass). Key: `io.github.chains-project:maven-lockfile`, `dependencies` [TEST]

## maven_plugin/src/test/resources-its/it/IntegrationTestsIT/singleDependencyCheckMustFail/

- `lockfile.json` - Lockfile fixture with a tampered checksum intended to fail validation. Key: `org.junit.jupiter:junit-jupiter-api:5.9.2`, `checksum` [TEST]
- `pom.xml` - Integration test POM that should produce a failing lockfile validation. Key: `io.github.chains-project:maven-lockfile`, `dependencies` [TEST]

## maven_plugin/src/test/resources-its/it/IntegrationTestsIT/singleDependencyCheckMustWarn/

- `lockfile.json` - Lockfile fixture with a tampered checksum meant to warn (allow failures). Key: `allowValidationFailure`, `org.junit.jupiter:junit-jupiter-api:5.9.2` [TEST]
- `pom.xml` - Integration test POM that allows validation failure to be treated as a warning. Key: `allowValidationFailure`, `io.github.chains-project:maven-lockfile` [TEST]

## maven_plugin/src/test/resources-its/it/IntegrationTestsIT/skipLockfile/

- `pom.xml` - Integration test POM demonstrating skipping lockfile generation. Key: `skip`, `dependencies` [TEST]

## maven_plugin/src/test/resources-its/it/IntegrationTestsIT/withEnvironment/

- `pom.xml` - Integration test POM that includes environment in generated lockfile. Key: `includeEnvironment`, `io.github.chains-project:maven-lockfile` [TEST]

## maven_plugin/src/test/resources-its/it/IntegrationTestsIT/withEnvironmentFromLockfile/

- `lockfile.json` - Integration-test lockfile fixture including captured environment metadata. Key: `artifactId`, `pom`, `metaData.environment`, `metaData.config` [TEST]
- `pom.xml` - Integration test POM validating environment inclusion during lockfile validation. Key: `includeEnvironment`, `io.github.chains-project:maven-lockfile` [TEST]

## maven_plugin/src/test/resources-its/it/IntegrationTestsIT/withoutEnvironment/

- `pom.xml` - Integration test POM generating lockfile without environment metadata. Key: `includeEnvironment`, `io.github.chains-project:maven-lockfile` [TEST]

## maven_plugin/src/test/resources-its/it/IntegrationTestsIT/withoutEnvironmentFromLockfile/

- `lockfile.json` - Integration-test lockfile fixture without environment metadata. Key: `artifactId`, `pom`, `metaData.config` [TEST]
- `pom.xml` - Integration test POM validating lockfile without environment fields. Key: `includeEnvironment`, `io.github.chains-project:maven-lockfile` [TEST]

## template/

- `action.yml` - A GitHub composite action template that runs the Maven Lockfile plugin to generate/validate and optionally commit lockfiles.. Key: `inputs`, `changed-files`, `POM_CHANGED`, `COMMIT_UPDATED_LOCKFILE`, `execute_maven_command` [CONFIG]


---
*This knowledge base was extracted by [Codeset](https://codeset.ai) and is available via `python .claude/docs/get_context.py <file_or_folder>`*
