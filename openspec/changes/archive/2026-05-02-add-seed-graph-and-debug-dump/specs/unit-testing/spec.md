## ADDED Requirements

### Requirement: Graph-stage classes SHALL have unit specs
The classes introduced by the seed-graph-and-debug-dump change SHALL each have a corresponding Spock specification under `processor/src/test/groovy/...`, tagged `@Tag('unit')`, that tests the class in isolation using Spock mocks for collaborators.

#### Scenario: SeedGraph unit spec exists
- **WHEN** the unit test suite runs
- **THEN** `SeedGraphSpec` verifies that an empty `MapperMappings` produces an empty `MapperGraph`; that each method's parameters become parameter-root nodes scoped to that method; that each method's return type becomes a target-root node scoped to that method; that each `MappingDirective` produces source and target chains and a single bridging edge with the directive's `AnnotationMirror`; that dotted source / target paths fan out into chains; that shared path prefixes between two directives on the same method are not duplicated; and that the resulting graph satisfies the forest invariant

#### Scenario: DumpGraph unit spec exists
- **WHEN** the unit test suite runs
- **THEN** `DumpGraphSpec` verifies that no resource is created when `ProcessorOptions.debugGraphs == false`; that a resource named `<MapperFQN>.seed.dot` is created at `SOURCE_OUTPUT` with the deterministic DOT renderer's output when the option is `true` and the graph is non-empty; that no resource is created for an empty graph even when the option is `true`; and that an `IOException` from the `Filer` results in a warning diagnostic and a normal return rather than an aborted compile

#### Scenario: ProcessorOptions unit spec exists
- **WHEN** the unit test suite runs
- **THEN** `ProcessorOptionsSpec` verifies that `debugGraphs` is `false` when the option is absent; `true` for case-insensitive `"true"`; and `false` for any other value

#### Scenario: MapperGraph unit spec exists
- **WHEN** the unit test suite runs
- **THEN** `MapperGraphSpec` verifies that `addNode` is idempotent on equal nodes; that `addEdge` rejects structurally-equal duplicates; that `nodes()` iterates in ascending `Node.id()` order; that `edges()` iterates in ascending natural `Edge` order; and that `nodesByScope(...)` filters and preserves order

#### Scenario: DotRenderer unit spec exists
- **WHEN** the unit test suite runs
- **THEN** `DotRendererSpec` verifies byte-stability of the output across two renderings of the same graph; per-method `cluster_*` subgraph emission with `label` attributes; vertex order matching `Node.id()` ascending; edge order matching natural `Edge` ascending; attribute keys ordered ascending within a statement; correct escaping of `"`, `\`, and newlines in labels; distinct shapes for source-located vs target-located nodes; and a directive marker on directive-seeded edges

#### Scenario: Node, Edge, Location, AccessPath, TargetPath, Scope value-type specs exist
- **WHEN** the unit test suite runs
- **THEN** value-type specs verify `Node.id()` determinism (same data produces same id; differing data produces different id); `Edge` natural ordering by `(from.id(), to.id(), weight, directive-presence)`; `AccessPath` and `TargetPath` immutability and `append(...)` semantics; and `Scope` text-encoding stability for `MethodScope`

### Requirement: Pipeline unit spec SHALL cover the new stages
`PipelineSpec` SHALL be updated to verify that `process(typeElement)` invokes all five stages in order and that the result of each upstream stage is threaded into the next, including the `MapperGraph` passed from `SeedGraph` to `DumpGraph` along with the originating `TypeElement`.

#### Scenario: Pipeline threads results through all five stages
- **WHEN** `PipelineSpec` runs the in-order test
- **THEN** the assertion verifies the strict order: `DiscoverAbstractMethods` → `DiscoverMappings` → `ValidateNoDuplicateTargets` → `SeedGraph` → `DumpGraph`
- **AND** the `MapperGraph` produced by `SeedGraph` is the same instance passed to `DumpGraph`
- **AND** the originating `TypeElement` is passed to `DumpGraph`

### Requirement: Single-aspect graph-shape specs SHALL exist
The processor module SHALL contain Spock specs that each pin one aspect of the seeded graph by compiling a small fixture mapper through Google Compile Testing and asserting against the resulting `MapperGraph` via the Groovy extension module DSL. Each aspect SHALL have its own spec file. Aspects covered SHALL include at least:
- a parameter becomes a source-root node,
- a return type becomes a target-root node,
- a `@Map` directive seeds a source chain, a target chain, and a bridging edge,
- a dotted `source` produces a multi-segment source chain,
- a dotted `target` produces a multi-segment target chain,
- two directives on the same method with a shared path prefix do not produce duplicate nodes or edges,
- two methods on the same mapper produce two disjoint method-scoped subgraphs in the same `MapperGraph`,
- an inherited generic method (with substituted types) is seeded the same as a directly declared method.

#### Scenario: One spec per aspect
- **WHEN** the test suite runs
- **THEN** each of the listed aspects is covered by exactly one Spock spec file
- **AND** failures point at the aspect that broke

### Requirement: Golden DOT specs SHALL guard the rendering pipeline
The processor module SHALL contain a small set of golden-DOT specs that compile a fixture mapper, dump the graph with `-Apercolate.debug.graphs=true`, read the resulting `<MapperFQN>.seed.dot` file from `SOURCE_OUTPUT`, and assert byte-equality against a checked-in golden file under `processor/src/test/resources/golden-graphs/`. The set SHALL be deliberately small and chosen for *rendering* concerns rather than per-aspect coverage:
- option-on emits the file with the expected name and contents (a representative two-method mapper);
- option-off emits no file;
- DOT label escaping for special characters;
- per-method cluster boundaries.

A documented developer workflow SHALL exist for re-generating the goldens (e.g., a Gradle task or a `-PupdateGoldens=true` flag) and the goldens SHALL be reviewed in PRs like code.

#### Scenario: Option-on golden test exists
- **WHEN** the test suite runs the option-on golden test
- **THEN** a fixture mapper is compiled with `-Apercolate.debug.graphs=true`
- **AND** the produced `<MapperFQN>.seed.dot` is byte-equal to `processor/src/test/resources/golden-graphs/<name>.dot`

#### Scenario: Option-off golden test exists
- **WHEN** the test suite runs the option-off golden test
- **THEN** a fixture mapper is compiled without `-Apercolate.debug.graphs`
- **AND** no `<MapperFQN>.seed.dot` resource exists in the SOURCE_OUTPUT of the compilation

### Requirement: Spock Groovy extension module SHALL exist for graph assertions
The `test` source set of the processor module SHALL contain a Groovy extension module that decorates `MapperGraph` (and related value types) with assertion methods used by specs. The module SHALL be registered via `processor/src/test/resources/META-INF/services/org.codehaus.groovy.runtime.ExtensionModule`. The production `MapperGraph` class SHALL NOT carry test-only methods.

#### Scenario: Extension module is registered in test resources
- **WHEN** the build assembles the test classpath
- **THEN** `META-INF/services/org.codehaus.groovy.runtime.ExtensionModule` is present in the test resources
- **AND** it lists at least one extension class targeting `MapperGraph`

#### Scenario: Production MapperGraph carries no test-only API
- **WHEN** the source of `MapperGraph` is inspected
- **THEN** it does not contain methods whose only purpose is test assertion (e.g., `hasNode`, `hasEdge`, `hasNoEdges`)
- **AND** all such methods live on extension classes in the `test` source set
