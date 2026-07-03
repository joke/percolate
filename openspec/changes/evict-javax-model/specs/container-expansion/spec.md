## MODIFIED Requirements

### Requirement: Containers helper

The percolate-spi module SHALL ship a public utility class
`io.github.joke.percolate.spi.Containers` exposing static
type-shape predicates and accessors for use by container
strategies — both built-in and external — expressed over the
`TypeRef` model. The class SHALL be `final`
with a private constructor (no instances). It SHALL reside in the
`percolate-spi` Gradle module so that third-party strategy authors
can use it without depending on the processor module.

The class SHALL expose at least these methods:

- `boolean isOptional(TypeRef t, ResolveCtx ctx)` — true iff `t`
  is a declared reference whose erasure is `java.util.Optional`.
- `boolean isStream(TypeRef t, ResolveCtx ctx)` — true iff `t` is
  a declared reference whose erasure is `java.util.stream.Stream`.
- `boolean isReferenceType(TypeRef element)` — true iff `element`
  is a reference type (declared, array, or type variable), i.e. usable
  as a generic type argument; false for a primitive.
- `boolean isList(TypeRef t, ResolveCtx ctx)` — true iff `t` is a
  declared reference whose erasure is `java.util.List`.
- `boolean isSet(TypeRef t, ResolveCtx ctx)` — true iff `t` is a
  declared reference whose erasure is `java.util.Set`.
- `boolean isCollection(TypeRef t, ResolveCtx ctx)` — true iff
  `t` is a declared reference assignable to `java.util.Collection`
  (per the `TypeSpace` walk), i.e. a `Collection` or a subtype thereof.
- `boolean isIterable(TypeRef t, ResolveCtx ctx)` — true iff `t`
  is a declared reference whose erasure is `java.lang.Iterable`, or a
  subtype thereof.
- `boolean isArray(TypeRef t)` — true iff `t` is an array reference.
- `TypeRef typeArgument(TypeRef declaredRef, int index)` —
  returns the `index`-th type argument of a parameterised declared
  reference. Behaviour for raw types or out-of-range indices is
  unspecified by the helper (callers SHALL `is*`-check first).
- `TypeRef arrayComponentType(TypeRef arrayRef)` — returns
  the component reference of an array.

The helper SHALL NOT throw on a non-applicable input to the
`is*` predicates — these SHALL return `false` and not throw. The
accessor methods (`typeArgument`, `arrayComponentType`) MAY throw if
the caller violates the precondition (e.g., calling
`arrayComponentType` on a non-array reference).

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
- **THEN** returns the `TypeRef` for `Dog`

#### Scenario: arrayComponentType extracts the element type
- **WHEN** `Containers.arrayComponentType(<Dog[]>)` is invoked
- **THEN** returns the `TypeRef` for `Dog`
