# Maven Central Publishing Spec

## Purpose

Defines how percolate's published artifacts reach Maven Central. Publishing happens only in CI, gated strictly on a release-versioning-created release (see the `release-versioning` capability) — never on an ordinary push and never as a `-SNAPSHOT`. Every publishable module targets the Central Portal API exclusively, signs every artifact, and carries declarative, uniformly-applied POM metadata plus sources/javadoc jars.

## Requirements

### Requirement: Publish gated strictly on release creation

The CI `publish` job SHALL run only when the `release-please` job reports `release_created`. No other trigger — including ordinary pushes to `main` or an updated-but-unmerged release PR — SHALL cause artifacts to be built for publication or uploaded to Maven Central. No `-SNAPSHOT` version SHALL ever be published.

#### Scenario: Ordinary merge does not publish

- **WHEN** a commit merges into `main` without triggering `release_created`
- **THEN** the `publish` job does not run and no artifact is uploaded to Maven Central

#### Scenario: Release PR merge triggers the publish job

- **WHEN** the release-please job reports `release_created = true` for a merged release PR
- **THEN** the `publish` job runs `./gradlew check` followed by `./gradlew publishToMavenCentral` against the tagged commit

#### Scenario: No snapshot version is ever published

- **WHEN** any workflow run occurs on a commit that is not a release tag
- **THEN** no task in that run publishes a `-SNAPSHOT`-versioned artifact to Maven Central

### Requirement: Central Portal is the sole publish target

Every publishable module SHALL publish to Maven Central via the Central Portal API (`io.github.sgtsilvio.gradle.maven-central-publishing`), authenticated by `mavenCentralUsername`/`mavenCentralPassword` Gradle properties. No module SHALL publish to GitHub Packages or any other repository.

#### Scenario: Publish task targets Central Portal only

- **WHEN** the `publishing.repositories` of a publishable module are inspected
- **THEN** exactly one repository is configured, targeting the Central Portal endpoint, and no GitHub Packages (or other) repository is present

### Requirement: Every published artifact is signed

Every artifact produced by a publishable module (jar, sources jar, javadoc jar, POM) SHALL be signed via a GPG agent (`useGpgCmd()`, delegating to the local `gpg`/`gpg-agent` populated with a real private key) before upload. A `publishToMavenCentral` invocation without a usable GPG agent key SHALL fail rather than upload unsigned artifacts. No signing key material SHALL be passed through Gradle properties (`signingKey`/`signingPassword`) or any other in-memory-key mechanism.

#### Scenario: Publish requires signing when a Maven Central task runs

- **WHEN** the Gradle task graph includes any task whose name contains `MavenCentral`
- **THEN** signing is required for every publication, and the build fails if no usable GPG agent key is available

#### Scenario: Published artifacts carry signatures

- **WHEN** a module's publication is inspected after a successful `publishToMavenCentral`
- **THEN** a `.asc` signature file exists alongside the jar, sources jar, javadoc jar, and POM

### Requirement: Declarative POM metadata across every publishable module

POM metadata (readable name, description, Apache 2.0 license, developer, and GitHub project URL/SCM coordinates) SHALL be declared once via `io.github.sgtsilvio.gradle.metadata`'s `metadata { }` block and SHALL apply uniformly to every module that applies `io.github.sgtsilvio.gradle.maven-central-publishing`, replacing per-module hand-written `pom { }` closures. No publishable module SHALL declare `io.github.sgtsilvio.gradle.metadata` (or `maven-publish`, or `signing`) directly in its own `plugins { }` block — these are applied transitively, either by `io.github.sgtsilvio.gradle.maven-central-publishing` itself or cascaded from it by the root build.

#### Scenario: Every published POM carries complete metadata

- **WHEN** any publishable module's generated POM is inspected
- **THEN** it declares a name, description, an Apache License 2.0 entry, at least one developer, and SCM/URL coordinates pointing at the project's GitHub repository

#### Scenario: Metadata is declared once, not per module

- **WHEN** root `build.gradle` and each module's own `build.gradle` are inspected
- **THEN** the `metadata { }` block appears exactly once (at the root, applied across subprojects) and no module `build.gradle` declares its own `pom { name = ...; description = ...; ... }` closure

#### Scenario: Publishable modules declare only the primary publishing plugin

- **WHEN** any publishable module's `plugins { }` block is inspected
- **THEN** it declares `io.github.sgtsilvio.gradle.maven-central-publishing` and does not separately declare `maven-publish`, `signing`, or `io.github.sgtsilvio.gradle.metadata`

### Requirement: Sources and javadoc jars are published

Every publishable Java module SHALL publish a sources jar and a javadoc jar alongside the main artifact, as required by Maven Central.

#### Scenario: Sources and javadoc jars accompany the main artifact

- **WHEN** a publishable module's publication is inspected
- **THEN** a `-sources.jar` and a `-javadoc.jar` are present in addition to the main jar and POM
