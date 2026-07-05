# Module Boundaries Spec

## Purpose

Defines the declared, build-enforced separation between percolate's modules, so the boundaries the dependency graph and conventions imply cannot silently erode. The `processor` engine partitions its packages into a public surface and an `internal` region; an architecture suite (ArchUnit, in a dedicated unpublished `architecture-tests` module) imports every module's classes and fails the build when a boundary is crossed — the inter-module layering, the rule that the engine↔strategy line is crossed only through `spi`, strategy myopia (no graph dependency), `*Stage` naming, acyclicity, and the rule that no code outside the engine reaches into `processor` internals. Engine-contract tests live in `processor` against a `FakeStrategy`, not in a strategy module. ArchUnit was chosen over Jigsaw because the leaks are test-scope and build-config and the rules are convention-level, which JPMS cannot express on this annotation-processor + Groovy/Spock + compile-testing stack.

## Requirements

### Requirement: The processor declares an explicit api/internal boundary

The `processor` engine SHALL partition its packages into a narrow public surface and an `internal` region. Engine machinery that other modules have no business touching — the bipartite graph, the expansion/discovery/generate stages, and plan-extraction internals — SHALL reside under a package segment named `internal`. The packages other modules legitimately depend on (the processor entry point and any types it must expose) SHALL remain outside `internal`. The split SHALL be a structural refactor only: it SHALL NOT change the processor's runtime behaviour, the generated-mapper contract, or the annotations/processing surface a consumer sees.

#### Scenario: Engine internals live under an internal segment
- **WHEN** the `processor` module's packages are inspected
- **THEN** the graph, stages, and plan-extraction implementation types reside in a package whose name contains an `internal` segment, and the processor's externally-used surface does not

#### Scenario: The split changes no behaviour
- **WHEN** the engine's end-to-end and unit suites run after the package move
- **THEN** they pass unchanged, confirming the refactor altered package placement only, not generated output or processing behaviour

### Requirement: Engine internals are encapsulated from other modules

No production or test class outside the `processor` module SHALL depend on any `processor` `internal` package. Other modules SHALL reach the engine only through its public surface and through `spi`. This makes "reaching into engine internals" a build-checkable violation rather than a matter of convention.

#### Scenario: External code may not import engine internals
- **WHEN** the architecture suite inspects every module other than `processor`
- **THEN** no class in those modules — production or test — depends on a `processor` `internal` package, and a newly introduced such dependency fails the build

### Requirement: Inter-module dependencies obey the declared layering

The architecture suite SHALL enforce the module layering. `annotations` SHALL depend on nothing within percolate. `spi` SHALL depend only on `annotations`. `processor` SHALL depend on `spi` (and `annotations`) and SHALL NOT depend on any strategy module (`strategies-builtin`, `reactor`, `reactor-blocking`) in any scope. Strategy modules SHALL depend on `spi` in production and MAY depend on `processor` and `test-foundation` only in test scope. `test-foundation` SHALL depend on `processor` and SHALL NOT depend on any strategy module.

#### Scenario: The engine has no edge to any strategy
- **WHEN** the architecture suite analyses `processor`'s production and test classes
- **THEN** none of them depend on a class in any strategy module

#### Scenario: The harness stays strategy-agnostic
- **WHEN** the architecture suite analyses `test-foundation`
- **THEN** it depends on `processor` but on no strategy module

#### Scenario: A strategy's production code reaches the engine only through spi
- **WHEN** the architecture suite analyses a strategy module's production classes
- **THEN** they depend on `spi` and on the consumer's own types, and not on `processor` at all in production scope

### Requirement: Strategies stay myopic

The architecture suite SHALL enforce that strategy implementations make only local decisions. Any class implementing an `spi` strategy type (e.g. `ExpansionStrategy`, `Strategy`) SHALL NOT depend on the engine's graph types. A strategy needing graph access is the red flag this rule catches.

#### Scenario: A strategy may not touch the graph
- **WHEN** the architecture suite finds the implementations of the `spi` strategy interfaces
- **THEN** none of them depends on the engine's graph package, and introducing such a dependency fails the build

### Requirement: Structural naming and acyclicity are enforced

The architecture suite SHALL enforce that every class implementing the engine's `Stage` type is named with a `*Stage` suffix, and that no package participates in a dependency cycle.

#### Scenario: Stage implementations are named *Stage
- **WHEN** the architecture suite finds classes implementing `Stage`
- **THEN** each has a simple name ending in `Stage`

#### Scenario: There are no package cycles
- **WHEN** the architecture suite slices percolate's packages
- **THEN** it finds no cyclic dependency between them

### Requirement: javax.lang.model.util (Types/Elements) is confined to the type-boundary packages

The architecture suite SHALL enforce that `javax.lang.model.util.Types` and `javax.lang.model.util.Elements`
— the two compiler-service classes that need a live compile environment to answer — are depended on **only**
by the enumerated type-boundary regions:

- the bare `io.github.joke.percolate.processor` package (the Dagger wiring — `ProcessorModule`/`MapperStep`
  and their generated `*_Factory`/`DaggerProcessorComponent` siblings, which mention `Types`/`Elements` in
  constructor/field types purely as DI plumbing),
- `processor.internal.stages.expand` (the type-query seam implementation, `CompileResolveCtx`),
- `processor.internal.stages.discover` (the discovery adapter),
- `processor.internal.stages.generate` (codegen emission),
- `processor.nullability` (the nullability resolver), and
- the `io.github.joke.percolate.spi.ResolveCtx` interface itself, which declares `types()`/`elements()` as a
  transitional bridge kept for the real-javac-backed `strategies-builtin` fixtures (`PrivateTypeUniverse`,
  `ResolveCtxBuilder`) not yet rewritten against a mocked `ResolveCtx` (deferred to
  `features-as-documentation`).

This is deliberately narrower than a blanket ban on all of `javax.lang.model` (which would also outlaw
holding `TypeMirror`/`TypeElement`/`Element` values as opaque pass-through tokens everywhere — the design
this rule protects): only the two compiler-**service** classes are confined; a `TypeMirror` or `Element`
value may be held, typed, and passed by any engine or strategy class, so long as no `Types`/`Elements`
method is invoked outside the boundary. Everywhere else — the engine graph/stages/plan-extraction, the
strategies (`strategies-builtin`, `reactor`, `reactor-blocking`), and the `Containers`/`TypeProbe` helpers —
asks its type questions through the `ResolveCtx` seam instead. A newly introduced `Types`/`Elements`
dependency outside the enumerated boundary SHALL fail the build.

#### Scenario: Engine and strategies depend on no Types/Elements
- **WHEN** the architecture suite analyses the engine graph/stages/plan packages (outside the enumerated
  boundary sub-packages), every strategy module, and the `Containers`/`TypeProbe` helpers
- **THEN** none of them depends on `javax.lang.model.util.Types` or `javax.lang.model.util.Elements`; type
  questions are routed through the `ResolveCtx` seam

#### Scenario: The boundary packages and ResolveCtx may depend on Types/Elements
- **WHEN** the architecture suite analyses the bare `processor` package, the seam implementation, the
  discovery adapter, the codegen-emit package, the nullability resolver, and the `ResolveCtx` interface
- **THEN** their dependency on `javax.lang.model.util.Types`/`Elements` is permitted

#### Scenario: A new leak outside the boundary fails the build
- **WHEN** a class outside the enumerated boundary is given a new dependency on `javax.lang.model.util.Types` or `Elements`
- **THEN** the architecture suite fails, flagging the out-of-boundary dependency

### Requirement: Engine-contract tests do not live in a strategy module

An end-to-end test that asserts engine behaviour — expansion, graph self-seeding, demand-driven leaf minting, weaving, cost selection, or realisation diagnostics — SHALL NOT reside in a strategy module. Such a test SHALL live in `processor` and SHALL be driven by a `FakeStrategy` rather than by ServiceLoading the real builtins. In particular, `SelfSeedExpansionSpec` SHALL be relocated from `strategies-builtin` into `processor`, and any sibling spec that the engine-internals encapsulation rule now makes illegal in a strategy module SHALL move with it.

#### Scenario: The self-seeding expansion spec is an engine test in the engine module
- **WHEN** the test suites are located after this change
- **THEN** `SelfSeedExpansionSpec` resides in `processor`, drives expansion through a `FakeStrategy`, and `strategies-builtin` no longer hosts any engine-only expansion spec

#### Scenario: A strategy module hosts only its own atom and output tests
- **WHEN** the remaining end-to-end specs in `strategies-builtin` are inspected
- **THEN** each asserts a builtin strategy's own atom, output, or targeted diagnostic — not a pure engine contract that the encapsulation rule would forbid
