## ADDED Requirements

### Requirement: Publish gated strictly on release creation

The CI `publish` job SHALL run only when the `release-please` job reports `release_created`. No other trigger — including ordinary pushes to `main` or an updated-but-unmerged release PR — SHALL cause artifacts to be built for publication or uploaded to Maven Central. No `-SNAPSHOT` version SHALL ever be published.

#### Scenario: Ordinary merge does not publish

- **WHEN** a commit merges into `main` without triggering `release_created`
- **THEN** the `publish` job does not run and no artifact is uploaded to Maven Central

#### Scenario: Release PR merge triggers the publish job

- **WHEN** the release-please job reports `release_created = true` for a merged release PR
- **THEN** the `publish` job runs `./gradlew check` followed by `./gradlew publish` against the tagged commit

#### Scenario: No snapshot version is ever published

- **WHEN** any workflow run occurs on a commit that is not a release tag
- **THEN** no task in that run publishes a `-SNAPSHOT`-versioned artifact to Maven Central

### Requirement: Central Portal is the sole publish target

Every publishable module SHALL publish to Maven Central via the Central Portal API (`io.github.sgtsilvio.gradle.maven-central-publishing`), authenticated by `mavenCentralUsername`/`mavenCentralPassword` Gradle properties. No module SHALL publish to GitHub Packages or any other repository.

#### Scenario: Publish task targets Central Portal only

- **WHEN** the `publishing.repositories` of a publishable module are inspected
- **THEN** exactly one repository is configured, targeting the Central Portal endpoint, and no GitHub Packages (or other) repository is present

### Requirement: Every published artifact is signed

Every artifact produced by a publishable module (jar, sources jar, javadoc jar, POM) SHALL be signed with an in-memory PGP key (`signingKey`/`signingPassword` Gradle properties) before upload. A `publish` invocation without valid signing credentials configured SHALL fail rather than upload unsigned artifacts.

#### Scenario: Publish requires signing when the publish task runs

- **WHEN** the Gradle task graph includes a `publish` task
- **THEN** signing is required for every publication, and the build fails if `signingKey`/`signingPassword` are not available

#### Scenario: Published artifacts carry signatures

- **WHEN** a module's publication is inspected after a successful `publish`
- **THEN** a `.asc` signature file exists alongside the jar, sources jar, javadoc jar, and POM

### Requirement: Declarative POM metadata across every publishable module

POM metadata (readable name, description, Apache 2.0 license, developer, and GitHub project URL/SCM coordinates) SHALL be declared once via `io.github.sgtsilvio.gradle.metadata`'s `metadata { }` block and SHALL apply uniformly to every module that applies `maven-publish`, replacing per-module hand-written `pom { }` closures.

#### Scenario: Every published POM carries complete metadata

- **WHEN** any publishable module's generated POM is inspected
- **THEN** it declares a name, description, an Apache License 2.0 entry, at least one developer, and SCM/URL coordinates pointing at the project's GitHub repository

#### Scenario: Metadata is declared once, not per module

- **WHEN** root `build.gradle` and each module's own `build.gradle` are inspected
- **THEN** the `metadata { }` block appears exactly once (at the root, applied across subprojects) and no module `build.gradle` declares its own `pom { name = ...; description = ...; ... }` closure

### Requirement: Sources and javadoc jars are published

Every publishable Java module SHALL publish a sources jar and a javadoc jar alongside the main artifact, as required by Maven Central.

#### Scenario: Sources and javadoc jars accompany the main artifact

- **WHEN** a publishable module's publication is inspected
- **THEN** a `-sources.jar` and a `-javadoc.jar` are present in addition to the main jar and POM
