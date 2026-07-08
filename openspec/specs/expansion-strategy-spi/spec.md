# Expansion Strategy SPI Spec

## Purpose

This spec defines the strategy author surface for the expansion engine: immutable result types, strategy interfaces, built-in strategies, and service registration via ServiceLoader and AutoService.

## Requirements

### Requirement: SPI package isolation

The percolate-spi Gradle module SHALL ship a package `io.github.joke.percolate.spi` containing exactly the strategy-author surface: two author interfaces (`ExpansionStrategy` and the source-facing `SourceProjection`) plus the `Container` base and the `Conversion` / `Accessor` archetype bases; the immutable result/context types (`OperationSpec`, `Port`, `PortType`, `Demand`, `Directive`, `ChildScopeSpec`, `Nullability`); the `ResolveCtx` interface; the codegen interfaces (`Codegen`, `OperationCodegen`, `ScopeCodegen`, `IncomingValues`); the `Receiver` / `ThisReceiver` / `CallableMethods` / `MethodCandidate` types; the `LiteralCoercion` helper; and the `TypeProbe`, `Containers`, and `Weights` utilities. The module SHALL depend only on JDK types plus `com.palantir.javapoet` (because `CodeBlock` is part of the codegen interface surface). It SHALL NOT depend on `percolate-annotations` or `percolate-processor`.

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
    default Stream<OperationSpec> expand(ProduceDemand demand, ResolveCtx ctx) { return Stream.empty(); }
    default Stream<OperationSpec> descend(DescendDemand demand, ResolveCtx ctx) { return Stream.empty(); }
    default int priority() { return 0; }
}
```

This is the sole strategy-author interface for expansion. A strategy SHALL override **exactly one** of `expand`
(a producer answering "what produces this demanded target?") or `descend` (an accessor answering "what does
reading this segment off this parent yield?"). The driver dispatches a produce demand to `expand` and a descend
demand to `descend`, and is the **sole invoker** of both — no helper invokes a strategy. Implementations SHALL
return zero or more `OperationSpec`s; an empty stream signals "this strategy does not apply", and implementations
MUST NOT throw on a non-applicable demand. Implementations SHALL make a purely local decision from their demand
(its typed fields, the `@Map` directive, and — for `expand` — the declared-children set), SHALL NOT receive or
traverse the graph, and SHALL NOT read a candidate snapshot.

#### Scenario: ExpansionStrategy with no match returns empty
- **WHEN** an implementor decides nothing applies to the demand
- **THEN** the overridden method returns `Stream.empty()` and does not throw

#### Scenario: A strategy overrides exactly one of expand or descend
- **WHEN** a producer strategy (e.g. `ConstructorCall`) and an accessor strategy (e.g. `GetterPathResolver`) are
  inspected
- **THEN** the producer overrides `expand(ProduceDemand, ...)` and the accessor overrides
  `descend(DescendDemand, ...)`, each leaving the other defaulted to empty

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

The `percolate-spi` module SHALL provide the abstract `Container` base for declaring a container in one class, plus archetype convenience bases for the recurring target-driven shapes — `Conversion` (a unary `target ← input` conversion) and `Accessor` (a directive-pinned source-path-segment accessor) — all on the single uniform `ExpansionStrategy.expand` surface. The `Container` base SHALL implement **both** `ExpansionStrategy` (its kind-local target-driven ops) and `SourceProjection` (projecting its kind to its own intermediate), so a container author still writes a single class. There SHALL be **no candidate-iterating mixin**: the former `CombinatorialMatch` (whose default `expand` iterated `demand.candidates()` and delegated to a per-`(from,to)` method) is removed, because the engine, not the strategy, sources inputs. A container declares its type predicate, element extractor, kind-local operation snippets, and its functor-lift `map` over its own intermediate; the base emits target-driven `OperationSpec`s (the lift carrying a type-variable input port). The `Conversion` base wires each author-declared input into a one-port `OperationSpec`; the `Accessor` base reads the pinned segment and parent type and wires the one-port accessor `OperationSpec`.

#### Scenario: No candidate-iterating mixin exists
- **WHEN** the `io.github.joke.percolate.spi` package is inspected
- **THEN** there is no `CombinatorialMatch` (or any mixin whose default `expand` iterates `demand.candidates()`)

#### Scenario: A container is authored on the uniform surface
- **WHEN** a developer declares a container
- **THEN** it extends `Container`, supplying `matches`/`element`, its kind-local snippets, and its functor-lift `map`, and it reads no candidates

#### Scenario: A conversion or accessor is authored on its archetype base
- **WHEN** a developer declares a unary conversion or a source accessor
- **THEN** it extends `Conversion` (supplying the input-typed conversion steps) or `Accessor` (supplying the member match for the pinned segment), and the base wires the one-port `OperationSpec` — the author reads no candidates

### Requirement: Built-in strategies bind to ExpansionStrategy

Every built-in strategy (`ConstructorCall`, `DirectAssign`, `MethodCallBridge`, the container strategies, and the `Getter` / `Method` / `Field` path resolvers) SHALL implement `ExpansionStrategy` (directly or via a base — `Container` / `Conversion` / `Accessor`) and SHALL register via `@AutoService(ExpansionStrategy.class)`. Their generated code (the codegen each emits) is unchanged; only the SPI binding and result type change.

`DirectAssign` SHALL be target-driven: for any demand it emits a single same-type identity `OperationSpec` (label `assign`, weight `Weights.NOOP`) whose lone port is **reuse-only** and nullness-transparent to the demand — the driver binds an in-scope source of the demanded type and nullness, or the operation does not apply (it is never minted). It reads no candidate. The zero-cost identity Operation flows the bound source value through; a round-trip that reuses a downstream Value closes a cycle the cost-extraction fold never chooses.

#### Scenario: built-ins register under the unified service type
- **WHEN** the source of any built-in strategy in `strategies-builtin/` is inspected
- **THEN** it carries `@AutoService(ExpansionStrategy.class)`
- **AND** it implements `ExpansionStrategy` directly or through a base

#### Scenario: DirectAssign emits a zero-cost reuse-only identity for the demand
- **WHEN** `DirectAssign.expand` processes a demand
- **THEN** it emits one `OperationSpec` of weight `Weights.NOOP` with a single reuse-only port carrying the demanded type and nullness
- **AND** the driver binds an in-scope same-type source to that port, or the operation does not apply

### Requirement: ResolveCtx is the narrow type-query seam

The percolate-spi module SHALL define `io.github.joke.percolate.spi.ResolveCtx` as a single narrow,
mockable **type-query seam**: beyond `callableMethods()`, it exposes the purpose-built type and
member-reflection questions the engine and strategies actually ask — realised as ~35 methods, not the
originally-measured ~13, once type-algebra (`isSameType`/`isAssignable`/`erasure`/`isPrimitive`/`isArray`/
`isDeclared`/`typeArgument`/`typeArgumentCount`/`arrayComponent`/`declaredType`/`arrayType`/`boxed`/
`unboxed`/`simpleName`/`qualifiedName`/…), higher-level container/type predicates (`isList`/`isSet`/
`isOptional`/`isStream`/`isCollection`/`isIterable`/`isEnum`/`isReferenceType`/`isType`/`typeElementNamed`),
and member reflection (`membersOf`/`isField`/`isMethod`/`isConstructor`/`isPrivate`/`isStatic`/
`superclassOf`) are all counted. It SHALL NOT expose `typeSpace()`, `mapperType()`, or `currentMethod()`,
nor any owned type-value model, nor any reference to `MapperGraph`, `Edge`, `Node`, `EdgeKind`,
`MapperStep`, or any other type from `processor.graph` or `processor.stages.*`.

`ResolveCtx` SHALL still declare `types()`/`elements()` — this is a **deliberate delegation seam**, not an
oversight: the production real-javac implementation (`CompileResolveCtx`) answers every seam question by
delegating through them, so a single `Types`/`Elements` pair supplies the whole surface for free. No
**test** constructs a `ResolveCtx` over a `Types`/`Elements` pair any more — `ResolveCtxBuilder` is deleted
and the `strategies-builtin` unit specs mock the seam directly (change `cutover-strategies-to-mock-seam`).
Engine and strategy *production* code SHALL NOT call `types()`/`elements()` directly — every type question
routes through the seam methods above instead — and the architecture suite confines the accessors' own
`javax.lang.model.util` imports to the `ResolveCtx` interface plus the enumerated boundary packages (see
`module-boundaries`). Removing `types()`/`elements()` from the interface entirely (so the production impl
overrides each seam method directly) is a separate later phase, out of scope here.

A method that returns a type SHALL return another opaque token (see "TypeMirror is an opaque pass-through
token"). `callableMethods()` SHALL return the per-mapper index produced by the discovery stage. The
`ResolveCtx` SHALL be constructed **per mapper**, binding its `callableMethods` at construction time; the
processor SHALL NOT use a `ThreadLocal` to back any accessor.

The production implementation (`CompileResolveCtx`) SHALL be the **only** engine-side type code that
touches real javac to answer a seam question — it delegates each seam method to `Types`/`Elements`. A
strategy author SHALL be able to write a complete strategy by importing only
`io.github.joke.percolate.spi.*`, `com.palantir.javapoet.*`, and JDK types — **no `javax.lang.model` import
is needed to ask a type question** (though a strategy MAY still hold a `TypeMirror`/`Element` value as an
opaque token without importing `Types`/`Elements`).

#### Scenario: The seam answers a type question without exposing Types or Elements to callers
- **WHEN** a strategy calls `ctx.isSameType(a, b)` or `ctx.typeArgumentCount(t)`
- **THEN** the seam returns the answer (a `boolean`, an `int`, or another opaque token) without the caller
  needing to call `types()`/`elements()` itself

#### Scenario: types()/elements() remain only as the production-impl delegation seam
- **WHEN** the `ResolveCtx` interface is inspected
- **THEN** it still declares `types()` and `elements()`, and it declares no `typeSpace()`, `mapperType()`,
  or `currentMethod()` method, and no method returning a `processor.graph` or `processor.stages.*` type
- **AND** no engine or strategy production class other than `CompileResolveCtx` calls `types()`/`elements()`
  directly to answer a type question
- **AND** no test constructs a `ResolveCtx` over a `Types`/`Elements` pair (`ResolveCtxBuilder` does not exist)

#### Scenario: A type-returning question yields an opaque token
- **WHEN** a strategy calls `ctx.typeArgument(t, 0)` on a declared `List<String>` token
- **THEN** it receives a `TypeMirror` token it passes back to the seam or to codegen emission, without interrogating it directly

#### Scenario: The seam provides the callable-method index
- **WHEN** `resolveCtx.callableMethods()` is invoked
- **THEN** it returns the `CallableMethods` instance produced by the discovery stage for the current mapper

#### Scenario: Only the production impl touches javac to answer a seam question
- **WHEN** the source of `CompileResolveCtx` is inspected
- **THEN** each seam method delegates to `Types`/`Elements`
- **AND** no other engine or strategy class imports `javax.lang.model.util` to answer a type question

#### Scenario: No ThreadLocal backs ResolveCtx
- **WHEN** the `ProcessorModule` source is inspected
- **THEN** no `ThreadLocal` is used to supply any `ResolveCtx` value; `callableMethods` is bound when
  the per-mapper `ResolveCtx` is constructed

### Requirement: TypeMirror is an opaque pass-through token

Engine and strategy code SHALL treat every `javax.lang.model.type.TypeMirror` (and `Element`) it handles as
an **opaque pass-through token**: it MAY hold one, store it in an `OperationSpec`/`Port`, and hand it back to
the `ResolveCtx` seam or to codegen emission, but SHALL NOT invoke a `TypeMirror`/`Element` method on it
(`getKind`, `getTypeArguments`, a cast to `DeclaredType`, …) and SHALL NOT call `Types`/`Elements` directly.
Consequently a unit test's mocked `ResolveCtx` never stubs a method **on** a `TypeMirror`; the mirror is a
never-stubbed opaque token, exactly as `ValidateNoDuplicateTargetsStageSpec` treats the `javax.lang.model`
values it passes through.

This is the default, not an absolute: design (`type-query-seam`) documents a small number of deliberate,
narrow exceptions that read `.getKind()`/cast a `TypeMirror` or `Element` directly with zero
`Types`/`Elements` involvement — `LiteralCoercion` (a static utility with no `ResolveCtx` in reach), the
debug-only `Labels`/`DotRenderer` (cosmetic label formatting, never the basis of a behavioural decision),
and two engine `Stage`s that structurally cannot reach a `ResolveCtx` (`ValidateSourceParametersStage`,
`ValidateConstantDefaultLegalityStage`, since `ResolveCtx` is constructed per-mapper inside `ExpandStage.run`
and unavailable to arbitrary stages). These carry no real-compiler burden and are unit-tested the same
mock-free way `ValidateNoDuplicateTargetsStageSpec` already does.

#### Scenario: Engine and strategy code ask the seam, not the mirror
- **WHEN** a strategy or engine stage needs a type fact about a `TypeMirror` it holds
- **THEN** it calls a `ResolveCtx` seam method, and never calls a method on the `TypeMirror` or casts it to a `javax.lang.model` subtype
- **UNLESS** it is one of the documented single-hop exceptions (`LiteralCoercion`, `Labels`/`DotRenderer`, `ValidateSourceParametersStage`, `ValidateConstantDefaultLegalityStage`)

#### Scenario: A mocked ResolveCtx passes mirrors as never-stubbed tokens
- **WHEN** a unit test drives a strategy or stage with a mocked `ResolveCtx`
- **THEN** the `TypeMirror` values handed in are plain tokens with no stubbed interactions, and the test stubs only seam methods

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
declared `TypeMirror`, declared `Nullability`), the produced output type and nullness, optionally
a child-scope declaration (container element mapping: element-in and element-out types), and
**optionally a neutral call-target identity** — the `ExecutableElement` a method-call production
invokes. The `label` SHALL be a fully-typed description the strategy composes from its match (e.g.
`int→long`, `new Address(int, String)`, `getStreet()`, `"ACTIVE"`, `map`); conversions SHALL use the
glyph arrow `→`. The call-target field SHALL be **additive and optional**: existing factory entry
points that build a production without one SHALL remain source-compatible, and a production that is
not a method call SHALL carry no call target. The call target is a **neutral structural fact** ("this
op calls this method"), recorded by a method-call strategy from identity it already holds — never a
"self-call" marker, which would require a strategy to know the method-under-generation (it cannot:
the demand context exposes no current method). Interpreting the fact (self-call vs delegation) is the
driver's concern, where the `MethodScope` is known. The spec is plain data; the driver turns it into
one atomic `AddOperation` delta. Strategies receive no graph access and stay myopic.

#### Scenario: Spec is plain data
- **WHEN** a strategy returns an `OperationSpec`
- **THEN** it contains a label, codegen, weight, ports, output typing, optional child-scope
  declaration, and an optional call-target identity, and exposes no graph or engine surface

#### Scenario: Label is a typed production description
- **WHEN** `WidenPrimitive` produces an `int`-to-`long` widening spec
- **THEN** the spec's `label` is `int→long` (using the glyph arrow), not a codegen class name

#### Scenario: A method-call production records its call target
- **WHEN** the method-call strategy produces the demanded type by calling a single-argument method
- **THEN** the resulting `OperationSpec` carries that method's `ExecutableElement` as its call target, so the driver can apply the binding-time self-call rule without inspecting the `label`

#### Scenario: A non-method production carries no call target
- **WHEN** a conversion, accessor, constant, constructor, wrap, iterate, collect, or element-map spec is produced
- **THEN** the resulting `OperationSpec` carries no call target (the field is absent)

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

The demand context SHALL come in two shapes, matching the two strategy questions:

- a **produce demand** (`ProduceDemand`, handed to `expand`) exposing: the demanded Value's type and nullness;
  the binding `Directive` in effect (its `defaultValue` / `constant`, carried by the work-list, see
  `graph-expansion`); the declared bindings at the current target level (for assembly); the binding/slot name the
  demand serves; and a nullness oracle.
- a **descend demand** (`DescendDemand`, handed to `descend`) exposing: the concrete **parent type** (and
  nullness) being descended and the single source-path **segment** to resolve, plus a nullness oracle. It SHALL
  NOT pun the parent as a "target type": the produced output type is the strategy's answer, not a field of the
  demand.

Neither shape SHALL expose a candidate snapshot of in-scope source Values (the engine sources inputs and grounds
type-variable ports by matching), nor the graph, nor any handle to traverse it.

#### Scenario: Assembly reads the goal spec from the produce demand
- **WHEN** `ConstructorCall` matches a produce demand
- **THEN** it reads the declared-children name set from the demand, not from a group

#### Scenario: A descend demand carries the parent type and segment, not a target pun
- **WHEN** `GetterPathResolver` is handed a descend demand for segment `name` on parent `Person`
- **THEN** it reads `Person` as the parent type and `name` as the segment, and its emitted output type is the
  accessor's return type — the demand carries no `targetType()` standing in for the parent

#### Scenario: Neither demand shape exposes candidates
- **WHEN** a strategy inspects its demand
- **THEN** there is no `candidates()` accessor; it cannot enumerate in-scope source Values

### Requirement: Nullness crossings and source accessors are strategies

Nullness crossings and source accessors SHALL be ordinary `ExpansionStrategy` implementations, not
engine-resident productions, registered through the existing `ServiceLoader` / `@AutoService` mechanism. A
crossing strategy is a **producer** (overriding `expand`): keyed on the demanded target it over-emits the
crossings that can produce it — a partial `[requireNonNull]` for a `NON_NULL` reference-scalar demand and (with a
declared `defaultValue`) total `[coalesce]` forms — each over a **reuse-only** input port the driver binds to the
in-scope nullable scalar / `Optional<T>` source (or the operation does not apply); it reads the slot name and any
`defaultValue` from the produce demand and reads no candidate. A source accessor is an **accessor** (overriding
`descend`): given a concrete parent type and one segment it emits one unary accessor `Operation` whose output
type it discovers; the driver lands it forward (parent already materialised) and walks to the next segment. An
accessor SHALL NOT re-demand a shallower `SourceLocation` (no backward parent re-demand).

#### Scenario: requireNonNull is a service-loadable producer strategy
- **WHEN** `ServiceLoader.load(ExpansionStrategy.class)` is enumerated
- **THEN** the nullness-crossing strategy is present, and for a `NON_NULL` reference-scalar produce demand it
  emits `[requireNonNull]` (and `[coalesce]` when the directive declares a default) over reuse-only ports

#### Scenario: An accessor descends from an already-landed parent
- **WHEN** the driver descends the `street` segment with the `p.address` `Value` already landed
- **THEN** an accessor strategy emits the `getStreet()` Operation producing `SourceLocation[p, address, street]`
  from the landed `address` parent, with no re-demand of `SourceLocation[p, address]` and no eager whole-path
  materialization

### Requirement: TypeProbe and Containers forward to the ResolveCtx seam

The `percolate-spi` module SHALL ship two public utilities forwarding onto the `ResolveCtx` type-query seam
(kept as source-compatible surfaces; new call sites SHOULD prefer the `ResolveCtx` methods directly):
`io.github.joke.percolate.spi.TypeProbe` (`asTypeElement`, `isType`, `isEnum`, `simpleName` — each taking a
`ResolveCtx` and delegating to it) and `io.github.joke.percolate.spi.Containers` (`isOptional`/`isStream`/
`isList`/`isSet`/`isCollection`/`isIterable`, each delegating to the corresponding `ResolveCtx` method).
Neither SHALL hold its own FQN/erasure-match logic or any direct `javax.lang.model` type/element/mirror
interrogation — every higher-level question is answered by the seam. Four purely-syntactic `Containers`
accessors — `isArray(TypeMirror)`, `isReferenceType(TypeMirror)`, `typeArgument(TypeMirror, int)`, and
`arrayComponentType(TypeMirror)` — never needed a compiler-backed `ResolveCtx` and keep their original
no-`ResolveCtx` shape, operating on the `TypeMirror`'s own structural shape (`getKind()`, casts) directly.

#### Scenario: Containers delegates directly to the seam
- **WHEN** `Containers.isOptional`/`isStream`/`isList`/`isSet`/`isCollection`/`isIterable` resolve a type
- **THEN** each calls the identically-named `ResolveCtx` method directly (not `TypeProbe.isType`); no FQN/erasure-match logic is re-implemented in `Containers`

#### Scenario: TypeProbe delegates directly to the seam
- **WHEN** `TypeProbe.asTypeElement`/`isType`/`isEnum`/`simpleName` are called
- **THEN** each calls the identically-named `ResolveCtx` method with the same arguments

#### Scenario: The four ctx-free Containers accessors need no ResolveCtx
- **WHEN** `Containers.isArray`, `isReferenceType`, `typeArgument`, or `arrayComponentType` is called
- **THEN** it operates on the `TypeMirror`'s own `getKind()`/structural shape with no `ResolveCtx` parameter

### Requirement: Two strategy questions, both candidate-free and myopic

Every `ExpansionStrategy` SHALL answer one of **two** questions and return `OperationSpec`s: a **produce**
question ("what produces this demanded target?", via `expand`) or a **descend** question ("what does reading this
segment off this parent yield?", via `descend`). A producer is distinguished only by what it reads from its
produce demand — the `targetType` (conversions, containers) or the `declaredChildren` (assembly). An accessor
reads only the parent type and segment of its descend demand. No strategy of either kind reads a candidate
snapshot to decide what to emit; the engine sources every input port. The element-mapping case that needs a
source element type declares a **type-variable port** (see `polymorphic-conversion`), grounded by the engine, not
enumerated by the strategy. These two questions are the only expansion surfaces; there SHALL be no third, and
both keep strategy decisions purely local (no graph, no candidates).

#### Scenario: Producers and accessors both read no candidates
- **WHEN** any conversion/assembly/container producer or any accessor decides what to emit
- **THEN** it reads only its demand (a produce demand's target/nullness/directive/declared-children, or a descend
  demand's parent type and segment) — never an in-scope candidate list

#### Scenario: Accessors answer the descend question, producers the produce question
- **WHEN** the strategy surface is inspected
- **THEN** conversions, containers, assembly, and nullness crossings answer `expand`; the getter / method / field
  accessors answer `descend`; no strategy answers both

### Requirement: Source-facing SourceProjection SPI

The `percolate-spi` module SHALL ship a second author interface, `io.github.joke.percolate.spi.SourceProjection`, parallel to `ExpansionStrategy` and discovered the same way (`ServiceLoader`). Where an `ExpansionStrategy` answers "what produces this **target**?", a `SourceProjection` answers "what other types may this in-scope **source** be viewed as?" — its only effect is to contribute extra grounding-match candidates (design D8). A projection SHALL be consulted **only** to widen grounding-by-match's match set; it SHALL NOT receive or traverse the graph, and it SHALL return an empty stream for a source it does not recognise. The engine SHALL consume the projected types **structurally** (unifying them like any other source type) and SHALL name no container kind.

#### Scenario: A collection source is projected to its element stream
- **WHEN** a `List<X>` source is in scope and a stream container's `SourceProjection` is registered
- **THEN** the projection contributes `Stream<X>`, so a type-variable `Stream<A>` element-map port grounds `A := X` and the concrete `Stream<X>` is produced target-driven by the container's own `iterate`

#### Scenario: An unrecognised source projects nothing
- **WHEN** a `SourceProjection` is handed a source it does not recognise
- **THEN** it returns an empty stream and contributes no grounding candidate (no bridge is invented)

### Requirement: Port declares an explicit sourcing mode

Each `Port` of an `OperationSpec` SHALL declare an explicit **sourcing mode** stating how the engine binds the feeding `Value`, so the driver dispatches on a declared fact and never reconstructs a port's intent from a name-match or a boolean. The mode SHALL be one of a closed set:

- `SUBTARGET` — a structural sub-target: the engine mints a fresh `FREE` demand at the child location (the parent target path extended by the port's name) and re-demands it. Assembly strategies (e.g. `ConstructorCall`) stamp their parameter ports `SUBTARGET`.
- `REUSE` — the feeding `Value` must already exist in scope: the engine binds a matching in-scope source, or the Operation does not apply (it is **never** minted). This is exactly the **reuse-only** port the built-in identity, nullness-crossing, and container-`unwrap` requirements describe — the bound mode for a consuming Operation whose input is structurally larger than its output.
- `REUSE_OR_MINT` — the default: the engine binds a matching in-scope source, or mints a fresh `FREE` intermediate of the port's type and nullness at the output location and re-demands it (a multi-hop conversion). An ordinary concrete conversion or accessor input port uses this mode.

A strategy SHALL choose a port's mode as a purely local decision; the mode carries **no** graph or candidate access, and the engine — not the strategy — owns the child location and every graph mutation. The mode set SHALL be **extensible**: a future binding mode (e.g. binding a port by name to an ambient captured source) SHALL be addable beside these three without changing the existing three or the strategies that declare them. `REUSE_OR_MINT` SHALL be the default of a plainly-constructed concrete port, so existing concrete-port construction is source-unaffected.

#### Scenario: An assembly port is a sub-target
- **WHEN** `ConstructorCall` emits a constructor parameter port
- **THEN** the port's sourcing mode is `SUBTARGET`, and the engine mints a child-target demand at the parent path extended by the port name

#### Scenario: A reuse-only port is REUSE
- **WHEN** `DirectAssign`, a nullness crossing, or a container `unwrap` emits its consuming input port
- **THEN** the port's sourcing mode is `REUSE`, and the engine binds an in-scope source or the Operation does not apply (never minted)

#### Scenario: A default conversion port is REUSE_OR_MINT
- **WHEN** a unary conversion (e.g. `int→long`) emits its value port without specifying a mode
- **THEN** the port's sourcing mode is `REUSE_OR_MINT`, and the engine binds an in-scope source or mints a fresh intermediate at the output location

#### Scenario: The mode set is closed but extensible
- **WHEN** the `Port` sourcing modes are enumerated
- **THEN** exactly `SUBTARGET`, `REUSE`, and `REUSE_OR_MINT` are defined, and a new mode could be added beside them without altering these three or their declaring strategies
