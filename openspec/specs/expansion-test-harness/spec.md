# expansion-test-harness Specification

## Purpose
This spec defines the test infrastructure for the expansion engine: the `assembleExpansionPipeline` factory that wires an `ExpandStage`, the `ResolveCtx` type-query seam that `processor` and `spi` unit specs mock or fake instead of standing up a shared javac substrate (change `type-query-seam`), the narrowed-scope `TypeUniverse` fixture kept only for the `strategies-builtin` specs not yet rewritten, and the module-location invariant that keeps engine-bound test helpers in the processor module. Engine tests drive the stage directly through the factory (compiling fixtures with `com.google.testing.compile`) and assert over the bipartite `MapperGraph` / extracted plan; there is no standalone `ExpansionHarness`/`ExpansionResult`/`ExpansionAssertions` façade.

## Requirements

### Requirement: assembleExpansionPipeline factory

The class `io.github.joke.percolate.processor.ProcessorModule` SHALL expose a `public static` method `assembleExpansionPipeline(List<ExpansionStrategy> strategies, Types types, Elements elements, NullabilityResolver nullabilityResolver)` that returns a wired `ExpandStage` instance (constructed directly — `ExpandStage` is a single uniform demand work-list with no internal phases). There is no `ExpandGroupsPhase`, `ResolveSourceChainsPhase`, `ResolveTargetChainsPhase`, or `BridgeSourceToTargetPhase`, and no separate `Bridge`/`SourceStep`/`GroupTarget` strategy lists — the expansion surface is the single unified `List<ExpansionStrategy>`.

The production `@Provides ExpandStage expandStage(...)` provider SHALL delegate to `assembleExpansionPipeline(...)`, so production Dagger wiring and test wiring compose the stage through the one factory. `assembleExpansionPipeline` SHALL be the single source of truth for `ExpandStage` composition: adding a constructor parameter to `ExpandStage` SHALL force callers to update through this one method.

#### Scenario: Factory returns a wired ExpandStage
- **WHEN** `ProcessorModule.assembleExpansionPipeline(strategies, types, elements, nullabilityResolver)` is invoked with non-null arguments
- **THEN** a non-null `ExpandStage` is returned
- **AND** invoking `run(ctx)` on it produces the same expanded graph that production Dagger wiring would produce given the same unified strategy list

#### Scenario: Dagger wiring delegates to the factory
- **WHEN** the source of `ProcessorModule` is inspected
- **THEN** the `@Provides ExpandStage` provider calls `assembleExpansionPipeline(...)` with the SPI-loaded `List<ExpansionStrategy>`, the injected `Types` and `Elements`, and the `NullabilityResolver`
- **AND** there is no separate construction of `ExpandStage` outside `assembleExpansionPipeline`

### Requirement: Engine tests drive ExpandStage through the factory

Engine tests SHALL exercise expansion by obtaining a wired `ExpandStage` from
`ProcessorModule.assembleExpansionPipeline(strategies, types, elements, nullabilityResolver)` with an
explicit unified `List<ExpansionStrategy>`, and running it (with the discover stages) over a
`MapperContext` built from sources compiled by `com.google.testing.compile`. There is no standalone
`ExpansionHarness`/`ExpansionResult` façade, no `ServiceLoader`-loading test overload, and no path that
takes separate `Bridge` / `SourceStep` / `GroupTarget` lists. Production-parity wiring smoke is asserted
directly against `ServiceLoader` in `percolate-strategies-builtin`'s `BuiltinServiceRegistrationSpec`
(see "Built-in service registration smoke spec").

#### Scenario: Explicit strategy list is used, not ServiceLoader
- **WHEN** an engine test assembles `ExpandStage` via `assembleExpansionPipeline(strategies, …)`
- **THEN** exactly the supplied unified `List<ExpansionStrategy>` is used, with no `ServiceLoader` call

#### Scenario: Tests go through the single factory
- **WHEN** an engine test constructs the expansion stage
- **THEN** it obtains it via `ProcessorModule.assembleExpansionPipeline(...)`
- **AND** no other path constructs `ExpandStage` in test code

### Requirement: Unit tests mock the ResolveCtx seam

Every `processor` and `spi` unit spec (`@Tag('unit')`) SHALL exercise its subject against a **mocked
`ResolveCtx`** — a Spock `Mock()` stubbing only the seam questions its subject asks — with **mocked
collaborators** for the subject's injected seams, and a **`Spy()` on the subject stubbing only a genuinely
self-recursive call** where a method recurses into its own type structure (unification, substitution).
`processor` SHALL contain **no `FakeResolveCtx`/`FakeType`**: once `Grounding` and `ExpandStage.Driver` are
decomposed into single-method collaborators, no `processor` unit spec drives a whole-pipeline pass, so none
needs a structural fake substrate. A hand-written, javac-free `FakeResolveCtx`/`FakeType` MAY remain only
for the small number of **`spi`** specs pinning the seam's own default-method composition (`ResolveCtxSpec`,
`ContainerSpec`), pending `spi`'s own decomposition. Either way, any `javax.lang.model` value is passed
through as a **never-stubbed opaque token** — the `ValidateNoDuplicateTargetsStageSpec` pattern. There SHALL
be **no javac in the unit path**: a unit spec SHALL NOT construct a `JavacTask`, compile a `@Mapper`, or
stand up any shared type substrate, so the suite is parallel-safe by construction. Specs SHALL be
**example-based Spock** (`where:` tables for algebra/laws); **jqwik SHALL NOT be used**.

Most specs stub only the 1-2 seam questions their subject asks (e.g. `ListContainerSeamSpec`,
`SourceCandidatesSpec`, `AccessorSpec`). A decomposed algorithm such as `Grounding`'s unification is tested
by mocking its extracted collaborators (`Unifier`, `BindingEnumerator`, `SpecInstantiator`) and spying only
its irreducible self-recursion — not through a `FakeResolveCtx`.

#### Scenario: A processor unit spec mocks the seam and collaborators, adds no javac, and uses no fake
- **WHEN** a `processor` unit spec drives its subject
- **THEN** it constructs `ResolveCtx ctx = Mock()` (stubbing only the seam questions the subject asks) and mocks the subject's injected collaborators, never constructs a `JavacTask` or compiles a `@Mapper`, and imports no `FakeResolveCtx`/`FakeType`

#### Scenario: Self-recursion is isolated with a spy
- **WHEN** a decomposed engine method recurses into its own type structure (unification or substitution)
- **THEN** its spec spies the subject and stubs the recursive call, rather than exercising the full recursion or standing up a fake type-world

#### Scenario: Unit specs are example-based Spock
- **WHEN** the unit specs are inspected
- **THEN** no spec imports `net.jqwik`; property-shaped cases are covered by `where:` tables

### Requirement: Codegen unit tests need no shared javac substrate

Once the codegen stages (`AssembleMapperType`, `BuildMethodBodies`) are decomposed, their pure assembly logic SHALL be unit-tested against mocked seams, and the only residue requiring a genuinely compiler-backed `TypeMirror` — a JavaPoet `TypeName.get(mirror)` rendering leaf — SHALL be covered by the compile-based feature-e2e layer, not by a shared unit-test javac substrate. `PrivateTypeUniverse` SHALL be reduced to that leaf's needs or removed outright; it SHALL NOT back any `processor` engine-logic unit spec.

#### Scenario: PrivateTypeUniverse no longer backs engine-logic unit specs
- **WHEN** the `processor` unit specs for decomposed stages are inspected
- **THEN** none of them constructs a `PrivateTypeUniverse` for engine-logic assertions; any surviving use is confined to a compile-tested codegen `TypeName.get` leaf

#### Scenario: Codegen assembly logic is mock-tested
- **WHEN** a decomposed codegen unit (an extracted method-signature or method-body assembler) is unit-tested
- **THEN** it is exercised against mocked seams, with real-mirror rendering left to the feature-e2e compile tests

### Requirement: TypeUniverse fixture

The class `io.github.joke.percolate.spi.test.TypeUniverse` in `spi/src/testFixtures/groovy/` (Groovy source, published via `percolate-spi`'s `testFixtures` configuration) SHALL expose a fixed set of `javax.lang.model.type.TypeMirror` instances backed by a single, JVM-lifetime `com.sun.source.util.JavacTask` held in a `static final` field. The task SHALL be obtained via `ToolProvider.getSystemJavaCompiler().getTask(...)` with no writer, no file manager, no diagnostic listener, no classes, and no compilation units — passing only an options list of `['-cp', <java.class.path>]` so the test JVM's classpath types resolve — and cast to `com.sun.source.util.JavacTask`. The fixture SHALL NOT call `task.parse()`, `task.analyze()`, or `task.call()`; bootstrap class resolution happens lazily inside `Elements.getTypeElement(...)`. The fixture SHALL NOT use `com.google.testing.compile` for type sourcing.

The set SHALL include at minimum: `int`, `long`, the primitive wrapper types `Integer` and `Long`, `String`, an enum type (`java.time.DayOfWeek`), `java.time.LocalDateTime`, `java.time.Instant`, and `java.util.List<E>` (as `List<Integer>` and `List<String>`). All `TypeMirror` instances SHALL come from the same `Types` and `Elements` instance — namely those returned by `task.getTypes()` and `task.getElements()` — so that javac's equality semantics hold across the universe.

`TypeUniverse` SHALL initialise javac at most once per JVM (modulo separate class loaders). Test classes SHALL access `TypeUniverse` through `static` fields initialised on first reference.

**Narrowed scope (change `type-query-seam`):** neither `processor` nor `spi` unit specs consume
`TypeUniverse` any longer — both mock or fake the `ResolveCtx` seam instead (see "Unit tests mock the
ResolveCtx seam"). `TypeUniverse` remains **only** as the substrate for the `strategies-builtin` specs not
yet rewritten against a mocked `ResolveCtx` (deferred to `features-as-documentation`); deleting it is
gated on that rewrite, not this change, since removing it now would break `strategies-builtin`'s build.

Consumers (the `strategies-builtin` module's per-strategy specs still on the shared substrate, and any
third-party strategy author testing against it) SHALL access `TypeUniverse` by declaring
`testImplementation testFixtures(project(':spi'))` (or the equivalent published-coordinate dependency).

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

#### Scenario: No processor or spi unit spec consumes TypeUniverse
- **WHEN** the `processor` and `spi` modules' `@Tag('unit')` specs are inspected
- **THEN** none of them imports `io.github.joke.percolate.spi.test.TypeUniverse`; only `strategies-builtin` specs deferred to `features-as-documentation` still do

### Requirement: Engine unit tests mock the seam; no shared type-system fixture module

Every `processor` and `spi` unit test SHALL exercise its subject against a mocked or faked `ResolveCtx`
(see "Unit tests mock the ResolveCtx seam"), needing no shared **javac** type-system fixture. No published
Gradle module SHALL exist whose sole purpose is to host engine-test infrastructure. `spi` SHALL export no
`testFixtures` type whose purpose is a shared javac substrate **for the `processor`/`spi` unit path**
specifically — `HarnessResolveCtx` (which existed only to hand strategy specs a `ResolveCtx` backed by
`TypeUniverse`'s real `Types`/`Elements`) is deleted, having no remaining consumer once `processor` and
`spi` went mock-only. `TypeUniverse` itself is **not** deleted (see its own requirement above): it remains
a `testFixtures` export, scoped down to serve only the `strategies-builtin` specs not yet rewritten.

Engine-bound helpers that a **compile-based** engine/e2e test still needs (`HarnessScope`, `TestFiler`, and the
Java `fixtures`) MAY remain co-located under `processor/src/test/` because they depend on engine internals
(`MapperContext`, `Diagnostics`, `ProcessorModule`, `MapperGraph`, `Scope`); they SHALL NOT be exported by any
other module. The e2e/doc compile tests continue to exercise the seam's real-javac implementation
(`CompileResolveCtx`) end-to-end.

#### Scenario: No separate test-support module
- **WHEN** `settings.gradle` is inspected
- **THEN** it does NOT include any module whose sole purpose is to host test infrastructure for the expansion engine

#### Scenario: HarnessResolveCtx is gone; TypeUniverse remains for strategies-builtin only
- **WHEN** `spi`'s `testFixtures` are inspected
- **THEN** `io.github.joke.percolate.spi.test.HarnessResolveCtx` does not exist
- **AND** `io.github.joke.percolate.spi.test.TypeUniverse` still exists, consumed only by `strategies-builtin` specs deferred to `features-as-documentation`

#### Scenario: Compile-bound engine helpers stay next to the engine tests
- **WHEN** the source tree is inspected
- **THEN** any `HarnessScope`, `TestFiler`, or Java `fixtures` a compile-based engine/e2e test needs live under `processor/src/test/` and are exported by no other module

### Requirement: Tests assert over the bipartite surface directly

Engine tests SHALL assert against the post-expansion `MapperGraph` and the `ExtractedPlan` directly:
the Values and Operations per scope, **reachability and cost** (the derived extraction queries —
replacing the former SAT predicate), and the chosen-producer plan view. There is no group- or
outcome-based accessor. The invariants a test may check include: every in-plan Value has exactly one
chosen producer, no `Dep` edge crosses a scope boundary, and every Operation's inbound edges match its
port signature exactly.

#### Scenario: Plan assertions read the chosen producer
- **WHEN** a test inspects the extracted plan for a fixture expansion
- **THEN** each in-plan Value exposes exactly one `chosenProducer`, and no `Dep` edge crosses a scope
  boundary

#### Scenario: Reachability assertions read from cost
- **WHEN** a test asserts a demand is satisfied
- **THEN** it queries the `ExtractedPlan`'s `reachable` (finite extraction cost) rather than a stored
  SAT predicate

### Requirement: Type resolution by Class literal

`TypeUniverse` SHALL expose `static javax.lang.model.element.TypeElement of(Class<?> type)` that resolves the given class through the same `Elements`/`JavacTask` substrate as `element(String)` (e.g. by canonical name). It is a rename-safe, IDE-tracked alternative to passing a fully-qualified string, and SHALL be the preferred way to resolve a fixture type that exists as a compiled `Class` on the test classpath. `element(String)` remains for JDK types and genuinely dynamic names.

`TypeUniverse` SHALL NOT retain members that exist only to serve removed test layers: it SHALL NOT expose a `pool()` of `TypeMirror`s (a fossil of a removed property-test layer) and SHALL NOT carry constants resolved by nothing else.

#### Scenario: of(Class) resolves a fixture from a Class literal

- **WHEN** a spec calls `TypeUniverse.of(SomeFixture.class)`
- **THEN** it returns a non-null `TypeElement` for that class, drawn from the same substrate as `TypeUniverse.element(...)`, such that `Types.isSameType` comparisons with other `TypeUniverse` types behave consistently

#### Scenario: of(Class) and element(String) agree

- **WHEN** both `TypeUniverse.of(SomeFixture.class)` and `TypeUniverse.element("<fully-qualified SomeFixture>")` are resolved
- **THEN** they return the same `TypeElement`
