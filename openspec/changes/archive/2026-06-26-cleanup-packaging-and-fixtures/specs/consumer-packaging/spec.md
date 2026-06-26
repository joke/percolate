## MODIFIED Requirements

### Requirement: Published Maven artifacts

Every publishable module SHALL apply Maven publication under the group `io.github.joke.percolate`: the annotations, spi, processor, strategies-builtin, reactor, reactor-blocking, the starter, and the BOM. The internal `:dependencies` version platform SHALL NOT be published. Published POMs SHALL be self-contained: they SHALL declare concrete dependency versions and SHALL NOT import the internal version platform or otherwise impose percolate's internal third-party version constraints on consumers. `publishToMavenLocal` SHALL produce consumable artifacts whose POMs carry the correct coordinates and dependency declarations (in particular, the starter's POM SHALL declare processor and strategies-builtin).

#### Scenario: publishToMavenLocal yields consumable coordinates

- **WHEN** `publishToMavenLocal` runs and the local repository is inspected
- **THEN** each publishable module is present under `io.github.joke.percolate` with a POM, and the starter's POM declares the processor and strategies-builtin dependencies

#### Scenario: POMs are self-contained

- **WHEN** a published percolate POM is inspected
- **THEN** its dependencies carry concrete versions and it contains no `<dependencyManagement>` import of the internal `:dependencies` platform, and no `percolate-dependencies` artifact is published

## REMOVED Requirements

### Requirement: Black-box consumer smoke build

**Reason**: The standalone build resolved the *published* coordinates from `mavenLocal()`, so it could only fail after the artifact already existed, polluted `~/.m2`, ran as a manual two-pass, and was not a `check` gate. A `project()` dependency resolves against the exact build outputs and tests the same behaviour as a real one-pass gate; the only thing publish-then-consume uniquely checked (POM closure, HTTP-downloadability) is either fixed structurally by self-contained POMs or is Maven Central's concern.

**Migration**: Replaced by the in-build consumer smoke module (see ADDED). Any real published-coordinate validation moves to a release-time staging gate alongside the deferred Maven Central work.

## ADDED Requirements

### Requirement: In-build consumer smoke module

The project SHALL provide an in-build `percolate-smoke` module that exercises the assembled consumer path against the exact build outputs. It SHALL depend via `annotationProcessor project(':percolate')` and `compileOnly project(':annotations')` — never putting any percolate artifact on its runtime classpath — compile one fixed mapper, and run the generated implementation. It SHALL be wired into `./gradlew check` as a one-pass gate and SHALL NOT apply Maven publication. It SHALL NOT enumerate conversion combinations; its purpose is to confirm the assembled product works in a normal developer setup.

#### Scenario: Smoke runs the generated mapper as part of check

- **WHEN** `./gradlew check` runs
- **THEN** the smoke module compiles its fixed `@Mapper`, runs the generated implementation on a runtime classpath that carries no percolate artifact, and the run succeeds — failing the build if generation or the mapping is wrong

#### Scenario: Smoke exercises starter aggregation via project dependencies

- **WHEN** the smoke module's `annotationProcessor` classpath is resolved from `project(':percolate')`
- **THEN** both the engine and the builtin strategies are present (the starter's aggregation), so builtin-backed generation is exercised without resolving any published coordinate
