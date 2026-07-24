## MODIFIED Requirements

### Requirement: Publish gated strictly on release creation

The CI `publish` job SHALL run only when the `release-please` job reports `release_created`. No other trigger — including ordinary pushes to `main` or an updated-but-unmerged release PR — SHALL cause artifacts to be built for publication or uploaded to Maven Central. No `-SNAPSHOT` version SHALL ever be published.

#### Scenario: Ordinary merge does not publish

- **WHEN** a commit merges into `main` without triggering `release_created`
- **THEN** the `publish` job does not run and no artifact is uploaded to Maven Central

#### Scenario: Release PR merge triggers the publish job

- **WHEN** the release-please job reports `release_created = true` for a merged release PR
- **THEN** the `publish` job runs `./gradlew publishAggregationToCentralPortal` against the tagged commit

#### Scenario: No snapshot version is ever published

- **WHEN** any workflow run occurs on a commit that is not a release tag
- **THEN** no task in that run publishes a `-SNAPSHOT`-versioned artifact to Maven Central

### Requirement: Central Portal is the sole publish target

Every publishable module SHALL publish to Maven Central via the Central Portal API, aggregated into a single deployment by `com.gradleup.nmcp.settings` and authenticated by `mavenCentralUsername`/`mavenCentralPassword` Gradle properties. No module SHALL publish to GitHub Packages or any other repository, and no module SHALL upload as an independent, standalone Central Portal deployment outside the aggregation.

#### Scenario: Publish task targets Central Portal only

- **WHEN** the `publishing.repositories` of a publishable module are inspected
- **THEN** no repository is configured directly on the module (publication upload is delegated entirely to the root-level `nmcpAggregation`/`nmcpSettings` configuration), and no GitHub Packages (or other) repository is present anywhere in the build

#### Scenario: All modules publish as one atomic deployment

- **WHEN** `./gradlew publishAggregationToCentralPortal` runs
- **THEN** every publishable module's artifacts are combined into a single Central Portal deployment, such that the deployment either validates and releases as a whole or fails as a whole — no subset of modules can go live while another subset fails validation

### Requirement: Every published artifact is signed

Every artifact produced by a publishable module (jar, sources jar, javadoc jar, POM) SHALL be signed via a GPG agent (`useGpgCmd()`, delegating to the local `gpg`/`gpg-agent` populated with a real private key) before upload. A `publishAggregationToCentralPortal` invocation without a usable GPG agent key SHALL fail rather than upload unsigned artifacts. No signing key material SHALL be passed through Gradle properties (`signingKey`/`signingPassword`) or any other in-memory-key mechanism.

#### Scenario: Publish requires signing when a Maven Central task runs

- **WHEN** the Gradle task graph includes any task whose name contains `Aggregation`
- **THEN** signing is required for every publication, and the build fails if no usable GPG agent key is available

#### Scenario: Published artifacts carry signatures

- **WHEN** a module's publication is inspected after a successful `publishAggregationToCentralPortal`
- **THEN** a `.asc` signature file exists alongside the jar, sources jar, javadoc jar, and POM

### Requirement: Declarative POM metadata across every publishable module

POM metadata (readable name, description, Apache 2.0 license, developer, and GitHub project URL/SCM coordinates) SHALL be declared once via `io.github.sgtsilvio.gradle.metadata`'s `metadata { }` block and SHALL apply uniformly to every module that applies `maven-publish`, replacing per-module hand-written `pom { }` closures. No publishable module SHALL declare `io.github.sgtsilvio.gradle.metadata` or `signing` directly in its own `plugins { }` block — these are cascaded transitively from `maven-publish` by the shared convention plugin.

#### Scenario: Every published POM carries complete metadata

- **WHEN** any publishable module's generated POM is inspected
- **THEN** it declares a name, description, an Apache License 2.0 entry, at least one developer, and SCM/URL coordinates pointing at the project's GitHub repository

#### Scenario: Metadata is declared once, not per module

- **WHEN** root `build.gradle` and each module's own `build.gradle` are inspected
- **THEN** the `metadata { }` block appears exactly once (at the root, applied across subprojects) and no module `build.gradle` declares its own `pom { name = ...; description = ...; ... }` closure

#### Scenario: Publishable modules declare only `maven-publish` directly

- **WHEN** any publishable module's `plugins { }` block is inspected
- **THEN** it declares `maven-publish` and does not separately declare `signing` or `io.github.sgtsilvio.gradle.metadata`
