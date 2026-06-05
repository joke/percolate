# Container Expansion Spec

## Purpose

This spec defines container expansion: the `Containers` type-shape helper and the one-class-per-container `ContainerMatch` strategies (`Optional` / `List` / `Set` / `Array`) that emit their iterate/collect/unwrap/wrap operations as `BOUNDARY` `ExpansionStep`s carrying the appropriate `ElementScope`, plus the strictly-linear (no-diamond) REALISED-chain invariant their expansion must satisfy.

## Requirements

### Requirement: Containers helper

The percolate-spi module SHALL ship a public utility class
`io.github.joke.percolate.spi.Containers` exposing static
type-shape predicates and accessors for use by container
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

Specifically, for the integration mapper `~/Projects/joke/percolate-integration/mappers/src/main/java/io/github/joke/testing/PersonMapper.java` with its `mapHuman` and `mapAddress` methods, the `*.transforms.dot` view of `mapHuman`'s expansion for `tgt[addresses]:Optional<Set<HA>>` SHALL trace a linear path through REALISED edges from `src[person]` through the element-entering iterate/unwrap steps, a `MethodCallBridge` element conversion, and the element-exiting Set-collect then Optional-collect/wrap steps to `tgt[addresses]`.

Every `elem(...)` node in the alive chain SHALL have at least one incoming and at least one outgoing REALISED edge. Parallel dead branches from other matching strategies MAY co-exist; their presence SHALL NOT block the alive chain from satisfying the slot.

#### Scenario: No outer container-map shortcut edges
- **WHEN** any container-bearing mapper is expanded
- **THEN** for every `Set<T>` / `List<T>` / `T[]` / `Optional<T>` target reached via the chain pattern (Unwrap → ... → Collect), the only REALISED edge incoming to that container node from a container-typed source is the `*Collect` edge from the element-scope chain
- **AND** no parallel REALISED edge connects the source container directly to the target container with a `*Map`-style label
