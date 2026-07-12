## MODIFIED Requirements

### Requirement: Consumer version platform

The project SHALL publish a Bill of Materials (`io.github.joke.percolate:bom`) that manages the versions of percolate's own published artifacts (annotations, starter, processor, spi, strategies-builtin, reactor, reactor-blocking, percolate-javapoet). A consumer importing the BOM as a platform SHALL be able to declare every percolate dependency without an explicit version. The BOM SHALL be distinct from the internal `:dependencies` platform that pins third-party versions across modules.

#### Scenario: Versions omitted when the BOM is imported

- **WHEN** a consumer imports `platform('io.github.joke.percolate:bom:<v>')` and declares percolate dependencies without versions
- **THEN** the dependencies resolve to the versions managed by the BOM

#### Scenario: BOM manages every published percolate artifact

- **WHEN** the BOM is inspected
- **THEN** it declares managed versions for the annotations, starter, processor, spi, strategies-builtin, reactor, reactor-blocking, and percolate-javapoet artifacts

### Requirement: Published Maven artifacts

Every publishable module SHALL apply Maven publication under the group `io.github.joke.percolate`, with the Maven `artifactId` defaulting to the Gradle project's own `project.name` (no `percolate-` prefix, no hardcoded per-module naming exception list): the annotations, spi, processor, strategies-builtin, reactor, reactor-blocking, the relocated `percolate-javapoet`, the starter (`percolate`), and the BOM (`bom`). The internal `:dependencies` version platform SHALL NOT be published. Published POMs SHALL be self-contained: they SHALL declare concrete dependency versions and SHALL NOT import the internal version platform or otherwise impose percolate's internal third-party version constraints on consumers. The `percolate-javapoet` POM SHALL NOT declare a dependency on `com.palantir.javapoet` — the upstream JavaPoet is fully relocated into the artifact. `publishToMavenLocal` SHALL produce consumable artifacts whose POMs carry the correct coordinates and dependency declarations (in particular, the starter's POM SHALL declare processor and strategies-builtin).

#### Scenario: publishToMavenLocal yields consumable coordinates

- **WHEN** `publishToMavenLocal` runs and the local repository is inspected
- **THEN** each publishable module is present under `io.github.joke.percolate` with a POM whose artifactId equals the module's own Gradle project name, and the starter's POM declares the processor and strategies-builtin dependencies

#### Scenario: POMs are self-contained

- **WHEN** a published percolate POM is inspected
- **THEN** its dependencies carry concrete versions and it contains no `<dependencyManagement>` import of the internal `:dependencies` platform, and no `dependencies` artifact is published
- **AND** the `percolate-javapoet` POM declares no `com.palantir.javapoet` dependency

#### Scenario: No hardcoded artifactId naming exceptions remain

- **WHEN** root `build.gradle`'s `maven-publish` wiring is inspected
- **THEN** it contains no per-module artifactId exception list or derivation logic — every publication's `artifactId` is left at its Gradle default
