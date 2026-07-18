## MODIFIED Requirements

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
