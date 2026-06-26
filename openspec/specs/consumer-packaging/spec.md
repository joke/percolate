# Consumer Packaging Spec

## Purpose

Defines how percolate is published and assembled for downstream developers. Percolate is a compile-time-only tool: the generated mappers carry no runtime dependency on percolate, so packaging is entirely about the `annotationProcessor` classpath. A single convenience starter supplies the engine plus the builtin strategies, a Bill of Materials manages the versions of percolate's own artifacts, and the engine module declares no edge to any strategy module so the separation is enforced by the dependency graph. A standalone black-box smoke build validates the real consumer path.

## Requirements

### Requirement: Convenience starter supplies engine and builtins via annotationProcessor

The project SHALL publish a single convenience starter artifact (`io.github.joke.percolate:percolate`) that aggregates the processor engine and the builtin strategies. Adding the starter to a consumer's `annotationProcessor` configuration SHALL make mapper generation work with the builtin strategies, without the consumer naming the engine or strategy modules individually.

#### Scenario: Starter alone enables builtin-backed generation

- **WHEN** a consumer project declares only `annotationProcessor 'io.github.joke.percolate:percolate'` (plus the annotations on its compile classpath) and compiles a `@Mapper` interface that requires a builtin conversion
- **THEN** the processor runs, a builtin strategy resolves the conversion, and a compiling mapper implementation is generated

#### Scenario: Starter places engine and builtins on the processor classpath

- **WHEN** the starter is resolved as an `annotationProcessor` dependency
- **THEN** both the processor engine and the strategies-builtin artifact (and their transitive dependencies) are present on the annotation-processor classpath, discoverable by ServiceLoader

### Requirement: Compile-time-only footprint

Percolate SHALL impose zero runtime footprint on the consumer. Neither generated mapper implementations nor the consumer's runtime classpath SHALL require any percolate artifact. The annotations SHALL be sufficient on a compile-only configuration.

#### Scenario: Generated code carries no percolate runtime dependency

- **WHEN** a generated mapper implementation is compiled and inspected
- **THEN** it references only the consumer's own types and `javax.annotation.processing.Generated` (from `java.base`), and no percolate engine, spi, or strategy type

#### Scenario: Annotations resolve as compile-only

- **WHEN** a consumer declares the annotations on a `compileOnly` configuration and the starter on `annotationProcessor`
- **THEN** the `@Mapper` interface compiles and generation succeeds with no annotation artifact on the runtime classpath

### Requirement: Consumer version platform

The project SHALL publish a Bill of Materials (`io.github.joke.percolate:percolate-bom`) that manages the versions of percolate's own published artifacts (annotations, starter, processor, spi, strategies-builtin, reactor, reactor-blocking). A consumer importing the BOM as a platform SHALL be able to declare every percolate dependency without an explicit version. The BOM SHALL be distinct from the internal `:dependencies` platform that pins third-party versions across modules.

#### Scenario: Versions omitted when the BOM is imported

- **WHEN** a consumer imports `platform('io.github.joke.percolate:percolate-bom:<v>')` and declares percolate dependencies without versions
- **THEN** the dependencies resolve to the versions managed by the BOM

#### Scenario: BOM manages every published percolate artifact

- **WHEN** the BOM is inspected
- **THEN** it declares managed versions for the annotations, starter, processor, spi, strategies-builtin, reactor, and reactor-blocking artifacts

### Requirement: Engine module excludes the builtins

The `processor` engine module SHALL NOT declare any dependency edge — compile, runtime, or otherwise — on `strategies-builtin`. The builtins SHALL reach the processor only through the convenience starter's aggregation. This makes the processor↔builtins separation enforced by the dependency graph rather than convention.

#### Scenario: No processor-to-builtins edge

- **WHEN** the resolved dependency graph of the `processor` module is inspected for compile and runtime configurations
- **THEN** `strategies-builtin` does not appear in any of them

#### Scenario: Builtins arrive only via the starter

- **WHEN** the convenience starter's resolved dependency graph is inspected
- **THEN** both `processor` and `strategies-builtin` appear, confirming the starter is the sole assembly point for the default distribution

### Requirement: Published Maven artifacts

Every publishable module SHALL apply Maven publication under the group `io.github.joke.percolate`: the annotations, spi, processor, strategies-builtin, reactor, reactor-blocking, the starter, and the BOM. `publishToMavenLocal` SHALL produce consumable artifacts whose POMs carry the correct coordinates and dependency declarations (in particular, the starter's POM SHALL declare processor and strategies-builtin).

#### Scenario: publishToMavenLocal yields consumable coordinates

- **WHEN** `publishToMavenLocal` runs and the local repository is inspected
- **THEN** each publishable module is present under `io.github.joke.percolate` with a POM, and the starter's POM declares the processor and strategies-builtin dependencies

### Requirement: Black-box consumer smoke build

The project SHALL provide a standalone `percolate-smoke` build that exercises the published consumer path. It SHALL resolve percolate artifacts by GAV from `mavenLocal()` — the starter on `annotationProcessor`, the annotations on `compileOnly`, versions via the BOM platform — and SHALL NOT reference any internal module (`project(':...')`) or the test harness. It SHALL compile one fixed mapper and assert generation succeeds. It SHALL NOT enumerate conversion combinations; its purpose is to confirm the assembled product works in a normal developer setup, not to provide behavioural coverage.

#### Scenario: Smoke build compiles a mapper through the published path

- **WHEN** the starter and annotations are resolved by GAV from `mavenLocal()` (after `publishToMavenLocal`) and the smoke build compiles its fixed `@Mapper`
- **THEN** a mapper implementation is generated and compiles successfully

#### Scenario: Smoke build has no privileged dependencies

- **WHEN** the smoke build's dependencies are inspected
- **THEN** they are resolved by GAV from `mavenLocal()` and contain only published consumer artifacts (starter, BOM, annotations), with no `project(':...')` reference to an internal module and no test-harness dependency
