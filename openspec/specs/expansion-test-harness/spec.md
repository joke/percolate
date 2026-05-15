# expansion-test-harness Specification

## Purpose
TBD - created by archiving change add-graph-expansion-tests. Update Purpose after archive.
## Requirements
### Requirement: assembleExpansionPipeline factory

The class `io.github.joke.percolate.processor.ProcessorModule` SHALL expose a `public static` method `assembleExpansionPipeline(List<Bridge> bridges, List<SourceStep> sourceSteps, List<GroupTarget> groupTargets)` that returns a wired `ExpandStage` instance with the three expansion phases (`ResolveSourceChainsPhase`, `ResolveTargetChainsPhase`, `BridgeSourceToTargetPhase`) constructed and configured with the supplied strategy lists.

The three existing `@Provides` methods (`bridgeStrategies`, `sourceSteps`, `groupTargets`) plus any provider of `ExpandStage` SHALL be reachable through this factory: production wiring SHALL invoke `assembleExpansionPipeline(...)` after the `ServiceLoader` lookups complete, rather than constructing `ExpandStage` independently. Test code SHALL invoke the same factory directly with explicit lists.

`assembleExpansionPipeline` SHALL be the single source of truth for `ExpandStage` composition. Adding a new constructor parameter to `ExpandStage` or to any of its phases SHALL force callers to update through this one method.

#### Scenario: Factory returns a wired ExpandStage
- **WHEN** `ProcessorModule.assembleExpansionPipeline(bridges, sourceSteps, groupTargets)` is invoked with non-null lists
- **THEN** a non-null `ExpandStage` is returned
- **AND** invoking `run(ctx)` on it produces the same expanded graph that production Dagger wiring would produce given the same inputs

#### Scenario: Dagger wiring delegates to the factory
- **WHEN** the source of `ProcessorModule` is inspected
- **THEN** the provider of `ExpandStage` (or the place where `ExpandStage` is constructed) calls `assembleExpansionPipeline(...)` with the SPI-loaded lists
- **AND** there is no separate construction of `ExpandStage` outside `assembleExpansionPipeline`

### Requirement: ExpansionHarness two-mode entry points

The class `io.github.joke.percolate.processor.test.ExpansionHarness` in `processor/src/test/groovy/` (Groovy source) SHALL expose two `public static` entry points:

- `ExpansionResult expand(MapperGraph seed)` — loads `Bridge`, `SourceStep`, and `GroupTarget` lists via `ServiceLoader` from the current thread's context class loader (production parity) and runs the expansion pipeline obtained from `ProcessorModule.assembleExpansionPipeline(...)`. Reserved for capability dog-fooding (real built-in strategies).
- `ExpansionResult expand(MapperGraph seed, List<Bridge> bridges, List<SourceStep> sourceSteps, List<GroupTarget> groupTargets)` — bypasses `ServiceLoader` and runs the expansion pipeline directly with the supplied lists. Used by engine-algebra property tests (which supply fake strategies) and failure-mode tests (which supply controlled bundles).

Both entry points SHALL go through `assembleExpansionPipeline(...)` to obtain the wired stage. Both entry points SHALL invoke `ValidatePathsPhase` and expose the resulting diagnostics on the returned `ExpansionResult`. Neither entry point SHALL assert auto-invariants; invariant checks are exposed on `ExpansionResult` for tests to opt into explicitly.

#### Scenario: SPI mode loads via ServiceLoader for capability tests
- **WHEN** `ExpansionHarness.expand(seed)` is called from a capability spec
- **THEN** the strategy lists are loaded via `ServiceLoader.load(...)`
- **AND** the same strategies that would run in production for the current classpath are used

#### Scenario: Explicit mode bypasses ServiceLoader for engine tests
- **WHEN** `ExpansionHarness.expand(seed, bridges, sourceSteps, groupTargets)` is called from a property or failure-mode test
- **THEN** no call to `ServiceLoader` is made
- **AND** exactly the supplied lists are used

#### Scenario: Both modes share pipeline assembly
- **WHEN** either entry point is invoked
- **THEN** the wired `ExpandStage` is obtained via `ProcessorModule.assembleExpansionPipeline(...)`
- **AND** no other path constructs `ExpandStage` in test code

### Requirement: TypeUniverse fixture

The class `io.github.joke.percolate.processor.test.TypeUniverse` in `processor/src/test/groovy/` (Groovy source) SHALL expose a fixed set of `javax.lang.model.type.TypeMirror` instances backed by a single, JVM-lifetime `com.sun.source.util.JavacTask` held in a `static final` field. The task SHALL be obtained via `ToolProvider.getSystemJavaCompiler().getTask(null, null, null, null, null, null)` (no sources, no diagnostic listener) and cast to `com.sun.source.util.JavacTask`. The fixture SHALL NOT call `task.parse()`, `task.analyze()`, or `task.call()`; bootstrap class resolution happens lazily inside `Elements.getTypeElement(...)`. The fixture SHALL NOT use `com.google.testing.compile` for type sourcing.

The set SHALL include at minimum: `int`, `long`, the primitive wrapper types `Integer` and `Long`, `String`, one user-defined enum, `java.time.LocalDateTime`, `java.time.Instant`, and `java.util.List<E>` where `E` is parameterised over the universe. All `TypeMirror` instances SHALL come from the same `Types` and `Elements` instance — namely those returned by `task.getTypes()` and `task.getElements()` — so that javac's equality semantics hold across the universe.

`TypeUniverse` SHALL initialise javac at most once per JVM (modulo separate class loaders). Test classes SHALL access `TypeUniverse` through `static` fields initialised on first reference.

#### Scenario: Universe types share an Elements
- **WHEN** `TypeUniverse.STRING` and `TypeUniverse.INTEGER` are obtained
- **THEN** both `TypeMirror` instances were resolved against the same `javax.lang.model.util.Elements`
- **AND** equality and `Types.isSameType(...)` behave consistently across the set

#### Scenario: Initialisation is amortised
- **WHEN** any two distinct test methods access `TypeUniverse` fields
- **THEN** the underlying `JavacTask` is constructed at most once across the JVM

#### Scenario: Type sourcing uses only public javac API
- **WHEN** the source of `TypeUniverse` is inspected
- **THEN** it imports `com.sun.source.util.JavacTask` and no class from `com.sun.tools.javac.*`
- **AND** it never invokes `task.parse()`, `task.analyze()`, or `task.call()`
- **AND** the `JavacTask` is held in a field whose lifetime equals the JVM's so that captured `TypeMirror` instances remain valid

### Requirement: Invariant checks exposed on `ExpansionResult`

`ExpansionResult` SHALL expose four invariant-related checks that tests MAY call explicitly. The harness itself SHALL NOT assert these before returning — under explicit opt-in there is no auto-invariant to opt out of, so the previously deferred opt-out mechanism is closed as not needed.

- **Convergence flag** — `result.converged()` returns `false` when `ExpandStage` emitted an "Expansion did not converge after N rounds" diagnostic, otherwise `true`.
- **Idempotence stub** — `result.isIdempotent()` is reserved for a structural same-graph check; the current implementation returns `true` unconditionally. Wiring it to a real comparison is a future concern.
- **Identity collapse** — `result.hasIdentityCollisions()` SHALL return `true` iff two distinct `Node` instances in the result share the same `Node.id()` (which encodes `(scope, location, type)`).
- **Orphan REALISED nodes** — `result.hasOrphanRealisedNodes()` SHALL return `true` iff any `REALISED` edge endpoint is unreachable, via `REALISED`/`MARKER`/`SUB_SEED` edges, from any `SEED`-edge endpoint.

The `roundCount()` accessor SHALL be present but its value is currently a placeholder (`1`) until `ExpandStage` publishes rounds through `MapperContext`. Tests SHOULD NOT rely on the value today.

#### Scenario: Identity-collapse check is structural and total
- **WHEN** `ExpansionHarness.expand(seed, ...)` returns
- **THEN** `result.hasIdentityCollisions()` examines every node in the expanded graph
- **AND** returns `true` iff at least two distinct nodes share `Node.id()`

#### Scenario: Orphan-detection respects the traversable lattice
- **WHEN** a result graph contains a `REALISED` edge whose endpoints have no `REALISED`/`MARKER`/`SUB_SEED` path to any `SEED`-edge endpoint
- **THEN** `result.hasOrphanRealisedNodes()` returns `true`
- **AND** a result graph whose `REALISED` edges are all anchored to `SEED` endpoints returns `false`

#### Scenario: Convergence reflects ExpandStage diagnostic
- **WHEN** the harness captures a diagnostic containing "did not converge"
- **THEN** `result.converged()` returns `false`
- **AND** `result.diagnostics()` contains that message verbatim

### Requirement: DOT rendering inlined in `ExpansionAssertions` failure messages

Every failure message produced by `ExpansionAssertions` (in `processor/src/test/groovy/io/github/joke/percolate/processor/test/`), including `reachable(...)`, `reportedError(...)`, and `Chain.forSeedEdge(...)`, SHALL include the DOT rendering of the expanded graph (produced by `io.github.joke.percolate.processor.graph.DotRenderer`) inlined in the message. No sibling file SHALL be written. If the result has no mapper type captured, a placeholder string SHALL be substituted for the DOT block.

If `ExpandStage` aborts before producing a complete graph (cycle detected, budget exhausted), the partial graph that the harness captured SHALL still be DOT-rendered and attached.

#### Scenario: Assertion failure carries DOT
- **WHEN** an `ExpansionAssertions.reachable(from, to)` assertion fails
- **THEN** the thrown `AssertionError`'s message contains the DOT rendering of the expanded graph

#### Scenario: Result without mapper type yields a placeholder block
- **WHEN** an `ExpansionResult` was constructed without a `TypeElement` (legacy `of(...)` overloads, or harness paths that supply `null`)
- **THEN** `result.dotRender()` returns a placeholder string instead of throwing
- **AND** assertion failure messages still produce a usable error

### Requirement: Capability specs in Spock data-driven form

The processor module SHALL contain a Spock specification at `processor/src/test/groovy/io/github/joke/percolate/processor/stages/expand/ExpansionCapabilitiesSpec.groovy` that asserts, for each built-in strategy that produces realised edges today, that a representative seed graph produces a reachable path between the named seed endpoints.

The spec SHALL use Spock's `@Unroll` and `where:` data-table style so that each row produces an independently named test failure. The spec SHALL use the **SPI-loaded mode** of `ExpansionHarness.expand(seed)`, mirroring production strategy loading. This is the dog-fooding layer: the inlined harness is exercised against the real production strategies, and each row makes a concrete claim about what those strategies enable.

Seed graphs in this spec SHALL be constructed via direct `MapperGraph` + typed `Node`/`Edge.elementSeed(...)` calls, not via a fluent DSL. Helper builders in `GraphFixtures` MAY be used for common shapes.

Each row SHALL assert reachability via `ExpansionAssertions.assertThat(result).reachable(...)` (asserting on the kind of structural outcome, not on diagnostic text). At minimum, `DirectAssign` SHALL be covered. Strategies that do not yet produce realised edges (because they are incomplete, stubs, or require setups the harness cannot replicate) SHALL be tracked in `tasks.md` as incremental work rather than as required rows; adding such a row when the strategy becomes capable is a separate change.

#### Scenario: At least one built-in strategy is dog-fooded
- **WHEN** `ExpansionCapabilitiesSpec` is executed
- **THEN** at least one row asserts `reachable(...)` for a built-in strategy
- **AND** that row uses SPI mode (no explicit strategy lists passed to the harness)

#### Scenario: Each capability gets a named row
- **WHEN** `ExpansionCapabilitiesSpec` is executed
- **THEN** at least one test method uses `@Unroll` with a `where:` table
- **AND** each row's scenario label appears in the test output as a distinct test name

#### Scenario: No message-text assertions
- **WHEN** the source of `ExpansionCapabilitiesSpec` is inspected
- **THEN** no assertion compares against the exact string contents of any diagnostic message

### Requirement: Failure-mode specs in Spock data-driven form

The processor module SHALL contain a Spock specification at `processor/src/test/groovy/io/github/joke/percolate/processor/stages/expand/ExpansionFailureModesSpec.groovy` that asserts, for each rejection case, that the expansion engine reports the correct diagnostic kind.

The spec SHALL cover at minimum:

- **No-path failure** — a directive whose source path has no compatible strategy → the harness exposes a "no realised path" diagnostic on `result.diagnostics()`.
- **Cycle failure** — a seed graph that induces a `SEED + SUB_SEED` cycle → the harness exposes the cycle diagnostic. The fixture SHALL construct the cyclic seed via `GraphFixtures.subSeedCycle()` (or equivalent direct construction) and run the harness in **explicit mode with a no-op fake bundle**, so the cycle detection is exercised independently of any production strategy.
- **Round-cap failure** — a strategy that prevents convergence → the harness exposes the round-cap diagnostic ("Expansion did not converge after N rounds"). The fixture SHALL use a new `DivergentBridge` fake (located in `processor/src/test/groovy/.../properties/fakes/`) whose `bridge(from, to, ctx)` introduces a fresh synthetic intermediate node on every invocation, so the engine never reaches a fixed point within `MAX_EXPANSION_ROUNDS = 64`. The fixture SHALL run the harness in **explicit mode** with a bundle containing `DivergentBridge`.

Each scenario SHALL assert on the **presence of a diagnostic of the expected kind**, identified by a keyword match against `result.diagnostics()`. No scenario SHALL assert on the exact diagnostic message text.

#### Scenario: No-path failure
- **WHEN** a seed declares a source-target pair with no strategy chain between the types and is expanded with a no-op fake bundle
- **THEN** `result.diagnostics()` contains a message containing "no realised path" (case-insensitive)

#### Scenario: Cycle failure
- **WHEN** a seed graph constructed via `GraphFixtures.subSeedCycle()` is expanded with a no-op fake bundle
- **THEN** `result.diagnostics()` contains a message containing "cycle" (case-insensitive)

#### Scenario: Round-cap failure
- **WHEN** a seed graph is expanded with an explicit bundle containing `DivergentBridge`
- **THEN** `result.diagnostics()` contains a message containing "did not converge"
- **AND** `result.converged()` returns `false`

#### Scenario: No message-text assertions
- **WHEN** the source of `ExpansionFailureModesSpec` is inspected
- **THEN** no assertion compares against the exact full string contents of any diagnostic message — only keyword presence

### Requirement: jqwik property tests

The processor module SHALL contain jqwik-driven property specs (Groovy sources) at `processor/src/test/groovy/io/github/joke/percolate/processor/stages/expand/properties/` covering the algebraic contract of `expand`. Each spec is a Groovy class named `*Spec.groovy` that `extends ExpansionPropertyBase` — jqwik provides the property-test engine via `@net.jqwik.api.Property`-annotated methods on the same class. The seven specs are:

- **`DeterminismSpec`**: `expand(g, S) == expand(g, S)` across repeated invocation.
- **`IdempotenceSpec`**: `expand(expand(g, S), S) == expand(g, S)`.
- **`OrderIndependenceSpec`**: for any permutation `S'` of `S`, `expand(g, S) == expand(g, S')`.
- **`MonotonicitySpec`**: for any `S₁ ⊆ S₂`, `edges(expand(g, S₁)) ⊆ edges(expand(g, S₂))`.
- **`IdentityCollapseSpec`**: across many generated seed/strategy pairs, no two distinct nodes share `(scope, location, type)`.
- **`DisjointAdditivitySpec`**: for disjoint `g₁`, `g₂`, `expand(g₁ ⊕ g₂, S) == expand(g₁, S) ⊕ expand(g₂, S)`.
- **`EmptyStrategyIdentitySpec`**: `expand(g, ∅)` equals `g` modulo non-strategy phases.

All property specs SHALL use `ExpansionHarness.expand(seed, ..., lists)` (**explicit mode**) with strategy lists drawn from the **fake-strategy alphabet** defined under "Fake strategies as the engine-algebra input alphabet". Property specs SHALL NOT use SPI-loaded production strategies — production strategy correctness is asserted by `ExpansionCapabilitiesSpec`, separately, so that a regression in one production strategy does not silently invalidate the engine algebra tests.

`@Property` method bodies SHALL be plain Groovy assertions (not Spock `given:/when:/then:` blocks — those AST transforms are not visible to jqwik's JUnit Platform engine). Spock feature methods (`def 'name'() { ... }`) MAY co-exist on the same class for non-property assertions.

Generators SHALL be `@net.jqwik.api.Provide` methods on `ExpansionPropertyBase` and SHALL produce `MapperGraph` instances directly (via `GraphFixtures` or inline construction) constrained to `TypeUniverse`. Generators SHALL NOT route through a `MapperSpec` / `SeedDsl` indirection. Plain `java.util.Random` is not a substitute because it cannot reproduce a shrunk counterexample from a pinned seed. Each `@Provide` SHALL construct a fresh `MapperGraph` per invocation (jqwik may call generators repeatedly during shrinking; mutable graphs MUST NOT be shared across iterations).

Fakes drawn into the property alphabet SHALL be stateless across invocations. `DivergentBridge` (which holds an `AtomicInteger` counter to force divergence) SHALL NOT appear in any `@Provide` method's output; it is reserved for `ExpansionFailureModesSpec` only.

#### Scenario: Seven property specs exist
- **WHEN** the directory `processor/src/test/groovy/.../expand/properties/` is inspected
- **THEN** it contains Groovy class files for each of the seven specs listed above
- **AND** each declares at least one method annotated with `net.jqwik.api.@Property`

#### Scenario: Properties extend the property base
- **WHEN** the source of any property spec is inspected
- **THEN** the class `extends ExpansionPropertyBase`
- **AND** `ExpansionPropertyBase` is a plain Groovy class (no `extends` clause) — jqwik does not discover `@Property` methods on Spock `Specification` subclasses (D2 probe confirmed this)

#### Scenario: Properties use explicit mode with fakes
- **WHEN** the source of any property spec is inspected
- **THEN** every `ExpansionHarness.expand(...)` call passes explicit strategy lists
- **AND** every strategy passed is an instance of a class under `processor/src/test/groovy/.../properties/fakes/`

#### Scenario: DivergentBridge is excluded from the property alphabet
- **WHEN** the `@Provide` methods on `ExpansionPropertyBase` are inspected
- **THEN** none returns an `Arbitrary<List<Bridge>>` (or `Arbitrary<Bridge>`) whose output may contain a `DivergentBridge` instance

### Requirement: jqwik configuration

`processor/build.gradle` SHALL configure jqwik with a default `@Property(tries = 100)` and store a property database under `build/jqwik-database` so that shrunken counterexamples are replayed deterministically. The default-tries configuration SHALL live as a class-level `@net.jqwik.api.PropertyDefaults(tries = 100)` annotation on `ExpansionPropertyBase` — not on individual specs, not in `jqwik.properties`. Individual `@Property` methods MAY override `tries` for slow properties; the base-class value is the project-wide default.

Each `@Property` method on a property spec SHALL pin a default seed via `@Property(seed = '<long>')` (jqwik requires a `long`-parseable string) to make CI failures locally reproducible on fresh databases.

#### Scenario: jqwik database is configured
- **WHEN** `processor/build.gradle` is inspected
- **THEN** jqwik's database directory is configured to `build/jqwik-database` (via `-Djqwik.database.directory=...` or equivalent)

#### Scenario: PropertyDefaults lives on the base class
- **WHEN** the source of `ExpansionPropertyBase` is inspected
- **THEN** the class carries `@net.jqwik.api.PropertyDefaults(tries = 100)`
- **AND** no individual property spec re-declares the same default

#### Scenario: Properties pin seeds
- **WHEN** any `@Property`-annotated method under `expand/properties/` is inspected
- **THEN** its `@Property` annotation specifies an explicit `seed` value

### Requirement: ExpansionPropertyBase shared base class

The processor module SHALL contain `processor/src/test/groovy/io/github/joke/percolate/processor/stages/expand/properties/ExpansionPropertyBase.groovy`, an `abstract` Groovy class that:

- Is a plain Groovy class with no `extends` clause — jqwik does not discover `@Property` methods on Spock `Specification` subclasses (D2 probe confirmed this).
- Carries `@net.jqwik.api.Tag('integration')` and `@org.junit.jupiter.api.Timeout(60)` at the class level so all subclasses inherit the integration test tier — property runs are too expensive for the unit-test feedback loop.
- Carries `@net.jqwik.api.PropertyDefaults(tries = 100)` so all `@Property` methods on subclasses inherit the default tries count.
- Defines `@net.jqwik.api.Provide` methods that return `net.jqwik.api.Arbitrary` instances for the inputs consumed by the seven property specs: at minimum `seedGraphs()` → `Arbitrary<MapperGraph>`, `fakeBridges()` → `Arbitrary<List<Bridge>>`, `fakeSourceSteps()` → `Arbitrary<List<SourceStep>>`, `fakeGroupTargets()` → `Arbitrary<List<GroupTarget>>`.

`ExpansionPropertyBase` SHALL be the single source of generator definitions. Individual property specs SHALL NOT define their own `@Provide` methods or maintain parallel `java.util.Random`-driven generators. The previously-existing `GraphGenerator.groovy` (which used `java.util.Random`) SHALL be removed.

Property specs reference inherited generators via `@net.jqwik.api.ForAll('seedGraphs')`, `@ForAll('fakeBridges')`, etc., on their `@Property` method parameters.

#### Scenario: Base class exists and is inherited
- **WHEN** the source tree is inspected
- **THEN** `ExpansionPropertyBase.groovy` exists at `processor/src/test/groovy/io/github/joke/percolate/processor/stages/expand/properties/`
- **AND** each of the seven property specs declares `extends ExpansionPropertyBase`

#### Scenario: Base class carries the shared annotations
- **WHEN** the source of `ExpansionPropertyBase` is inspected
- **THEN** the class carries `@net.jqwik.api.Tag('integration')`, `@org.junit.jupiter.api.Timeout(60)`, and `@net.jqwik.api.PropertyDefaults(tries = 100)`
- **AND** the class declares `@Provide` methods returning `Arbitrary<MapperGraph>`, `Arbitrary<List<Bridge>>`, `Arbitrary<List<SourceStep>>`, and `Arbitrary<List<GroupTarget>>`

#### Scenario: GraphGenerator is removed
- **WHEN** the source tree is inspected
- **THEN** no file named `GraphGenerator.groovy` (or any class that uses `java.util.Random` to generate property-test inputs) exists under `processor/src/test/groovy/.../properties/`

#### Scenario: Generators produce fresh graphs per invocation
- **WHEN** an `@Provide Arbitrary<MapperGraph>` is sampled multiple times
- **THEN** each sample returns a freshly constructed `MapperGraph` instance
- **AND** no two samples share the same node or edge references

### Requirement: Property specs use ForAll on inherited generators

Each `@Property` method on a property spec SHALL declare its inputs via `@net.jqwik.api.ForAll('<providerMethodName>')` parameters that reference the `@Provide` methods on `ExpansionPropertyBase`. Inline `@ForAll`-without-name (which falls back to jqwik's built-in arbitraries) SHALL NOT appear for `MapperGraph`, `List<Bridge>`, `List<SourceStep>`, or `List<GroupTarget>` parameters — those types have no useful built-in arbitrary.

#### Scenario: ForAll references named provider
- **WHEN** the source of any property spec is inspected
- **THEN** every `MapperGraph`, `List<Bridge>`, `List<SourceStep>`, and `List<GroupTarget>` parameter on a `@Property` method carries a `@ForAll('<name>')` annotation
- **AND** the named `<name>` matches a `@Provide` method on `ExpansionPropertyBase`

### Requirement: AssertJ-style fluent assertions

The class `io.github.joke.percolate.processor.test.ExpansionAssertions` in `processor/src/test/groovy/` (Groovy source) SHALL expose AssertJ-style fluent assertions over `ExpansionResult`. The assertion API SHALL include at minimum:

- `.reachable(SeedEndpoint from, SeedEndpoint to)` — asserts a `REALISED` path exists.
- `.reportedError(DiagnosticKind kind)` — asserts a diagnostic of the given kind was emitted.
- `.reportedError(DiagnosticKind kind).forSeedEdge(SeedEndpoint from, SeedEndpoint to)` — narrows the assertion to a specific seed edge.

Each assertion SHALL produce a failure message that includes the DOT-rendered graph. No assertion SHALL accept a raw `String` for message-content matching.

#### Scenario: Reachability assertion is the primary surface
- **WHEN** `assertThat(result).reachable(source("p.x"), target("x"))` is invoked on a result containing such a path
- **THEN** the assertion passes
- **AND** the same assertion against a result without such a path fails with a DOT-rendered graph in the message

#### Scenario: Error assertions match on kind only
- **WHEN** the API of `ExpansionAssertions` is inspected
- **THEN** no method takes a `String` parameter intended to match diagnostic text

### Requirement: Fake strategies as the engine-algebra input alphabet

The processor module SHALL contain a `FakeStrategies` package at `processor/src/test/groovy/io/github/joke/percolate/processor/stages/expand/properties/fakes/` containing concrete `Bridge`, `SourceStep`, and `GroupTarget` implementations (Groovy sources) that the engine-algebra property tests and explicit-mode failure-mode tests use as their controlled input alphabet. The fakes SHALL include at minimum:

- `IdentityBridge(inType, outType)` — for a configured `(inType, outType)` pair, emits a single `BridgeStep` that produces a realised edge. For any other type pair, returns an empty stream.
- `ChainBridge(inType, midType, outType)` — for a configured triple, emits a two-step chain via the intermediate type. For non-matching pairs, returns an empty stream.
- `NoOpBridge` — returns an empty stream for every input. Used to drive the engine without any productive strategy.
- `DivergentBridge` — returns a step that introduces a fresh synthetic intermediate node on every invocation; used by the round-cap failure-mode fixture (see "Failure-mode specs in Spock data-driven form").

Each fake SHALL be parameterised over `TypeUniverse` so that test inputs draw from the same type set the rest of the harness uses. Each fake SHALL emit a no-op `EdgeCodegen` (`(vars, inputs) -> CodeBlock.of("")`) — the engine does not consume codegen during expansion, so no real codegen logic is needed.

The fakes SHALL be `final` (Groovy defaults to public visibility; the `final` modifier prevents subclass-based misuse) and SHALL NOT carry `@AutoService` registrations — they are test-internal and must never appear in production SPI surface.

#### Scenario: IdentityBridge fires for matched type pair
- **WHEN** a property test runs the harness with `IdentityBridge(STRING, STRING)` against a seed whose source and target nodes both carry the `String` type
- **THEN** `result.expandedGraph().edges()` contains at least one `REALISED` edge between those endpoints

#### Scenario: IdentityBridge stays silent for unmatched pair
- **WHEN** a property test runs the harness with `IdentityBridge(STRING, INTEGER)` against a seed whose source carries `String` and target carries `Integer`
- **THEN** `result.expandedGraph()` contains zero `REALISED` edges produced by the bridge (the bridge's `bridge(from, to, ctx)` returned an empty stream)

#### Scenario: NoOpBridge never produces realised edges
- **WHEN** any seed is expanded with a bundle containing only `NoOpBridge`
- **THEN** `result.expandedGraph().edges().filter(e -> e.getKind() == EdgeKind.REALISED).count() == 0`

#### Scenario: Fakes are not SPI-registered
- **WHEN** the built artifact of the `processor` module is inspected
- **THEN** no file under `META-INF/services/io.github.joke.percolate.processor.spi.*` lists any class under the `fakes` package

### Requirement: Canary test for realised-edge production

The processor module SHALL contain a canary spec at `processor/src/test/groovy/io/github/joke/percolate/processor/stages/expand/properties/RealisedEdgeCanarySpec.groovy` (or equivalent) that asserts the engine wires fakes through phases by checking that at least one `REALISED` edge appears on a known-solvable seed-and-strategy pair (`String → String` seed, `IdentityBridge(STRING, STRING)` bundle).

The canary exists specifically to catch the silent-no-op regression encountered under the previous test architecture, where every property test passed because the engine had nothing to do.

#### Scenario: Canary fires on no-op pipeline regression
- **WHEN** the canary runs `ExpansionHarness.expand(seed, [IdentityBridge(STRING, STRING)], [], [])` against an identity `String → String` seed
- **THEN** `result.expandedGraph().edges().anyMatch(e -> e.getKind() == EdgeKind.REALISED)` is `true`
- **AND** any change that breaks fake-to-phase wiring fails this test before any property test does

### Requirement: Engine tests live in `processor/src/test/`, not in a published module

Every test that exercises `ExpandStage`, its phases, the harness, the assertions, the type universe, or the fake strategies SHALL live in the `processor` module's test sources (`processor/src/test/groovy/`). No published gradle module SHALL exist whose sole purpose is to host this infrastructure.

The helpers that survive — `TypeUniverse`, `GraphFixtures`, `ExpansionAssertions`, `ExpansionHarness`, plus any internal stubs the harness needs — SHALL be co-located with the tests at `processor/src/test/groovy/io/github/joke/percolate/processor/test/` (Groovy sources). The fake strategies SHALL be co-located at `processor/src/test/groovy/io/github/joke/percolate/processor/stages/expand/properties/fakes/` (Groovy sources).

#### Scenario: No separate test-support module
- **WHEN** `settings.gradle` is inspected
- **THEN** it does NOT include any module whose sole purpose is to host test infrastructure for the expansion engine

#### Scenario: Test helpers live next to tests
- **WHEN** the source tree is inspected
- **THEN** `processor/src/test/groovy/io/github/joke/percolate/processor/test/` contains `TypeUniverse`, `GraphFixtures`, `ExpansionAssertions`, `ExpansionHarness`, and any internal stubs they require (all Groovy sources)
- **AND** no other module exports these classes
