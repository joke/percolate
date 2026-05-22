## REMOVED Requirements

### Requirement: ElementSeed result type

**Reason**: The fused container-map bridge pattern that needed `ElementSeed` is retired. With Optional and collections unified under one element-scope model (per `[[design.md#decision-1]]`), each container ships a one-hop `*Unwrap` (scope-entering) and one-hop `*Collect` (scope-exiting), neither of which carries an `ElementSeed`. Scope is declared per `BridgeStep` via the new `ScopeTransition` field (see MODIFIED requirement below).

**Migration**: Strategy authors who previously emitted `BridgeStep` with `elementSeeds = [ElementSeed("element", innerIn, innerOut)]` SHALL replace the fused step with two ordinary `Bridge` implementations:
1. A scope-entering `*Unwrap` that emits one `BridgeStep` with `inputType = outerContainer<innerIn>`, `outputType = innerIn`, `scopeTransition = ScopeTransition.ENTERING`, `elementRole = "element"` (or another role).
2. A scope-exiting `*Collect` that emits one `BridgeStep` with `inputType = innerOut`, `outputType = outerContainer<innerOut>`, `scopeTransition = ScopeTransition.EXITING`, `elementRole = "element"`.

The driver materialises the element-scope chain between them via ordinary bridge expansion; no engine-side `registerElementSeedGroup` is invoked.

## MODIFIED Requirements

### Requirement: BridgeStep result type

The percolate-spi module SHALL define an immutable Lombok `@Value` type `io.github.joke.percolate.spi.BridgeStep` with these fields, in this order:

- `TypeMirror inputType` — the type the strategy consumes.
- `TypeMirror outputType` — the type the strategy produces.
- `int weight` — the cost; documented to use values from `Weights`.
- `EdgeCodegen codegen` — the codegen lambda that renders the step.
- `ScopeTransition scopeTransition` — how this step relates to element scope. Default `ScopeTransition.PRESERVING`. See the `ScopeTransition enum` requirement.
- `String elementRole` — the role name for the element scope this step participates in. Consulted only when `scopeTransition != PRESERVING`. Default `"element"`. Container authors may pass a non-default role to disambiguate parallel element scopes within one chain (e.g., `Map<K,V>` could ship two bridges using `"key"` and `"value"`).

For a direct same-scope bridge (the strategy emits an edge whose input and output live at the same scope — DirectAssign, MethodCallBridge, GetterPathResolver, conversion strategies), `scopeTransition = PRESERVING`.

For a scope-entering bridge (the strategy crosses from a regular-scope container into element scope — `IterableUnwrap`, `OptionalUnwrap`, user-authored `MonoUnwrap` / `FluxUnwrap` / etc.), `scopeTransition = ENTERING`. The bridge's output is structurally at `ElementLocation(elementRole)`; the driver allocates the output node accordingly. Input is typically at regular scope; the driver's `allocateOrReuseInputNode` may match an existing same-element-scope candidate first (flatMap composition; see `graph-expansion`).

For a scope-exiting bridge (the strategy collects element-scope values into a regular-scope container — `SetCollect`, `ListCollect`, `ArrayCollect`, `OptionalCollect`, user-authored `MonoCollect` / `FluxCollect` / etc.), `scopeTransition = EXITING`. The bridge's input is at `ElementLocation(elementRole)`; the bridge's output is at the surrounding scope (typically regular).

#### Scenario: BridgeStep exposes its six fields
- **WHEN** a `BridgeStep` is constructed with `inputType`, `outputType`, `weight`, `codegen`, `scopeTransition`, and `elementRole`
- **THEN** `getInputType()`, `getOutputType()`, `getWeight()`, `getCodegen()`, `getScopeTransition()`, and `getElementRole()` return those values

#### Scenario: BridgeStep is value-equal
- **WHEN** two `BridgeStep` instances are constructed with equal field values
- **THEN** they are `equal` and have equal `hashCode`s

#### Scenario: BridgeStep with PRESERVING scope is the default for same-scope bridges
- **WHEN** a `BridgeStep` is constructed with the four-argument constructor (omitting `scopeTransition` and `elementRole`)
- **THEN** `getScopeTransition()` returns `ScopeTransition.PRESERVING`
- **AND** `getElementRole()` returns `"element"`
- **AND** the step describes a single-edge emission with no scope change; existing strategies (DirectAssign, MethodCallBridge, conversion strategies) emit such steps unchanged

#### Scenario: BridgeStep with ENTERING scope identifies a scope-enter bridge
- **WHEN** a `BridgeStep` is constructed with `scopeTransition = ScopeTransition.ENTERING`, `elementRole = "element"`, `inputType = List<String>`, and `outputType = String`
- **THEN** the driver allocates the bridge's output node at `ElementLocation("element")`
- **AND** the input matches a regular-scope `List<String>` candidate via standard candidate selection

#### Scenario: BridgeStep with EXITING scope identifies a scope-exit bridge
- **WHEN** a `BridgeStep` is constructed with `scopeTransition = ScopeTransition.EXITING`, `elementRole = "element"`, `inputType = String`, and `outputType = Set<String>`
- **THEN** the driver requires the bridge's input node to be at `ElementLocation("element")` (allocating fresh if necessary)
- **AND** the bridge's output is at the frontier's scope (typically regular)

### Requirement: SPI package isolation

The percolate-spi Gradle module SHALL ship a package `io.github.joke.percolate.spi` containing exactly the strategy-author surface: three interfaces (`SourceStep`, `GroupTarget`, `Bridge`), four immutable result types (`Step`, `BridgeStep`, `Slot`, `GroupBuild`), the `ResolveCtx` interface, the codegen interfaces (`EdgeCodegen`, `GroupCodegen`, `IncomingValues`, `VarNames`), the `Receiver` / `ThisReceiver` / `CallableMethods` / `MethodCandidate` types, the `ScopeTransition` enum, and the `Containers` and `Weights` utilities. The module SHALL depend only on JDK types plus `com.palantir.javapoet`. It SHALL NOT depend on `percolate-annotations` or `percolate-processor`.

The package SHALL declare `@NullMarked` via `package-info.java`.

The package SHALL NOT contain `ElementSeed` — that type is removed in this change.

Built-in strategies (`ConstructorCall`, `DirectAssign`, the container Unwrap/Collect/Wrap bridges, `MethodCallBridge`, and the `*PathResolver`s) SHALL ship from a separate Gradle module `percolate-strategies-builtin` whose only compile dependency is `percolate-spi`. They SHALL NOT import any class from `io.github.joke.percolate.processor.graph` or `io.github.joke.percolate.processor.stages.expand`. This invariant SHALL be enforced structurally by the module compile graph.

#### Scenario: spi package has @NullMarked
- **WHEN** the source of `spi/src/main/java/io/github/joke/percolate/spi/package-info.java` is inspected
- **THEN** the package declaration carries `@org.jspecify.annotations.NullMarked`

#### Scenario: ElementSeed type does not exist
- **WHEN** the `io.github.joke.percolate.spi` package source tree is inspected
- **THEN** no class named `ElementSeed.java` exists
- **AND** no source file imports or references `io.github.joke.percolate.spi.ElementSeed`

#### Scenario: ScopeTransition enum exists in spi package
- **WHEN** the `io.github.joke.percolate.spi` package is inspected
- **THEN** a public enum `ScopeTransition` exists with three constants: `PRESERVING`, `ENTERING`, `EXITING`

#### Scenario: Built-in strategies have no forbidden imports
- **WHEN** the import statements of `ConstructorCall`, `DirectAssign`, the container bridges, and `MethodCallBridge` are inspected
- **THEN** none reference any class in `io.github.joke.percolate.processor.graph.*` or `io.github.joke.percolate.processor.stages.expand.*`

#### Scenario: SPI module has no dependency on annotations or processor
- **WHEN** `spi/build.gradle` is inspected
- **THEN** it declares neither `project(':annotations')` nor `project(':processor')` on any `compile` / `implementation` / `api` configuration
- **AND** its only non-JDK `api` dependency is `com.palantir.javapoet:javapoet`

### Requirement: Weights.CONTAINER constant

The percolate-spi module SHALL extend `io.github.joke.percolate.spi.Weights` with a constant `CONTAINER` (a positive `int`) representing the base cost of a container-shaped hop.

For v1, `Weights.CONTAINER` SHALL equal `2`, slightly heavier than `Weights.STEP` and `Weights.METHOD` (both `1`) and cheaper than `Weights.EXPENSIVE` (`3`). Container built-in strategies (`OptionalWrap`, `OptionalUnwrap`, `OptionalCollect`, `ListWrap`, `ListCollect`, `SetWrap`, `SetCollect`, `ArrayCollect`, `IterableUnwrap`) SHALL use this constant unmodified as the `weight` field of every emitted `BridgeStep`.

#### Scenario: Weights.CONTAINER exists and is positive
- **WHEN** the source of `Weights` is inspected
- **THEN** the class declares a public static final int `CONTAINER`
- **AND** `Weights.CONTAINER > 0`

#### Scenario: Weights.CONTAINER value
- **WHEN** `Weights.CONTAINER` is read
- **THEN** the value is `2`

### Requirement: Built-in service registration smoke spec

The `percolate-strategies-builtin` module SHALL contain a Spock specification at `strategies-builtin/src/test/groovy/io/github/joke/percolate/spi/builtins/BuiltinServiceRegistrationSpec.groovy` that asserts the contract between modules: when `percolate-strategies-builtin` is on the classpath, `ServiceLoader.load(...)` discovers exactly the expected built-in classes for each SPI interface.

The spec SHALL load each of `Bridge`, `SourceStep`, `GroupTarget`, and `PathSegmentResolver` via `ServiceLoader.load(<Interface>.class)` and assert that the discovered set contains, at minimum, the shipped built-ins:

- `Bridge`: `DirectAssign`, `MethodCallBridge`, `IterableUnwrap`, `OptionalUnwrap`, `SetCollect`, `ListCollect`, `ArrayCollect`, `OptionalCollect`, `SetWrap`, `ListWrap`, `OptionalWrap`.
- `SourceStep`: (none after `GetterRead` removal in `bind-seed-chain-realisation`).
- `GroupTarget`: `ConstructorCall`.
- `PathSegmentResolver`: `GetterPathResolver`, `FieldPathResolver`, `RecordPathResolver`.

The spec SHALL additionally assert that `SetMap`, `ListMap`, and `OptionalMap` are NOT discovered — those classes are removed by this change.

The spec SHALL be tagged `@spock.lang.Tag('unit')` and SHALL NOT invoke `ExpansionHarness` or any expansion-pipeline code.

#### Scenario: ServiceLoader discovers all expected Bridge builtins
- **WHEN** `ServiceLoader.load(Bridge.class)` is invoked from `BuiltinServiceRegistrationSpec`
- **THEN** the returned stream's classes contain, as a subset, `DirectAssign`, `MethodCallBridge`, `IterableUnwrap`, `OptionalUnwrap`, `SetCollect`, `ListCollect`, `ArrayCollect`, `OptionalCollect`, `SetWrap`, `ListWrap`, and `OptionalWrap`

#### Scenario: ServiceLoader does NOT discover removed fused container bridges
- **WHEN** `ServiceLoader.load(Bridge.class)` is invoked from `BuiltinServiceRegistrationSpec`
- **THEN** the returned stream's classes do NOT contain `SetMap`, `ListMap`, or `OptionalMap`
- **AND** the source files `SetMap.java`, `ListMap.java`, `OptionalMap.java` do not exist in `strategies-builtin/src/main/`

#### Scenario: ServiceLoader discovers all expected GroupTarget builtins
- **WHEN** `ServiceLoader.load(GroupTarget.class)` is invoked from `BuiltinServiceRegistrationSpec`
- **THEN** the returned stream's classes contain `ConstructorCall`

#### Scenario: ServiceLoader discovers all expected PathSegmentResolver builtins
- **WHEN** `ServiceLoader.load(PathSegmentResolver.class)` is invoked from `BuiltinServiceRegistrationSpec`
- **THEN** the returned stream's classes contain `GetterPathResolver`, `FieldPathResolver`, and `RecordPathResolver`

#### Scenario: Spec does not depend on the expansion pipeline
- **WHEN** the source of `BuiltinServiceRegistrationSpec` is inspected
- **THEN** no import references `io.github.joke.percolate.processor.*`
- **AND** no invocation of `ExpansionHarness`, `ExpandStage`, or `ProcessorModule` appears

## ADDED Requirements

### Requirement: ScopeTransition enum

The percolate-spi module SHALL define a Java enum `io.github.joke.percolate.spi.ScopeTransition` with exactly three constants:

```java
public enum ScopeTransition {
    PRESERVING,  // input and output at the same scope as the frontier
    ENTERING,    // output at ElementLocation; input typically at regular scope
    EXITING      // input at ElementLocation; output at the surrounding (typically regular) scope
}
```

The enum SHALL be the type of the `BridgeStep.scopeTransition` field. The driver SHALL consult this enum to:

1. Decide whether a bridge step matches the current frontier (an `ENTERING` step matches only when the frontier is at `ElementLocation`; an `EXITING` step matches only when the frontier is at regular scope).
2. Allocate fresh input nodes at the correct `Location` (an `ENTERING` step allocates input at the surrounding scope of the frontier; an `EXITING` step allocates input at `ElementLocation`).
3. Apply the same-element-scope candidate preference rule (`ENTERING` bridges may match an existing element-scope candidate of the right type in the same scope as the frontier — flatMap composition; see `graph-expansion`).

The default value of `BridgeStep.scopeTransition` is `PRESERVING`. Strategies that do not interact with element scope (the majority — DirectAssign, MethodCallBridge, GetterPathResolver, future conversion strategies) SHALL use the default by omitting the field from their `BridgeStep` construction.

#### Scenario: ScopeTransition has exactly three constants
- **WHEN** the source of `ScopeTransition` is inspected
- **THEN** the enum declares exactly `PRESERVING`, `ENTERING`, and `EXITING`
- **AND** no other constants exist

#### Scenario: BridgeStep default scope transition is PRESERVING
- **WHEN** a `BridgeStep` is constructed using the four-argument constructor (no scope-transition argument)
- **THEN** `getScopeTransition()` returns `ScopeTransition.PRESERVING`

#### Scenario: ENTERING bridges declare ElementLocation output structurally
- **WHEN** any built-in `Bridge` emits a `BridgeStep` with `scopeTransition == ENTERING`
- **THEN** the driver allocates the bridge's output node at `ElementLocation(step.getElementRole())`
- **AND** the bridge's `outputType` is the element type (not a container of that type)

#### Scenario: EXITING bridges declare ElementLocation input structurally
- **WHEN** any built-in `Bridge` emits a `BridgeStep` with `scopeTransition == EXITING`
- **THEN** the driver requires the matching input candidate to be at `ElementLocation(step.getElementRole())`
- **AND** if no such candidate exists, the driver allocates a fresh input node at `ElementLocation(step.getElementRole())`

#### Scenario: PRESERVING bridges propagate frontier scope to input
- **WHEN** any built-in `Bridge` emits a `BridgeStep` with `scopeTransition == PRESERVING`
- **THEN** the driver allocates the bridge's input at the same `Location` as the frontier
- **AND** scope inheritance is automatic: a PRESERVING bridge match against an `ElementLocation` frontier produces an `ElementLocation` input at the same role
