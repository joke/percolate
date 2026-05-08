# Expansion Strategy SPI Spec

## Purpose

This spec defines the strategy author surface for the expansion engine: immutable result types, strategy interfaces, built-in strategies, and service registration via ServiceLoader and AutoService.

## Requirements

### Requirement: SPI package isolation

The processor SHALL ship a new package `io.github.joke.percolate.processor.spi` containing exactly the strategy-author surface: three interfaces (`SourceStep`, `GroupTarget`, `Bridge`), four immutable result types (`Step`, `BridgeStep`, `Slot`, `GroupBuild`), the `ResolveCtx` interface, and re-exports of the codegen interfaces (`EdgeCodegen`, `GroupCodegen`, `IncomingValues`, `VarNames`) shipped by the alignment change.

The package SHALL declare `@NullMarked` via `package-info.java`.

Built-in strategies (`GetterRead`, `ConstructorCall`, `DirectAssign`) SHALL NOT import any class from `io.github.joke.percolate.processor.graph` or `io.github.joke.percolate.processor.stages.expand`. This invariant SHALL be enforced by an architectural test.

#### Scenario: spi package has @NullMarked
- **WHEN** the source of `processor/spi/package-info.java` is inspected
- **THEN** the package declaration carries `@org.jspecify.annotations.NullMarked`

#### Scenario: Built-in strategies have no forbidden imports
- **WHEN** the import statements of `GetterRead`, `ConstructorCall`, and `DirectAssign` are inspected
- **THEN** none reference any class in `io.github.joke.percolate.processor.graph.*` or `io.github.joke.percolate.processor.stages.expand.*`

### Requirement: SourceStep interface

The processor SHALL define a Java interface `io.github.joke.percolate.processor.spi.SourceStep` with the following shape:

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

The processor SHALL define a Java interface `io.github.joke.percolate.processor.spi.GroupTarget` with the following shape:

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

The processor SHALL define a Java interface `io.github.joke.percolate.processor.spi.Bridge` with the following shape:

```java
public interface Bridge {
    Optional<BridgeStep> bridge(TypeMirror sourceType, TypeMirror targetType, ResolveCtx ctx);
    default int priority() { return 0; }
}
```

Implementations SHALL return one `BridgeStep` describing a typed-to-typed connection between `sourceType` and `targetType`, or `Optional.empty()` if the strategy does not apply.

#### Scenario: Bridge with no match returns empty
- **WHEN** an implementor decides the input pair is not bridgeable by it
- **THEN** `bridge(...)` returns `Optional.empty()`
- **AND** does not throw

#### Scenario: Bridge priority defaults to zero
- **WHEN** an implementor does not override `priority()`
- **THEN** `priority()` returns `0`

### Requirement: ResolveCtx exposes only Types and Elements

The processor SHALL define an interface `io.github.joke.percolate.processor.spi.ResolveCtx` with exactly two methods:

```java
public interface ResolveCtx {
    Types types();
    Elements elements();
}
```

The interface SHALL NOT expose any reference to `MapperGraph`, `Edge`, `Node`, `EdgeKind`, `MapperStep`, or any other type from `processor.graph` or `processor.stages.expand`. A strategy author SHALL be able to write a complete strategy by importing only `processor.spi.*`, `javax.lang.model.*`, `com.squareup.javapoet.*`, and JDK types.

#### Scenario: ResolveCtx provides Types
- **WHEN** `resolveCtx.types()` is invoked
- **THEN** it returns the `javax.lang.model.util.Types` instance from the active `ProcessingEnvironment`

#### Scenario: ResolveCtx provides Elements
- **WHEN** `resolveCtx.elements()` is invoked
- **THEN** it returns the `javax.lang.model.util.Elements` instance from the active `ProcessingEnvironment`

#### Scenario: ResolveCtx interface has no other methods
- **WHEN** the source of `ResolveCtx` is inspected
- **THEN** the interface declares exactly two methods (`types()` and `elements()`) and zero default methods

### Requirement: Step result type

The processor SHALL define an immutable Lombok `@Value` type `io.github.joke.percolate.processor.spi.Step` with these fields, in this order:
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

The processor SHALL define an immutable Lombok `@Value` type `io.github.joke.percolate.processor.spi.BridgeStep` with these fields:
- `int weight`,
- `EdgeCodegen codegen`.

A `BridgeStep` does not declare a `produces` type because a bridge connects two already-typed nodes; the type pair is implicit in the endpoints.

#### Scenario: BridgeStep exposes weight and codegen
- **WHEN** a `BridgeStep` is constructed with `weight` and `codegen`
- **THEN** `getWeight()` and `getCodegen()` return those values

### Requirement: Slot result type

The processor SHALL define an immutable Lombok `@Value` type `io.github.joke.percolate.processor.spi.Slot` with these fields:
- `String name` — the slot's binding name (matches a directive target tail or a builder-method name).
- `TypeMirror type` — the slot's required type.
- `int weight` — the cost of filling the slot.

#### Scenario: Slot exposes its three fields
- **WHEN** a `Slot` is constructed with `name`, `type`, and `weight`
- **THEN** the getters return those values

### Requirement: GroupBuild result type

The processor SHALL define an immutable Lombok `@Value` type `io.github.joke.percolate.processor.spi.GroupBuild` with these fields:
- `List<Slot> slots` — the slots required to build, in positional-binding order.
- `GroupCodegen codegen` — the lambda that assembles the group's combined expression from the inputs.

The `slots` list SHALL be retained by reference; the driver SHALL preserve order when emitting `REALISED` edges and `MARKER` edges.

#### Scenario: GroupBuild slots are positional
- **WHEN** a `GroupBuild` is constructed with `[Slot("firstName", String, 1), Slot("lastName", String, 1)]`
- **THEN** `getSlots()` returns the list in that exact order
- **AND** the driver's emitted REALISED edges respect the order via the shared `groupId` and the codegen's positional binding

### Requirement: Strategy registration via ServiceLoader and AutoService

Strategies SHALL be discovered uniformly via Java `ServiceLoader<Interface>` for each strategy interface (`SourceStep`, `GroupTarget`, `Bridge`). Built-in strategies SHALL declare their service registration via Google `@AutoService(<Interface>.class)` so that the `auto-service`-generated `META-INF/services/...` files ship automatically. User-supplied strategies in third-party JARs SHALL register the same way.

The `ExpandStage`'s Dagger module SHALL provide each strategy list as `@Singleton List<Interface>` by:
1. Calling `ServiceLoader.load(<Interface>.class, classLoader)` exactly once.
2. Materialising the iterator into a list.
3. Sorting that list lexicographically by `getClass().getName()` (FQN ascending).
4. Wrapping in `Collections.unmodifiableList(...)` before publishing.

#### Scenario: Built-in GetterRead is annotated AutoService
- **WHEN** the source of `GetterRead` is inspected
- **THEN** the class carries `@AutoService(SourceStep.class)`

#### Scenario: Built-in ConstructorCall is annotated AutoService
- **WHEN** the source of `ConstructorCall` is inspected
- **THEN** the class carries `@AutoService(GroupTarget.class)`

#### Scenario: Built-in DirectAssign is annotated AutoService
- **WHEN** the source of `DirectAssign` is inspected
- **THEN** the class carries `@AutoService(Bridge.class)`

#### Scenario: Provided strategy list is sorted by FQN
- **WHEN** Dagger provides the `List<SourceStep>` for a round
- **THEN** the list is sorted ascending by `getClass().getName()` for each element

#### Scenario: User strategy registered via META-INF/services is discovered
- **WHEN** a JAR on the annotation-processor classpath contains `META-INF/services/io.github.joke.percolate.processor.spi.SourceStep` referencing a user class
- **THEN** the user class is included in the `List<SourceStep>` provided by Dagger
- **AND** the list remains sorted by FQN including the user class

#### Scenario: ServiceLoader is invoked once per round
- **WHEN** `ExpandStage.apply(graph)` is invoked twice in a round
- **THEN** `ServiceLoader.load(...)` is invoked exactly once for each strategy interface across the round

### Requirement: GetterRead built-in

The processor SHALL ship `io.github.joke.percolate.processor.spi.builtins.GetterRead` implementing `SourceStep`. `GetterRead` SHALL inspect `ctx.elements().getAllMembers(<TypeElement of sourceType>)` for a zero-argument method whose simple name matches conventional getter naming for the given `pathTail`:
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

The processor SHALL ship `io.github.joke.percolate.processor.spi.builtins.ConstructorCall` implementing `GroupTarget`. `ConstructorCall` SHALL inspect the accessible constructors of the return type. A constructor matches iff `Set.copyOf(ctor.parameterNames())` equals `Set.copyOf(targetTails)`.

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

The processor SHALL ship `io.github.joke.percolate.processor.spi.builtins.DirectAssign` implementing `Bridge`. `DirectAssign` SHALL return `Optional.of(BridgeStep(Weights.NOOP, identityCodegen))` whenever `ctx.types().isSameType(sourceType, targetType)` returns `true`. Otherwise it SHALL return `Optional.empty()`.

The `identityCodegen` lambda SHALL render the single incoming variable from `IncomingValues.single()` unchanged.

#### Scenario: DirectAssign matches identical types
- **WHEN** `DirectAssign.bridge(<String>, <String>, ctx)` is invoked
- **THEN** the result is `Optional` containing a `BridgeStep`
- **AND** the step's `weight` is `Weights.NOOP`
- **AND** the step's `codegen` renders the single incoming variable unchanged

#### Scenario: DirectAssign rejects different types
- **WHEN** `DirectAssign.bridge(<String>, <Integer>, ctx)` is invoked
- **THEN** the result is `Optional.empty()`

#### Scenario: DirectAssign uses isSameType (not isAssignable)
- **WHEN** `DirectAssign.bridge(<List<String>>, <Collection<String>>, ctx)` is invoked
- **THEN** the result is `Optional.empty()` because `Types.isSameType` returns `false` for these types
