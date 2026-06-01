## ADDED Requirements

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

A step with `intent == CONVERSION` SHALL have exactly one input and SHALL describe an in-place re-typing of the value at the frontier's position (same flow identity). A step with `intent == BOUNDARY` SHALL describe crossing into a new flow identity and its `inputs` SHALL be the slots of the subgroup it opens (`0` for a terminal producer, `1` for a getter or unary call, `N` for an assembly). `scope` SHALL be present only on container boundary steps.

#### Scenario: CONVERSION step has exactly one input
- **WHEN** an `ExpansionStep` is constructed with `intent == CONVERSION`
- **THEN** `inputs().size()` equals `1`

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

## MODIFIED Requirements

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

## REMOVED Requirements

### Requirement: GroupTarget interface
**Reason**: Collapsed into the single `ExpansionStrategy` interface. N-ary assembly is now a `BOUNDARY` `ExpansionStep` with `N` slots.
**Migration**: Implement `ExpansionStrategy`; from `expand(frontier, ctx)` read `frontier.targetType()` and emit a `BOUNDARY` step whose `inputs` are the assembly slots.

### Requirement: Bridge interface
**Reason**: Collapsed into the single `ExpansionStrategy` interface.
**Migration**: Implement `ExpansionStrategy` (optionally via the `CombinatorialMatch` mixin); emit `ExpansionStep`s with the appropriate `Intent`.

### Requirement: PathSegmentResolver interface
**Reason**: Collapsed into the single `ExpansionStrategy` interface; the `segment` is read from `frontier.directive()` rather than passed as a parameter.
**Migration**: Implement `ExpansionStrategy` directly; resolve the segment from the directive and emit a `BOUNDARY` step.

### Requirement: BridgeStep result type
**Reason**: Replaced by `ExpansionStep`.
**Migration**: Return `ExpansionStep` with `inputs`, `output`, `codegen`, `intent`, optional `scope`, `weight`.

### Requirement: GroupBuild result type
**Reason**: Replaced by `ExpansionStep` (a `BOUNDARY` step's `inputs` are the build slots).
**Migration**: Emit a `BOUNDARY` `ExpansionStep` instead of a `GroupBuild`.

### Requirement: ResolvedSegment result type
**Reason**: Replaced by `ExpansionStep` (a `BOUNDARY` step describing the segment access).
**Migration**: Emit a `BOUNDARY` `ExpansionStep` from the unified `expand`.

### Requirement: ResolvedSegment carries producedFrom
**Reason**: `ResolvedSegment` is removed; the consumer contract is carried by `Slot` metadata on the `ExpansionStep`'s inputs.
**Migration**: Record the `producedFrom` contract on the relevant `Slot`.

### Requirement: PathSegmentResolver registration via ServiceLoader and AutoService
**Reason**: Folded into the single `ExpansionStrategy` registration.
**Migration**: Register path resolvers via `@AutoService(ExpansionStrategy.class)`.

### Requirement: ScopeTransition enum
**Reason**: `PRESERVING` no longer exists as a step attribute — in-place conversion is expressed by `Intent.CONVERSION`, and element-scope crossing is expressed by the optional `ElementScope` on a `BOUNDARY` step.
**Migration**: Replace `PRESERVING` steps with `Intent.CONVERSION` steps; replace `ENTERING`/`EXITING` `ScopeTransition` with `ExpansionStep.scope` carrying `ElementScope.ENTERING`/`EXITING`.
