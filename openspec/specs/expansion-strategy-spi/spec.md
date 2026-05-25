# Expansion Strategy SPI Spec

## Purpose

This spec defines the strategy author surface for the expansion engine: immutable result types, strategy interfaces, built-in strategies, and service registration via ServiceLoader and AutoService.

## Requirements

### Requirement: SPI package isolation

The percolate-spi Gradle module SHALL ship a package `io.github.joke.percolate.spi` containing exactly the strategy-author surface: three interfaces (`GroupTarget`, `Bridge`, `PathSegmentResolver`), three immutable result types (`BridgeStep`, `Slot`, `GroupBuild`, `ResolvedSegment`), the `ResolveCtx` interface, the codegen interfaces (`EdgeCodegen`, `GroupCodegen`, `IncomingValues`, `VarNames`), the `Receiver` / `ThisReceiver` / `CallableMethods` / `MethodCandidate` types, the `ScopeTransition` enum, and the `Containers` and `Weights` utilities. The module SHALL depend only on JDK types plus `com.palantir.javapoet` (because `CodeBlock` is part of the codegen interface surface). It SHALL NOT depend on `percolate-annotations` or `percolate-processor`.

The package SHALL NOT contain `SourceStep` or `Step` — those types were removed when seed-time path resolution (`PathSegmentResolver`) replaced expansion-time getter walks.

The package SHALL declare `@NullMarked` via `package-info.java`.

The package SHALL NOT contain `ElementSeed` — that type is removed in `split-container-bridges`.

Built-in strategies (`ConstructorCall`, `DirectAssign`, the container Unwrap/Collect/Wrap bridges, `MethodCallBridge`, and the `*PathResolver`s) SHALL ship from a separate Gradle module `percolate-strategies-builtin` whose only compile dependency is `percolate-spi`. They SHALL NOT import any class from `io.github.joke.percolate.processor.graph` or `io.github.joke.percolate.processor.stages.expand`. This invariant SHALL be enforced structurally by the module compile graph: `percolate-strategies-builtin`'s `build.gradle` declares no compile dependency on `percolate-processor`, so engine internals are unreachable from built-in sources.

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
- **AND** the enforcement is structural: `strategies-builtin`'s `build.gradle` declares no compile dependency on `processor`, so attempting such an import would fail compilation

#### Scenario: SPI module has no dependency on annotations or processor
- **WHEN** `spi/build.gradle` is inspected
- **THEN** it declares neither `project(':annotations')` nor `project(':processor')` on any `compile` / `implementation` / `api` configuration
- **AND** its only non-JDK `api` dependency is `com.palantir.javapoet:javapoet`

### Requirement: GroupTarget interface

The percolate-spi module SHALL define a Java interface `io.github.joke.percolate.spi.GroupTarget` with the following shape:

```java
public interface GroupTarget {
    Optional<GroupBuild> buildFor(TypeMirror returnType, List<String> targetTails, ResolveCtx ctx);
    default int priority() { return 0; }
}
```

Implementations SHALL return one `GroupBuild` describing a multi-arg construction (constructor, builder, factory method, …) producing `returnType` from inputs labelled by the entries of `targetTails`, or `Optional.empty()` if the strategy does not apply.

#### Scenario: GroupTarget with no match returns empty
- **WHEN** an implementor decides nothing matches the inputs
- **THEN** `buildFor(...)` returns `Optional.empty()`
- **AND** does not throw

#### Scenario: GroupTarget priority defaults to zero
- **WHEN** an implementor does not override `priority()`
- **THEN** `priority()` returns `0`

### Requirement: Bridge interface

The percolate-spi module SHALL define a Java interface `io.github.joke.percolate.spi.Bridge` with the following shape:

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

### Requirement: ResolveCtx exposes Types, Elements, mapperType, currentMethod, callableMethods

The percolate-spi module SHALL define an interface `io.github.joke.percolate.spi.ResolveCtx` with exactly these methods:

```java
public interface ResolveCtx {
    Types types();
    Elements elements();
    TypeElement mapperType();
    ExecutableElement currentMethod();
    CallableMethods callableMethods();
}
```

The interface SHALL NOT expose any reference to `MapperGraph`, `Edge`, `Node`, `EdgeKind`, `MapperStep`, or any other type from `processor.graph` or `processor.stages.*`. A strategy author SHALL be able to write a complete strategy by importing only `io.github.joke.percolate.spi.*`, `javax.lang.model.*`, `com.palantir.javapoet.*`, and JDK types.

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
- **AND** the step describes a single-edge emission with no scope change

#### Scenario: BridgeStep with ENTERING scope identifies a scope-enter bridge
- **WHEN** a `BridgeStep` is constructed with `scopeTransition = ScopeTransition.ENTERING`, `elementRole = "element"`, `inputType = List<String>`, and `outputType = String`
- **THEN** the driver allocates the bridge's output node at `ElementLocation("element")`
- **AND** the input matches a regular-scope `List<String>` candidate via standard candidate selection

#### Scenario: BridgeStep with EXITING scope identifies a scope-exit bridge
- **WHEN** a `BridgeStep` is constructed with `scopeTransition = ScopeTransition.EXITING`, `elementRole = "element"`, `inputType = String`, and `outputType = Set<String>`
- **THEN** the driver requires the bridge's input node to be at `ElementLocation("element")` (allocating fresh if necessary)
- **AND** the bridge's output is at the frontier's scope (typically regular)

### Requirement: Slot result type

The percolate-spi module SHALL define an immutable Lombok `@Value` type `io.github.joke.percolate.spi.Slot` with these fields, in this order:
- `String name` — the slot's binding name (matches a directive target tail or a builder-method name).
- `TypeMirror type` — the slot's required type. Used by the engine to drive target-to-source candidate search; SHALL NOT be assumed by callers to equal the realised producer's actual type post-commit.
- `int weight` — the cost of filling the slot.
- `AnnotatedConstruct producedFrom` — the underlying consumer-side `Element` (the constructor parameter, setter parameter, or field that the slot represents). The engine consults this at code-generation time to derive the consumer's nullability contract via `NullabilityResolver`.

`producedFrom` enables consumer-contract derivation without leaking nullability into the strategy SPI: strategy authors simply pass the `VariableElement` they already have in hand when constructing slots; they do not reason about nullability themselves.

#### Scenario: Slot exposes its four fields
- **WHEN** a `Slot` is constructed with `name`, `type`, `weight`, and `producedFrom`
- **THEN** `getName()`, `getType()`, `getWeight()`, and `getProducedFrom()` return those values

#### Scenario: Slot.producedFrom is the consumer-side Element
- **WHEN** `ConstructorCall` constructs a `Slot` for parameter index `i` of constructor `ctor`
- **THEN** the slot's `producedFrom` is `ctor.getParameters().get(i)` (a `VariableElement`)

#### Scenario: Slot is value-equal
- **WHEN** two `Slot` instances are constructed with equal `name`, `type`, `weight`, and `producedFrom`
- **THEN** they are `equal` and have equal `hashCode`s

### Requirement: ResolvedSegment carries producedFrom

The `ResolvedSegment` type defined by the `source-path-resolution` capability SHALL grow an additional `AnnotatedConstruct producedFrom` field surfacing the underlying `Element` (the getter `ExecutableElement`, the field `VariableElement`, etc.) that the resolver matched. See the `source-path-resolution` capability spec for the full updated requirement.

#### Scenario: ResolvedSegment cross-reference is consistent
- **WHEN** the `source-path-resolution` capability's "ResolvedSegment result type" requirement is inspected
- **THEN** it declares a `producedFrom` field of type `AnnotatedConstruct`

### Requirement: GroupBuild result type

The percolate-spi module SHALL define an immutable Lombok `@Value` type `io.github.joke.percolate.spi.GroupBuild` with these fields:
- `List<Slot> slots` — the slots required to build, in positional-binding order.
- `GroupCodegen codegen` — the lambda that assembles the group's combined expression from the inputs.

The `slots` list SHALL be retained by reference; the driver SHALL preserve order when emitting `REALISED` edges and `MARKER` edges.

#### Scenario: GroupBuild slots are positional
- **WHEN** a `GroupBuild` is constructed with `[Slot("firstName", String, 1), Slot("lastName", String, 1)]`
- **THEN** `getSlots()` returns the list in that exact order
- **AND** the driver's emitted REALISED edges respect the order via membership in the registered `ExpansionGroup`'s view and the codegen's positional binding

### Requirement: Strategy registration via ServiceLoader and AutoService

Strategies SHALL be discovered uniformly via Java `ServiceLoader<Interface>` for each strategy interface (`GroupTarget`, `Bridge`, `PathSegmentResolver`). Built-in strategies — shipped from the `percolate-strategies-builtin` module — SHALL declare their service registration via Google `@AutoService(<Interface>.class)` so that the `auto-service`-generated `META-INF/services/...` files ship inside that module's JAR. User-supplied strategies in third-party JARs SHALL register the same way.

The processor's Dagger module SHALL provide each strategy list as `@Singleton List<Interface>` by:
1. Calling `ServiceLoader.load(<Interface>.class, classLoader)` exactly once.
2. Materialising the iterator into a list.
3. Sorting that list lexicographically by `getClass().getName()` (FQN ascending).
4. Wrapping in `Collections.unmodifiableList(...)` before publishing.

The processor module SHALL declare `percolate-strategies-builtin` as a `runtimeOnly` Gradle dependency so that, by default, end users receive the built-in strategies on their annotation-processor classpath without an explicit declaration. End users wanting a custom-only setup MAY `exclude` the `strategies-builtin` artifact.

#### Scenario: Built-in ConstructorCall is annotated AutoService
- **WHEN** the source of `ConstructorCall` (in `strategies-builtin/src/main/java/io/github/joke/percolate/spi/builtins/`) is inspected
- **THEN** the class carries `@AutoService(GroupTarget.class)`

#### Scenario: Built-in DirectAssign is annotated AutoService
- **WHEN** the source of `DirectAssign` (in `strategies-builtin/src/main/java/io/github/joke/percolate/spi/builtins/`) is inspected
- **THEN** the class carries `@AutoService(Bridge.class)`

#### Scenario: Provided strategy list is sorted by FQN
- **WHEN** Dagger provides the `List<Bridge>` for a round
- **THEN** the list is sorted ascending by `getClass().getName()` for each element

#### Scenario: User strategy registered via META-INF/services is discovered
- **WHEN** a JAR on the annotation-processor classpath contains `META-INF/services/io.github.joke.percolate.spi.Bridge` referencing a user class
- **THEN** the user class is included in the `List<Bridge>` provided by Dagger
- **AND** the list remains sorted by FQN including the user class

#### Scenario: ServiceLoader is invoked once per round
- **WHEN** `ExpandStage.apply(graph)` is invoked twice in a round
- **THEN** `ServiceLoader.load(...)` is invoked exactly once for each strategy interface across the round

#### Scenario: Processor declares strategies-builtin as runtimeOnly
- **WHEN** `processor/build.gradle` is inspected
- **THEN** it declares `runtimeOnly project(':strategies-builtin')`
- **AND** no `compile` / `implementation` / `api` configuration mentions `:strategies-builtin`

### Requirement: PathSegmentResolver interface

The percolate-spi module SHALL define a Java interface `io.github.joke.percolate.spi.PathSegmentResolver` with the following shape:

```java
public interface PathSegmentResolver {
    Optional<ResolvedSegment> resolve(
        TypeMirror parentType,
        String segment,
        ResolveCtx ctx);
}
```

Implementations SHALL return `Optional.of(ResolvedSegment)` describing a typed access for `segment` against a value of `parentType`, or `Optional.empty()` if the resolver does not apply. Implementations MUST NOT throw on a non-applicable input — they SHALL return `Optional.empty()` instead.

`PathSegmentResolver` is part of the SPI package surface defined in *SPI package isolation* and SHALL therefore live in `io.github.joke.percolate.spi` under the same `@NullMarked` declaration as the other SPI types.

The full per-resolver semantics (probe order, codegen shape, weight) are defined in the `source-path-resolution` capability.

#### Scenario: PathSegmentResolver with no match returns empty
- **WHEN** an implementor decides nothing matches the inputs
- **THEN** `resolve(...)` returns `Optional.empty()`
- **AND** does not throw

### Requirement: ResolvedSegment result type

The percolate-spi module SHALL define an immutable value type `io.github.joke.percolate.spi.ResolvedSegment` with three fields:

- `TypeMirror getReturnType()` — the type produced by the access.
- `EdgeCodegen getCodegen()` — renders the access expression.
- `int getWeight()` — strategy weight.

The type SHALL be Lombok `@Value`-style: final fields, all-args constructor, value semantics, equality field-by-field. The codegen SHALL receive one slot through `IncomingValues` (representing the parent value) and a `VarNames` placeholder.

#### Scenario: ResolvedSegment is immutable
- **WHEN** a `ResolvedSegment` is constructed via its all-args constructor
- **THEN** none of the three fields can be reassigned (no setters)

### Requirement: PathSegmentResolver registration via ServiceLoader and AutoService

Concrete `PathSegmentResolver` implementations SHALL register through `java.util.ServiceLoader` by adding the resource `META-INF/services/io.github.joke.percolate.spi.PathSegmentResolver`. The recommended mechanism for built-ins is `@com.google.auto.service.AutoService(PathSegmentResolver.class)` on the implementing class, which Google AutoService translates into the service file at compile time.

`ProcessorModule.pathSegmentResolvers()` SHALL collect the resolvers via `ServiceLoader.load(PathSegmentResolver.class, ProcessorModule.class.getClassLoader())`, sort by `Class.getName()` ascending, and return as `@Singleton` `List<PathSegmentResolver>`.

#### Scenario: ProcessorModule provides a sorted, singleton resolver list
- **WHEN** the `pathSegmentResolvers()` `@Provides` method is invoked twice
- **THEN** both invocations return lists in `Class.getName()` ascending order
- **AND** the iteration order is identical across invocations

### Requirement: ConstructorCall built-in (exact match)

The `percolate-strategies-builtin` module SHALL ship `io.github.joke.percolate.spi.builtins.ConstructorCall` implementing `GroupTarget`. `ConstructorCall` SHALL inspect the accessible constructors of the return type. A constructor matches iff `Set.copyOf(ctor.parameterNames())` equals `Set.copyOf(targetTails)`.

For an exact match, `ConstructorCall` SHALL return `Optional.of(GroupBuild)` whose `slots` list contains one `Slot` per constructor parameter, in constructor-declaration order, with:
- `name` = the parameter's simple name,
- `type` = the parameter's declared type,
- `weight` = `Weights.STEP`.

The `GroupCodegen` SHALL render `new <ReturnType>(<positional-input-list>)` where the positional input list is the `IncomingValues.byGroupPosition()` joined by `", "`.

If no constructor matches exactly, `ConstructorCall` SHALL return `Optional.empty()`. Subset and superset matches SHALL NOT match.

#### Scenario: ConstructorCall matches an exact-name constructor
- **WHEN** `ConstructorCall.buildFor(<Human>, ["firstName", "lastName"], ctx)` is invoked and `Human` declares `Human(String firstName, String lastName)`
- **THEN** the result is `Optional` containing one `GroupBuild`
- **AND** the build's `slots` list contains, in order, `Slot("firstName", String, Weights.STEP)` and `Slot("lastName", String, Weights.STEP)`
- **AND** the `GroupCodegen` renders `new Human(firstNameVar, lastNameVar)` for inputs named `firstNameVar` and `lastNameVar`

#### Scenario: ConstructorCall does not match a subset
- **WHEN** `ConstructorCall.buildFor(<Human>, ["firstName", "lastName", "age"], ctx)` is invoked and only `Human(String firstName, String lastName)` exists
- **THEN** the result is `Optional.empty()`

#### Scenario: ConstructorCall does not match a superset
- **WHEN** `ConstructorCall.buildFor(<Human>, ["firstName"], ctx)` is invoked and only `Human(String firstName, String lastName)` exists
- **THEN** the result is `Optional.empty()`

#### Scenario: ConstructorCall ignores parameter order in matching
- **WHEN** `ConstructorCall.buildFor(<Human>, ["lastName", "firstName"], ctx)` is invoked and `Human(String firstName, String lastName)` is the only constructor
- **THEN** the result is `Optional` containing a `GroupBuild` whose `slots` are in constructor-declaration order (`firstName` then `lastName`), regardless of `targetTails` order

### Requirement: DirectAssign built-in

The `percolate-strategies-builtin` module SHALL ship `io.github.joke.percolate.spi.builtins.DirectAssign` implementing `Bridge`. `DirectAssign` SHALL return `Stream.of(BridgeStep(sourceType, targetType, Weights.NOOP, identityCodegen))` whenever `ctx.types().isSameType(sourceType, targetType)` returns `true`. Otherwise it SHALL return `Stream.empty()`.

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

### Requirement: Weights.METHOD constant

The percolate-spi module SHALL extend the `Weights` constants in `io.github.joke.percolate.spi.Weights` with a constant `METHOD`. The constant SHALL be a positive `int` representing the base cost of a method-call hop.

For v1, `Weights.METHOD` SHALL equal `Weights.STEP`. The numerical value is documented as the base cost; method-call edges may carry weights greater than `Weights.METHOD` when JLS-specificity distance adds to the cost (see `MethodCallBridge` requirement).

#### Scenario: Weights.METHOD exists and is positive
- **WHEN** the source of `Weights` is inspected
- **THEN** the class declares a public static final int `METHOD`
- **AND** `Weights.METHOD > 0`

### Requirement: MethodCallBridge built-in

The `percolate-strategies-builtin` module SHALL ship `io.github.joke.percolate.spi.builtins.MethodCallBridge` implementing `Bridge` and annotated `@AutoService(Bridge.class)`.

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

### Requirement: Weights.CONTAINER constant

The percolate-spi module SHALL extend `io.github.joke.percolate.spi.Weights`
with a constant `CONTAINER` (a positive `int`) representing the
base cost of a container-shaped hop.

For v1, `Weights.CONTAINER` SHALL equal `2`, slightly heavier than
`Weights.STEP` and `Weights.METHOD` (both `1`) and cheaper than
`Weights.EXPENSIVE` (`3`). Container built-in strategies
(`OptionalWrap`, `OptionalUnwrap`, `OptionalCollect`, `ListWrap`,
`ListCollect`, `SetWrap`, `SetCollect`, `ArrayCollect`,
`IterableUnwrap`) SHALL use this constant unmodified as the
`weight` field of every emitted `BridgeStep`. No per-shape
weight variation is defined for v1.

#### Scenario: Weights.CONTAINER exists and is positive
- **WHEN** the source of `Weights` is inspected
- **THEN** the class declares a public static final int `CONTAINER`
- **AND** `Weights.CONTAINER > 0`

#### Scenario: Weights.CONTAINER value
- **WHEN** `Weights.CONTAINER` is read
- **THEN** the value is `2`

### Requirement: Built-in service registration smoke spec

The `percolate-strategies-builtin` module SHALL contain a Spock specification at `strategies-builtin/src/test/groovy/io/github/joke/percolate/spi/builtins/BuiltinServiceRegistrationSpec.groovy` that asserts the contract between modules: when `percolate-strategies-builtin` is on the classpath, `ServiceLoader.load(...)` discovers exactly the expected built-in classes for each SPI interface.

The spec SHALL load each of `Bridge`, `GroupTarget`, and `PathSegmentResolver` via `ServiceLoader.load(<Interface>.class)` and assert that the discovered set contains, at minimum, the shipped built-ins:

- `Bridge`: `DirectAssign`, `MethodCallBridge`, `IterableUnwrap`, `OptionalUnwrap`, `SetCollect`, `ListCollect`, `ArrayCollect`, `OptionalCollect`, `SetWrap`, `ListWrap`, `OptionalWrap`.
- `GroupTarget`: `ConstructorCall`.
- `PathSegmentResolver`: `GetterPathResolver`, `FieldPathResolver`, `RecordPathResolver`.

The spec SHALL additionally assert that `SetMap`, `ListMap`, and `OptionalMap` are NOT discovered — those classes are removed by `split-container-bridges`.

The spec SHALL be tagged `@spock.lang.Tag('unit')` and SHALL NOT invoke `ExpansionHarness` or any expansion-pipeline code. Its sole concern is verifying that the `META-INF/services/...` files generated by `auto-service` correctly register the strategy classes.

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
