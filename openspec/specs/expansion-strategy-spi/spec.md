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

### Requirement: ExpansionStep result type

The `percolate-spi` module SHALL define an immutable value type `io.github.joke.percolate.spi.ExpansionStep` carrying: an ordered `List<Slot> inputs` of length `0..N`; a `TypeMirror output`; a `Codegen codegen` that assembles the inputs into the output; an `Intent intent`; an `Optional<ElementScope> scope`; and an `int weight`.

A step with `intent == CONVERSION` SHALL have exactly one input and SHALL describe an in-place re-typing of the value at the frontier's position (same flow identity). The single input names a **type the driver reuses-or-synthesizes** in the current group's view: the input value need NOT already exist as an in-view candidate — when no node of the input type is present, the driver synthesizes one and resolves it as a frontier within the same view (see `graph-expansion`). A strategy author MAY therefore emit a `CONVERSION` step whose input names an intermediate type (e.g. a `long` between `int` and `Long`) that the engine will produce. A step with `intent == BOUNDARY` SHALL describe crossing into a new flow identity and its `inputs` SHALL be the slots of the subgroup it opens (`0` for a terminal producer, `1` for a getter or unary call, `N` for an assembly). `scope` SHALL be present only on container boundary steps.

#### Scenario: CONVERSION step has exactly one input
- **WHEN** an `ExpansionStep` is constructed with `intent == CONVERSION`
- **THEN** `inputs().size()` equals `1`

#### Scenario: CONVERSION input need not pre-exist in view
- **WHEN** a strategy emits a `CONVERSION` step whose single input type has no matching node in the frontier's group view
- **THEN** the step is still valid (the driver synthesizes the input node rather than discarding the step)

#### Scenario: scope is absent on non-container steps
- **WHEN** a non-container `ExpansionStep` is constructed
- **THEN** `scope()` returns `Optional.empty()`

#### Scenario: boundary step slot count is unconstrained
- **WHEN** an `ExpansionStep` is constructed with `intent == BOUNDARY` and `N` inputs
- **THEN** `inputs().size()` equals `N` for any `N >= 0`

### Requirement: Intent enum

The `percolate-spi` module SHALL define a Java enum `io.github.joke.percolate.spi.Intent` with exactly two constants: `CONVERSION` and `BOUNDARY`. The driver SHALL branch solely on this enum to decide whether a step folds into the current subgroup (`CONVERSION`) or opens a new subgroup rooted at the frontier (`BOUNDARY`).

#### Scenario: Intent has exactly two constants
- **WHEN** the source of `Intent` is inspected
- **THEN** the enum declares exactly `CONVERSION` and `BOUNDARY`
- **AND** no other constants exist

### Requirement: ElementScope enum

The `percolate-spi` module SHALL define a Java enum `io.github.joke.percolate.spi.ElementScope` with exactly two constants: `ENTERING` and `EXITING`. It SHALL appear only as the payload of `ExpansionStep.scope` and only on container boundary steps; `ENTERING` denotes the output lives at element scope, `EXITING` denotes the input lives at element scope.

#### Scenario: ElementScope has exactly two constants
- **WHEN** the source of `ElementScope` is inspected
- **THEN** the enum declares exactly `ENTERING` and `EXITING`
- **AND** there is no `PRESERVING` constant

### Requirement: Frontier decision context

The `percolate-spi` module SHALL define a Java interface `io.github.joke.percolate.spi.Frontier` exposing exactly three accessors: `TypeMirror targetType()`, `Optional<Directive> directive()`, and `List<Candidate> candidates()`. `Frontier` SHALL NOT expose the graph, any `ExpansionGroup`, or any handle from which a strategy could traverse the graph. `candidates()` SHALL be a flat snapshot of the in-scope source values drawn from the current group's view.

#### Scenario: Frontier exposes no graph handle
- **WHEN** the `Frontier` interface is inspected
- **THEN** no accessor returns a type in `io.github.joke.percolate.processor.graph.*` or `io.github.joke.percolate.processor.stages.expand.*`

#### Scenario: candidates are scoped to the current group's view
- **WHEN** the driver builds a `Frontier` for a frontier node in group `G`
- **THEN** `candidates()` contains exactly the typed, non-frontier vertices of `G`'s view as `Candidate` snapshots
- **AND** contains no vertex outside `G`'s view

### Requirement: Directive type

The `percolate-spi` module SHALL define a `io.github.joke.percolate.spi.Directive` type that exposes the relevant `@Map` configuration to strategies (source path / segment access, and any author-declared attributes such as conversion patterns or default values) WITHOUT exposing raw compiler internals as the primary surface. A strategy SHALL read its per-binding configuration from `Directive`; it SHALL NOT need to inspect an `AnnotationMirror` directly for the common cases.

#### Scenario: Directive hides compiler internals
- **WHEN** the `Directive` type is inspected
- **THEN** its public accessors expose `@Map` configuration through `Directive`'s own surface
- **AND** a strategy reading the source path or a declared attribute does not require importing `javax.lang.model` annotation-mirror types

### Requirement: Candidate snapshot type

The `percolate-spi` module SHALL define an immutable `io.github.joke.percolate.spi.Candidate` carrying the candidate's `TypeMirror type`. A strategy reads only the type; the driver binds a step's input `Slot` back to a graph node by type. `Candidate` SHALL expose no handle from which a strategy could traverse the graph.

#### Scenario: Candidate exposes type but not traversal
- **WHEN** the `Candidate` type is inspected
- **THEN** it exposes the candidate's `TypeMirror`
- **AND** it exposes no method returning graph edges or neighbouring nodes

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
