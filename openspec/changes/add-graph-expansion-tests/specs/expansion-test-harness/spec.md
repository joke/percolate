## ADDED Requirements

### Requirement: processor-test-support module

The project SHALL include a gradle module named `processor-test-support` registered in `settings.gradle`, located at `processor-test-support/`. The module SHALL apply the `java` plugin (not `groovy`) and depend on `project(':processor')` as `api`. The module SHALL declare `api` dependencies on `net.jqwik:jqwik` and `org.assertj:assertj-core`. The module SHALL be available to consumers via `testImplementation project(':processor-test-support')`.

The module SHALL NOT import any class from the `com.sun.tools.javac.*` package hierarchy. All javac access SHALL go through public API: `javax.tools`, `javax.lang.model`, and `com.sun.source.util`. The module's `build.gradle` SHALL NOT contain `--add-exports` flags for the `jdk.compiler` module. The module SHALL NOT depend on `com.google.testing.compile:compile-testing` for type sourcing.

The module SHALL NOT contain any class annotated with `@AutoService(Bridge.class)`, `@AutoService(SourceStep.class)`, or `@AutoService(GroupTarget.class)`. The module SHALL NOT ship any file under `META-INF/services/` that names a `Bridge`, `SourceStep`, or `GroupTarget` implementation. This isolation ensures that consumers of `processor-test-support` do not inherit test-only strategies into their production SPI surface.

#### Scenario: Module is registered and depended on
- **WHEN** `settings.gradle` is inspected
- **THEN** it contains `include 'processor-test-support'`

#### Scenario: Module declares its purpose-bound dependencies
- **WHEN** `processor-test-support/build.gradle` is inspected
- **THEN** it declares `api project(':processor')`
- **AND** it declares `api` dependencies on `jqwik` and `assertj-core`
- **AND** it does NOT declare a dependency on `com.google.testing.compile:compile-testing` for type sourcing
- **AND** it does NOT declare `implementation` or `runtimeOnly` of any class that registers a strategy via `@AutoService`

#### Scenario: Module uses only public javac APIs
- **WHEN** the sources under `processor-test-support/src/main/java` are inspected
- **THEN** no file imports any class from `com.sun.tools.javac.*`
- **AND** `processor-test-support/build.gradle` declares no `--add-exports` flags for the `jdk.compiler` module

#### Scenario: Module ships no SPI registrations
- **WHEN** the built artifact of `processor-test-support` is inspected
- **THEN** no file under `META-INF/services/` lists an `io.github.joke.percolate.processor.spi.*` implementation

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

The class `io.github.joke.percolate.test.ExpansionHarness` in `processor-test-support` SHALL expose two `public static` entry points:

- `ExpansionResult expand(MapperGraph seed)` — loads `Bridge`, `SourceStep`, and `GroupTarget` lists via `ServiceLoader` from the current thread's context class loader (production parity) and runs the expansion pipeline obtained from `ProcessorModule.assembleExpansionPipeline(...)`.
- `ExpansionResult expand(MapperGraph seed, List<Bridge> bridges, List<SourceStep> sourceSteps, List<GroupTarget> groupTargets)` — bypasses `ServiceLoader` and runs the expansion pipeline directly with the supplied lists.

Both entry points SHALL go through `assembleExpansionPipeline(...)` to obtain the wired stage. Both entry points SHALL invoke the auto-invariants (see "Auto-invariants on every harness call") before returning the result. Both entry points SHALL also invoke `ValidatePathsPhase` and expose the resulting diagnostics on the returned `ExpansionResult`.

#### Scenario: Default mode loads via ServiceLoader
- **WHEN** `ExpansionHarness.expand(seed)` is called
- **THEN** the strategy lists are loaded via `ServiceLoader.load(...)`
- **AND** the same strategies that would run in production for the current classpath are used

#### Scenario: Explicit mode bypasses ServiceLoader
- **WHEN** `ExpansionHarness.expand(seed, bridges, sourceSteps, groupTargets)` is called
- **THEN** no call to `ServiceLoader` is made
- **AND** exactly the supplied lists are used

#### Scenario: Both modes share pipeline assembly
- **WHEN** either entry point is invoked
- **THEN** the wired `ExpandStage` is obtained via `ProcessorModule.assembleExpansionPipeline(...)`
- **AND** no other path constructs `ExpandStage` in test code

### Requirement: Fluent seed-graph DSL

The class `io.github.joke.percolate.test.SeedDsl` in `processor-test-support` SHALL provide a fluent builder for `MapperGraph` instances populated only with `EdgeKind.SEED` (and where appropriate `EdgeKind.SUB_SEED`) edges. The DSL SHALL be usable from Java and from Groovy/Spock.

The DSL SHALL accept declarations of: mapper scope, one or more method scopes (each with named arguments and a return type), and seed directives (target path, source path). Types referenced by the DSL SHALL come from `TypeUniverse` (see next requirement).

The DSL SHALL produce graphs whose node identity rule `(scope, location, type)` matches the production rule exactly, so that graphs built by `SeedDsl` are observationally indistinguishable from graphs produced by `SeedGraph` on equivalent annotation inputs.

#### Scenario: DSL builds a minimal seed graph
- **WHEN** `SeedDsl.seed().scope(method("m").arg("p", Person).returns(Human)).directive(target("lastName"), source("p.lastName")).build()` is called
- **THEN** a `MapperGraph` is returned
- **AND** it contains exactly one `EdgeKind.SEED` edge from a node at `SourceLocation("p.lastName")` to a node at `TargetLocation("lastName")`

#### Scenario: DSL nodes follow the identity rule
- **WHEN** two distinct DSL calls build seed graphs and reference the same `(scope, location, type)` triple
- **THEN** the resulting graphs contain a node with that triple
- **AND** no graph contains two distinct nodes with the same triple

### Requirement: TypeUniverse fixture

The class `io.github.joke.percolate.test.TypeUniverse` in `processor-test-support` SHALL expose a fixed set of `javax.lang.model.type.TypeMirror` instances backed by a single, JVM-lifetime `com.sun.source.util.JavacTask` held in a `static final` field. The task SHALL be obtained via `ToolProvider.getSystemJavaCompiler().getTask(null, null, null, null, null, null)` (no sources, no diagnostic listener) and cast to `com.sun.source.util.JavacTask`. The fixture SHALL NOT call `task.parse()`, `task.analyze()`, or `task.call()`; bootstrap class resolution happens lazily inside `Elements.getTypeElement(...)`. The fixture SHALL NOT use `com.google.testing.compile` for type sourcing.

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

`ExpansionResult` SHALL expose four invariant-related checks that tests MAY call explicitly. The harness itself SHALL NOT assert these before returning — a future change MAY strengthen this requirement (see design D6).

- **Convergence flag** — `result.converged()` returns `false` when `ExpandStage` emitted an "Expansion did not converge after N rounds" diagnostic, otherwise `true`.
- **Idempotence stub** — `result.isIdempotent()` is reserved for a structural same-graph check; the current implementation returns `true` unconditionally and SHALL be wired alongside the harness-side assertion strengthening.
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

Every failure message produced by `ExpansionAssertions` (including `reachable(...)`, `reportedError(...)`, and `Chain.forSeedEdge(...)`) SHALL include the DOT rendering of the expanded graph (produced by `io.github.joke.percolate.processor.graph.DotRenderer`) inlined in the message. No sibling file SHALL be written. If the result has no mapper type captured, a placeholder string SHALL be substituted for the DOT block.

If `ExpandStage` aborts before producing a complete graph (cycle detected, budget exhausted), the partial graph that the harness captured SHALL still be DOT-rendered and attached.

A future strengthening of harness-side invariant assertion (see design D6) is expected to extend DOT attachment to invariant-failure errors as well; that extension is **out of scope for this requirement**.

#### Scenario: Assertion failure carries DOT
- **WHEN** an `ExpansionAssertions.reachable(from, to)` assertion fails
- **THEN** the thrown `AssertionError`'s message contains the DOT rendering of the expanded graph

#### Scenario: Result without mapper type yields a placeholder block
- **WHEN** an `ExpansionResult` was constructed without a `TypeElement` (legacy `of(...)` overloads, or harness paths that supply `null`)
- **THEN** `result.dotRender()` returns a placeholder string instead of throwing
- **AND** assertion failure messages still produce a usable error

### Requirement: Capability specs in Spock data-driven form

The processor module SHALL contain a Spock specification at `processor/src/test/groovy/io/github/joke/percolate/processor/stages/expand/ExpansionCapabilitiesSpec.groovy` that asserts, for each supported capability of the expansion engine and each registered strategy, that a representative seed graph produces a reachable path between the named seed endpoints.

The spec SHALL use Spock's `@Unroll` and `where:` data-table style so that each row produces an independently named test failure. The spec SHALL use the SPI-loaded mode of `ExpansionHarness.expand(seed)`, mirroring production strategy loading.

Adding a new strategy SHALL be accompanied by at least one new row in this spec demonstrating the strategy's primary capability. The row SHALL exercise the strategy in the presence of all other SPI-loaded strategies, not in isolation.

#### Scenario: Each capability gets a named row
- **WHEN** `ExpansionCapabilitiesSpec` is executed
- **THEN** at least one test method uses `@Unroll` with a `where:` table
- **AND** each row's scenario label appears in the test output as a distinct test name

#### Scenario: SPI mode is used
- **WHEN** any row in `ExpansionCapabilitiesSpec` runs
- **THEN** `ExpansionHarness.expand(seed)` (no explicit strategy lists) is invoked

### Requirement: Failure-mode specs in Spock data-driven form

The processor module SHALL contain a Spock specification at `processor/src/test/groovy/io/github/joke/percolate/processor/stages/expand/ExpansionFailureModesSpec.groovy` that asserts, for each rejection case, that the expansion engine reports the correct diagnostic kind attached to the correct seed edge.

The spec SHALL cover at minimum:

- A directive whose source path has no compatible strategy → "no realised path" diagnostic.
- A seed graph that induces a `SEED + SUB_SEED` cycle → cycle diagnostic.
- A seed graph that exceeds the per-mapper round budget → round-cap diagnostic.

Each scenario SHALL assert on the **diagnostic kind** and the **seed edge** the diagnostic was attached to. No scenario SHALL assert on the exact diagnostic message text.

#### Scenario: No-path failure
- **WHEN** a seed declares a source-target pair with no strategy chain between the types
- **THEN** the harness reports a "no realised path" diagnostic
- **AND** the diagnostic is attached to the offending seed edge

#### Scenario: Cycle failure
- **WHEN** a seed graph induces mutual-recursion through SUB_SEED lineage
- **THEN** the harness reports the cycle diagnostic
- **AND** expansion terminates before exceeding `MAX_EXPANSION_ROUNDS`

#### Scenario: Round-cap failure
- **WHEN** a seed graph triggers more outer-loop iterations than `MAX_EXPANSION_ROUNDS` without converging
- **THEN** the harness reports the round-cap diagnostic

#### Scenario: No message-text assertions
- **WHEN** the source of `ExpansionFailureModesSpec` is inspected
- **THEN** no assertion compares against the exact string contents of any diagnostic message

### Requirement: jqwik property tests

The processor module SHALL contain jqwik property tests at `processor/src/test/java/io/github/joke/percolate/processor/stages/expand/properties/` covering the algebraic contract of `expand`:

- **`DeterminismProperty`**: `expand(g, S) == expand(g, S)` across repeated invocation.
- **`IdempotenceProperty`**: `expand(expand(g, S), S) == expand(g, S)`.
- **`OrderIndependenceProperty`**: for any permutation `S'` of `S`, `expand(g, S) == expand(g, S')`.
- **`MonotonicityProperty`**: for any `S₁ ⊆ S₂`, `edges(expand(g, S₁)) ⊆ edges(expand(g, S₂))`.
- **`IdentityCollapseProperty`**: across many generated seed/strategy pairs, no two distinct nodes share `(scope, location, type)`.
- **`DisjointAdditivityProperty`**: for disjoint `g₁`, `g₂`, `expand(g₁ ⊕ g₂, S) == expand(g₁, S) ⊕ expand(g₂, S)`.
- **`EmptyStrategyIdentityProperty`**: `expand(g, ∅)` equals `g` modulo non-strategy phases.

All property tests SHALL use `ExpansionHarness.expand(seed, ..., lists)` (explicit mode). Generators SHALL produce `MapperSpec` objects (not raw graphs) constrained to `TypeUniverse`, with the universe sized so that roughly half of generated specs are solvable by the strategy subset.

#### Scenario: Seven property classes exist
- **WHEN** the directory `processor/src/test/java/.../expand/properties/` is inspected
- **THEN** it contains class files for each of the seven properties listed above

#### Scenario: Properties use explicit mode
- **WHEN** the source of any property class is inspected
- **THEN** every `ExpansionHarness.expand(...)` call passes explicit strategy lists

### Requirement: jqwik configuration

`processor-test-support` SHALL configure jqwik with a default `@Property(tries = 500)` (overridable per property) and store a property database under `build/jqwik-database` so that shrunken counterexamples are replayed deterministically. Each property class SHALL pin a default seed via `@Property(seed = ...)` to make CI failures locally reproducible.

#### Scenario: jqwik database is configured
- **WHEN** `processor/build.gradle` (or `processor-test-support`'s contribution to it) is inspected
- **THEN** jqwik's database directory is configured to `build/jqwik-database`

#### Scenario: Properties pin seeds
- **WHEN** any property class under `expand/properties/` is inspected
- **THEN** every `@Property` annotation specifies an explicit `seed` value

### Requirement: AssertJ-style fluent assertions

The class `io.github.joke.percolate.test.ExpansionAssertions` in `processor-test-support` SHALL expose AssertJ-compatible fluent assertions over `ExpansionResult`. The assertion API SHALL include at minimum:

- `.reachable(SeedEndpoint from, SeedEndpoint to)` — asserts a `REALISED` path exists.
- `.chainAt(SeedEndpoint).hasTypes(TypeMirror... ordered)` — asserts which intermediate types appear at a source location.
- `.reportedError(DiagnosticKind kind)` — asserts a diagnostic of the given kind was emitted.
- `.reportedError(DiagnosticKind kind).forSeedEdge(SeedEndpoint from, SeedEndpoint to)` — narrows the assertion to a specific seed edge.

Each assertion SHALL produce a failure message that includes the DOT-rendered graph (see "DotRenderer dump on failure"). No assertion SHALL accept a raw `String` for message-content matching.

#### Scenario: Reachability assertion is the primary surface
- **WHEN** `assertThat(result).reachable(source("p.x"), target("x"))` is invoked on a result containing such a path
- **THEN** the assertion passes
- **AND** the same assertion against a result without such a path fails with a DOT-rendered graph in the message

#### Scenario: Error assertions match on kind only
- **WHEN** the API of `ExpansionAssertions` is inspected
- **THEN** no method takes a `String` parameter intended to match diagnostic text
