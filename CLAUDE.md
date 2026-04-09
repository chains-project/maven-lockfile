# Project Overview

Maven Lockfile

- What: A Maven plugin + supporting GitHub Action that generates, persists and validates a repository-level lockfile (JSON) containing resolved dependency graphs, checksums, plugin metadata and POM/BOM metadata. It supports generate, validate and freeze goals to produce lockfile.json, verify an on-disk lockfile, and produce a frozen pom (pom.lockfile.xml).
- Ecosystem: Java / Maven plugin. The main module is maven_plugin.
- Intended usage: add to CI to validate reproducibility and integrity of Maven builds; optionally commit regenerated lockfiles back to PRs; enable reproducible rebuilds by freezing POMs.

# Key Components

- action.yml: composite GitHub Action wrapper that runs the plugin in CI (generate vs validate), detects changed files, optionally commits updated lockfiles. **This file is generated from template/action.yml** by the gmavenplus-plugin during the `generate-resources` phase (configured in the parent pom.xml). Do not edit action.yml directly — edit template/action.yml instead and regenerate.
- template/action.yml: source template for action.yml; contains the runtime bash driver (helpers like execute_maven_command) and the `PLUGIN_VERSION` placeholder that is substituted during the generate-resources phase. Changes here are propagated to action.yml at build time.
- maven_plugin/pom.xml: module POM (packaging=maven-plugin) — authoritative build config for the plugin artifact.

Primary Java constructs (use these when changing behavior):
- maven_plugin/src/main/java/io/github/chains_project/maven_lockfile/GenerateLockFileMojo.java:execute
- maven_plugin/src/main/java/io/github/chains_project/maven_lockfile/ValidateChecksumMojo.java:execute
- maven_plugin/src/main/java/io/github/chains_project/maven_lockfile/FreezeDependencyMojo.java:execute
- maven_plugin/src/main/java/io/github/chains_project/maven_lockfile/AbstractLockfileMojo.java:getConfig, getChecksumCalculator, generateMetaInformation
- maven_plugin/src/main/java/io/github/chains_project/maven_lockfile/LockFileFacade.java:generateLockFileFromProject, getAllPlugins, constructRecursivePom
- maven_plugin/src/main/java/io/github/chains_project/maven_lockfile/graph/DependencyGraph.java:of, createDependencyNode, getDependencySet
- maven_plugin/src/main/java/io/github/chains_project/maven_lockfile/graph/DependencyNode.java (data model)
- maven_plugin/src/main/java/io/github/chains_project/maven_lockfile/data/LockFile.java:readLockFile
- maven_plugin/src/main/java/io/github/chains_project/maven_lockfile/checksum/AbstractChecksumCalculator.java (API)
- maven_plugin/src/main/java/io/github/chains_project/maven_lockfile/checksum/FileSystemChecksumCalculator.java
- maven_plugin/src/main/java/io/github/chains_project/maven_lockfile/checksum/RemoteChecksumCalculator.java
- maven_plugin/src/main/java/io/github/chains_project/maven_lockfile/JsonUtils.java:getGson, toJson, fromJson

# Architecture

High-level data flow:

    Developer/CI
        │
        ▼
    GitHub Action (action.yml / template)  ── invokes ──▶ mvn io.github.chains-project:maven-lockfile:generate|validate|freeze
                                                          │
                                                          ▼
                                              Maven Mojo (Generate/Validate/Freeze)
                                                          │
                           ┌──────────────────────────────┴──────────────────────────────┐
                           │                                                             │
                           ▼                                                             ▼
                  LockFileFacade.generateLockFileFromProject                         FreezeDependencyMojo
                           │                                                             │
                           ▼                                                             ▼
                  DependencyGraph.of(...)  ←── AbstractChecksumCalculator ──▶ checksum impls
                           │                                                             │
                           ▼                                                             ▼
                    LockFile (data model)  ── JsonUtils ▸ write/read lockfile.json   pom.lockfile.xml

# Core Data Structures

- maven_plugin/src/main/java/io/github/chains_project/maven_lockfile/data/LockFile.java
  - Root model serialized to lockfile.json. Contains: project Pom, Set<DependencyNode> dependencies, Set<MavenPlugin> mavenPlugins, MetaData (Environment + Config), Set<Pom> boms.
  - Use LockFile.readLockFile(Path) to deserialize.

- maven_plugin/src/main/java/io/github/chains_project/maven_lockfile/graph/DependencyNode.java
  - Node model: GroupId, ArtifactId, VersionNumber, Classifier, ArtifactType, checksumAlgorithm, checksum, ResolvedUrl, RepositoryId, scope, selectedVersion, included, children (TreeSet), id (NodeId). Children and comparator strings enforce deterministic ordering.

- maven_plugin/src/main/java/io/github/chains_project/maven_lockfile/data/Pom.java
  - Represents a POM record: GAV, relativePath (for local), resolved url, repository id, checksumAlgorithm, checksum and parent chain.

- maven_plugin/src/main/java/io/github/chains_project/maven_lockfile/data/Config.java
  - Persisted generation/validation options (includeMavenPlugins, reduced, checksumMode/algorithm, validation policies). Defaults are tied to ChecksumModes and FileSystemChecksumCalculator.getDefaultChecksumAlgorithm().

- maven_plugin/src/main/java/io/github/chains_project/maven_lockfile/checksum/AbstractChecksumCalculator.java
  - Abstraction for checksum retrieval and resolved info. Key methods: calculateArtifactChecksum, calculatePluginChecksum, getArtifactResolvedField, getPluginResolvedField, calculatePomChecksum, prewarmArtifactCache.

# Control Flow (end-to-end)

Generate (mvn ...:generate)
- GenerateLockFileMojo.execute:
  - PluginLogManager.setLog(getLog())
  - Optionally read existing lockfile via LockFile.readLockFile
  - Determine Config (getConfig / getConfigFromFile semantics)
  - Build MetaData (Environment + Config)
  - getChecksumCalculator(config) → FileSystem or Remote
  - LockFileFacade.generateLockFileFromProject(project, collector, checksumCalculator, metadata)
  - JsonUtils.toJson(...) -> write lockfile.json

Validate (mvn ...:validate)
- ValidateChecksumMojo.execute:
  - Read existing lockfile, build Config from it (fall back to mojo config)
  - Force local checksum mode: getChecksumCalculator(config, true)
  - Regenerate lockfile via LockFileFacade.generateLockFileFromProject
  - Compare environment, POM checksum and full LockFile equality. Use LockFileDifference to compute structured diffs and respect config policies (warn vs fail).

Freeze (mvn ...:freeze)
- FreezeDependencyMojo.execute:
  - Read lockfile.json, compute nearest/selected versions from DependencyNode.isIncluded()
  - Update dependencies in a new POM (pom.lockfile.xml) with pinned direct versions and add transitive deps to dependencyManagement. exactVersionStrings controls writing as ranges.

GitHub Action (action.yml / template/action.yml)
- Checkout PR/branch (explicit repository/ref for PRs)
- Use tj-actions/changed-files to compute POM_CHANGED (watch pom.xml and configured workflow filename)
- Decide action: generate (if POM_CHANGED or forced) or validate
- Optionally check existing lockfile metaData.config.mavenLockfileVersion vs PLUGIN_VERSION and skip older regeneration (--skip-older-regeneration behavior uses jq and sort -V)
- Optionally commit updated lockfile via a commit action when inputs.commit-lockfile == 'true'

# Test-Driven Development

- Run module/unit tests (fast): mvn -pl maven_plugin test
- Run all tests / integration verify: mvn test (or mvn -pl maven_plugin verify to include failsafe ITs)
- Run a single unit test class: mvn -pl maven_plugin -Dtest=io.github.chains_project.maven_lockfile.checksum.FileSystemChecksumCalculatorTest test
- Run integration tests (IT harness): mvn -pl maven_plugin -Dtest=it.IntegrationTestsIT test
- Useful files for IT fixtures: maven_plugin/src/test/resources-its/it/IntegrationTestsIT/*

Before adding changes:
- Run maven_plugin unit + integration tests locally with the same Maven wrapper (maven_plugin/mvnw) to reproduce CI environment.
- When changing serialization or defaults, update integration test fixtures under resources-its.

# Bash / Maven Commands

- Generate lockfile (module/project):
  mvn io.github.chains-project:maven-lockfile:generate

- Validate against on-disk lockfile:
  mvn io.github.chains-project:maven-lockfile:validate

- Freeze into new POM (pom.lockfile.xml):
  mvn io.github.chains-project:maven-lockfile:freeze

- Run plugin unit tests:
  mvn -pl maven_plugin test

- Run integration tests (module):
  mvn -pl maven_plugin -Dtest=it.IntegrationTestsIT test

# Code Style & Conventions (non-obvious)

- Lockfile model uses Sets and deterministic comparators (TreeSet) for stable JSON diffs. Do not revert collections to Lists or unordered types.
- Many mojo parameters are Strings that are parsed (Boolean.parseBoolean) in getConfig — changing parameter types requires updating binding and callers.
- Json serialization is centralized in JsonUtils; add type adapters there when you add small wrapper value objects.
- PluginLogManager must be initialized in Mojos via PluginLogManager.setLog(getLog()) before other code expects to log.

# Gotchas (common pitfalls & warnings)

- POM_CHANGED casing: the GitHub Action and script rely on the environment variable exactly named POM_CHANGED. Changing the name/casing breaks generate vs validate detection. (action.yml / changed-files step)
- PLUGIN_VERSION and skip-older-regeneration: template/action.yml substitutes PLUGIN_VERSION; if older action tries to overwrite a lockfile generated by a newer plugin then skip-older-regeneration logic (jq + sort -V) protects the file. Keep the PLUGIN_VERSION semantics stable.
- jq and sort -V availability: action script depends on jq and GNU sort -V in runner images. Changing logic requires proving availability across runners.
- Checksum algorithm defaults: Config default checksumAlgorithm is obtained from FileSystemChecksumCalculator.getDefaultChecksumAlgorithm(). If you change defaults, update Config and tests accordingly.
- Root node checksum suppression: DependencyGraph.createDependencyNode sets checksum empty for project root nodes; do not compute root checksum or tests will break.
- Do not mutate fields that affect comparator/equality after inserting nodes into TreeSet/HashSet. Fields used by compareTo/getComparatorString and equals/hashCode must remain stable while in collections (DependencyNode, children ordering).
- SpyingDependencyNodeUtils.getWinnerVersion is reflective and brittle (relies on Maven internals). If it breaks on a Maven upgrade, it should surface an explicit error or warning — do not silently fall back, as that may produce an incorrect frozen POM without any indication of the failure.
- Validation checksum mode: ValidateChecksumMojo uses getChecksumCalculator(config, true) which forces local (filesystem) checksum resolution. The primary purpose is to verify that the artifacts in the developer's local `.m2` repository match the lockfile — not to avoid network calls. Remote checksum verification (against Maven Central) is a different guarantee. Note: CI does run remote-mode lockfiles; forcing local mode in all cases would defeat that use-case.
- Changing serialization (JsonUtils adapters, field names, @SerializedName) breaks existing lockfile.json compatibility and CI validation.
- Platform-dependent artifacts: checksums can differ across OS/JDK/platform-specific classifiers. CI may run on ubuntu-latest while developer runs on other OS — tests and CI may diverge.

# Pattern Examples (good examples to copy)

- maven_plugin/src/main/java/io/github/chains_project/maven_lockfile/GenerateLockFileMojo.java:execute
  - Orchestrates reading existing lockfile, merging config, building checksum calculator and calling LockFileFacade.

- maven_plugin/src/main/java/io/github/chains_project/maven_lockfile/AbstractLockfileMojo.java:getChecksumCalculator(Config)
  - Centralized factory for checksum calculators (remote vs local). Keep this single source for consistent behavior across Mojos.

- maven_plugin/src/main/java/io/github/chains_project/maven_lockfile/LockFileFacade.java:generateLockFileFromProject
  - High-level orchestration that collects plugins, constructs graph, pre-warms checksum cache, and assembles LockFile. Keep orchestration logic here and delegate resolution/IO.

- maven_plugin/src/main/java/io/github/chains_project/maven_lockfile/graph/DependencyGraph.java:of/createDependencyNode
  - Convert Maven dependency graph to deterministic DependencyNode model; call calc.prewarmArtifactCache(uniqueArtifacts) before artifact resolution. Note: prewarmArtifactCache is a meaningful operation only in remote mode (RemoteChecksumCalculator uses it to batch-fetch checksums in parallel); it is a no-op for local (FileSystem) mode. It exists on the AbstractChecksumCalculator interface so callers do not need to know which mode is active.

# Common Mistakes → Fast Fixes

- Symptom: Validate Mojo unexpectedly performs network calls or fails on CI.
  - Check: Validate what checksum mode the lockfile was generated with. If the lockfile was generated in local (filesystem) mode, ValidateChecksumMojo should be using getChecksumCalculator(config, true) to force local resolution. However, if the lockfile was intentionally generated in remote mode (e.g., to verify Maven Central integrity), do not force local mode — fix the underlying cause instead.

- Symptom: Lockfile appears with different ordering or tests fail with ordering assertions.
  - Check: Ensure sets are collected into TreeSet with the same comparator (DependencyNode::getComparatorString) and you didn't change comparator fields.

- Symptom: Regeneration skipped unexpectedly or PLUGIN_VERSION comparisons behave wrong.
  - Check: action template relies on exact git-substitution of PLUGIN_VERSION and uses jq to extract .metaData.config.mavenLockfileVersion; ensure jq exists and fields in lockfile are named correctly.

- Symptom: Freeze produced wrong versions (highest lexicographic instead of resolved winner).
  - Check: FreezeDependencyMojo.getNearestVersionDependency uses DependencyNode.isIncluded() and BFS; do not replace with lexicographic selection. SpyingDependencyNodeUtils is used to detect winner; preserve that logic or update tests accordingly.

# Invariants (always hold)

- Lockfile JSON shape is stable: fields and @SerializedName values are part of the public contract.
- Checksum hex strings are lowercase base16 with leading zeros preserved (Guava BaseEncoding.base16().toLowerCase()).
- Root project node has empty checksum and unresolved RepositoryInformation.
- Default checksum algorithm for filesystem mode: SHA-256 (FileSystemChecksumCalculator.getDefaultChecksumAlgorithm()).
- Config defaults are tied to ChecksumModes and FileSystemChecksumCalculator; update both when changing defaults.
- Mojo classes must call PluginLogManager.setLog(getLog()) prior to delegating work.

# Anti-patterns (avoid)

- Performing artifact resolution or network IO inside GraphBuildingNodeVisitor. Use AbstractChecksumCalculator and prewarm hooks instead.
- Mutating nodes' comparator/equality fields while nodes are stored in ordered/hash collections. Remove/reinsert if mutation is required.
- Replacing Sets with Lists for dependencies/plugins; this breaks order-insensitive equality and deterministic outputs.
- Changing public serialized names/enum @SerializedName without migration plan — this breaks consumers and CI validation.

# Helpful References

- Key test classes and fixtures:
  - maven_plugin/src/test/java/io/github/chains_project/maven_lockfile/graph/LockfileTest.java
  - maven_plugin/src/test/java/it/IntegrationTestsIT.java and resources-its/*

- CI workflow example: .github/workflows/Lockfile.yml uses the published action chain and demonstrates runner hardening and action invocation.

If you are about to change any of the above major components, run unit tests and the integration tests (mvn -pl maven_plugin verify) and update the README when public behavior (defaults, PLUGIN_VERSION contract, or lockfile schema) changes. Do NOT update the CHANGELOG — it is auto-generated by jreleaser.

# Verification Checklist

- Run the full test matrix locally or in CI
- Confirm failing test fails before fix, passes after
- Run linters and formatters

# Test Integrity

- NEVER modify existing tests to make your implementation pass
- If a test fails after your change, fix the implementation, not the test
- Only modify tests when explicitly asked to, or when the test itself is demonstrably incorrect

# Suggestions for Thorough Investigation

When working on a task, consider looking beyond the immediate file:
- Test files can reveal expected behavior and edge cases
- Config or constants files may define values the code depends on
- Files that are frequently changed together (coupled files) often share context

# Must-Follow Rules

1. Work in short cycles. In each cycle: choose the single highest-leverage next action, execute it, verify with the strongest available check (tests, typecheck, run, lint, or a minimal repro), then write a brief log entry of what changed + what you'll do next.
2. Prefer the smallest change that can be verified. Keep edits localized, avoid broad formatting churn, and structure work so every change is easy to revert.
3. If you're missing information (requirements, environment behavior, API contracts), do not assume. Instead: inspect code, read docs in-repo, run a targeted experiment, add temporary instrumentation, or create a minimal reproduction to learn the truth quickly.


# Index Files

I have provided an index file to help navigate this codebase:
- `.claude/docs/general_index.md`

The file is organized by directory (## headers), with each file listed as:
`- `filename` - short description. Key: `construct1`, `construct2` [CATEGORY]`

You can grep for directory names, filenames, construct names, or categories (TEST, CLI, PUBLIC_API, GENERATED, SOURCE_CODE) to quickly find relevant files without reading the entire index.

**MANDATORY RULE — NO EXCEPTIONS:** After you read, reference, or consider editing a file or folder, you MUST run:
`python .claude/docs/get_context.py <path>`

This works for **both files and folders**:
- For a file: `python .claude/docs/get_context.py <file_path>`
- For a folder: `python .claude/docs/get_context.py <folder_path>`

This is a hard requirement for EVERY file and folder you touch. Without this, you'll miss recent important information and your edit will likely fail verification. Do not skip this step. Do not assume you already know enough. Do not batch it "for later." Do not skip files even if you have obtained context about a parent directory. Run it immediately after any other action on that path.

The command returns critical context you cannot infer on your own:

**For files:**
- Edit checklist with tests to run, constants to check, and related files
- Historical insights (past bugs, fixes, lessons learned)
- Key constructs defined in the file
- Tests that exercise this file
- Related files and semantic overview
- Common pitfalls

**For folders:**
- Folder role and responsibility in the codebase
- Key files and why they matter
- Cross-cutting behaviors across the subtree
- Distilled insights from every file in that folder

**Workflow (follow this exact order every time):**
1. Identify the file or folder you need to work with.
2. Run `python .claude/docs/get_context.py <path>` and read the output.
3. Only then proceed to read, edit, or reason about it.

If you need to work with multiple paths, run the command for each one before touching any of them.

**Violations:** If you read or edit a file or folder without first running get_context.py on it, you are violating a project-level rule. Stop, run the command, and re-evaluate your changes with the new context.



---
*This knowledge base was extracted by [Codeset](https://codeset.ai) and is available via `python .claude/docs/get_context.py <file_or_folder>`*
