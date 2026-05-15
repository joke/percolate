## MODIFIED Requirements

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

The `percolate-strategies-builtin` module SHALL ship `io.github.joke.percolate.spi.builtins.OptionalWrap`
implementing `Bridge` and annotated `@AutoService(Bridge.class)`.

`OptionalWrap.bridge(from, to, ctx)` SHALL emit a single `BridgeStep`
when `to` is an `Optional<X>` declared type. The emitted step SHALL
have:

- `inputType` = the type argument `X` of `to`.
- `outputType` = `to` itself (the `Optional<X>` type).
- `weight` = `Weights.CONTAINER`.
- `codegen` rendering `Optional.ofNullable(<input>)` where
  `<input>` is the single incoming variable supplied by
  `IncomingValues.single()`.
- `elementSeeds` = `List.of()`.

When `to` is not an `Optional`, `OptionalWrap.bridge` SHALL return
`Stream.empty()`.

#### Scenario: OptionalWrap emits for Optional target
- **WHEN** `OptionalWrap.bridge(<Dog>, <Optional<Pet>>, ctx)` is invoked
- **THEN** the stream contains one `BridgeStep`
- **AND** the step's `inputType` is `<Pet>` and `outputType` is `<Optional<Pet>>`
- **AND** the step's `weight` equals `Weights.CONTAINER`
- **AND** the step's `codegen` renders `Optional.ofNullable(<inputVar>)`
- **AND** the step's `elementSeeds` is empty

#### Scenario: OptionalWrap declines non-Optional target
- **WHEN** `OptionalWrap.bridge(<Dog>, <Pet>, ctx)` is invoked
- **THEN** the result is `Stream.empty()`

### Requirement: OptionalUnwrap built-in

The `percolate-strategies-builtin` module SHALL ship `io.github.joke.percolate.spi.builtins.OptionalUnwrap`
implementing `Bridge` and annotated `@AutoService(Bridge.class)`.

`OptionalUnwrap.bridge(from, to, ctx)` SHALL emit a single
`BridgeStep` when `from` is an `Optional<X>` declared type. The
emitted step SHALL have:

- `inputType` = `from` itself (the `Optional<X>` type).
- `outputType` = the type argument `X` of `from`.
- `weight` = `Weights.CONTAINER`.
- `codegen` rendering `<input>.orElse(null)` where `<input>` is the
  single incoming variable supplied by `IncomingValues.single()`.
- `elementSeeds` = `List.of()`.

When `from` is not an `Optional`, `OptionalUnwrap.bridge` SHALL
return `Stream.empty()`.

The v1 codegen unconditionally emits `.orElse(null)`. Future
changes will refine this based on `@Nullable` / `@Default`
enrichment; that is out of scope here.

#### Scenario: OptionalUnwrap emits for Optional source
- **WHEN** `OptionalUnwrap.bridge(<Optional<Dog>>, <Pet>, ctx)` is invoked
- **THEN** the stream contains one `BridgeStep`
- **AND** the step's `inputType` is `<Optional<Dog>>` and `outputType` is `<Dog>`
- **AND** the step's `weight` equals `Weights.CONTAINER`
- **AND** the step's `codegen` renders `<inputVar>.orElse(null)`
- **AND** the step's `elementSeeds` is empty

#### Scenario: OptionalUnwrap declines non-Optional source
- **WHEN** `OptionalUnwrap.bridge(<Dog>, <Pet>, ctx)` is invoked
- **THEN** the result is `Stream.empty()`

### Requirement: OptionalMap built-in

The `percolate-strategies-builtin` module SHALL ship `io.github.joke.percolate.spi.builtins.OptionalMap`
implementing `Bridge` and annotated `@AutoService(Bridge.class)`.

`OptionalMap.bridge(from, to, ctx)` SHALL emit a single
`BridgeStep` when both `from` and `to` are `Optional<...>` declared
types. The emitted step SHALL have:

- `inputType` = `from`.
- `outputType` = `to`.
- `weight` = `Weights.CONTAINER`.
- `codegen` lambda that, when invoked, throws
  `UnsupportedOperationException` with a message naming the future
  codegen capability. (The graph shape is the contract today; the
  renderer that interprets it is a future change.)
- `elementSeeds` = a single-element list `[ElementSeed("element",
  innerFrom, innerTo)]` where `innerFrom` is `from`'s type argument
  and `innerTo` is `to`'s type argument.

When either `from` or `to` is not an `Optional`, `OptionalMap.bridge`
SHALL return `Stream.empty()`.

#### Scenario: OptionalMap emits for Optional → Optional
- **WHEN** `OptionalMap.bridge(<Optional<Dog>>, <Optional<Pet>>, ctx)` is invoked
- **THEN** the stream contains one `BridgeStep`
- **AND** the step's `inputType` is `<Optional<Dog>>` and `outputType` is `<Optional<Pet>>`
- **AND** the step's `weight` equals `Weights.CONTAINER`
- **AND** the step's `elementSeeds` is a single entry with `role = "element"`, `inputType = <Dog>`, `outputType = <Pet>`

#### Scenario: OptionalMap codegen lambda throws
- **WHEN** the codegen lambda of an `OptionalMap`-emitted step is invoked
- **THEN** it throws `UnsupportedOperationException`
- **AND** the exception message names the future codegen capability

#### Scenario: OptionalMap declines mixed source/target
- **WHEN** `OptionalMap.bridge(<Dog>, <Optional<Pet>>, ctx)` is invoked
- **THEN** the result is `Stream.empty()`
- **WHEN** `OptionalMap.bridge(<Optional<Dog>>, <Pet>, ctx)` is invoked
- **THEN** the result is `Stream.empty()`

### Requirement: ListWrap built-in

The `percolate-strategies-builtin` module SHALL ship `io.github.joke.percolate.spi.builtins.ListWrap`
implementing `Bridge` and annotated `@AutoService(Bridge.class)`.

`ListWrap.bridge(from, to, ctx)` SHALL emit a single `BridgeStep`
when `to` is a `List<X>` declared type. The emitted step SHALL have:

- `inputType` = the type argument `X` of `to`.
- `outputType` = `to`.
- `weight` = `Weights.CONTAINER`.
- `codegen` rendering `List.of(<input>)` (a single-element list).
- `elementSeeds` = `List.of()`.

When `to` is not a `List`, `ListWrap.bridge` SHALL return
`Stream.empty()`.

#### Scenario: ListWrap emits for List target
- **WHEN** `ListWrap.bridge(<Dog>, <List<Pet>>, ctx)` is invoked
- **THEN** the stream contains one `BridgeStep`
- **AND** the step's `inputType` is `<Pet>` and `outputType` is `<List<Pet>>`
- **AND** the step's `codegen` renders `List.of(<inputVar>)`

#### Scenario: ListWrap declines non-List target
- **WHEN** `ListWrap.bridge(<Dog>, <Set<Pet>>, ctx)` is invoked
- **THEN** the result is `Stream.empty()`

### Requirement: ListMap built-in

The `percolate-strategies-builtin` module SHALL ship `io.github.joke.percolate.spi.builtins.ListMap`
implementing `Bridge` and annotated `@AutoService(Bridge.class)`.

`ListMap.bridge(from, to, ctx)` SHALL emit a single `BridgeStep`
when `to` is a `List<B>` declared type AND `from` is one of the
following input shapes:

- `Iterable<A>` (and subtypes — `Collection<A>`, `List<A>`, `Set<A>`,
  etc.).
- `A[]` (array type).
- `Optional<A>` (treated as a 0-or-1 element iterable via
  `Optional.stream()`).

For each accepted input shape, the emitted step SHALL have:

- `inputType` = `from`.
- `outputType` = `to`.
- `weight` = `Weights.CONTAINER`.
- `codegen` lambda that throws `UnsupportedOperationException` (per
  the codegen-deferred policy).
- `elementSeeds` = `[ElementSeed("element", innerFrom, innerTo)]`
  where `innerFrom` is the element type of `from` (the type
  argument for parameterised types, or the component type for
  arrays) and `innerTo` is `to`'s type argument.

When `to` is not a `List` or `from` is none of the recognised input
shapes, `ListMap.bridge` SHALL return `Stream.empty()`.

#### Scenario: ListMap emits for List → List
- **WHEN** `ListMap.bridge(<List<Dog>>, <List<Pet>>, ctx)` is invoked
- **THEN** the stream contains one `BridgeStep`
- **AND** the step's `inputType` is `<List<Dog>>` and `outputType` is `<List<Pet>>`
- **AND** the step's `elementSeeds` is a single entry with `role = "element"`, `inputType = <Dog>`, `outputType = <Pet>`

#### Scenario: ListMap emits for Set → List (cross-container)
- **WHEN** `ListMap.bridge(<Set<Dog>>, <List<Pet>>, ctx)` is invoked
- **THEN** the stream contains one `BridgeStep`
- **AND** the step's `elementSeeds` carries `inputType = <Dog>`, `outputType = <Pet>`

#### Scenario: ListMap emits for Optional → List
- **WHEN** `ListMap.bridge(<Optional<Dog>>, <List<Pet>>, ctx)` is invoked
- **THEN** the stream contains one `BridgeStep`
- **AND** the step's `elementSeeds` carries `inputType = <Dog>`, `outputType = <Pet>`

#### Scenario: ListMap emits for array → List
- **WHEN** `ListMap.bridge(<Dog[]>, <List<Pet>>, ctx)` is invoked
- **THEN** the stream contains one `BridgeStep`
- **AND** the step's `elementSeeds` carries `inputType = <Dog>`, `outputType = <Pet>`

#### Scenario: ListMap declines non-List target
- **WHEN** `ListMap.bridge(<List<Dog>>, <Set<Pet>>, ctx)` is invoked
- **THEN** the result is `Stream.empty()`

#### Scenario: ListMap declines unsupported source
- **WHEN** `ListMap.bridge(<Dog>, <List<Pet>>, ctx)` is invoked
- **THEN** the result is `Stream.empty()` (single non-container source uses `ListWrap` if applicable)

### Requirement: SetWrap built-in

The `percolate-strategies-builtin` module SHALL ship `io.github.joke.percolate.spi.builtins.SetWrap`
implementing `Bridge` and annotated `@AutoService(Bridge.class)`.

`SetWrap.bridge(from, to, ctx)` SHALL emit a single `BridgeStep`
when `to` is a `Set<X>` declared type. The emitted step SHALL have:

- `inputType` = the type argument `X` of `to`.
- `outputType` = `to`.
- `weight` = `Weights.CONTAINER`.
- `codegen` rendering `Set.of(<input>)` (a single-element set).
- `elementSeeds` = `List.of()`.

When `to` is not a `Set`, `SetWrap.bridge` SHALL return
`Stream.empty()`.

#### Scenario: SetWrap emits for Set target
- **WHEN** `SetWrap.bridge(<Dog>, <Set<Pet>>, ctx)` is invoked
- **THEN** the stream contains one `BridgeStep`
- **AND** the step's `inputType` is `<Pet>` and `outputType` is `<Set<Pet>>`
- **AND** the step's `codegen` renders `Set.of(<inputVar>)`

#### Scenario: SetWrap declines non-Set target
- **WHEN** `SetWrap.bridge(<Dog>, <List<Pet>>, ctx)` is invoked
- **THEN** the result is `Stream.empty()`

### Requirement: SetMap built-in

The `percolate-strategies-builtin` module SHALL ship `io.github.joke.percolate.spi.builtins.SetMap`
implementing `Bridge` and annotated `@AutoService(Bridge.class)`.

`SetMap.bridge(from, to, ctx)` SHALL emit a single `BridgeStep` when
`to` is a `Set<B>` declared type AND `from` is one of the accepted
input shapes (same set as `ListMap`: iterables, arrays, `Optional`).

The emitted step SHALL have:

- `inputType` = `from`.
- `outputType` = `to`.
- `weight` = `Weights.CONTAINER`.
- `codegen` lambda that throws `UnsupportedOperationException`.
- `elementSeeds` = `[ElementSeed("element", innerFrom, innerTo)]`
  with element types determined the same way as `ListMap`.

When `to` is not a `Set` or `from` is none of the recognised input
shapes, `SetMap.bridge` SHALL return `Stream.empty()`.

#### Scenario: SetMap emits for Set → Set
- **WHEN** `SetMap.bridge(<Set<Dog>>, <Set<Pet>>, ctx)` is invoked
- **THEN** the stream contains one `BridgeStep`
- **AND** the step's `elementSeeds` carries `inputType = <Dog>`, `outputType = <Pet>`

#### Scenario: SetMap emits for List → Set (cross-container)
- **WHEN** `SetMap.bridge(<List<Dog>>, <Set<Pet>>, ctx)` is invoked
- **THEN** the stream contains one `BridgeStep`

#### Scenario: SetMap declines non-Set target
- **WHEN** `SetMap.bridge(<List<Dog>>, <List<Pet>>, ctx)` is invoked
- **THEN** the result is `Stream.empty()`

### Requirement: Container strategies registered via AutoService

Every container built-in strategy SHALL be annotated `@AutoService(Bridge.class)`. The seven strategies covered are `OptionalWrap`, `OptionalUnwrap`, `OptionalMap`, `ListWrap`, `ListMap`, `SetWrap`, and `SetMap`. The compile-time-generated `META-INF/services/io.github.joke.percolate.spi.Bridge` file (produced from the `percolate-strategies-builtin` module's compilation) SHALL list all seven strategies.

#### Scenario: All seven container strategies are registered
- **WHEN** the generated `META-INF/services/io.github.joke.percolate.spi.Bridge`
  is inspected (inside the `percolate-strategies-builtin` JAR)
- **THEN** the file lists `OptionalWrap`, `OptionalUnwrap`,
  `OptionalMap`, `ListWrap`, `ListMap`, `SetWrap`, and `SetMap`
  (with the new `io.github.joke.percolate.spi.builtins.` prefix)
