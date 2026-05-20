## MODIFIED Requirements

### Requirement: GetterRead built-in

The `strategies-builtin` module SHALL provide a `GetterRead` strategy implementing **`Bridge`** (not `SourceStep` — the latter SPI is removed). `GetterRead` SHALL be registered via `@AutoService(Bridge.class)`.

When invoked as `bridge(parentType, fieldType, ctx)`, `GetterRead` SHALL:

- Inspect the `parentType` (via `ctx.types().asElement(parentType)` and `ctx.elements()`) for a public, parameterless `get<FieldName>()` method whose declared return type matches `fieldType` under the `ResolveCtx`'s `Types.isSameType(...)`.
- If a matching getter exists, emit one `BridgeStep` with `inputType = parentType`, `outputType = fieldType`, `weight = Weights.STEP`, and `codegen` producing `parent.getFieldName()` at codegen time.
- If no matching getter exists, return an empty `Stream<BridgeStep>`.

`GetterRead` is queried by `ExpandGroupsPhase` like any other `Bridge` during slot resolution. Multi-hop access (`person.address.street`) emerges from the recursive expansion: when a `String` slot has no direct producer from `Person`, the next round picks up an intermediate frontier `Address` node and resolves `Person → Address` via `GetterRead`.

#### Scenario: GetterRead emits a step when the parent has a matching getter

- **WHEN** `GetterRead.bridge(Person, String, ctx)` is invoked and `Person` has a `String getLastName()` method
- **THEN** the returned stream contains a `BridgeStep` with `inputType == Person`, `outputType == String`, `weight == Weights.STEP`

#### Scenario: GetterRead returns empty when no matching getter exists

- **WHEN** `GetterRead.bridge(Person, java.util.UUID, ctx)` is invoked and `Person` has no `UUID`-returning getter
- **THEN** the returned stream is empty

#### Scenario: GetterRead is registered as a Bridge service

- **WHEN** the `META-INF/services/io.github.joke.percolate.spi.Bridge` file in `strategies-builtin` is inspected
- **THEN** the file contains the line `io.github.joke.percolate.spi.builtins.GetterRead`

#### Scenario: GetterRead is not registered as a SourceStep service

- **WHEN** the `strategies-builtin` resources are inspected
- **THEN** there is no `META-INF/services/io.github.joke.percolate.spi.SourceStep` file
- **AND** the `SourceStep` SPI does not exist in the `spi` module

### Requirement: Strategy registration via ServiceLoader and AutoService

Strategy implementations SHALL be discovered by `java.util.ServiceLoader` on three SPI types:

- `io.github.joke.percolate.spi.Bridge`
- `io.github.joke.percolate.spi.GroupTarget`
- (No third type — `SourceStep` is removed.)

Each implementation SHALL be annotated with `@com.google.auto.service.AutoService(<SPI>.class)` so the `META-INF/services/` files are generated at build time. The processor module SHALL inject the discovered strategies via Dagger `@Provides` methods returning `List<Bridge>` and `List<GroupTarget>` from `ServiceLoader.load(...)`, sorted by `getClass().getName()` for deterministic order.

The `List<SourceStep>` provider is removed.

#### Scenario: Bridge and GroupTarget services are discovered

- **WHEN** the processor module's strategy providers are invoked at runtime
- **THEN** `List<Bridge>` is populated from `ServiceLoader.load(Bridge.class, ...)`
- **AND** `List<GroupTarget>` is populated from `ServiceLoader.load(GroupTarget.class, ...)`
- **AND** there is no `List<SourceStep>` provider

#### Scenario: Built-in services registered

- **WHEN** the processor classpath includes the `strategies-builtin` module
- **THEN** `ServiceLoader.load(Bridge.class, ...)` returns instances including `GetterRead`, `DirectAssign`, `OptionalWrap`, `OptionalUnwrap`, `OptionalMap`, `ListWrap`, `ListMap`, `SetWrap`, `SetMap`, `MethodCallBridge`
- **AND** `ServiceLoader.load(GroupTarget.class, ...)` returns instances including `ConstructorCall`

## REMOVED Requirements

### Requirement: SourceStep interface

**Reason:** Source-side traversal (getter chains, field reads) is handled by `Bridge` strategy implementations in the new target-driven model. A dedicated `SourceStep` SPI required engine-side forward-direction logic that has regressed multiple times; folding the role into `Bridge` keeps one SPI type and one expansion direction.

**Migration:** Delete `spi/src/main/java/io/github/joke/percolate/spi/SourceStep.java`. Re-implement any `SourceStep` consumers (only `GetterRead` in `strategies-builtin`) as `Bridge` implementations per the modified `GetterRead built-in` requirement. Update `META-INF/services/` resources accordingly. Update `ProcessorModule` to remove the `sourceSteps()` provider.

### Requirement: Step result type

**Reason:** The `Step` value type was the result of a `SourceStep.stepsFrom(...)` call. With `SourceStep` removed, `Step` has no consumers.

**Migration:** Delete `spi/src/main/java/io/github/joke/percolate/spi/Step.java`. Bridge-side traversal returns `BridgeStep`, which is unchanged.
