## ADDED Requirements

### Requirement: ResolveCtx exposes processor options through one generic keyed lookup

`ResolveCtx` SHALL expose exactly one processor-option accessor, `Optional<String> option(String key)`, returning the raw value declared for `key` in `processingEnv.getOptions()` or empty when the option is unset. It SHALL NOT declare any option accessor named after, or typed for, a particular feature.

`ProcessorOptions` in the `processor` module remains the parsing and validation owner; the seam is only how a strategy reaches a value. A strategy SHALL interpret its own option's raw value — the engine continues to make no code-generation or selection choice from any option.

A strategy or codegen that needs an option value SHALL read it through this lookup. Adding a feature that consumes a processor option SHALL NOT widen the seam.

#### Scenario: A strategy reads its own option by key
- **WHEN** a strategy calls `ctx.option("percolate.construction.preference")` and the option is set to `builder`
- **THEN** the seam returns `Optional.of("builder")`

#### Scenario: An unset option yields empty
- **WHEN** a strategy calls `ctx.option(key)` for a key absent from the compiler options
- **THEN** the seam returns `Optional.empty()`

#### Scenario: The seam declares no feature-named option accessor
- **WHEN** the `ResolveCtx` interface is inspected
- **THEN** `option(String)` is its only processor-option method
- **AND** it declares no `configuredTimeZone()`, no `constructionPreference()`, and no other per-feature option accessor

#### Scenario: A new option-consuming feature does not widen the seam
- **WHEN** a feature that reads a processor option is added
- **THEN** it reads through `option(String)` and adds no method to `ResolveCtx`

## MODIFIED Requirements

### Requirement: ResolveCtx is the narrow type-query seam

The percolate-spi module SHALL define `io.github.joke.percolate.spi.ResolveCtx` as a single narrow,
mockable **type-query seam**: beyond `callableMethods()` and the generic `option(String)` lookup, it exposes the
purpose-built type and member-reflection questions the engine and strategies actually ask — realised as ~35
methods, not the originally-measured ~13, once type-algebra (`isSameType`/`isAssignable`/`erasure`/`isPrimitive`/
`isArray`/`isDeclared`/`typeArgument`/`typeArgumentCount`/`arrayComponent`/`declaredType`/`arrayType`/`boxed`/
`unboxed`/`simpleName`/`qualifiedName`/…), higher-level container/type predicates (`isList`/`isSet`/
`isOptional`/`isStream`/`isCollection`/`isIterable`/`isEnum`/`isReferenceType`/`isType`/`typeElementNamed`),
and member reflection (`membersOf`/`isField`/`isMethod`/`isConstructor`/`isPrivate`/`isStatic`/
`superclassOf`) are all counted. It SHALL NOT expose `typeSpace()`, `mapperType()`, or `currentMethod()`,
nor any owned type-value model, nor any reference to `MapperGraph`, `Edge`, `Node`, `EdgeKind`,
`MapperStep`, or any other type from `processor.graph` or `processor.stages.*`. It SHALL NOT expose a
feature-named processor-option accessor; `configuredTimeZone()` is removed in favour of `option(String)`.

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
`io.github.joke.percolate.spi.*`, `io.github.joke.percolate.javapoet.*`, and JDK types — **no `javax.lang.model` import
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

#### Scenario: The seam carries no feature-named option accessor
- **WHEN** the `ResolveCtx` interface is inspected
- **THEN** it declares `option(String)` and no `configuredTimeZone()` method

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

### Requirement: Source-aware render context for BodyCodegen

A `BodyCodegen` SHALL render against a context that is a **superset of `IncomingValues`** — carrying the same
port-keyed incoming expressions — and that additionally exposes, per port, the **grounded concrete `TypeMirror`**
bound to that port, a `ResolveCtx`, and the target `SourceVersion`. This lets a
conversion whose emitted text depends on the source's shape (e.g. enumerating a source enum's constants) read the
grounded source type and choose its rendering, while remaining myopic: the context exposes only the resolved types
of the operation's own ports, never a graph or candidate-Value snapshot. `OperationCodegen.render` SHALL continue to
receive only `IncomingValues`.

The context SHALL NOT expose a feature-named option accessor. `switchStyle()` is removed; a codegen that needs a
processor option SHALL read it through `resolveCtx().option(key)` and interpret the raw value itself.

#### Scenario: BodyCodegen context surfaces the grounded source type
- **WHEN** a `BodyCodegen` renders for a production whose input port grounded to `MyStatus`
- **THEN** its context returns the grounded `TypeMirror` `MyStatus` for that port, plus a `ResolveCtx` and the
  target `SourceVersion`

#### Scenario: The context carries no feature-named option accessor
- **WHEN** the `BodyCodegen` render context is inspected
- **THEN** it declares no `switchStyle()` method
- **AND** a codegen needing the switch style reads `resolveCtx().option("percolate.switch.style")`

#### Scenario: The context exposes no graph or candidate snapshot
- **WHEN** the `BodyCodegen` render context is inspected
- **THEN** it exposes only the operation's own port expressions and grounded port types, with no in-scope source
  Value snapshot and no engine/graph surface

#### Scenario: OperationCodegen still receives only IncomingValues
- **WHEN** an `OperationCodegen` production renders
- **THEN** its sole render argument remains an `IncomingValues`, unchanged by this capability

### Requirement: Built-in strategies bind to ExpansionStrategy

Every built-in strategy (`ConstructorCall`, `DirectAssign`, `MethodCallBridge`, the four builder strategies `FluentBuilder` / `ProtobufBuilder` / `WithBuilder` / `SideLocatedBuilder`, the container strategies, and the `Getter` / `Method` / `Field` path resolvers) SHALL implement `ExpansionStrategy` (directly or via a base — `Container` / `Conversion` / `Accessor`) and SHALL register via `@AutoService(ExpansionStrategy.class)`. Their generated code (the codegen each emits) is unchanged; only the SPI binding and result type change.

`DirectAssign` SHALL be target-driven: for any demand it emits a single same-type identity `OperationSpec` (label `assign`, weight `Weights.NOOP`) whose lone port is **reuse-only** and nullness-transparent to the demand — the driver binds an in-scope source of the demanded type and nullness, or the operation does not apply (it is never minted). It reads no candidate. The zero-cost identity Operation flows the bound source value through; a round-trip that reuses a downstream Value closes a cycle the cost-extraction fold never chooses.

#### Scenario: built-ins register under the unified service type
- **WHEN** the source of any built-in strategy in `strategies-builtin/` is inspected
- **THEN** it carries `@AutoService(ExpansionStrategy.class)`
- **AND** it implements `ExpansionStrategy` directly or through a base

#### Scenario: The builder strategies bind like every other built-in
- **WHEN** the sources of `FluentBuilder`, `ProtobufBuilder`, `WithBuilder`, and `SideLocatedBuilder` are inspected
- **THEN** each implements `ExpansionStrategy` directly and carries `@AutoService(ExpansionStrategy.class)`

#### Scenario: DirectAssign emits a zero-cost reuse-only identity for the demand
- **WHEN** `DirectAssign.expand` processes a demand
- **THEN** it emits one `OperationSpec` of weight `Weights.NOOP` with a single reuse-only port carrying the demanded type and nullness
- **AND** the driver binds an in-scope same-type source to that port, or the operation does not apply

### Requirement: Built-in service registration smoke spec

The `percolate-strategies-builtin` module SHALL contain a Spock specification at `strategies-builtin/src/test/groovy/io/github/joke/percolate/spi/builtins/BuiltinServiceRegistrationSpec.groovy` that asserts the cross-module contract: when `percolate-strategies-builtin` is on the classpath, `ServiceLoader.load(ExpansionStrategy.class)` discovers exactly the expected built-in classes. There is a **single** strategy SPI interface (`ExpansionStrategy`); there is no separate `Bridge` / `GroupTarget` / `PathSegmentResolver` registration.

The spec SHALL assert that `ServiceLoader.load(ExpansionStrategy.class)` discovers, at minimum, the shipped built-ins: `DirectAssign`, `MethodCallBridge`, `ConstructorCall`, `FluentBuilder`, `ProtobufBuilder`, `WithBuilder`, `SideLocatedBuilder`, `WidenPrimitive`, `PrimitiveWrapperConversion`, `ConstantValue`, `NullnessCrossing`, `OptionalContainer`, `ListContainer`, `SetContainer`, `ArrayContainer`, `GetterPathResolver`, `FieldPathResolver`, and `MethodPathResolver`.

The spec SHALL additionally assert that superseded classes (the per-operation container bridges such as `OptionalUnwrap`, `SetCollect`, `ListCollect`, `ListWrap`, `IterableUnwrap`; the former `GetterRead` and `RecordPathResolver`; and the separate `DefaultValue` strategy folded into `NullnessCrossing`) are NOT discovered.

The spec SHALL be tagged `@spock.lang.Tag('unit')` and SHALL NOT invoke `ExpansionHarness` or any expansion-pipeline code. Its sole concern is verifying that the `META-INF/services/...` files generated by `auto-service` correctly register the strategy classes under `ExpansionStrategy`.

#### Scenario: ServiceLoader discovers all expected ExpansionStrategy builtins
- **WHEN** `ServiceLoader.load(ExpansionStrategy.class)` is invoked from `BuiltinServiceRegistrationSpec`
- **THEN** the returned classes contain, as a subset, `DirectAssign`, `MethodCallBridge`, `ConstructorCall`, `FluentBuilder`, `ProtobufBuilder`, `WithBuilder`, `SideLocatedBuilder`, `WidenPrimitive`, `PrimitiveWrapperConversion`, `ConstantValue`, `NullnessCrossing`, `OptionalContainer`, `ListContainer`, `SetContainer`, `ArrayContainer`, `GetterPathResolver`, `FieldPathResolver`, and `MethodPathResolver`

#### Scenario: Superseded builtins are absent
- **WHEN** the discovered `ExpansionStrategy` set is inspected
- **THEN** it contains no class named `IterableUnwrap`, `OptionalUnwrap`, `SetCollect`, `ListCollect`, `ListWrap`, `GetterRead`, `RecordPathResolver`, or `DefaultValue`

#### Scenario: Spec does not depend on the expansion pipeline
- **WHEN** the source of `BuiltinServiceRegistrationSpec` is inspected
- **THEN** no import references `io.github.joke.percolate.processor.*`
- **AND** no invocation of `ExpansionHarness`, `ExpandStage`, or `ProcessorModule` appears
