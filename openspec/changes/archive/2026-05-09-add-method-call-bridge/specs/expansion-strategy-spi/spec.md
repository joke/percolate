## MODIFIED Requirements

### Requirement: Bridge interface

The processor SHALL define a Java interface `io.github.joke.percolate.processor.spi.Bridge` with the following shape:

```java
public interface Bridge {
    Stream<BridgeStep> bridge(TypeMirror sourceType, TypeMirror targetType, ResolveCtx ctx);
    default int priority() { return 0; }
}
```

Implementations SHALL return zero or more `BridgeStep` results describing typed-to-typed connections relevant to the queried `(sourceType, targetType)` pair. An empty stream signals "this strategy does not apply". Implementations MUST NOT throw on a non-applicable input — they SHALL return `Stream.empty()` instead.

A `Bridge` MAY emit multiple `BridgeStep`s in a single invocation. Each emitted step represents an alternative one-hop conversion the strategy is offering. Multiple emissions are appropriate when (a) Java overload resolution admits several methods with different specificities for the same query, or (b) the strategy emits chain-step candidates whose `inputType` differs from `sourceType`.

#### Scenario: Bridge with no match returns empty stream
- **WHEN** an implementor decides the input pair is not bridgeable by it
- **THEN** `bridge(...)` returns `Stream.empty()`
- **AND** does not throw

#### Scenario: Bridge priority defaults to zero
- **WHEN** an implementor does not override `priority()`
- **THEN** `priority()` returns `0`

#### Scenario: Bridge may emit multiple parallel candidates
- **WHEN** an implementor's logic finds several distinct bridge candidates for one `(sourceType, targetType)` query
- **THEN** the returned stream contains one `BridgeStep` per candidate
- **AND** each `BridgeStep` carries its own `inputType`, `outputType`, `weight`, and `codegen`

### Requirement: BridgeStep result type

The processor SHALL define an immutable Lombok `@Value` type `io.github.joke.percolate.processor.spi.BridgeStep` with these fields, in this order:

- `TypeMirror inputType` — the type the strategy consumes.
- `TypeMirror outputType` — the type the strategy produces.
- `int weight` — the cost; documented to use values from `Weights`.
- `EdgeCodegen codegen` — the codegen lambda that renders the step.

For a direct bridge (the strategy emits an edge between the two endpoints of the seed query), `inputType` equals the seed's source-side type and `outputType` equals the seed's target-side type.

For a chain-step bridge (the strategy emits an edge that requires an upstream value of a type not present in the seed's source side), `inputType` is the type the strategy needs and `outputType` is the type the strategy produces. The driver materialises the input and output endpoints accordingly (see `graph-expansion`).

#### Scenario: BridgeStep exposes its four fields
- **WHEN** a `BridgeStep` is constructed with `inputType`, `outputType`, `weight`, and `codegen`
- **THEN** `getInputType()`, `getOutputType()`, `getWeight()`, and `getCodegen()` return those values

#### Scenario: BridgeStep is value-equal
- **WHEN** two `BridgeStep` instances are constructed with equal field values
- **THEN** they are `equal` and have equal `hashCode`s

### Requirement: ResolveCtx exposes Types, Elements, mapperType, currentMethod, callableMethods

The processor SHALL define an interface `io.github.joke.percolate.processor.spi.ResolveCtx` with exactly these methods:

```java
public interface ResolveCtx {
    Types types();
    Elements elements();
    TypeElement mapperType();
    ExecutableElement currentMethod();
    CallableMethods callableMethods();
}
```

The interface SHALL NOT expose any reference to `MapperGraph`, `Edge`, `Node`, `EdgeKind`, `MapperStep`, or any other type from `processor.graph` or `processor.stages.*`. A strategy author SHALL be able to write a complete strategy by importing only `processor.spi.*`, `javax.lang.model.*`, `com.palantir.javapoet.*`, and JDK types.

`mapperType()` SHALL return the `@Mapper`-annotated `TypeElement` whose method is currently being expanded. `currentMethod()` SHALL return the `ExecutableElement` of that method. `callableMethods()` SHALL return the per-mapper index produced by `DiscoverCallableMethods`.

#### Scenario: ResolveCtx provides Types
- **WHEN** `resolveCtx.types()` is invoked
- **THEN** it returns the `javax.lang.model.util.Types` instance from the active `ProcessingEnvironment`

#### Scenario: ResolveCtx provides Elements
- **WHEN** `resolveCtx.elements()` is invoked
- **THEN** it returns the `javax.lang.model.util.Elements` instance from the active `ProcessingEnvironment`

#### Scenario: ResolveCtx provides the @Mapper TypeElement
- **WHEN** `resolveCtx.mapperType()` is invoked during expansion of any method on a given `@Mapper`
- **THEN** it returns the `TypeElement` for that `@Mapper` interface

#### Scenario: ResolveCtx provides the currently-expanding method
- **WHEN** `resolveCtx.currentMethod()` is invoked while the driver is expanding `MethodScope(M)`
- **THEN** it returns the `ExecutableElement` for `M`

#### Scenario: ResolveCtx provides the callable-method index
- **WHEN** `resolveCtx.callableMethods()` is invoked
- **THEN** it returns the `CallableMethods` instance produced by `DiscoverCallableMethods` for the current mapper

### Requirement: DirectAssign built-in

The processor SHALL ship `io.github.joke.percolate.processor.spi.builtins.DirectAssign` implementing `Bridge`. `DirectAssign` SHALL return `Stream.of(BridgeStep(sourceType, targetType, Weights.NOOP, identityCodegen))` whenever `ctx.types().isSameType(sourceType, targetType)` returns `true`. Otherwise it SHALL return `Stream.empty()`.

The `identityCodegen` lambda SHALL render the single incoming variable from `IncomingValues.single()` unchanged.

#### Scenario: DirectAssign matches identical types
- **WHEN** `DirectAssign.bridge(<String>, <String>, ctx)` is invoked
- **THEN** the result is a stream containing one `BridgeStep`
- **AND** the step's `inputType` and `outputType` both equal the `String` `TypeMirror`
- **AND** the step's `weight` is `Weights.NOOP`
- **AND** the step's `codegen` renders the single incoming variable unchanged

#### Scenario: DirectAssign rejects different types
- **WHEN** `DirectAssign.bridge(<String>, <Integer>, ctx)` is invoked
- **THEN** the result is `Stream.empty()`

#### Scenario: DirectAssign uses isSameType (not isAssignable)
- **WHEN** `DirectAssign.bridge(<List<String>>, <Collection<String>>, ctx)` is invoked
- **THEN** the result is `Stream.empty()` because `Types.isSameType` returns `false` for these types

## ADDED Requirements

### Requirement: Weights.METHOD constant

The processor SHALL extend the `Weights` constants in `io.github.joke.percolate.processor.spi.Weights` with a new constant `METHOD`. The constant SHALL be a positive `int` representing the base cost of a method-call hop.

For v1, `Weights.METHOD` SHALL equal `Weights.STEP`. The numerical value is documented as the base cost; method-call edges may carry weights greater than `Weights.METHOD` when JLS-specificity distance adds to the cost (see `MethodCallBridge` requirement).

#### Scenario: Weights.METHOD exists and is positive
- **WHEN** the source of `Weights` is inspected
- **THEN** the class declares a public static final int `METHOD`
- **AND** `Weights.METHOD > 0`

### Requirement: MethodCallBridge built-in

The processor SHALL ship `io.github.joke.percolate.processor.spi.builtins.MethodCallBridge` implementing `Bridge` and annotated `@AutoService(Bridge.class)`.

`MethodCallBridge.bridge(sourceType, targetType, ctx)` SHALL invoke `ctx.callableMethods().producing(targetType)` and emit one `BridgeStep` per returned `MethodCandidate` whose method's parameter type is assignable from `sourceType` (covariant input — i.e. a value of `sourceType` could be passed where the method's parameter type is required).

For each emitted step:

- `inputType` SHALL be the candidate's method's single parameter type.
- `outputType` SHALL be the candidate's method's return type.
- `weight` SHALL be `Weights.METHOD + paramSubtypeDistance + returnSubtypeDistance`, where:
  - `paramSubtypeDistance` is the number of supertype steps between `sourceType` and `method.paramType` (`0` if equal).
  - `returnSubtypeDistance` is the number of subtype steps between `method.returnType` and `targetType` (`0` if equal).
- `codegen` SHALL render `<receiver>.<methodName>(<input>)` where `<receiver>` is `candidate.getReceiver().asExpression()` and `<input>` is the single incoming variable supplied by `IncomingValues.single()`.

`MethodCallBridge` SHALL NOT filter the currently-expanding method (`ctx.currentMethod()`) from emission. Self-call edges may legitimately appear when a directive recurses on a structurally smaller value (e.g. `@Map(target = "buddy", source = "d.companion")`).

`MethodCallBridge` SHALL NOT consider candidates whose method has more than one declared parameter (the `DiscoverCallableMethods` filter already excludes them; this is restated for documentation).

#### Scenario: MethodCallBridge emits a direct-match candidate
- **WHEN** `MethodCallBridge.bridge(<Dog>, <Pet>, ctx)` is invoked and `callableMethods.producing(<Pet>)` returns one candidate `Pet adopt(Dog d)`
- **THEN** the result stream contains one `BridgeStep`
- **AND** the step's `inputType` is `<Dog>` and `outputType` is `<Pet>`
- **AND** the step's `weight` is `Weights.METHOD` (zero specificity distance)
- **AND** the step's `codegen` renders `this.adopt(<inputVar>)` for any input variable name

#### Scenario: MethodCallBridge emits a chain-hop candidate
- **WHEN** `MethodCallBridge.bridge(<GoldenRetriever>, <Pet>, ctx)` is invoked and `callableMethods.producing(<Pet>)` returns only `Pet adopt(Dog d)` (no direct GR→Pet method)
- **THEN** the result stream contains one `BridgeStep`
- **AND** the step's `inputType` is `<Dog>` and `outputType` is `<Pet>`
- **AND** the step's `codegen` renders `this.adopt(<inputVar>)`

#### Scenario: MethodCallBridge emits multiple candidates with different specificity weights
- **WHEN** `MethodCallBridge.bridge(<Dog>, <Cat>, ctx)` is invoked and `callableMethods.producing(<Cat>)` returns both `Cat mapDog(Dog d)` (exact) and `Cat mapAnimal(Animal a)` (Dog `<:` Animal)
- **THEN** the result stream contains two `BridgeStep`s
- **AND** the `mapDog` step's weight equals `Weights.METHOD`
- **AND** the `mapAnimal` step's weight is greater than `Weights.METHOD` by the supertype distance from `Dog` to `Animal`

#### Scenario: MethodCallBridge emits self-call without filtering
- **WHEN** `MethodCallBridge.bridge(<Dog>, <Pet>, ctx)` is invoked while expanding `ctx.currentMethod() == adopt(Dog)`, and `callableMethods.producing(<Pet>)` returns the candidate for `adopt(Dog)` itself
- **THEN** the result stream contains one `BridgeStep` for that candidate
- **AND** the step's codegen renders `this.adopt(<inputVar>)`

#### Scenario: MethodCallBridge returns empty when no candidate produces the target
- **WHEN** `MethodCallBridge.bridge(<X>, <Y>, ctx)` is invoked and `callableMethods.producing(<Y>)` returns no candidates
- **THEN** the result stream is empty

#### Scenario: MethodCallBridge uses Receiver abstraction for codegen
- **WHEN** any candidate emitted by `MethodCallBridge` has its codegen rendered
- **THEN** the rendered code uses the candidate's `getReceiver().asExpression()` as the call's receiver
- **AND** the rendered code does NOT hardcode the literal string `this`

#### Scenario: MethodCallBridge is registered via @AutoService
- **WHEN** the source of `MethodCallBridge` is inspected
- **THEN** the class carries `@AutoService(Bridge.class)`
