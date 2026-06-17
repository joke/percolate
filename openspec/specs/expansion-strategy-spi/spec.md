# Expansion Strategy SPI Spec

## Purpose

This spec defines the strategy author surface for the expansion engine: immutable result types, strategy interfaces, built-in strategies, and service registration via ServiceLoader and AutoService.

## Requirements

### Requirement: SPI package isolation

The percolate-spi Gradle module SHALL ship a package `io.github.joke.percolate.spi` containing exactly the strategy-author surface: one strategy interface (`ExpansionStrategy`) plus its optional mixin interfaces (`CombinatorialMatch`, `ContainerMatch`) and the `Container` base; the immutable result/context types (`OperationSpec`, `Port`, `Demand`, `Candidate`, `Directive`, `ChildScopeSpec`, `Nullability`); the `ResolveCtx` interface; the codegen interfaces (`Codegen`, `OperationCodegen`, `ScopeCodegen`, `IncomingValues`); the `Receiver` / `ThisReceiver` / `CallableMethods` / `MethodCandidate` types; the `LiteralCoercion` helper; and the `Containers` and `Weights` utilities. The module SHALL depend only on JDK types plus `com.palantir.javapoet` (because `CodeBlock` is part of the codegen interface surface). It SHALL NOT depend on `percolate-annotations` or `percolate-processor`.

The package SHALL NOT contain `ExpansionStep`, `Slot`, `Frontier`, `EdgeCodegen`, `GroupCodegen`, `VarNames`, `Intent`, `ElementScope`, `Bridge`, `GroupTarget`, `PathSegmentResolver`, `BridgeStep`, `GroupBuild`, `ResolvedSegment`, `ScopeTransition`, `SourceStep`, `Step`, or `ElementSeed` — these are removed or replaced by the unified `OperationSpec` / `Port` / `Demand` / `OperationCodegen` surface.

The package SHALL declare `@NullMarked` via `package-info.java`.

Built-in strategies SHALL ship from a separate Gradle module `percolate-strategies-builtin` whose only compile dependency is `percolate-spi`. They SHALL NOT import any class from `io.github.joke.percolate.processor.graph` or `io.github.joke.percolate.processor.stages.expand`. This invariant SHALL be enforced structurally by the module compile graph: `percolate-strategies-builtin`'s `build.gradle` declares no compile dependency on `percolate-processor`.

#### Scenario: spi package has @NullMarked
- **WHEN** the source of `spi/src/main/java/io/github/joke/percolate/spi/package-info.java` is inspected
- **THEN** the package declaration carries `@org.jspecify.annotations.NullMarked`

#### Scenario: retired SPI types do not exist
- **WHEN** the `io.github.joke.percolate.spi` package source tree is inspected
- **THEN** no class or enum named `ExpansionStep`, `Slot`, `Frontier`, `EdgeCodegen`, `GroupCodegen`, `VarNames`, `Intent`, `ElementScope`, `Bridge`, `GroupTarget`, `PathSegmentResolver`, `BridgeStep`, `GroupBuild`, `ResolvedSegment`, or `ScopeTransition` exists

#### Scenario: result and context types are present
- **WHEN** the `io.github.joke.percolate.spi` package is inspected
- **THEN** the result type `OperationSpec`, the port type `Port`, the demand context `Demand`, and the codegen interfaces `Codegen` / `OperationCodegen` / `ScopeCodegen` are present

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
    Stream<OperationSpec> expand(Demand demand, ResolveCtx ctx);
    default int priority() { return 0; }
}
```

This is the sole strategy-author interface for expansion. Implementations SHALL return zero or more `OperationSpec`s describing how the demanded value can be produced. An empty stream signals "this strategy does not apply"; implementations MUST NOT throw on a non-applicable demand. Implementations SHALL make a purely local decision from the `Demand` (its target type and nullness, its `@Map` directive, its declared-children set, and its candidate snapshot) and SHALL NOT receive or traverse the graph.

#### Scenario: ExpansionStrategy with no match returns empty
- **WHEN** an implementor decides nothing applies to the demand
- **THEN** `expand(...)` returns `Stream.empty()`
- **AND** does not throw

#### Scenario: ExpansionStrategy priority defaults to zero
- **WHEN** an implementor does not override `priority()`
- **THEN** `priority()` returns `0`

### Requirement: Directive type

The `percolate-spi` module SHALL define a `io.github.joke.percolate.spi.Directive` type that exposes the relevant `@Map` configuration to strategies (source path / segment access, and the author-declared `constant` and `defaultValue` attributes) WITHOUT exposing raw compiler internals as the primary surface. A strategy SHALL read its per-binding configuration from `Directive`; it SHALL NOT need to inspect an `AnnotationMirror` directly for the common cases.

`Directive` SHALL expose the directive's `constant` and `defaultValue` values to strategies. Each SHALL be reported **present** only when it is not equal to `Map.UNSET`; an empty string SHALL be reported as a present value, never as absent. `ConstantValue` reads `constant` and `NullnessCrossing` reads `defaultValue` (the `[coalesce]` crossing) through this surface.

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

- `CombinatorialMatch` — its default `expand` SHALL iterate `demand.candidates()` and delegate to an author-supplied `bridge(TypeMirror from, Demand, ResolveCtx)` method, emitting the `OperationSpec`s applicable to each `(candidateType, targetType)` pair.
- `ContainerMatch` — extends `CombinatorialMatch`; the `Container` abstract base supplies its `bridge`, emitting kind-local `iterate`/`collect`/`wrap`/`unwrap`/`map` (`mapPresence`) `OperationSpec`s over an explicit `Stream<E>` intermediate, from the author-supplied `matches` / `element` predicates and operation snippets.

Both mixins SHALL extend `ExpansionStrategy` so that an implementor remains a single `ExpansionStrategy` to the loader. Segment-directed strategies (path resolvers) SHALL implement `expand(...)` directly rather than via a mixin.

#### Scenario: a combinatorial author writes no candidate loop
- **WHEN** a strategy implements `CombinatorialMatch` and its `bridge` per-pair method
- **THEN** it inherits the candidate iteration from the default `expand`
- **AND** it is discoverable as a single `ExpansionStrategy`

### Requirement: Built-in strategies bind to ExpansionStrategy

Every built-in strategy (`ConstructorCall`, `DirectAssign`, `MethodCallBridge`, the container strategies, and the `Getter` / `Method` / `Field` path resolvers) SHALL implement `ExpansionStrategy` (directly or via a mixin) and SHALL register via `@AutoService(ExpansionStrategy.class)`. Their generated code (the codegen each emits) is unchanged; only the SPI binding and result type change.

`DirectAssign` SHALL implement `CombinatorialMatch` and emit a single same-type identity `OperationSpec` (label `assign`, weight `Weights.NOOP`, one port nullness-transparent to the demand) when a candidate's type equals the demanded type. The zero-cost identity Operation chains over the candidate Value; a round-trip that reuses a downstream Value closes a cycle the cost-extraction fold never chooses.

#### Scenario: built-ins register under the unified service type
- **WHEN** the source of any built-in strategy in `strategies-builtin/` is inspected
- **THEN** it carries `@AutoService(ExpansionStrategy.class)`
- **AND** it implements `ExpansionStrategy` directly or through a mixin

#### Scenario: DirectAssign emits a zero-cost identity for a same-type candidate
- **WHEN** `DirectAssign.bridge` sees a candidate whose type equals the demanded type
- **THEN** it emits one `OperationSpec` of weight `Weights.NOOP` with a single port carrying the demanded nullness
- **AND** for a non-matching candidate type it emits nothing

### Requirement: ResolveCtx exposes Types, Elements, callableMethods

The percolate-spi module SHALL define an interface `io.github.joke.percolate.spi.ResolveCtx` with
exactly these methods:

```java
public interface ResolveCtx {
    Types types();
    Elements elements();
    @Nullable CallableMethods callableMethods();
}
```

The interface SHALL NOT expose `mapperType()` or `currentMethod()` — they were dead in production
strategy code (only `callableMethods()` is read, by `MethodCallBridge`) and `currentMethod()` was a
footgun under whole-graph expansion (there is no single current method). The interface SHALL NOT
expose any reference to `MapperGraph`, `Edge`, `Node`, `EdgeKind`, `MapperStep`, or any other type
from `processor.graph` or `processor.stages.*`. A strategy author SHALL be able to write a complete
strategy by importing only `io.github.joke.percolate.spi.*`, `javax.lang.model.*`,
`com.palantir.javapoet.*`, and JDK types.

`callableMethods()` SHALL return the per-mapper index produced by `DiscoverCallableMethodsStage`. The
`ResolveCtx` SHALL be constructed **per mapper**, binding its `callableMethods` at construction time;
the processor SHALL NOT use a `ThreadLocal` to back any `ResolveCtx` accessor.

#### Scenario: ResolveCtx provides Types
- **WHEN** `resolveCtx.types()` is invoked
- **THEN** it returns the `javax.lang.model.util.Types` instance from the active `ProcessingEnvironment`

#### Scenario: ResolveCtx provides Elements
- **WHEN** `resolveCtx.elements()` is invoked
- **THEN** it returns the `javax.lang.model.util.Elements` instance from the active `ProcessingEnvironment`

#### Scenario: ResolveCtx provides the callable-method index
- **WHEN** `resolveCtx.callableMethods()` is invoked
- **THEN** it returns the `CallableMethods` instance produced by `DiscoverCallableMethodsStage` for the
  current mapper

#### Scenario: Retired ResolveCtx accessors do not exist
- **WHEN** the `ResolveCtx` interface is inspected
- **THEN** it declares no `mapperType()` and no `currentMethod()` method

#### Scenario: No ThreadLocal backs ResolveCtx
- **WHEN** the `ProcessorModule` source is inspected
- **THEN** no `ThreadLocal` is used to supply any `ResolveCtx` value; `callableMethods` is bound when
  the per-mapper `ResolveCtx` is constructed

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

#### Scenario: ServiceLoader is invoked once via the singleton provider
- **WHEN** the Dagger `@Singleton List<ExpansionStrategy>` provider is consulted (regardless of how many mappers or `ExpandStage` runs follow)
- **THEN** `ServiceLoader.load(ExpansionStrategy.class, ...)` is invoked exactly once and its result is shared

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

The percolate-spi module SHALL expose a constant `CONTAINER` (a positive `int`) on `io.github.joke.percolate.spi.Weights` representing the base cost of a container-shaped hop. For v1, `Weights.CONTAINER` SHALL equal `2`, heavier than `Weights.STEP` and `Weights.METHOD` (both `1`) and cheaper than `Weights.EXPENSIVE` (`3`). The container built-in strategies (`OptionalContainer`, `ListContainer`, `SetContainer`, `ArrayContainer` — extending the single `Container` base, with `CollectionContainer` as a shared collection base) SHALL use this constant as the base `weight` of the container `OperationSpec`s they emit.

#### Scenario: Weights.CONTAINER exists and is positive
- **WHEN** the source of `Weights` is inspected
- **THEN** the class declares a public static final int `CONTAINER`
- **AND** `Weights.CONTAINER > 0`

#### Scenario: Weights.CONTAINER value
- **WHEN** `Weights.CONTAINER` is read
- **THEN** the value is `2`

### Requirement: Built-in service registration smoke spec

The `percolate-strategies-builtin` module SHALL contain a Spock specification at `strategies-builtin/src/test/groovy/io/github/joke/percolate/spi/builtins/BuiltinServiceRegistrationSpec.groovy` that asserts the cross-module contract: when `percolate-strategies-builtin` is on the classpath, `ServiceLoader.load(ExpansionStrategy.class)` discovers exactly the expected built-in classes. There is a **single** strategy SPI interface (`ExpansionStrategy`); there is no separate `Bridge` / `GroupTarget` / `PathSegmentResolver` registration.

The spec SHALL assert that `ServiceLoader.load(ExpansionStrategy.class)` discovers, at minimum, the shipped built-ins: `DirectAssign`, `MethodCallBridge`, `ConstructorCall`, `WidenPrimitive`, `PrimitiveWrapperConversion`, `ConstantValue`, `NullnessCrossing`, `OptionalContainer`, `ListContainer`, `SetContainer`, `ArrayContainer`, `GetterPathResolver`, `FieldPathResolver`, and `MethodPathResolver`.

The spec SHALL additionally assert that superseded classes (the per-operation container bridges such as `OptionalUnwrap`, `SetCollect`, `ListCollect`, `ListWrap`, `IterableUnwrap`; the former `GetterRead` and `RecordPathResolver`; and the separate `DefaultValue` strategy folded into `NullnessCrossing`) are NOT discovered.

The spec SHALL be tagged `@spock.lang.Tag('unit')` and SHALL NOT invoke `ExpansionHarness` or any expansion-pipeline code. Its sole concern is verifying that the `META-INF/services/...` files generated by `auto-service` correctly register the strategy classes under `ExpansionStrategy`.

#### Scenario: ServiceLoader discovers all expected ExpansionStrategy builtins
- **WHEN** `ServiceLoader.load(ExpansionStrategy.class)` is invoked from `BuiltinServiceRegistrationSpec`
- **THEN** the returned classes contain, as a subset, `DirectAssign`, `MethodCallBridge`, `ConstructorCall`, `WidenPrimitive`, `PrimitiveWrapperConversion`, `ConstantValue`, `NullnessCrossing`, `OptionalContainer`, `ListContainer`, `SetContainer`, `ArrayContainer`, `GetterPathResolver`, `FieldPathResolver`, and `MethodPathResolver`

#### Scenario: Superseded builtins are absent
- **WHEN** the discovered `ExpansionStrategy` set is inspected
- **THEN** it contains no class named `IterableUnwrap`, `OptionalUnwrap`, `SetCollect`, `ListCollect`, `ListWrap`, `GetterRead`, `RecordPathResolver`, or `DefaultValue`

#### Scenario: Spec does not depend on the expansion pipeline
- **WHEN** the source of `BuiltinServiceRegistrationSpec` is inspected
- **THEN** no import references `io.github.joke.percolate.processor.*`
- **AND** no invocation of `ExpansionHarness`, `ExpandStage`, or `ProcessorModule` appears

### Requirement: OperationSpec result type

A strategy match SHALL produce an `OperationSpec`: a **required, human-readable `label`** describing
the production, the operation's codegen, its weight, its ordered port signature (per port: name,
declared `TypeMirror`, declared `Nullability`), the produced output type and nullness, and optionally
a child-scope declaration (container element mapping: element-in and element-out types). The `label`
SHALL be a fully-typed description the strategy composes from its match (e.g. `int→long`,
`new Address(int, String)`, `getStreet()`, `"ACTIVE"`, `map`); conversions SHALL use the glyph arrow
`→`. The spec is plain data; the driver turns it into one atomic `AddOperation` delta. Strategies
receive no graph access and stay myopic.

#### Scenario: Spec is plain data
- **WHEN** a strategy returns an `OperationSpec`
- **THEN** it contains a label, codegen, weight, ports, output typing, and optional child-scope
  declaration, and exposes no graph or engine surface

#### Scenario: Label is a typed production description
- **WHEN** `WidenPrimitive` produces an `int`-to-`long` widening spec
- **THEN** the spec's `label` is `int→long` (using the glyph arrow), not a codegen class name

#### Scenario: Codegen render contract survives
- **WHEN** an Operation's codegen renders
- **THEN** it implements `render(IncomingValues)` with incoming values keyed by port name

### Requirement: Codegen surface omits VarNames and LoopContainerCodegen

The `io.github.joke.percolate.spi` codegen surface SHALL NOT declare a `VarNames` type or a
`LoopContainerCodegen` type. `OperationCodegen.render` SHALL take only `IncomingValues`; there SHALL
be no `VarNames` parameter threaded through the render contract. The `Codegen` marker and its
`OperationCodegen` (scalar `render`) / `ScopeCodegen` (`weave`) split are retained.

#### Scenario: VarNames does not exist in the SPI
- **WHEN** the `io.github.joke.percolate.spi` package is inspected
- **THEN** no `VarNames` type exists, and no `render` signature references it

#### Scenario: LoopContainerCodegen does not exist
- **WHEN** the `io.github.joke.percolate.spi` package is inspected
- **THEN** no `LoopContainerCodegen` type exists

### Requirement: Demand decision context

Strategies SHALL receive a demand context exposing: the demanded Value's type and nullness; the
binding `Directive` in effect (carried by the work-list, see `graph-expansion`); the declared
bindings at the current target level (for assembly strategies); the **binding/slot name** the demand
serves (so a crossing strategy can name it, e.g. in a `requireNonNull` message); and a candidate
snapshot of the **in-scope source Values** — the current scope's parameter roots and the source
accessor Values already materialized in it, **not** a per-demand list hand-curated by the driver.
Directive selection of a specific source is carried by the demanded `SourceLocation`, not by
candidate filtering. The context exposes neither the graph nor any handle to traverse it.

#### Scenario: Assembly reads the goal spec from the context
- **WHEN** `ConstructorCall` matches a demand
- **THEN** it reads the declared-children name set from the demand context, not from a group

#### Scenario: Candidates are the in-scope source values
- **WHEN** a strategy inspects a demand's candidates
- **THEN** it sees the in-scope source Values of the current (method or child) scope, not a list the
  driver curated for that one demand

### Requirement: Nullness crossings and source accessors are strategies

Nullness crossings and source accessors SHALL be ordinary `ExpansionStrategy` implementations, not
engine-resident productions: the `NULLABLE → NON_NULL` crossings (`[requireNonNull]` and, with a
declared default, `[coalesce]`) and the per-segment source accessors (getter / method / field)
register through the existing `ServiceLoader`/`@AutoService` mechanism. A crossing strategy fires on a `(nullable candidate,
non-null demand)` pair, reading the binding/slot name and any `defaultValue` from the demand context;
an accessor strategy produces a source `Value` from its parent (a shallower `SourceLocation` demand).

#### Scenario: requireNonNull is a service-loadable strategy
- **WHEN** `ServiceLoader.load(ExpansionStrategy.class)` is enumerated
- **THEN** the nullness-crossing strategy is present, and it emits `[requireNonNull]` (or `[coalesce]`
  when the demand's directive declares a default) for a nullable-to-non-null pair

#### Scenario: An accessor strategy pulls its parent
- **WHEN** a `Value` at `SourceLocation("p.address.street")` is demanded
- **THEN** an accessor strategy emits the `getStreet()` Operation and demands `SourceLocation("p.address")`,
  which recurses to the parameter root — no eager whole-path materialization
