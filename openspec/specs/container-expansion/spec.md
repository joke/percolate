# Container Expansion Spec

## Purpose

This spec defines container expansion: the `Containers` type-shape helper and the one-class-per-container strategies (`Optional` / `List` / `Set` / `Array`) that compose container conversions through an explicit `Stream<E>` intermediate. Each container emits plain kind-local `iterate` / `collect` / `wrap` / `unwrap` Operations for its own kind only; a generic, kind-free stream strategy emits the scope-owning `map` / `flatMap` over `Stream` Values (and wrappers a same-kind `mapPresence`). Cross-kind and flatten conversions emerge from OR-matching on shared `Stream` Values — no Operation knows two kinds — under the scope-ownership invariant (no dependency edge crosses a child-scope boundary).

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
  `t` is a declared type assignable to `java.util.Collection`, i.e.
  a `Collection` or a subtype thereof (the structural test
  `Containers.streamElement` relies on, line 154).
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

### Requirement: Containers iterate and collect through a Stream intermediate

A container SHALL expose its element sequence as an explicit `Stream<E>` `Value`. Each container emits
a plain `iterate` Operation `Cont<E> → Stream<E>` for **its own kind only** (a sequence via
`Collection.stream()`/`Arrays.stream`; a presence wrapper via `Optional.stream()` — the 0-or-1 stream
that realises drop-empties). A container that supplies `collect` (i.e. **a sequence** — kind is
emergent from the presence of `collect`, not a separate base type) emits a plain `collect` Operation
`Stream<E> → Seq<E>` for its own kind; a presence wrapper supplies no `collect`. No container
Operation SHALL reference a container kind other than its own.

#### Scenario: A list iterates and a set collects
- **WHEN** a `Stream<E>` is demanded from a `List<E>` candidate, and a `Set<E>` from a `Stream<E>`
  candidate
- **THEN** the `List` container emits the `iterate` (`.stream()`) and the `Set` container emits the
  `collect` (`Collectors.toSet()`), each unaware of the other kind

#### Scenario: A presence wrapper iterates but does not collect
- **WHEN** a `Stream<E>` is demanded from an `Optional<E>` candidate
- **THEN** the `Optional` container emits an `iterate` rendering `Optional.stream()`, and supplies no
  `collect` (it is a wrapper by the absence of `collect`)

### Requirement: Element mapping is a scope-owning Operation over a Stream

The per-element transform SHALL be a single **generic**, kind-free stream strategy that emits
scope-owning `map` (`Stream<A> → Stream<B>`, child `elem:A → elem:B`) and `flatMap`
(`Stream<A> → Stream<B>`, child `elem:A → Stream<B>`) Operations. The child scope holds an element
param-root `Value` (`elem:A`, base-case SAT) and an element return-root demand; the Operation is SAT
iff its outer port `Stream` and the child return-root are SAT. The child demand expands on the same
work-list with candidate search confined to the child scope.

#### Scenario: Child demand expands like a method body
- **WHEN** a `map` over `Stream<A> → Stream<B>` is emitted
- **THEN** the demand `elem:B` joins the work-list and resolves against the child scope's param-root,
  exactly as a method return-root resolves against method parameters

#### Scenario: The element strategy is kind-free
- **WHEN** the stream `map`/`flatMap` strategy is inspected
- **THEN** it matches on `Stream<…>` types only and references no specific container kind

#### Scenario: Nested containers nest scopes
- **WHEN** the target is `List<List<B>>` from `List<List<A>>`
- **THEN** the chosen `map` Operation's child plan contains another scope-owning `map` Operation

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

### Requirement: Cross-kind and flatten emerge from Stream OR-matching

The engine SHALL produce cross-kind conversions (`List → Set`) and mismatched-nesting / flatten
conversions (`List<Optional<A>> → Optional<Set<B>>`) with no dedicated Operation, by composing the
kind-local `iterate`/`collect`/`wrap`/`unwrap` and the generic `map`/`flatMap` over shared `Stream`
Values. To bootstrap the first `Stream` port from a non-stream candidate (target→source), the
stream strategy SHALL read the candidate's stream-element type from a shared structural helper
(`Containers.streamElement`: assignable-to-`Collection<E>` → E; array → component;
`Optional<E>`/`Stream<E>` → E), and the existing port-synthesis turns it into the `iterate` demand. No
container Operation and no engine component SHALL hold multi-kind composition logic.

#### Scenario: Mismatched nesting composes from single-kind operations
- **WHEN** `List<Optional<A>> → Optional<Set<B>>` is demanded with the source `List` as the only
  candidate
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
