# Builtin Strategy Unit Tests Spec

## Purpose

This spec defines the discipline contract for testing the strategies in `percolate-strategies-builtin` in isolation. It specifies that every built-in `Bridge` / `SourceStep` / `GroupTarget` has a corresponding Spock specification, where it lives, how it is tagged, what it must verify (preconditions, happy path, codegen output, element seeds), the test-time substrate it consumes (`TypeUniverse` + `HarnessResolveCtx` from `spi` `testFixtures`), and the boundary at which mocking is appropriate (`CallableMethods` and adjacent `ResolveCtx` surfaces; never `Types` / `Elements`).

The contract exists to make per-strategy regressions diagnosable in seconds (a failing spec names the strategy that broke), to establish the copyable pattern third-party strategy authors should follow, and to keep the test substrate identical to production (one javac, one element table) so identity-mismatch bugs cannot creep in.

## Requirements

### Requirement: Per-strategy unit spec presence, naming and location

Every concrete `@AutoService` implementation of `io.github.joke.percolate.spi.Bridge`, `io.github.joke.percolate.spi.SourceStep`, or `io.github.joke.percolate.spi.GroupTarget` shipped from the `percolate-strategies-builtin` module SHALL have a corresponding Spock specification named `<StrategyClassSimpleName>Spec.groovy`.

The spec SHALL reside at `strategies-builtin/src/test/groovy/io/github/joke/percolate/spi/builtins/<StrategyClassSimpleName>Spec.groovy` (mirroring the strategy's main-source package). The spec SHALL `extend spock.lang.Specification` and SHALL carry the annotation `@spock.lang.Tag('unit')` at the class level.

For the eleven built-ins shipped after `extract-spi-and-builtins`, the eleven required specs are: `DirectAssignSpec`, `ListMapSpec`, `ListWrapSpec`, `SetMapSpec`, `SetWrapSpec`, `OptionalMapSpec`, `OptionalUnwrapSpec`, `OptionalWrapSpec`, `MethodCallBridgeSpec`, `GetterReadSpec`, `ConstructorCallSpec`.

#### Scenario: Every builtin has a matching spec
- **WHEN** the contents of `strategies-builtin/src/main/java/io/github/joke/percolate/spi/builtins/` and `strategies-builtin/src/test/groovy/io/github/joke/percolate/spi/builtins/` are inspected
- **THEN** for every public final class in the main tree annotated with `@AutoService(Bridge.class)`, `@AutoService(SourceStep.class)`, or `@AutoService(GroupTarget.class)`, a sibling `<SimpleName>Spec.groovy` file exists in the test tree

#### Scenario: Specs are tagged as unit
- **WHEN** any one of the eleven required strategy specs is inspected
- **THEN** the class is annotated with `@spock.lang.Tag('unit')` (the Spock-package `Tag`, not `org.junit.jupiter.api.Tag`)
- **AND** the class extends `spock.lang.Specification`

### Requirement: Single-substrate javac invariant

All `javax.lang.model.type.TypeMirror` instances flowing through any strategy unit spec SHALL originate from the single `com.sun.source.util.JavacTask` held inside `io.github.joke.percolate.test.TypeUniverse` (the fixture published by `percolate-spi`'s `testFixtures` configuration).

No strategy unit spec SHALL construct a second `JavacTask`, instantiate a separate `com.google.testing.compile.Compiler`, or otherwise introduce a parallel `Elements`/`Types` pair. Both JDK types and `strategies-builtin/src/test/java/.../fixtures/` user types SHALL resolve through that single substrate.

If a future build configuration prevents `TypeUniverse.element('…fixtures.PersonRecord')` from resolving user-defined classpath types, the remediation SHALL be a local change to `TypeUniverse` that wires an explicit `javax.tools.StandardJavaFileManager` with the test classpath. Introducing a second javac task to work around the visibility issue SHALL NOT be considered an acceptable remediation.

#### Scenario: No second JavacTask in any spec
- **WHEN** the imports of every file under `strategies-builtin/src/test/groovy/io/github/joke/percolate/spi/builtins/` are inspected
- **THEN** no file imports `com.sun.source.util.JavacTask`, `com.google.testing.compile.Compiler`, or `com.google.testing.compile.JavaFileObjects`
- **AND** every `TypeMirror`-typed expression is sourced from `io.github.joke.percolate.test.TypeUniverse` (directly or through `HarnessResolveCtx` / `ResolveCtxBuilder`)

#### Scenario: Fixture types resolve through TypeUniverse
- **WHEN** a strategy spec needs a user-defined fixture type
- **THEN** the spec obtains it via `TypeUniverse.element('io.github.joke.percolate.spi.builtins.fixtures.<FixtureSimpleName>').asType()`
- **AND** `Types.isSameType(...)` between this type and any JDK type drawn from `TypeUniverse` behaves consistently (both come from the same javac element table)

### Requirement: ResolveCtxBuilder test helper

A class `io.github.joke.percolate.spi.builtins.test.ResolveCtxBuilder` SHALL exist under `strategies-builtin/src/test/groovy/`. It SHALL produce instances of `io.github.joke.percolate.spi.ResolveCtx` whose defaults are:

- `types()` returns `TypeUniverse.types()`,
- `elements()` returns `TypeUniverse.elements()`,
- `mapperType()` returns `null`,
- `currentMethod()` returns `null`,
- `callableMethods()` returns a `CallableMethods` instance whose `producing(...)` returns an empty `Stream`.

The builder SHALL expose fluent `with*` methods for each surface — `withCallableMethods(CallableMethods)`, `withMapperType(TypeElement)`, `withCurrentMethod(ExecutableElement)` — so that per-scenario overrides are one method chain. The builder SHALL be immutable per `build()` call (each `with*` returns a new builder or stages state safely for one terminal `build()`).

Every per-strategy spec SHALL assemble its `ResolveCtx` through this builder. Specs SHALL NOT instantiate anonymous `ResolveCtx` implementations.

#### Scenario: Default builder produces a HarnessResolveCtx-equivalent ctx
- **WHEN** `new ResolveCtxBuilder().build()` is called
- **THEN** the returned `ResolveCtx`'s `types()` equals `TypeUniverse.types()`
- **AND** its `elements()` equals `TypeUniverse.elements()`
- **AND** its `mapperType()` is `null`
- **AND** its `currentMethod()` is `null`
- **AND** its `callableMethods().producing(<any type>)` returns an empty `Stream`

#### Scenario: Builder applies CallableMethods override
- **WHEN** a Spock-mocked `CallableMethods` is supplied via `.withCallableMethods(mock)` and the builder is built
- **THEN** the resulting `ResolveCtx.callableMethods()` returns the supplied mock

### Requirement: Mocking boundary

`javax.lang.model.util.Types` and `javax.lang.model.util.Elements` SHALL NOT be mocked, stubbed, or otherwise replaced in any strategy unit spec. Their behaviour is always provided by the real `JavacTask` held by `TypeUniverse`.

`io.github.joke.percolate.spi.CallableMethods`, and the optional ctx surfaces `mapperType()` and `currentMethod()`, MAY be Spock-mocked (`Mock(CallableMethods)`, etc.) when a spec needs to control their behaviour. Other `ResolveCtx`-reachable surfaces (`types()`, `elements()`) MAY NOT be mocked.

The strategy under test (`DirectAssign`, `ListMap`, etc.) SHALL NOT be mocked, stubbed, partially-mocked, or spied. It SHALL be constructed via its public no-args constructor and exercised through its public interface methods.

#### Scenario: No Mockito stubbing of platform types
- **WHEN** the imports of any strategy spec are inspected
- **THEN** no `Mockito.mock(Types.class)`, `Mockito.mock(Elements.class)`, `Mock(Types)`, `Mock(Elements)`, or `Stub(Types)` / `Stub(Elements)` appears

#### Scenario: Strategy under test is plainly constructed
- **WHEN** any strategy spec invokes the strategy under test
- **THEN** the invocation site reads `new <StrategyClass>().<method>(...)` or holds a reference produced by such a call — no `Spy()`, `Mock()`, or `Stub()` wrapping the strategy itself

### Requirement: Assertion scope is metadata-only

Strategy unit specs SHALL assert on the metadata of returned `BridgeStep` / `Step` / `GroupBuild` values: input types, output types, weights, element seeds (presence and inner types), the presence or emptiness of the returned `Stream` or `Optional`, and any other plain-data accessor exposed by these value types.

Strategy unit specs SHALL NOT invoke `io.github.joke.percolate.spi.EdgeCodegen.render(...)` or `io.github.joke.percolate.spi.GroupCodegen.render(...)`. They SHALL NOT assert on `com.palantir.javapoet.CodeBlock.toString()` output or otherwise pin the rendered Java source produced by a strategy's codegen. Codegen pinning is the scope of a separate future change and is explicitly excluded from this contract.

#### Scenario: No codegen invocation in unit specs
- **WHEN** the source of every strategy spec is inspected
- **THEN** no method call to `EdgeCodegen.render`, `GroupCodegen.render`, or `CodeBlock.toString()` appears
- **AND** no `import com.palantir.javapoet.CodeBlock` appears in the strategy specs (the test helpers are free to use CodeBlock internally if needed)

### Requirement: Per-strategy scenario coverage

For each of the eleven strategy specs, the spec SHALL include at minimum:

- **Empty-return scenarios.** One Spock feature method per declared precondition demonstrating that the strategy returns an empty `Stream<BridgeStep>` / `Stream<Step>` / `Optional.empty()` when that precondition is not met. Examples: `ListMap` returns empty when the target is not a `List`; `OptionalUnwrap` returns empty when the source is not an `Optional`; `GetterRead` returns empty when the path tail is empty or the source kind is not declared; `ConstructorCall` returns empty when target tails are empty or no matching constructor exists.

- **Happy-path scenarios.** At least one feature method that exercises the strategy on inputs that satisfy all preconditions, asserting on the returned step's metadata (`inputType`, `outputType`, `weight`).

- **Branch scenarios.** For strategies whose behaviour differs across multiple accepted input shapes or member-resolution paths, one feature method per distinguishable branch. Examples: `ListMap` must cover array input, `Iterable` input, and `Optional` input separately; `GetterRead` must cover `getX`, `isX`, and field-named accessor branches; `ConstructorCall` must cover the constructor-by-parameter-name path and the constructor-by-arity-and-fields path.

- **Element-seed scenarios.** For strategies that populate `BridgeStep.getElementSeeds()` (`ListMap`, `ListWrap`, `SetMap`, `SetWrap`, `OptionalMap`, `OptionalWrap`), at least one feature method asserting the seed's name, inner-from type, and inner-to type on the happy path.

#### Scenario: Every strategy spec exercises at least one precondition failure
- **WHEN** any strategy spec is inspected
- **THEN** at least one feature method named to indicate a precondition (e.g. `'returns empty when target is not a List'`) asserts an empty `Stream` / empty `Optional` result

#### Scenario: Container bridges cover all input shapes
- **WHEN** `ListMapSpec` is inspected
- **THEN** there exists a feature method exercising array input
- **AND** a feature method exercising `Iterable` (e.g. `List<E>`) input
- **AND** a feature method exercising `Optional<E>` input (pinning current behaviour — see Findings)

### Requirement: Shape fixtures for ConstructorCall and GetterRead

Java fixture types SHALL exist under `strategies-builtin/src/test/java/io/github/joke/percolate/spi/builtins/fixtures/`. They SHALL be plain Java sources compiled by the project's standard `compileTestJava` task into the test classpath; specs SHALL resolve them through `TypeUniverse.element('io.github.joke.percolate.spi.builtins.fixtures.<SimpleName>')`.

The fixture set SHALL include at minimum:

- A record with named parameters (e.g. `record PersonRecord(int age, String name) {}`) — exercises `ConstructorCall`'s constructor-by-parameter-name path.
- A JavaBean with paired getter/setter accessors (e.g. `getName`/`setName`, `getAge`/`setAge`) — exercises `GetterRead`'s `getX` path.
- A class exposing a boolean-returning accessor named `isFlag()` — exercises `GetterRead`'s `isX` branch.
- A class whose constructor parameters are positional but whose fields carry meaningful names — exercises `ConstructorCall`'s constructor-by-arity-and-fields path.

#### Scenario: Fixtures are on the test classpath
- **WHEN** the four-or-more fixture classes exist under `strategies-builtin/src/test/java/io/github/joke/percolate/spi/builtins/fixtures/`
- **THEN** `TypeUniverse.element('io.github.joke.percolate.spi.builtins.fixtures.PersonRecord')` returns a non-null `TypeElement` whose `asType()` is a `DeclaredType`
- **AND** the same is true for the JavaBean, boolean-accessor, and positional-ctor fixtures

### Requirement: Pinning policy for current-behaviour findings

Where a unit spec discovers behaviour that is unintentional or worth questioning but lies outside the scope of the current change to fix, the spec SHALL pin the current behaviour with a feature method whose name signals the pinning intent (e.g. `'pins current behaviour: ListMap accepts Optional inputs'`).

Adjacent to the feature method's `then:` block, the spec SHALL place a single-line source comment of the form `// FOLLOW-UP: <one-line summary>` so that the audit trail is discoverable by the next maintainer reading the test.

The two findings identified while authoring the initial eleven specs SHALL be pinned at minimum: `ListMap` accepting `Optional` inputs in `ListMapSpec`, and `MethodCallBridge.subtypeDistance` returning `0` for both same-type and not-assignable inputs in `MethodCallBridgeSpec`.

#### Scenario: Known findings are pinned with FOLLOW-UP markers
- **WHEN** `ListMapSpec` is inspected
- **THEN** at least one feature method exercises `Optional<E>` as input and asserts a non-empty result
- **AND** a `// FOLLOW-UP:` source comment appears within twenty lines of that feature method

- **WHEN** `MethodCallBridgeSpec` is inspected
- **THEN** at least one feature method demonstrates that `subtypeDistance` returns `0` for both same-type and non-assignable inputs, and pins the resulting `weight` in the returned `BridgeStep`
- **AND** a `// FOLLOW-UP:` source comment appears within twenty lines of that feature method
