# Expansion Strategy SPI Spec

## Purpose

This spec defines the strategy author surface for the expansion engine: immutable result types, strategy interfaces, built-in strategies, and service registration via ServiceLoader and AutoService.

## Requirements

### Requirement: SPI package isolation

The percolate-spi Gradle module SHALL ship a package `io.github.joke.percolate.spi` containing exactly the strategy-author surface: three interfaces (`SourceStep`, `GroupTarget`, `Bridge`), four immutable result types (`Step`, `BridgeStep`, `Slot`, `GroupBuild`), the `ResolveCtx` interface, the codegen interfaces (`EdgeCodegen`, `GroupCodegen`, `IncomingValues`, `VarNames`), the `Receiver` / `ThisReceiver` / `CallableMethods` / `MethodCandidate` / `ElementSeed` types, and the `Containers` and `Weights` utilities. The module SHALL depend only on JDK types plus `com.palantir.javapoet` (because `CodeBlock` is part of the codegen interface surface). It SHALL NOT depend on `percolate-annotations` or `percolate-processor`.

The package SHALL declare `@NullMarked` via `package-info.java`.

Built-in strategies (`GetterRead`, `ConstructorCall`, `DirectAssign`, the seven container bridges, `MethodCallBridge`) SHALL ship from a separate Gradle module `percolate-strategies-builtin` whose only compile dependency is `percolate-spi`. They SHALL NOT import any class from `io.github.joke.percolate.processor.graph` or `io.github.joke.percolate.processor.stages.expand`. This invariant SHALL be enforced structurally by the module compile graph: `percolate-strategies-builtin`'s `build.gradle` declares no compile dependency on `percolate-processor`, so engine internals are unreachable from built-in sources.

#### Scenario: spi package has @NullMarked
- **WHEN** the source of `spi/src/main/java/io/github/joke/percolate/spi/package-info.java` is inspected
- **THEN** the package declaration carries `@org.jspecify.annotations.NullMarked`

#### Scenario: Built-in strategies have no forbidden imports
- **WHEN** the import statements of `GetterRead`, `ConstructorCall`, `DirectAssign`, and the seven container bridges are inspected
- **THEN** none reference any class in `io.github.joke.percolate.processor.graph.*` or `io.github.joke.percolate.processor.stages.expand.*`
- **AND** the enforcement is structural: `strategies-builtin`'s `build.gradle` declares no compile dependency on `processor`, so attempting such an import would fail compilation

#### Scenario: SPI module has no dependency on annotations or processor
- **WHEN** `spi/build.gradle` is inspected
- **THEN** it declares neither `project(':annotations')` nor `project(':processor')` on any `compile` / `implementation` / `api` configuration
- **AND** its only non-JDK `api` dependency is `com.palantir.javapoet:javapoet`

### Requirement: SourceStep interface

The percolate-spi module SHALL define a Java interface `io.github.joke.percolate.spi.SourceStep` with the following shape:

```java
public interface SourceStep {
    Stream<Step> stepsFrom(TypeMirror sourceType, String pathTail, ResolveCtx ctx);
    default int priority() { return 0; }
}
```

Implementations SHALL return zero or more `Step` results describing realisations from a typed source value into a typed step keyed by the path tail. An empty stream signals "this strategy does not apply". Implementations MUST NOT throw on a non-applicable input — they SHALL return `Stream.empty()` instead.

#### Scenario: SourceStep with no match returns empty stream
- **WHEN** an implementor decides nothing matches the inputs
- **THEN** `stepsFrom(...)` returns `Stream.empty()`
- **AND** does not throw

#### Scenario: SourceStep priority defaults to zero
- **WHEN** an implementor does not override `priority()`
- **THEN** `priority()` returns `0`

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

### Requirement: Step result type

The percolate-spi module SHALL define an immutable Lombok `@Value` type `io.github.joke.percolate.spi.Step` with these fields, in this order:
- `TypeMirror produces` — the type the step produces.
- `int weight` — the cost; documented to use values from `Weights` (`NOOP`, `STEP`, `COPY`, `EXPENSIVE`).
- `EdgeCodegen codegen` — the codegen lambda that renders the step.

#### Scenario: Step exposes its three fields
- **WHEN** a `Step` is constructed with `produces`, `weight`, and `codegen`
- **THEN** `getProduces()`, `getWeight()`, and `getCodegen()` return those values

#### Scenario: Step is value-equal
- **WHEN** two `Step` instances are constructed with equal `produces`, `weight`, and `codegen`
- **THEN** they are `equal` and have equal `hashCode`s

### Requirement: BridgeStep result type

The percolate-spi module SHALL define an immutable Lombok `@Value` type `io.github.joke.percolate.spi.BridgeStep` with these fields, in this order:

- `TypeMirror inputType` — the type the strategy consumes.
- `TypeMirror outputType` — the type the strategy produces.
- `int weight` — the cost; documented to use values from `Weights`.
- `EdgeCodegen codegen` — the codegen lambda that renders the step.
- `List<ElementSeed> elementSeeds` — zero or more inner-scope conversion requests the driver SHALL spawn alongside the outer REALISED edge produced from this step. Empty for same-location bridge steps (existing strategies); non-empty for container "map" steps that need element-scope sub-conversions.

For a direct bridge (the strategy emits an edge between the two endpoints of the seed query), `inputType` equals the seed's source-side type and `outputType` equals the seed's target-side type.

For a chain-step bridge (the strategy emits an edge that requires an upstream value of a type not present in the seed's source side), `inputType` is the type the strategy needs and `outputType` is the type the strategy produces. The driver materialises the input and output endpoints accordingly (see `graph-expansion`).

For a container "map" step (the strategy emits an outer edge between two container-typed nodes, requiring an inner per-element sub-conversion), `inputType` and `outputType` describe the OUTER container endpoints; the inner element-level conversion is described by `elementSeeds` and resolved by the driver and downstream strategies through the same fixed-point machinery.

`elementSeeds` SHALL be a defensive copy held by the value type; mutations to a caller-passed list after construction SHALL NOT affect the constructed step.

#### Scenario: BridgeStep exposes its five fields
- **WHEN** a `BridgeStep` is constructed with `inputType`, `outputType`, `weight`, `codegen`, and `elementSeeds`
- **THEN** `getInputType()`, `getOutputType()`, `getWeight()`, `getCodegen()`, and `getElementSeeds()` return those values

#### Scenario: BridgeStep is value-equal
- **WHEN** two `BridgeStep` instances are constructed with equal field values (including equal `elementSeeds` lists)
- **THEN** they are `equal` and have equal `hashCode`s

#### Scenario: BridgeStep with empty elementSeeds behaves as a same-location step
- **WHEN** a `BridgeStep` is constructed with `elementSeeds = List.of()`
- **THEN** the step describes a single-edge emission with no inner element-scope work
- **AND** the driver applies the unified edge-emission rule without spawning element nodes

#### Scenario: BridgeStep with non-empty elementSeeds describes a container map
- **WHEN** a `BridgeStep` is constructed with `elementSeeds = [ElementSeed("element", innerIn, innerOut)]`
- **THEN** the driver emits the outer REALISED edge between the step's `inputType` and `outputType` endpoints AND spawns a pair of parent-linked element nodes plus a SEED between them (see `graph-expansion`)

### Requirement: Slot result type

The percolate-spi module SHALL define an immutable Lombok `@Value` type `io.github.joke.percolate.spi.Slot` with these fields:
- `String name` — the slot's binding name (matches a directive target tail or a builder-method name).
- `TypeMirror type` — the slot's required type.
- `int weight` — the cost of filling the slot.

#### Scenario: Slot exposes its three fields
- **WHEN** a `Slot` is constructed with `name`, `type`, and `weight`
- **THEN** the getters return those values

### Requirement: GroupBuild result type

The percolate-spi module SHALL define an immutable Lombok `@Value` type `io.github.joke.percolate.spi.GroupBuild` with these fields:
- `List<Slot> slots` — the slots required to build, in positional-binding order.
- `GroupCodegen codegen` — the lambda that assembles the group's combined expression from the inputs.

The `slots` list SHALL be retained by reference; the driver SHALL preserve order when emitting `REALISED` edges and `MARKER` edges.

#### Scenario: GroupBuild slots are positional
- **WHEN** a `GroupBuild` is constructed with `[Slot("firstName", String, 1), Slot("lastName", String, 1)]`
- **THEN** `getSlots()` returns the list in that exact order
- **AND** the driver's emitted REALISED edges respect the order via the shared `groupId` and the codegen's positional binding

### Requirement: Strategy registration via ServiceLoader and AutoService

Strategies SHALL be discovered uniformly via Java `ServiceLoader<Interface>` for each strategy interface (`SourceStep`, `GroupTarget`, `Bridge`). Built-in strategies — shipped from the `percolate-strategies-builtin` module — SHALL declare their service registration via Google `@AutoService(<Interface>.class)` so that the `auto-service`-generated `META-INF/services/...` files ship inside that module's JAR. User-supplied strategies in third-party JARs SHALL register the same way.

The processor's Dagger module SHALL provide each strategy list as `@Singleton List<Interface>` by:
1. Calling `ServiceLoader.load(<Interface>.class, classLoader)` exactly once.
2. Materialising the iterator into a list.
3. Sorting that list lexicographically by `getClass().getName()` (FQN ascending).
4. Wrapping in `Collections.unmodifiableList(...)` before publishing.

The processor module SHALL declare `percolate-strategies-builtin` as a `runtimeOnly` Gradle dependency so that, by default, end users receive the built-in strategies on their annotation-processor classpath without an explicit declaration. End users wanting a custom-only setup MAY `exclude` the `strategies-builtin` artifact.

#### Scenario: Built-in GetterRead is annotated AutoService
- **WHEN** the source of `GetterRead` (in `strategies-builtin/src/main/java/io/github/joke/percolate/spi/builtins/`) is inspected
- **THEN** the class carries `@AutoService(SourceStep.class)`

#### Scenario: Built-in ConstructorCall is annotated AutoService
- **WHEN** the source of `ConstructorCall` (in `strategies-builtin/src/main/java/io/github/joke/percolate/spi/builtins/`) is inspected
- **THEN** the class carries `@AutoService(GroupTarget.class)`

#### Scenario: Built-in DirectAssign is annotated AutoService
- **WHEN** the source of `DirectAssign` (in `strategies-builtin/src/main/java/io/github/joke/percolate/spi/builtins/`) is inspected
- **THEN** the class carries `@AutoService(Bridge.class)`

#### Scenario: Provided strategy list is sorted by FQN
- **WHEN** Dagger provides the `List<SourceStep>` for a round
- **THEN** the list is sorted ascending by `getClass().getName()` for each element

#### Scenario: User strategy registered via META-INF/services is discovered
- **WHEN** a JAR on the annotation-processor classpath contains `META-INF/services/io.github.joke.percolate.spi.SourceStep` referencing a user class
- **THEN** the user class is included in the `List<SourceStep>` provided by Dagger
- **AND** the list remains sorted by FQN including the user class

#### Scenario: ServiceLoader is invoked once per round
- **WHEN** `ExpandStage.apply(graph)` is invoked twice in a round
- **THEN** `ServiceLoader.load(...)` is invoked exactly once for each strategy interface across the round

#### Scenario: Processor declares strategies-builtin as runtimeOnly
- **WHEN** `processor/build.gradle` is inspected
- **THEN** it declares `runtimeOnly project(':strategies-builtin')`
- **AND** no `compile` / `implementation` / `api` configuration mentions `:strategies-builtin`

### Requirement: GetterRead built-in

The `percolate-strategies-builtin` module SHALL ship `io.github.joke.percolate.spi.builtins.GetterRead` implementing `SourceStep`. `GetterRead` SHALL inspect `ctx.elements().getAllMembers(<TypeElement of sourceType>)` for a zero-argument method whose simple name matches conventional getter naming for the given `pathTail`:
- `get<PathTailCapitalised>` (e.g., `lastName` → `getLastName`),
- `is<PathTailCapitalised>` if the method's return type is `boolean` or `Boolean`.

For each matching method, `GetterRead` SHALL emit one `Step` whose `produces` is the method's return type, `weight` is `Weights.STEP`, and `codegen` renders `<input>.<methodName>()` where `<input>` is the single incoming variable supplied by `IncomingValues.single()`.

`GetterRead` SHALL NOT consider methods declared on `java.lang.Object` or its subclasses up to (but not including) the `sourceType`'s declaring element — i.e., `getClass()` is excluded.

#### Scenario: GetterRead finds a public getter
- **WHEN** `GetterRead.stepsFrom(<Person>, "lastName", ctx)` is invoked and `Person` declares `String getLastName()`
- **THEN** the result stream contains one `Step`
- **AND** the step's `produces` is the `TypeMirror` for `String`
- **AND** the step's `weight` equals `Weights.STEP`
- **AND** the step's `codegen` renders `personVar.getLastName()` when given variable name `"personVar"`

#### Scenario: GetterRead finds a boolean is-getter
- **WHEN** `GetterRead.stepsFrom(<Foo>, "active", ctx)` is invoked and `Foo` declares `boolean isActive()`
- **THEN** the result stream contains one `Step` whose `codegen` renders `fooVar.isActive()`

#### Scenario: GetterRead returns empty when no getter exists
- **WHEN** `GetterRead.stepsFrom(<Person>, "nonexistent", ctx)` is invoked and no matching accessor exists
- **THEN** the result stream is empty

#### Scenario: GetterRead excludes Object members
- **WHEN** `GetterRead.stepsFrom(<Person>, "class", ctx)` is invoked
- **THEN** the result stream is empty (`getClass()` from Object is excluded)

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

## MODIFIED Requirements

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

### Requirement: ElementSeed result type

The percolate-spi module SHALL define an immutable Lombok `@Value` type
`io.github.joke.percolate.spi.ElementSeed` with three fields:

- `String role` — a stable discriminator naming the element scope's
  role within its container. The default value for single-element-
  scope containers is the literal string `"element"`. Future multi-
  role containers (e.g., `Map<K,V>` with `"key"` and `"value"`) use
  this field to distinguish their scopes.
- `TypeMirror inputType` — the type of the element-scope value
  flowing FROM the container's input side.
- `TypeMirror outputType` — the type of the element-scope value
  flowing INTO the container's output side.

`ElementSeed` SHALL be value-equal by all three fields. Two
`ElementSeed`s with the same role but different types are distinct
seeds and produce distinct element-scope SEEDs in the graph.

The `role` field SHALL be a non-empty `String`. The driver uses the
role as part of the `ElementLocation` segment and consequently as
part of `Node.id()` for element nodes; an empty role would produce
ambiguous ids.

#### Scenario: ElementSeed exposes its three fields
- **WHEN** an `ElementSeed` is constructed with `role`, `inputType`, and `outputType`
- **THEN** `getRole()`, `getInputType()`, and `getOutputType()` return those values

#### Scenario: ElementSeed is value-equal
- **WHEN** two `ElementSeed` instances are constructed with equal field values
- **THEN** they are `equal` and have equal `hashCode`s

#### Scenario: ElementSeed default role for single-scope containers is "element"
- **WHEN** a container `Bridge` strategy ships for a single-element-scope container (Optional, List, Set, …)
- **THEN** its emitted `ElementSeed`s SHALL carry `role = "element"`

### Requirement: Weights.CONTAINER constant

The percolate-spi module SHALL extend `io.github.joke.percolate.spi.Weights`
with a constant `CONTAINER` (a positive `int`) representing the
base cost of a container-shaped hop.

For v1, `Weights.CONTAINER` SHALL equal `2`, slightly heavier than
`Weights.STEP` and `Weights.METHOD` (both `1`) and cheaper than
`Weights.EXPENSIVE` (`3`). Container built-in strategies
(`OptionalWrap`, `OptionalUnwrap`, `OptionalMap`, `ListWrap`,
`ListMap`, `SetWrap`, `SetMap`) SHALL use this constant unmodified
as the `weight` field of every emitted `BridgeStep`. No per-shape
weight variation is defined for v1.

#### Scenario: Weights.CONTAINER exists and is positive
- **WHEN** the source of `Weights` is inspected
- **THEN** the class declares a public static final int `CONTAINER`
- **AND** `Weights.CONTAINER > 0`

#### Scenario: Weights.CONTAINER value
- **WHEN** `Weights.CONTAINER` is read
- **THEN** the value is `2`

## ADDED Requirements

### Requirement: Built-in service registration smoke spec

The `percolate-strategies-builtin` module SHALL contain a Spock specification at `strategies-builtin/src/test/groovy/io/github/joke/percolate/spi/builtins/BuiltinServiceRegistrationSpec.groovy` that asserts the contract between modules: when `percolate-strategies-builtin` is on the classpath, `ServiceLoader.load(...)` discovers exactly the expected built-in classes for each SPI interface.

The spec SHALL load each of `Bridge`, `SourceStep`, and `GroupTarget` via `ServiceLoader.load(<Interface>.class)` and assert that the discovered set contains, at minimum, the eleven shipped built-ins:

- `Bridge`: `DirectAssign`, `ListMap`, `ListWrap`, `SetMap`, `SetWrap`, `OptionalMap`, `OptionalUnwrap`, `OptionalWrap`, `MethodCallBridge`.
- `SourceStep`: `GetterRead`.
- `GroupTarget`: `ConstructorCall`.

The spec SHALL be tagged `@spock.lang.Tag('unit')` and SHALL NOT invoke `ExpansionHarness` or any expansion-pipeline code. Its sole concern is verifying that the `META-INF/services/...` files generated by `auto-service` correctly register the strategy classes.

#### Scenario: ServiceLoader discovers all expected Bridge builtins
- **WHEN** `ServiceLoader.load(Bridge.class)` is invoked from `BuiltinServiceRegistrationSpec`
- **THEN** the returned stream's classes contain, as a subset, `DirectAssign`, `ListMap`, `ListWrap`, `SetMap`, `SetWrap`, `OptionalMap`, `OptionalUnwrap`, `OptionalWrap`, and `MethodCallBridge`

#### Scenario: ServiceLoader discovers all expected SourceStep builtins
- **WHEN** `ServiceLoader.load(SourceStep.class)` is invoked from `BuiltinServiceRegistrationSpec`
- **THEN** the returned stream's classes contain `GetterRead`

#### Scenario: ServiceLoader discovers all expected GroupTarget builtins
- **WHEN** `ServiceLoader.load(GroupTarget.class)` is invoked from `BuiltinServiceRegistrationSpec`
- **THEN** the returned stream's classes contain `ConstructorCall`

#### Scenario: Spec does not depend on the expansion pipeline
- **WHEN** the source of `BuiltinServiceRegistrationSpec` is inspected
- **THEN** no import references `io.github.joke.percolate.processor.*`
- **AND** no invocation of `ExpansionHarness`, `ExpandStage`, or `ProcessorModule` appears
