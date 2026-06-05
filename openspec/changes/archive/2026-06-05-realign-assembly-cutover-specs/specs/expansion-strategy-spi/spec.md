## REMOVED Requirements

### Requirement: ConstructorCall built-in (exact match)

**Reason**: This requirement describes the deleted `GroupTarget` SPI (`buildFor(...) → Optional<GroupBuild>`) and an exact name-set matching behaviour (`Set.copyOf(parameterNames()) == Set.copyOf(targetTails)`, with subset/superset rejection) that the **shipped** `ConstructorCall` does not implement. The shipped `ConstructorCall` is an `AssemblyStrategy` that myopically **over-emits** one boundary per accessible constructor with no match-filtering; the driver binds slots by name and prunes non-matching constructors.

**Migration**: See the new "ConstructorCall built-in" requirement (below) for shipped behaviour. (The name-set exact-match contract is being reintroduced — at the driver, not the strategy — by the separate `fix-overloaded-constructor-assembly` change.)

## ADDED Requirements

### Requirement: ConstructorCall built-in

The `percolate-strategies-builtin` module SHALL ship `io.github.joke.percolate.spi.builtins.ConstructorCall` implementing `AssemblyStrategy` (the marker over `ExpansionStrategy` that restricts firing to assembly roots) and registered via `@AutoService(ExpansionStrategy.class)`.

`ConstructorCall.expand(frontier, ctx)` SHALL resolve the frontier's target type; if it is a `DECLARED` type, `ConstructorCall` SHALL emit one `BOUNDARY` `ExpansionStep` per **accessible (non-`private`) constructor** of that type — it is myopic and over-emits, performing no match-filtering of its own. For each emitted step:

- `inputs` SHALL contain one `Slot` per constructor parameter, in declaration order, with `name` = the parameter's simple name, `type` = the parameter's declared type, and `weight` = `Weights.STEP`;
- `output` SHALL be the target type;
- `weight` SHALL be `Weights.STEP`;
- the `EdgeCodegen` SHALL render `new <TargetType>(<inputs by slot name, comma-joined>)`.

Constructions whose slots cannot all be bound (or whose binding the driver prunes) go UNSAT and are dropped by the fixed-point loop and the cost oracle.

#### Scenario: ConstructorCall over-emits one boundary per accessible constructor
- **WHEN** `ConstructorCall.expand(frontier, ctx)` is invoked for a frontier whose target type is `Address` declaring `Address(int number, String street)` and `Address(long number, String street)`
- **THEN** the result stream contains two `BOUNDARY` `ExpansionStep`s, one per constructor
- **AND** each step's `inputs` are `[Slot("number", <int|long>, Weights.STEP), Slot("street", String, Weights.STEP)]` in declaration order
- **AND** each step's `output` is `Address` and its codegen renders `new Address(numberVar, streetVar)`

#### Scenario: ConstructorCall ignores private constructors
- **WHEN** the target type declares only a `private` constructor
- **THEN** `ConstructorCall.expand(...)` emits no step for it

## MODIFIED Requirements

### Requirement: DirectAssign built-in

The `percolate-strategies-builtin` module SHALL ship `io.github.joke.percolate.spi.builtins.DirectAssign` implementing the `CombinatorialMatch` author mixin (a convenience over `ExpansionStrategy` that offers each in-view candidate to `bridge(from, to, ctx)`) and registered via `@AutoService(ExpansionStrategy.class)`. When a candidate's type equals the target, `DirectAssign.bridge(from, to, ctx)` SHALL return `Stream.of(ExpansionStep.conversion(Slot("value", from, Weights.NOOP, null), to, identityCodegen, Weights.NOOP))`; otherwise it SHALL return `Stream.empty()`. The step's `Intent` is `CONVERSION`, so it folds an identity edge in place rather than opening a sub-group.

The `identityCodegen` lambda SHALL render the single incoming value from `IncomingValues.single()` unchanged.

#### Scenario: DirectAssign matches identical types
- **WHEN** `DirectAssign.bridge(<String>, <String>, ctx)` is invoked
- **THEN** the result is a stream containing one `ExpansionStep`
- **AND** the step's `intent` is `CONVERSION`, its single input type and output type both equal the `String` `TypeMirror`
- **AND** the step's `weight` is `Weights.NOOP`
- **AND** the step's `codegen` renders the single incoming value unchanged

#### Scenario: DirectAssign rejects different types
- **WHEN** `DirectAssign.bridge(<String>, <Integer>, ctx)` is invoked
- **THEN** the result is `Stream.empty()`

#### Scenario: DirectAssign uses isSameType (not isAssignable)
- **WHEN** `DirectAssign.bridge(<List<String>>, <Collection<String>>, ctx)` is invoked
- **THEN** the result is `Stream.empty()` because `Types.isSameType` returns `false` for these types

### Requirement: MethodCallBridge built-in

The `percolate-strategies-builtin` module SHALL ship `io.github.joke.percolate.spi.builtins.MethodCallBridge` implementing `ExpansionStrategy` and annotated `@AutoService(ExpansionStrategy.class)`.

`MethodCallBridge.expand(frontier, ctx)` SHALL invoke `ctx.callableMethods().producing(frontier.targetType())` and emit one `BOUNDARY` `ExpansionStep` per returned `MethodCandidate` whose method has exactly one parameter and whose return type is assignable to the target type. For each emitted step:

- `inputs` SHALL contain a single `Slot` named after the method's parameter, typed to the parameter's declared type;
- `output` SHALL be the candidate's method's return type;
- `weight` SHALL be `Weights.METHOD + returnSubtypeDistance`, where `returnSubtypeDistance` is the number of supertype steps between the method's return type and the target type (`0` if equal);
- the `EdgeCodegen` SHALL render `<receiver>.<methodName>(<input>)` where `<receiver>` is `candidate.getReceiver().asExpression()` and `<input>` is `IncomingValues.single()`.

`MethodCallBridge` SHALL NOT filter the currently-expanding method (`ctx.currentMethod()`); self-call edges may legitimately appear when a directive recurses on a structurally smaller value.

#### Scenario: MethodCallBridge emits a direct-match candidate
- **WHEN** `MethodCallBridge.expand(frontier, ctx)` is invoked for a frontier with target `Pet` and `callableMethods.producing(<Pet>)` returns one candidate `Pet adopt(Dog d)`
- **THEN** the result stream contains one `BOUNDARY` `ExpansionStep`
- **AND** its single slot is typed `Dog` and its `output` is `Pet`
- **AND** its `weight` is `Weights.METHOD` (zero return distance)
- **AND** its `codegen` renders `this.adopt(<inputVar>)`

#### Scenario: MethodCallBridge emits self-call without filtering
- **WHEN** `MethodCallBridge.expand(frontier, ctx)` is invoked while expanding `ctx.currentMethod() == adopt(Dog)` and `callableMethods.producing(<Pet>)` returns the candidate for `adopt(Dog)` itself
- **THEN** the result stream contains one step for that candidate
- **AND** its codegen renders `this.adopt(<inputVar>)`

#### Scenario: MethodCallBridge returns empty when no candidate produces the target
- **WHEN** `MethodCallBridge.expand(frontier, ctx)` is invoked and `callableMethods.producing(target)` returns no single-parameter candidate assignable to the target
- **THEN** the result stream is empty

#### Scenario: MethodCallBridge is registered via @AutoService
- **WHEN** the source of `MethodCallBridge` is inspected
- **THEN** the class carries `@AutoService(ExpansionStrategy.class)`

### Requirement: Weights.CONTAINER constant

The percolate-spi module SHALL expose a constant `CONTAINER` (a positive `int`) on `io.github.joke.percolate.spi.Weights` representing the base cost of a container-shaped hop. For v1, `Weights.CONTAINER` SHALL equal `2`, heavier than `Weights.STEP` and `Weights.METHOD` (both `1`) and cheaper than `Weights.EXPENSIVE` (`3`). The container built-in strategies (`OptionalContainer`, `ListContainer`, `SetContainer`, `ArrayContainer`, sharing the `WrapperContainer` / `SequenceContainer` / `CollectionContainer` bases) SHALL use this constant as the base `weight` of the container `ExpansionStep`s they emit.

#### Scenario: Weights.CONTAINER exists and is positive
- **WHEN** the source of `Weights` is inspected
- **THEN** the class declares a public static final int `CONTAINER`
- **AND** `Weights.CONTAINER > 0`

#### Scenario: Weights.CONTAINER value
- **WHEN** `Weights.CONTAINER` is read
- **THEN** the value is `2`

### Requirement: Built-in service registration smoke spec

The `percolate-strategies-builtin` module SHALL contain a Spock specification at `strategies-builtin/src/test/groovy/io/github/joke/percolate/spi/builtins/BuiltinServiceRegistrationSpec.groovy` that asserts the cross-module contract: when `percolate-strategies-builtin` is on the classpath, `ServiceLoader.load(ExpansionStrategy.class)` discovers exactly the expected built-in classes. There is a **single** strategy SPI interface (`ExpansionStrategy`); there is no separate `Bridge` / `GroupTarget` / `PathSegmentResolver` registration.

The spec SHALL assert that `ServiceLoader.load(ExpansionStrategy.class)` discovers, at minimum, the shipped built-ins: `DirectAssign`, `MethodCallBridge`, `ConstructorCall`, `WidenPrimitive`, `PrimitiveWrapperConversion`, `OptionalContainer`, `ListContainer`, `SetContainer`, `ArrayContainer`, `GetterPathResolver`, `FieldPathResolver`, and `MethodPathResolver`.

The spec SHALL additionally assert that the superseded per-operation container classes (`OptionalWrap`, `OptionalUnwrap`, `OptionalCollect`, `ListWrap`, `ListCollect`, `SetWrap`, `SetCollect`, `ArrayCollect`, `IterableUnwrap`, `SetMap`, `ListMap`, `OptionalMap`) are NOT discovered — they were folded into the one-class-per-container-type strategies.

The spec SHALL be tagged `@spock.lang.Tag('unit')` and SHALL NOT invoke `ExpansionHarness` or any expansion-pipeline code. Its sole concern is verifying that the `META-INF/services/...` files generated by `auto-service` correctly register the strategy classes under `ExpansionStrategy`.

#### Scenario: ServiceLoader discovers all expected ExpansionStrategy builtins
- **WHEN** `ServiceLoader.load(ExpansionStrategy.class)` is invoked from `BuiltinServiceRegistrationSpec`
- **THEN** the returned classes contain, as a subset, `DirectAssign`, `MethodCallBridge`, `ConstructorCall`, `WidenPrimitive`, `PrimitiveWrapperConversion`, `OptionalContainer`, `ListContainer`, `SetContainer`, `ArrayContainer`, `GetterPathResolver`, `FieldPathResolver`, and `MethodPathResolver`

#### Scenario: Superseded per-operation container classes are absent
- **WHEN** the discovered `ExpansionStrategy` set is inspected
- **THEN** it contains no class named `OptionalWrap`, `OptionalUnwrap`, `OptionalCollect`, `ListWrap`, `ListCollect`, `SetWrap`, `SetCollect`, `ArrayCollect`, `IterableUnwrap`, `SetMap`, `ListMap`, or `OptionalMap`
