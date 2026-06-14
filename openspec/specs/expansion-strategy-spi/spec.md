# Expansion Strategy SPI Spec

## Purpose

This spec defines the strategy author surface for the expansion engine: immutable result types, strategy interfaces, built-in strategies, and service registration via ServiceLoader and AutoService.

## Requirements

### Requirement: SPI package isolation

The percolate-spi Gradle module SHALL ship a package `io.github.joke.percolate.spi` containing exactly the strategy-author surface: one strategy interface (`ExpansionStrategy`) plus its optional mixin interfaces (`CombinatorialMatch`, `ContainerMatch`), the immutable result/context types (`ExpansionStep`, `Slot`, `Frontier`, `Candidate`, `Directive`), the `Intent` and `ElementScope` enums, the `ResolveCtx` interface, the codegen interfaces (`EdgeCodegen`, `GroupCodegen`, `IncomingValues`, `VarNames`), the `Receiver` / `ThisReceiver` / `CallableMethods` / `MethodCandidate` types, and the `Containers` and `Weights` utilities. The module SHALL depend only on JDK types plus `com.palantir.javapoet` (because `CodeBlock` is part of the codegen interface surface). It SHALL NOT depend on `percolate-annotations` or `percolate-processor`.

The package SHALL NOT contain `Bridge`, `GroupTarget`, `PathSegmentResolver`, `BridgeStep`, `GroupBuild`, `ResolvedSegment`, `ScopeTransition`, `SourceStep`, `Step`, or `ElementSeed` — these are removed or replaced by the unified surface.

The package SHALL declare `@NullMarked` via `package-info.java`.

Built-in strategies SHALL ship from a separate Gradle module `percolate-strategies-builtin` whose only compile dependency is `percolate-spi`. They SHALL NOT import any class from `io.github.joke.percolate.processor.graph` or `io.github.joke.percolate.processor.stages.expand`. This invariant SHALL be enforced structurally by the module compile graph: `percolate-strategies-builtin`'s `build.gradle` declares no compile dependency on `percolate-processor`.

#### Scenario: spi package has @NullMarked
- **WHEN** the source of `spi/src/main/java/io/github/joke/percolate/spi/package-info.java` is inspected
- **THEN** the package declaration carries `@org.jspecify.annotations.NullMarked`

#### Scenario: retired SPI types do not exist
- **WHEN** the `io.github.joke.percolate.spi` package source tree is inspected
- **THEN** no class or enum named `Bridge`, `GroupTarget`, `PathSegmentResolver`, `BridgeStep`, `GroupBuild`, `ResolvedSegment`, or `ScopeTransition` exists

#### Scenario: Intent and ElementScope enums exist in spi package
- **WHEN** the `io.github.joke.percolate.spi` package is inspected
- **THEN** a public enum `Intent` exists with constants `CONVERSION`, `BOUNDARY`
- **AND** a public enum `ElementScope` exists with constants `ENTERING`, `EXITING`

#### Scenario: Built-in strategies have no forbidden imports
- **WHEN** the import statements of the built-in strategies are inspected
- **THEN** none reference any class in `io.github.joke.percolate.processor.graph.*` or `io.github.joke.percolate.processor.stages.expand.*`

#### Scenario: SPI module has no dependency on annotations or processor
- **WHEN** `spi/build.gradle` is inspected
- **THEN** it declares neither `project(':annotations')` nor `project(':processor')` on any `compile` / `implementation` / `api` configuration
- **AND** its only non-JDK `api` dependency is `com.palantir.javapoet:javapoet`

### Requirement: ExpansionStrategy interface

The `percolate-spi` module SHALL define a single Java interface `io.github.joke.percolate.spi.ExpansionStrategy` with the following shape:

```java
public interface ExpansionStrategy {
    Stream<ExpansionStep> expand(Frontier frontier, ResolveCtx ctx);
    default int priority() { return 0; }
}
```

This is the sole strategy-author interface for expansion. Implementations SHALL return zero or more `ExpansionStep`s describing how the value at the frontier can be produced. An empty stream signals "this strategy does not apply"; implementations MUST NOT throw on a non-applicable frontier. Implementations SHALL make a purely local decision from the `Frontier` (its target type, its `@Map` directive, and its candidate snapshot) and SHALL NOT receive or traverse the graph.

#### Scenario: ExpansionStrategy with no match returns empty
- **WHEN** an implementor decides nothing applies to the frontier
- **THEN** `expand(...)` returns `Stream.empty()`
- **AND** does not throw

#### Scenario: ExpansionStrategy priority defaults to zero
- **WHEN** an implementor does not override `priority()`
- **THEN** `priority()` returns `0`

### Requirement: Directive type

The `percolate-spi` module SHALL define a `io.github.joke.percolate.spi.Directive` type that exposes the relevant `@Map` configuration to strategies (source path / segment access, and the author-declared `constant` and `defaultValue` attributes) WITHOUT exposing raw compiler internals as the primary surface. A strategy SHALL read its per-binding configuration from `Directive`; it SHALL NOT need to inspect an `AnnotationMirror` directly for the common cases.

`Directive` SHALL expose the directive's `constant` and `defaultValue` values to strategies. Each SHALL be reported **present** only when it is not equal to `Map.UNSET`; an empty string SHALL be reported as a present value, never as absent. `ConstantValue` reads `constant` and `DefaultValue` reads `defaultValue` through this surface.

#### Scenario: Directive hides compiler internals
- **WHEN** the `Directive` type is inspected
- **THEN** its public accessors expose `@Map` configuration through `Directive`'s own surface
- **AND** a strategy reading the source path or a declared attribute does not require importing `javax.lang.model` annotation-mirror types

#### Scenario: Directive exposes a present constant
- **WHEN** a strategy reads the `constant` of a `Directive` built from `@Map(target = "status", constant = "ACTIVE")`
- **THEN** it observes the value present as `"ACTIVE"`

#### Scenario: Directive reports an unspecified attribute as absent
- **WHEN** a strategy reads the `defaultValue` of a `Directive` built from `@Map(target = "x", source = "in.x")`
- **THEN** it observes the value absent (equal to `Map.UNSET`)

#### Scenario: Directive reports an empty-string attribute as present
- **WHEN** a strategy reads the `constant` of a `Directive` built from `@Map(target = "note", constant = "")`
- **THEN** it observes the value present as the empty string, not absent

### Requirement: Strategy author mixins

The `percolate-spi` module SHALL provide optional mixin interfaces with `default expand(...)` implementations to absorb common boilerplate without reintroducing kind-ordering at the loader:

- `CombinatorialMatch` — its default `expand` SHALL iterate `frontier.candidates()` and delegate to an author-supplied per-pair method, emitting one `ExpansionStep` per applicable `(candidateType, targetType)` pair.
- `ContainerMatch` — its default `expand` SHALL emit the iterate/collect/unwrap/wrap `BOUNDARY` steps carrying the appropriate `ElementScope`, from author-supplied `matches` / `element` snippets.

Both mixins SHALL extend `ExpansionStrategy` so that an implementor remains a single `ExpansionStrategy` to the loader. Segment-directed strategies (path resolvers) SHALL implement `expand(...)` directly rather than via a mixin.

#### Scenario: a combinatorial author writes no candidate loop
- **WHEN** a strategy implements `CombinatorialMatch` and its per-pair method
- **THEN** it inherits the candidate iteration from the default `expand`
- **AND** it is discoverable as a single `ExpansionStrategy`

### Requirement: Built-in strategies bind to ExpansionStrategy

Every built-in strategy (`ConstructorCall`, `DirectAssign`, `MethodCallBridge`, the container strategies, and the `Getter` / `Method` / `Field` path resolvers) SHALL implement `ExpansionStrategy` (directly or via a mixin) and SHALL register via `@AutoService(ExpansionStrategy.class)`. Their generated code (the codegen each emits) is unchanged; only the SPI binding and result type change.

`DirectAssign` SHALL emit a single `CONVERSION` step (a cost-zero identity assignment). Because a `CONVERSION` step folds in place and identical-type reuse may collapse the synthesized node, the driver SHALL treat a same-type identity assignment as a zero-cost realised edge that survives folding rather than being silently dropped as a duplicate.

#### Scenario: built-ins register under the unified service type
- **WHEN** the source of any built-in strategy in `strategies-builtin/` is inspected
- **THEN** it carries `@AutoService(ExpansionStrategy.class)`
- **AND** it implements `ExpansionStrategy` directly or through a mixin

#### Scenario: DirectAssign identity assignment survives folding
- **WHEN** `DirectAssign` emits its `CONVERSION` step for a frontier whose source value already has the target type in view
- **THEN** the resulting realised edge is retained as a zero-cost identity assignment
- **AND** the assignment is not dropped as a duplicate-type no-op

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

### Requirement: Strategy registration via ServiceLoader and AutoService

Strategies SHALL be discovered uniformly via a single `ServiceLoader<ExpansionStrategy>`. Built-in strategies — shipped from the `percolate-strategies-builtin` module — SHALL declare their service registration via Google `@AutoService(ExpansionStrategy.class)` so that the `auto-service`-generated `META-INF/services/io.github.joke.percolate.spi.ExpansionStrategy` file ships inside that module's JAR. User-supplied strategies in third-party JARs SHALL register the same way.

The processor's Dagger module SHALL provide one strategy list as `@Singleton List<ExpansionStrategy>` by:
1. Calling `ServiceLoader.load(ExpansionStrategy.class, classLoader)` exactly once.
2. Materialising the iterator into a list.
3. Sorting that list by `priority()` then lexicographically by `getClass().getName()` (FQN ascending).
4. Wrapping in an unmodifiable list before publishing.

The single list SHALL be tried as one round each expansion pass; there SHALL be no per-kind ordering (no "match strategies before assembly strategies"). The processor module SHALL declare `percolate-strategies-builtin` as a `runtimeOnly` Gradle dependency.

#### Scenario: built-ins are annotated AutoService(ExpansionStrategy)
- **WHEN** the source of any built-in strategy is inspected
- **THEN** the class carries `@AutoService(ExpansionStrategy.class)`

#### Scenario: Provided strategy list is one sorted list
- **WHEN** Dagger provides the `List<ExpansionStrategy>` for a round
- **THEN** the list contains every registered strategy regardless of kind
- **AND** the list is sorted by `priority()` then ascending `getClass().getName()`

#### Scenario: User strategy registered via META-INF/services is discovered
- **WHEN** a JAR on the annotation-processor classpath contains `META-INF/services/io.github.joke.percolate.spi.ExpansionStrategy` referencing a user class
- **THEN** the user class is included in the `List<ExpansionStrategy>` provided by Dagger

#### Scenario: ServiceLoader is invoked once per round
- **WHEN** `ExpandStage.apply(graph)` is invoked twice in a round
- **THEN** `ServiceLoader.load(ExpansionStrategy.class, ...)` is invoked exactly once across the round

#### Scenario: Processor declares strategies-builtin as runtimeOnly
- **WHEN** `processor/build.gradle` is inspected
- **THEN** it declares `runtimeOnly project(':strategies-builtin')`
- **AND** no `compile` / `implementation` / `api` configuration mentions `:strategies-builtin`

### Requirement: Weights.METHOD constant

The percolate-spi module SHALL extend the `Weights` constants in `io.github.joke.percolate.spi.Weights` with a constant `METHOD`. The constant SHALL be a positive `int` representing the base cost of a method-call hop.

For v1, `Weights.METHOD` SHALL equal `Weights.STEP`. The numerical value is documented as the base cost; method-call edges may carry weights greater than `Weights.METHOD` when JLS-specificity distance adds to the cost (see `MethodCallBridge` requirement).

#### Scenario: Weights.METHOD exists and is positive
- **WHEN** the source of `Weights` is inspected
- **THEN** the class declares a public static final int `METHOD`
- **AND** `Weights.METHOD > 0`

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

The spec SHALL assert that `ServiceLoader.load(ExpansionStrategy.class)` discovers, at minimum, the shipped built-ins: `DirectAssign`, `MethodCallBridge`, `ConstructorCall`, `WidenPrimitive`, `PrimitiveWrapperConversion`, `ConstantValue`, `DefaultValue`, `OptionalContainer`, `ListContainer`, `SetContainer`, `ArrayContainer`, `GetterPathResolver`, `FieldPathResolver`, and `MethodPathResolver`.

The spec SHALL additionally assert that the superseded per-operation container classes (`OptionalWrap`, `OptionalUnwrap`, `OptionalCollect`, `ListWrap`, `ListCollect`, `SetWrap`, `SetCollect`, `ArrayCollect`, `IterableUnwrap`, `SetMap`, `ListMap`, `OptionalMap`) are NOT discovered — they were folded into the one-class-per-container-type strategies.

The spec SHALL be tagged `@spock.lang.Tag('unit')` and SHALL NOT invoke `ExpansionHarness` or any expansion-pipeline code. Its sole concern is verifying that the `META-INF/services/...` files generated by `auto-service` correctly register the strategy classes under `ExpansionStrategy`.

#### Scenario: ServiceLoader discovers all expected ExpansionStrategy builtins
- **WHEN** `ServiceLoader.load(ExpansionStrategy.class)` is invoked from `BuiltinServiceRegistrationSpec`
- **THEN** the returned classes contain, as a subset, `DirectAssign`, `MethodCallBridge`, `ConstructorCall`, `WidenPrimitive`, `PrimitiveWrapperConversion`, `ConstantValue`, `DefaultValue`, `OptionalContainer`, `ListContainer`, `SetContainer`, `ArrayContainer`, `GetterPathResolver`, `FieldPathResolver`, and `MethodPathResolver`

#### Scenario: Superseded per-operation container classes are absent
- **WHEN** the discovered `ExpansionStrategy` set is inspected
- **THEN** it contains no class named `OptionalWrap`, `OptionalUnwrap`, `OptionalCollect`, `ListWrap`, `ListCollect`, `SetWrap`, `SetCollect`, `ArrayCollect`, `IterableUnwrap`, `SetMap`, `ListMap`, or `OptionalMap`

#### Scenario: Spec does not depend on the expansion pipeline
- **WHEN** the source of `BuiltinServiceRegistrationSpec` is inspected
- **THEN** no import references `io.github.joke.percolate.processor.*`
- **AND** no invocation of `ExpansionHarness`, `ExpandStage`, or `ProcessorModule` appears

### Requirement: OperationSpec result type

A strategy match SHALL produce an `OperationSpec`: the operation's codegen, its weight, its ordered
port signature (per port: name, declared `TypeMirror`, declared `Nullability`), the produced output
type and nullness, and optionally a child-scope declaration (container element mapping: element-in
and element-out types). The spec is plain data; the driver turns it into one atomic `AddOperation`
delta. Strategies receive no graph access and stay myopic.

#### Scenario: Spec is plain data
- **WHEN** a strategy returns an `OperationSpec`
- **THEN** it contains codegen, weight, ports, output typing, and optional child-scope declaration,
  and exposes no graph or engine surface

#### Scenario: Codegen render contract survives
- **WHEN** an Operation's codegen renders
- **THEN** it implements `render(VarNames, IncomingValues)` with incoming values keyed by port name

### Requirement: Demand decision context

Strategies SHALL receive a demand context exposing: the demanded Value's type and nullness, the
binding `Directive` in effect (carried by the work-list, see `graph-expansion`), the declared
bindings at the current target level (for assembly strategies), and the candidate snapshot of
in-scope Values. The context replaces the former frontier/`ExpansionGroup` surfaces.

#### Scenario: Assembly reads the goal spec from the context
- **WHEN** `ConstructorCall` matches a demand
- **THEN** it reads the declared-children name set from the demand context, not from a group
