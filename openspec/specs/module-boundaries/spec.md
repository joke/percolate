# Module Boundaries Spec

## Purpose

Defines the declared, build-enforced separation between percolate's modules, so the boundaries the dependency graph and conventions imply cannot silently erode. The `processor` engine partitions its packages into a public surface and an `internal` region. Every boundary rule — the inter-module layering, the rule that the engine↔strategy line is crossed only through `spi`, strategy myopia (no graph dependency), `*Stage` naming, the `javax.lang.model.util` confinement, and the rule that no code outside the engine reaches into `processor` internals — is authored once in the unpublished `architecture-tests` rule library and evaluated inside each consuming module against that module's own classes. Neither of the two earlier mechanisms survives: no module imports every sibling's classes off a union classpath, and no module wraps a `testFixtures`-published rule builder in a specification of its own. How that distribution works, and the two properties it depends on, are specified separately in the `architecture-rule-distribution` capability. Engine-contract tests live in `processor` against a `FakeStrategy`, not in a strategy module. ArchUnit was chosen over Jigsaw because the leaks are test-scope and build-config and the rules are convention-level, which JPMS cannot express on this annotation-processor + Groovy/Spock + compile-testing stack.

Package acyclicity is deliberately **not** enforced. Its former rule sliced on the first package segment, producing one slice per module, so it never saw an intra-module cycle; and every cross-module pair it did see is already forbidden by the layering rules above together with the acyclic Gradle module graph.

Method shape is deliberately **not** specified here. The testability doctrine — every authored method must be interceptable by a test double — is unchanged, but it constrains a property of one declaration rather than an edge between packages, so ArchUnit is the wrong owner for it: the question *does no subclass use this protected method?* cannot be answered from one module's own classes, and cannot be answered at all for `spi`, a published contract whose implementors live outside the build. Those rules moved wholesale to PMD, which reads authored source per compilation unit and checks a declared marker instead of inferring one. See the `method-shape-analysis` capability.
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

No production or test class outside the `processor` module SHALL depend on any `processor` `internal`
package. Other modules SHALL reach the engine only through its public surface and through `spi`. This makes
"reaching into engine internals" a build-checkable violation rather than a matter of convention.

The rule SHALL exist exactly once, in the shared architecture-rule library, and SHALL be evaluated inside
each module that applies the rule runner against that module's own source-set output — not by a central
module reaching into sibling modules' build output directories, and not by a per-module specification that
wraps a shared rule builder. The earlier arrangement, in which `architecture-tests` published an
`EncapsulationRules` builder as `testFixtures` and `strategies-builtin`, `reactor`, and `reactor-blocking`
each carried a near-identical `EngineEncapsulationSpec` around it, SHALL be replaced: one library rule now
runs everywhere the runner is applied, which is strictly broader coverage than the three modules that
previously carried a copy.

#### Scenario: External code may not import engine internals
- **WHEN** any module that applies the rule runner — including `strategies-builtin`, `reactor`, and
  `reactor-blocking` — runs its architecture check against its own classes
- **THEN** no class in that module depends on a `processor` `internal` package, and a newly introduced such
  dependency fails that module's own build

#### Scenario: The rule is shared, not duplicated per module
- **WHEN** the rule-construction logic backing this check is located
- **THEN** it exists once as a rule in the `archRules` library, and no module declares a specification,
  fixture, or builder of its own for it

#### Scenario: No test fixture carries the rule
- **WHEN** `architecture-tests` is inspected
- **THEN** it publishes no `testFixtures` variant and no `EncapsulationRules` class, and no module depends
  on such a fixture
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

An `ExpansionStrategy` implementation SHALL NOT depend on the engine's internal packages, SHALL NOT receive or
traverse the graph, and SHALL NOT read a candidate snapshot. A strategy decides locally from its `Demand` and its
`ResolveCtx` and returns `Offer`s as plain data. Returning a **refusal** is returning data and SHALL NOT be
treated as a side effect; a strategy SHALL NOT emit a diagnostic, write to the `Messager`, or mutate any shared
state.

A `DirectiveReader` is explicitly **not** myopic in the same sense — it sees a whole mapper method — but it SHALL
likewise depend on no engine internal package and SHALL communicate only through its `DirectiveSink`.

#### Scenario: A strategy reaches no engine internal
- **WHEN** the architecture test analyses the strategy modules
- **THEN** no strategy class depends on an engine internal package

#### Scenario: A strategy emits no diagnostic
- **WHEN** the architecture test analyses the strategy modules
- **THEN** no strategy class depends on the `Messager` or on the processor's diagnostic types

#### Scenario: A reader reaches no engine internal
- **WHEN** the architecture test analyses the directive readers
- **THEN** no reader depends on an engine internal package, and each communicates only through its sink

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
- the `io.github.joke.percolate.spi.ResolveCtx` interface itself, which declares `types()`/`elements()` so
  that a real-javac production implementation (`CompileResolveCtx`) can answer every seam question by
  delegating through them; strategy and engine *production* code never calls them, and no test now
  constructs a `ResolveCtx` over a `Types`/`Elements` pair (`ResolveCtxBuilder` is deleted and the
  `strategies-builtin` unit specs mock the seam).

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

### Requirement: Mapping annotations are read only by directive readers

No class in the `processor` module SHALL depend on a mapping annotation — `@Map`, `@MapList`, `@MapEnum`,
`@MapEnumList`, or `@Ambient`. The single permitted exception SHALL be the processor's `@Mapper` step, which
reads `@Mapper` to decide which types to generate for.

An architecture test SHALL enforce this over the whole `processor` module, matching the annotations by their
exact package rather than by a prefix, so it cannot silently pass by matching nothing. Its failure message
SHALL state the rule it protects: user-facing annotations are read at the directive-reader boundary and never
inside the processor.

#### Scenario: No processor class imports a mapping annotation
- **WHEN** the architecture test analyses the `processor` module
- **THEN** it finds no dependency on `@Map`, `@MapList`, `@MapEnum`, `@MapEnumList`, or `@Ambient`

#### Scenario: The mapper step may read @Mapper
- **WHEN** the architecture test analyses the class implementing the `@Mapper` processing step
- **THEN** its dependency on `@Mapper` is permitted

#### Scenario: The rule matches the annotations exactly
- **WHEN** the rule's package predicate is inspected
- **THEN** it matches the annotations' own package without a trailing wildcard, so it selects the annotation types and not every percolate class

### Requirement: The engine reads no annotations at all

No class in the engine SHALL call `getAnnotationMirrors()`, `getAnnotation(Class)`, or otherwise inspect an
annotation — neither the graph package, nor the expansion collaborators, nor the generation collaborators.
Annotation reading belongs to the directive readers and to the single nullability resolver, both outside the
engine.

This is stated separately from the rule above because an annotation can be matched by name without importing
it, which an import-based rule cannot catch.

#### Scenario: The engine calls no annotation-reading API
- **WHEN** the architecture test analyses the engine packages
- **THEN** it finds no call to `getAnnotationMirrors()` or `getAnnotation(Class)`

#### Scenario: The nullability resolver is unaffected
- **WHEN** the architecture test analyses the nullability resolver
- **THEN** its annotation reading is permitted, because it lies outside the engine packages

#### Scenario: A name-matched annotation is caught
- **WHEN** an engine class matches an annotation by simple name without importing it
- **THEN** the architecture test fails, because the rule targets the reading API rather than the import

### Requirement: Stage implementations are named with a Stage suffix

The architecture suite SHALL enforce that every class implementing the engine's `Stage` type has a simple
name ending in `Stage`. The rule SHALL be evaluated in the module that declares the implementation, and
SHALL resolve the `Stage` supertype through the evaluating module's classpath rather than requiring the
engine's classes to be imported alongside it.

#### Scenario: Stage implementations are named *Stage
- **WHEN** the architecture suite finds classes implementing `Stage`
- **THEN** each has a simple name ending in `Stage`

#### Scenario: A misnamed Stage implementation fails the build
- **WHEN** a class implements `Stage` under a name that does not end in `Stage`
- **THEN** its owning module's architecture check fails, flagging the class
