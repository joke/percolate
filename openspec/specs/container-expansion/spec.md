# Container Expansion Spec

## Purpose

This spec defines container expansion: the `Containers` type-shape helper and the one-class-per-container strategies (`Optional` / `List` / `Set` / `Array`) that compose container conversions through each container's **own author-declared intermediate** (JDK containers over `Stream<E>`; a reactive container over its own type — no privileged universal intermediate). Each container emits plain kind-local `iterate` / `collect` / `wrap` / `unwrap` Operations for its own kind only, plus a same-kind functor-lift `mapPresence`; a generic, kind-free stream strategy emits the scope-owning `map` / `flatMap` over `Stream` Values. Element mapping is a functor lift grounded by type-variable matching (see `polymorphic-conversion`); cross-kind and flatten conversions emerge from composing over shared intermediates — bootstrapped by each container's `SourceProjection` of its own kind to its intermediate, no Operation knowing two kinds — under the scope-ownership invariant (no dependency edge crosses a child-scope boundary). The engine invents no cross-paradigm bridge.

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
- `boolean isStream(TypeMirror t, ResolveCtx ctx)` — true iff `t` is
  a declared type whose erasure is `java.util.stream.Stream`.
- `boolean isReferenceType(TypeMirror element)` — true iff `element`
  is a reference type (declared, array, or type variable), i.e. usable
  as a generic type argument; false for a primitive.
- `boolean isList(TypeMirror t, ResolveCtx ctx)` — true iff `t` is a
  declared type whose erasure is `java.util.List`.
- `boolean isSet(TypeMirror t, ResolveCtx ctx)` — true iff `t` is a
  declared type whose erasure is `java.util.Set`.
- `boolean isCollection(TypeMirror t, ResolveCtx ctx)` — true iff
  `t` is a declared type assignable to `java.util.Collection`, i.e.
  a `Collection` or a subtype thereof.
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

#### Scenario: isList matches only the List erasure
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

### Requirement: Containers iterate and collect through an author-declared intermediate

A container SHALL expose its element sequence as an explicit intermediate `Value` of **its own declared
type** (a JVM-collection container over `Stream<E>`; a reactive container over its own reactive type).
Each container emits a plain `iterate` Operation `Cont<E> → Intermediate<E>` and (if it is a sequence —
kind emergent from the presence of `collect`) a plain `collect` Operation `Intermediate<E> → Seq<E>`,
**for its own kind only**, referencing no other container kind and **no privileged universal
intermediate**. `java.util.stream.Stream` is one container's intermediate, not the engine's.

#### Scenario: A list iterates and a set collects through Stream
- **WHEN** a `Stream<E>` is demanded from a `List<E>` source, and a `Set<E>` from a `Stream<E>` source
- **THEN** the `List` container emits `iterate` (`.stream()`) and the `Set` container emits `collect`
  (`Collectors.toSet()`), each unaware of the other kind

#### Scenario: A presence wrapper iterates but does not collect
- **WHEN** a `Stream<E>` is demanded from an `Optional<E>` source
- **THEN** the `Optional` container emits an `iterate` rendering `Optional.stream()`, and supplies no
  `collect` (it is a wrapper by the absence of `collect`)

#### Scenario: A reactive container uses its own intermediate
- **WHEN** a third-party `Flux` container is declared
- **THEN** it iterates/collects through its own reactive intermediate (not `java.util.stream.Stream`),
  with no engine change

### Requirement: A container projects its own kind to its intermediate for cross-kind grounding

A container SHALL implement `SourceProjection` (design D8) to project an in-scope source of **its own
kind** to its declared intermediate — a JVM-collection container projects `Cont<X> → Stream<X>`; a
reactive container projects `Flux<X> → Flux<X>`. This source-facing projection is what lets the engine
ground a type-variable element-map port against a cross-kind source without the engine naming any kind:
the projected intermediate is then produced target-driven by the same container's `iterate`. A
container SHALL project **only** its own kind and SHALL NOT project across paradigms (no
`Flux → Stream`), so cross-paradigm bridges stay un-invented (see "No engine-invented cross-paradigm
bridges").

#### Scenario: A list projects to a stream so a stream map grounds across the kind boundary
- **WHEN** `Optional<Set<Claw>>` is demanded from a `List<Optional<Paw>>` source (cross-kind, mismatched
  nesting, drop-empties)
- **THEN** the list container's projection contributes `Stream<Optional<Paw>>`, the decomposed
  `wrap ← collect ← map ← flatMap ← iterate` pipeline grounds with every demand concrete, and the
  empty-dropping `flatMap` branch is the one totality keeps

### Requirement: Element mapping is a functor lift grounded by matching

The per-element transform SHALL be a generic functor lift declared per container: *given child
`A → B`, produce `F<B> ← F<A>`*, emitted as a scope-owning `OperationSpec` whose input port is `F<A>`
for a type variable `A` and whose child scope is `A → B`. The engine grounds `A` by **matching** the
`F<A>` port against an in-scope concrete source (see `polymorphic-conversion` / `graph-expansion`), not
by the strategy reading candidates and not by demanding an abstract type. The child scope then expands
on the same work-list confined to the child scope.

#### Scenario: The map input element type is grounded from the source
- **WHEN** a `Stream<B>` is demanded and a `Stream<A> → Stream<B>` lift is offered with a
  `Stream<Person>` source in scope
- **THEN** the engine grounds `A := Person`, the child scope becomes `Person → B`, and `B` resolves on
  the work-list like a method return-root

#### Scenario: The lift names no specific container kind in its grounding
- **WHEN** the functor-lift strategy and its grounding are inspected
- **THEN** the lift declares `F<B> ← F<A>` for its own `F` and the grounding mechanic references no
  container kind by name

#### Scenario: Nested containers nest scopes
- **WHEN** the target is `List<List<B>>` from `List<List<A>>`
- **THEN** the chosen lift's child plan contains another scope-owning lift Operation

### Requirement: Wrappers map presence in their own kind

A presence wrapper SHALL emit a same-kind scope-owning `mapPresence` Operation
(`Optional<A> → Optional<B>`, child `A → B`) that preserves presence, distinct from the stream path
(a wrapper has no `collect` terminal). `Optional<A> → Optional<B>` SHALL render `opt.map(a -> …)`, not
a stream round-trip.

#### Scenario: Optional maps presence directly
- **WHEN** `Optional<A> → Optional<B>` is produced
- **THEN** the plan contains a scope-owning `mapPresence` Operation rendering `opt.map(a -> …)`, with
  no `iterate`/`collect`

### Requirement: Wrap and unwrap are plain Operations

Wrapping (`Optional.of`, singleton collection) and unwrapping (element get) SHALL be plain unary
Operations with no child scope. `unwrap` (`Optional.orElseThrow`) SHALL be marked **partial** (it may
throw on an empty input; see `plan-extraction` totality dominance). The wrap-versus-element-mapping
distinction is structural (plain Operation vs scope-owning Operation), not an SPI mode.

#### Scenario: Wrap emits no child scope
- **WHEN** `T → Optional<T>` is produced by wrapping
- **THEN** the emitted Operation declares no child scope and is total

#### Scenario: Unwrap is partial
- **WHEN** `Optional<T> → T` is produced by unwrapping
- **THEN** the emitted Operation is plain and flagged partial

### Requirement: No engine-invented cross-paradigm bridges

The engine SHALL NOT synthesise a conversion between container paradigms that no strategy declared. A
collection ↔ reactive bridge (e.g. a blocking `Flux → List` via `collectList().block()`) is produced
only if a strategy explicitly emits it; absent that, the demand is reported unrealisable. This keeps
reactive code from being silently given a blocking conversion.

#### Scenario: Reactive-to-collection is not auto-bridged
- **WHEN** a `List<B>` target is fed only by a `Flux<A>` source and no strategy declares the bridge
- **THEN** no blocking conversion is generated and the demand has no producer

### Requirement: Cross-kind and flatten emerge from Stream OR-matching

The engine SHALL produce cross-kind conversions (`List → Set`) and mismatched-nesting / flatten
conversions (`List<Optional<A>> → Optional<Set<B>>`) with no dedicated Operation, by composing the
kind-local `iterate`/`collect`/`wrap`/`unwrap` and the generic `map`/`flatMap` over shared `Stream`
Values. To bootstrap the first `Stream` port from a non-stream source (target→source), the engine
grounds the stream strategy's type-variable `Stream<A>` port against the `Stream<X>` each in-scope
container source projects via its `SourceProjection` (see "A container projects its own kind to its
intermediate for cross-kind grounding"); the grounded concrete `Stream<X>` is then produced by that
container's own `iterate`. No container Operation and no engine component SHALL hold multi-kind
composition logic.

#### Scenario: Mismatched nesting composes from single-kind operations
- **WHEN** `List<Optional<A>> → Optional<Set<B>>` is demanded with the source `List` as the only
  in-scope source
- **THEN** the plan is `wrap ⟵ collect ⟵ map[A→B] ⟵ flatMap[Optional→Stream] ⟵ iterate(List)`, every
  Operation single-kind or kind-free

#### Scenario: Flatten drops empties, never throws
- **WHEN** a sequence element is itself a presence wrapper (`Stream<Optional<A>> → Stream<A>`)
- **THEN** the chosen producer is the total `flatMap` (`Optional.stream`) drop, not a partial
  `unwrap`/`orElseThrow` (see `plan-extraction` totality dominance)

### Requirement: Scope-ownership invariant for containers

No dependency edge SHALL cross a container child-scope boundary; the owning Operation is the only
coupling (see `graph-model` "Scope tree and child-scope ownership"). This replaces the former
strictly-linear REALISED-chain invariant.

#### Scenario: Element values stay inside the child scope
- **WHEN** the child plan for an element mapping is extracted
- **THEN** every vertex it contains belongs to the child scope, and the parent plan references it
  only through the owning Operation
