# Container Expansion Spec

## Purpose

This spec defines the container-shaped Bridge strategies (Optional/List/Set Wrap/Unwrap/Map), the Containers helper they share, and the codegen-seam deferral policy for container-map strategies.

## Requirements

### Requirement: Containers helper

The percolate-spi module SHALL ship a public utility class
`io.github.joke.percolate.spi.Containers` exposing static
type-shape predicates and accessors for use by container `Bridge`
strategies — both built-in and external. The class SHALL be `final`
with a private constructor (no instances). It SHALL reside in the
`percolate-spi` Gradle module so that third-party strategy authors
can use it without depending on the processor module.

The class SHALL expose at least these methods:

- `boolean isOptional(TypeMirror t, ResolveCtx ctx)` — true iff `t`
  is a declared type whose erasure is `java.util.Optional`.
- `boolean isList(TypeMirror t, ResolveCtx ctx)` — true iff `t` is a
  declared type whose erasure is `java.util.List`.
- `boolean isSet(TypeMirror t, ResolveCtx ctx)` — true iff `t` is a
  declared type whose erasure is `java.util.Set`.
- `boolean isCollection(TypeMirror t, ResolveCtx ctx)` — true iff
  `t` is a declared type whose erasure is `java.util.Collection`.
- `boolean isIterable(TypeMirror t, ResolveCtx ctx)` — true iff `t`
  is a declared type whose erasure is `java.lang.Iterable`, or a
  subtype thereof.
- `boolean isArray(TypeMirror t)` — true iff `t` is an array type.
- `TypeMirror typeArgument(TypeMirror declaredType, int index)` —
  returns the `index`-th type argument of a parameterised declared
  type. Behaviour for raw types or out-of-range indices is
  unspecified by the helper (callers SHALL `is*`-check first).
- `TypeMirror arrayComponentType(TypeMirror arrayType)` — returns
  the component type of an array.

The helper SHALL NOT throw on a non-applicable input to the
`is*` predicates — these SHALL return `false` and not throw. The
accessor methods (`typeArgument`, `arrayComponentType`) MAY throw if
the caller violates the precondition (e.g., calling
`arrayComponentType` on a non-array type).

#### Scenario: isOptional matches parameterised and raw Optional
- **WHEN** `Containers.isOptional(<Optional<String>>, ctx)` is invoked
- **THEN** returns `true`
- **WHEN** `Containers.isOptional(<Optional>, ctx)` (raw) is invoked
- **THEN** returns `true`
- **WHEN** `Containers.isOptional(<String>, ctx)` is invoked
- **THEN** returns `false`

#### Scenario: isList matches List subtypes
- **WHEN** `Containers.isList(<List<String>>, ctx)` is invoked
- **THEN** returns `true`
- **WHEN** `Containers.isList(<ArrayList<String>>, ctx)` is invoked
- **THEN** returns `false` (only the exact erasure matches; ArrayList is a List but not List itself)

#### Scenario: isIterable matches the broad subtype family
- **WHEN** `Containers.isIterable(<List<String>>, ctx)` is invoked
- **THEN** returns `true`
- **WHEN** `Containers.isIterable(<Collection<String>>, ctx)` is invoked
- **THEN** returns `true`
- **WHEN** `Containers.isIterable(<String[]>, ctx)` is invoked
- **THEN** returns `false` (arrays do not implement `Iterable`)

#### Scenario: typeArgument extracts the element type
- **WHEN** `Containers.typeArgument(<List<Dog>>, 0)` is invoked
- **THEN** returns the `TypeMirror` for `Dog`

#### Scenario: arrayComponentType extracts the element type
- **WHEN** `Containers.arrayComponentType(<Dog[]>)` is invoked
- **THEN** returns the `TypeMirror` for `Dog`

### Requirement: OptionalWrap built-in

The `percolate-strategies-builtin` module SHALL ship `io.github.joke.percolate.spi.builtins.OptionalWrap` implementing `Bridge` and annotated `@AutoService(Bridge.class)`.

`OptionalWrap.bridge(from, to, ctx)` SHALL emit a single `BridgeStep` when `to` is an `Optional<X>` declared type AND `from` is the element type `X`. The emitted step SHALL have:

- `inputType` = the type argument `X` of `to`.
- `outputType` = `to` itself (the `Optional<X>` type).
- `weight` = `Weights.CONTAINER`.
- `codegen` = a lambda rendering `Optional.ofNullable(<input>)` where `<input>` is the single incoming variable.
- `scopeTransition` = `ScopeTransition.PRESERVING`.
- `elementRole` = `"element"` (default; not consulted for PRESERVING).

When `to` is not an `Optional`, `OptionalWrap.bridge` SHALL return `Stream.empty()`.

`OptionalWrap` covers the regular-scope `T → Optional<T>` direct case. It coexists with `OptionalCollect` (scope-exit); the engine picks via weight tie-break (`OptionalWrap.weight == OptionalCollect.weight == Weights.CONTAINER`).

#### Scenario: OptionalWrap emits for Optional target
- **WHEN** `OptionalWrap.bridge(<Dog>, <Optional<Dog>>, ctx)` is invoked
- **THEN** the stream contains one `BridgeStep`
- **AND** the step's `inputType` is `<Dog>` and `outputType` is `<Optional<Dog>>`
- **AND** the step's `weight` equals `Weights.CONTAINER`
- **AND** the step's `scopeTransition` equals `ScopeTransition.PRESERVING`
- **AND** the step's `codegen` renders `Optional.ofNullable(<inputVar>)`

#### Scenario: OptionalWrap declines non-Optional target
- **WHEN** `OptionalWrap.bridge(<Dog>, <Pet>, ctx)` is invoked
- **THEN** the result is `Stream.empty()`

### Requirement: OptionalUnwrap built-in

The `percolate-strategies-builtin` module SHALL ship `io.github.joke.percolate.spi.builtins.OptionalUnwrap` implementing `Bridge` and annotated `@AutoService(Bridge.class)`.

`OptionalUnwrap.bridge(from, to, ctx)` SHALL emit a single `BridgeStep` when `from` is an `Optional<X>` declared type AND `to` is the element type `X`. The emitted step SHALL have:

- `inputType` = `from` itself (the `Optional<X>` type).
- `outputType` = the type argument `X` of `from`.
- `weight` = `Weights.CONTAINER`.
- `codegen` = a pass-through lambda rendering `<input>`. The actual `.orElse(null)` / `.stream().findFirst()` materialisation is deferred to a future codegen capability that selects iteration style.
- `scopeTransition` = `ScopeTransition.ENTERING`.
- `elementRole` = `"element"`.

When `from` is not an `Optional`, or when `to` is not the type argument of `from`, `OptionalUnwrap.bridge` SHALL return `Stream.empty()`.

#### Scenario: OptionalUnwrap emits for Optional source
- **WHEN** `OptionalUnwrap.bridge(<Optional<Dog>>, <Dog>, ctx)` is invoked
- **THEN** the stream contains one `BridgeStep`
- **AND** the step's `inputType` is `<Optional<Dog>>` and `outputType` is `<Dog>`
- **AND** the step's `weight` equals `Weights.CONTAINER`
- **AND** the step's `scopeTransition` equals `ScopeTransition.ENTERING`
- **AND** the step's `elementRole` equals `"element"`
- **AND** the step's `codegen` renders `<inputVar>` (pass-through)

#### Scenario: OptionalUnwrap declines non-Optional source
- **WHEN** `OptionalUnwrap.bridge(<Dog>, <Pet>, ctx)` is invoked
- **THEN** the result is `Stream.empty()`

### Requirement: IterableUnwrap built-in

The `percolate-strategies-builtin` module SHALL ship `io.github.joke.percolate.spi.builtins.IterableUnwrap` implementing `Bridge` and annotated `@AutoService(Bridge.class)`.

`IterableUnwrap.bridge(from, to, ctx)` SHALL emit a single `BridgeStep` when:

- `from` is either an `Iterable<X>` declared type (or a subtype: `Collection`, `List`, `Set`, `Queue`, `Deque`, etc.) or an array type `X[]`, AND
- `to` is the element type `X`.

`from` MUST NOT be an `Optional` (Optional handling is owned by `OptionalUnwrap`).

The emitted step SHALL have `inputType = from`, `outputType = X`, `weight = Weights.CONTAINER`, pass-through codegen, `scopeTransition = ScopeTransition.ENTERING`, `elementRole = "element"`.

#### Scenario: IterableUnwrap emits for List source
- **WHEN** `IterableUnwrap.bridge(<List<Dog>>, <Dog>, ctx)` is invoked
- **THEN** the stream contains one `BridgeStep` whose `inputType` is `<List<Dog>>` and `outputType` is `<Dog>`
- **AND** the step's `scopeTransition` equals `ScopeTransition.ENTERING`

#### Scenario: IterableUnwrap emits for Set source
- **WHEN** `IterableUnwrap.bridge(<Set<Dog>>, <Dog>, ctx)` is invoked
- **THEN** the stream contains one `BridgeStep` whose `inputType` is `<Set<Dog>>` and `outputType` is `<Dog>`

#### Scenario: IterableUnwrap emits for array source
- **WHEN** `IterableUnwrap.bridge(<Dog[]>, <Dog>, ctx)` is invoked
- **THEN** the stream contains one `BridgeStep` whose `inputType` is `<Dog[]>` and `outputType` is `<Dog>`

#### Scenario: IterableUnwrap declines Optional source
- **WHEN** `IterableUnwrap.bridge(<Optional<Dog>>, <Dog>, ctx)` is invoked
- **THEN** the result is `Stream.empty()`

#### Scenario: IterableUnwrap declines non-iterable source
- **WHEN** `IterableUnwrap.bridge(<Dog>, <Pet>, ctx)` is invoked
- **THEN** the result is `Stream.empty()`

### Requirement: ListWrap built-in

The `percolate-strategies-builtin` module SHALL ship `io.github.joke.percolate.spi.builtins.ListWrap` implementing `Bridge` and annotated `@AutoService(Bridge.class)`.

`ListWrap.bridge(from, to, ctx)` SHALL emit a single `BridgeStep` when `to` is a `List<X>` declared type AND `from` is the element type `X`. Step fields: `inputType = X`, `outputType = to`, `weight = Weights.CONTAINER`, codegen renders `List.of(<input>)`, `scopeTransition = ScopeTransition.PRESERVING`, `elementRole = "element"` (default).

This covers the regular-scope `T → List<T>` singleton case. Coexists with `ListCollect` (scope-exit); engine picks via weight tie-break.

#### Scenario: ListWrap emits for List target with singleton element
- **WHEN** `ListWrap.bridge(<Dog>, <List<Dog>>, ctx)` is invoked
- **THEN** the stream contains one `BridgeStep` whose `inputType` is `<Dog>`, `outputType` is `<List<Dog>>`, `scopeTransition` is `PRESERVING`, and `codegen` renders `List.of(<inputVar>)`

#### Scenario: ListWrap declines non-List target
- **WHEN** `ListWrap.bridge(<Dog>, <Pet>, ctx)` is invoked
- **THEN** the result is `Stream.empty()`

### Requirement: SetWrap built-in

The `percolate-strategies-builtin` module SHALL ship `io.github.joke.percolate.spi.builtins.SetWrap` implementing `Bridge` and annotated `@AutoService(Bridge.class)`.

`SetWrap.bridge(from, to, ctx)` SHALL emit a single `BridgeStep` when `to` is a `Set<X>` declared type AND `from` is the element type `X`. Step fields: `inputType = X`, `outputType = to`, `weight = Weights.CONTAINER`, codegen renders `Set.of(<input>)`, `scopeTransition = ScopeTransition.PRESERVING`, `elementRole = "element"` (default).

This covers the regular-scope `T → Set<T>` singleton case. Coexists with `SetCollect` (scope-exit); engine picks via weight tie-break.

#### Scenario: SetWrap emits for Set target with singleton element
- **WHEN** `SetWrap.bridge(<Dog>, <Set<Dog>>, ctx)` is invoked
- **THEN** the stream contains one `BridgeStep` whose `inputType` is `<Dog>`, `outputType` is `<Set<Dog>>`, `scopeTransition` is `PRESERVING`, and `codegen` renders `Set.of(<inputVar>)`

#### Scenario: SetWrap declines non-Set target
- **WHEN** `SetWrap.bridge(<Dog>, <Pet>, ctx)` is invoked
- **THEN** the result is `Stream.empty()`

### Requirement: OptionalCollect built-in

The `percolate-strategies-builtin` module SHALL ship `io.github.joke.percolate.spi.builtins.OptionalCollect` implementing `Bridge` and annotated `@AutoService(Bridge.class)`.

`OptionalCollect.bridge(from, to, ctx)` SHALL emit a single `BridgeStep` when `to` is an `Optional<X>` declared type AND `from` is the element type `X`. Step fields: `inputType = X`, `outputType = Optional<X>`, `weight = Weights.CONTAINER`, pass-through codegen, `scopeTransition = ScopeTransition.EXITING`, `elementRole = "element"`.

#### Scenario: OptionalCollect emits for Optional target with element source
- **WHEN** `OptionalCollect.bridge(<Dog>, <Optional<Dog>>, ctx)` is invoked
- **THEN** the stream contains one `BridgeStep` whose `scopeTransition` is `EXITING` and `elementRole` is `"element"`

#### Scenario: OptionalCollect declines non-Optional target
- **WHEN** `OptionalCollect.bridge(<Dog>, <Set<Dog>>, ctx)` is invoked
- **THEN** the result is `Stream.empty()`

### Requirement: SetCollect built-in

The `percolate-strategies-builtin` module SHALL ship `io.github.joke.percolate.spi.builtins.SetCollect` implementing `Bridge` and annotated `@AutoService(Bridge.class)`.

`SetCollect.bridge(from, to, ctx)` SHALL emit a single `BridgeStep` when `to` is a `Set<X>` declared type AND `from` is the element type `X`. Step fields: `inputType = X`, `outputType = Set<X>`, `weight = Weights.CONTAINER`, pass-through codegen, `scopeTransition = ScopeTransition.EXITING`, `elementRole = "element"`.

#### Scenario: SetCollect emits for Set target with element source
- **WHEN** `SetCollect.bridge(<Dog>, <Set<Dog>>, ctx)` is invoked
- **THEN** the stream contains one `BridgeStep` whose `scopeTransition` is `EXITING`

#### Scenario: SetCollect declines non-Set target
- **WHEN** `SetCollect.bridge(<Dog>, <List<Dog>>, ctx)` is invoked
- **THEN** the result is `Stream.empty()`

### Requirement: ListCollect built-in

The `percolate-strategies-builtin` module SHALL ship `io.github.joke.percolate.spi.builtins.ListCollect` implementing `Bridge` and annotated `@AutoService(Bridge.class)`.

`ListCollect.bridge(from, to, ctx)` SHALL emit a single `BridgeStep` when `to` is a `List<X>` declared type AND `from` is `X`. Step fields: `inputType = X`, `outputType = List<X>`, `weight = Weights.CONTAINER`, pass-through codegen, `scopeTransition = ScopeTransition.EXITING`, `elementRole = "element"`.

#### Scenario: ListCollect emits for List target with element source
- **WHEN** `ListCollect.bridge(<Dog>, <List<Dog>>, ctx)` is invoked
- **THEN** the stream contains one `BridgeStep` whose `scopeTransition` is `EXITING`

#### Scenario: ListCollect declines non-List target
- **WHEN** `ListCollect.bridge(<Dog>, <Set<Dog>>, ctx)` is invoked
- **THEN** the result is `Stream.empty()`

### Requirement: ArrayCollect built-in

The `percolate-strategies-builtin` module SHALL ship `io.github.joke.percolate.spi.builtins.ArrayCollect` implementing `Bridge` and annotated `@AutoService(Bridge.class)`.

`ArrayCollect.bridge(from, to, ctx)` SHALL emit a single `BridgeStep` when `to` is an array type `X[]` AND `from` is `X`. Step fields: `inputType = X`, `outputType = X[]`, `weight = Weights.CONTAINER`, pass-through codegen, `scopeTransition = ScopeTransition.EXITING`, `elementRole = "element"`.

#### Scenario: ArrayCollect emits for array target with element source
- **WHEN** `ArrayCollect.bridge(<Dog>, <Dog[]>, ctx)` is invoked
- **THEN** the stream contains one `BridgeStep` whose `scopeTransition` is `EXITING`

#### Scenario: ArrayCollect declines non-array target
- **WHEN** `ArrayCollect.bridge(<Dog>, <List<Dog>>, ctx)` is invoked
- **THEN** the result is `Stream.empty()`

### Requirement: Container strategies bind to ExpansionStrategy via ContainerMatch

Container strategies (the `Optional` / `List` / `Set` / `Array` iterate/collect/unwrap/wrap families) SHALL be `ExpansionStrategy` implementations, written via the `ContainerMatch` mixin from author-supplied `matches` / `element` snippets. Each iterate/collect/unwrap/wrap operation SHALL be emitted as an `ExpansionStep` with `intent == BOUNDARY` carrying the appropriate `ElementScope` in `scope`:

- An operation whose output lives at element scope (unwrap / iterate into elements) carries `ElementScope.ENTERING`.
- An operation whose input lives at element scope (collect elements into a container) carries `ElementScope.EXITING`.
- A single-element wrap (e.g. `List.of(x)`, `Optional.of(x)`) is a `BOUNDARY` step with no `scope`.

The generated code (the stream/loop snippets each container emits) is unchanged; only the SPI binding and the step representation change.

#### Scenario: a container iterate/collect carries ElementScope
- **WHEN** a container strategy emits its collect operation
- **THEN** the emitted `ExpansionStep` has `intent == BOUNDARY`
- **AND** `scope()` returns `Optional.of(ElementScope.EXITING)`

#### Scenario: a single-element wrap has no scope
- **WHEN** a container strategy emits a single-element wrap step
- **THEN** the emitted `ExpansionStep` has `intent == BOUNDARY`
- **AND** `scope()` returns `Optional.empty()`

#### Scenario: container strategies register under the unified service type
- **WHEN** the source of any container strategy is inspected
- **THEN** it carries `@AutoService(ExpansionStrategy.class)`
- **AND** it implements `ExpansionStrategy` through the `ContainerMatch` mixin

### Requirement: Linear container chain (no diamond)

Any expansion involving the container built-ins SHALL produce a strictly linear REALISED chain — no diamonds, no parallel "outer" edges, no incoming-only `ElementLocation` leaves, no outgoing-only `ElementLocation` leaves except at the source-parameter-root boundary.

Specifically, for the integration mapper `~/Projects/joke/percolate-integration/mappers/src/main/java/io/github/joke/testing/PersonMapper.java` with its `mapHuman` and `mapAddress` methods, the `*.transforms.dot` view of `mapHuman`'s expansion for `tgt[addresses]:Optional<Set<HA>>` SHALL trace a linear path through REALISED edges from `src[person]` through `IterableUnwrap → OptionalUnwrap → MethodCallBridge → SetCollect → OptionalCollect/OptionalWrap` to `tgt[addresses]`.

Every `elem(...)` node in the alive chain SHALL have at least one incoming and at least one outgoing REALISED edge. Parallel dead branches from other matching bridges MAY co-exist; their presence SHALL NOT block the alive chain from satisfying the slot.

#### Scenario: No outer container-map shortcut edges
- **WHEN** any container-bearing mapper is expanded
- **THEN** for every `Set<T>` / `List<T>` / `T[]` / `Optional<T>` target reached via the chain pattern (Unwrap → ... → Collect), the only REALISED edge incoming to that container node from a container-typed source is the `*Collect` edge from the element-scope chain
- **AND** no parallel REALISED edge connects the source container directly to the target container with a `*Map`-style label
