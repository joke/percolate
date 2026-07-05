## ADDED Requirements

### Requirement: TypeRef is the immutable type currency

The `percolate-spi` module SHALL define an immutable `TypeRef` value hierarchy as the sole type currency of
the engine and SPI: a declared reference (qualified name plus ordered type arguments), a primitive reference
(one per primitive kind), an array reference (component `TypeRef`), a type-variable reference (name, for
functor-lift matching), and a none/void reference. All `TypeRef` values SHALL be deeply immutable with
**value equality** (`equals`/`hashCode` derived from the data), and `toString` SHALL render the Java source
form (e.g. `java.util.List<java.lang.String>`). No `TypeRef` SHALL hold any reference to
`javax.lang.model` types or to any live compiler session.

#### Scenario: TypeRef is value-equal
- **WHEN** two `TypeRef` values are constructed independently for `List<String>`
- **THEN** they are `equals` with equal `hashCode`s and are usable as map keys and set members

#### Scenario: toString renders the source form
- **WHEN** the `TypeRef` for `java.util.List<java.lang.String>` is rendered via `toString`
- **THEN** the result is `java.util.List<java.lang.String>`

#### Scenario: TypeRef holds no compiler handle
- **WHEN** the `TypeRef` hierarchy's fields are inspected
- **THEN** no field references `javax.lang.model` types, `Types`, `Elements`, or any javac class

### Requirement: TypeSpace snapshot owns the type algebra

The `percolate-spi` module SHALL define `TypeSpace`: an immutable snapshot value answering the type-system
queries the engine and strategies ask — structural sameness, assignability, erasure, declared-type
construction, array construction, the boxing/unboxing table, declaration lookup, and member enumeration.
`TypeSpace` SHALL be **passed as a value** (through `ResolveCtx`); it SHALL NOT be held in mutable static
state, and no operation on it SHALL mutate it. Assignability SHALL be a reflexive-transitive walk over
declared supertype edges with type-argument substitution along each edge (e.g. `ArrayList<E> → List<E>`);
same-erasure comparisons SHALL treat type arguments invariantly (pairwise sameness); raw types SHALL fall
back to erasure comparison. Boxing SHALL be the fixed eight-entry primitive↔wrapper table.

#### Scenario: Assignability walks declared edges with substitution
- **WHEN** a `TypeSpace` declares `ArrayList<E>` with supertype edge `List<E>` and `isAssignable(ArrayList<String>, List<String>)` is queried
- **THEN** the result is `true`, and `isAssignable(ArrayList<String>, List<Integer>)` is `false` (invariant arguments)

#### Scenario: Erasure drops type arguments
- **WHEN** `erasure(List<String>)` is queried
- **THEN** the result is the raw `List` reference, and `erasure` applied twice equals `erasure` applied once

#### Scenario: Boxing round-trips
- **WHEN** `boxed(int)` and then `unboxed(Integer)` are queried
- **THEN** the results are `Integer` and `int` respectively, for all eight primitive kinds

#### Scenario: Two snapshots are independent values
- **WHEN** two specs construct two different `TypeSpace` values concurrently on different threads
- **THEN** queries on one are unaffected by the other; there is no shared mutable state and no synchronisation

### Requirement: TypeDecl member model

`TypeSpace` declarations SHALL carry the structural member model production code reads: a `TypeDecl` with
kind (class/interface/enum), modifiers, supertype edges, and members — method signatures (name, ordered
parameters, return `TypeRef`, static/default/abstract flags, constructor marker) and field signatures (name,
type, visibility, static flag). Member values SHALL be immutable and value-equal. Signature nullness (return,
parameter, field) SHALL be carried as resolved `Nullability` data on the signature — resolved once at the
boundary, never re-derived downstream.

#### Scenario: Members are enumerable from the declaration
- **WHEN** a `TypeDecl` for a bean with `getName()` and a public field `value` is queried for members
- **THEN** the method signature for `getName` (zero parameters, return `String`) and the field signature for `value` are both present with their modifiers

#### Scenario: Signatures carry resolved nullness
- **WHEN** a method signature is produced for an accessor whose return type is `@Nullable` under JSpecify rules
- **THEN** the signature's return nullness is `NULLABLE` without any downstream call into a `NullabilityResolver`

### Requirement: The discovery adapter materialises the snapshot at the processor boundary

The processor SHALL construct the `TypeSpace` during discovery, once per mapper round, by walking the real
`javax.lang.model` elements: the mapper's declared surface (parameter and return types), their member types
transitively (accessors, fields, constructors), callable-method signatures, and directive-referenced types.
The walk SHALL be eager and cycle-safe (visited set). `javax.lang.model` values SHALL NOT escape the
adapter into the model: everything downstream of discovery consumes only `TypeRef`/`TypeDecl` values. A type
the walk cannot resolve SHALL fail loudly (deferral/diagnostic), never silently produce a partial
declaration.

#### Scenario: The reachable closure is materialised
- **WHEN** a mapper maps `Person → Human` and `Person` has an accessor returning `Address`
- **THEN** the constructed `TypeSpace` contains declarations for `Person`, `Human`, and `Address` without any lazy javac callback at query time

#### Scenario: Self-referential types terminate
- **WHEN** the walk encounters `Person` with an accessor returning `Person`
- **THEN** materialisation terminates with a single `Person` declaration

### Requirement: Diagnostics positioning via opaque Origin

Model values that may be reported against (declarations, method signatures, directives) SHALL carry an
opaque `Origin` token. The engine SHALL treat `Origin` as pass-through data; only the processor boundary
SHALL resolve it (via an adapter-populated registry) back to the `Element`/`AnnotationMirror` needed for
`Messager` positioning. Diagnostic message content and positions SHALL be unchanged from before the
migration.

#### Scenario: Engine code never resolves an Origin
- **WHEN** engine (non-boundary) sources are inspected
- **THEN** no engine class resolves an `Origin` to a `javax.lang.model` value; resolution happens only in the diagnostics boundary

#### Scenario: Positions survive the indirection
- **WHEN** a realisation diagnostic is emitted for an unmappable binding
- **THEN** the reported position matches the same element/annotation position as before the currency change

### Requirement: TypeRef to TypeName emission

Codegen SHALL obtain every JavaPoet `TypeName` from a single `TypeRef → TypeName` emitter (replacing
`TypeName.get(TypeMirror)`): qualified names to `ClassName` (nested-declaration aware), type arguments to
`ParameterizedTypeName`, arrays and primitives directly, and type-use `@Nullable` annotations from the
nullness axis exactly as today. The emitter's output SHALL match what JavaPoet's own mirror-based rendering
produced for the same shapes.

#### Scenario: Emission matches JavaPoet rendering
- **WHEN** the emitter renders the `TypeRef`s for a nested generic (`Optional<Set<Address>>`), an array, a primitive, and a nested class
- **THEN** each produced `TypeName`'s rendering equals the rendering `TypeName.get(mirror)` produced for the equivalent mirror before the migration

#### Scenario: Nullable type-use annotations are emitted
- **WHEN** a generated signature requires `@Nullable String`
- **THEN** the emitter attaches the type-use annotation from the value's nullness axis, with unchanged generated output

### Requirement: Test-side construction is plain values

`spi` testFixtures SHALL provide two construction paths, both javac-free and thread-safe by construction:
**literal builders** (declare declarations, supertype edges, and members inline in a spec and obtain a
`TypeSpace` value) and a **reflection mirror** (`TestTypes.of(Class<?>)`-style) that derives a fixture
class's structure — methods, fields, constructors, generic supertypes — from `java.lang.reflect` for
compiled fixture classes. Common constants (`STRING`, `INT`, `LIST_OF_STRING`, …) SHALL be prebuilt plain
values, safe in `where:` blocks and across parallel executions. No test-fixture path SHALL start a javac
task or hold shared mutable state.

#### Scenario: A spec builds a space inline
- **WHEN** a spec declares two types and a supertype edge through the literal builder
- **THEN** it obtains an immutable `TypeSpace` answering sameness/assignability/erasure over exactly those declarations

#### Scenario: A fixture class is mirrored by reflection
- **WHEN** `TestTypes.of(PersonRecord.class)` is invoked for a compiled test fixture
- **THEN** the resulting declaration exposes the class's methods, fields, and constructors with their generic signatures, derived without javac

#### Scenario: Constants are parallel-safe
- **WHEN** two specs read `STRING` and `LIST_OF_STRING` concurrently in `where:` blocks
- **THEN** both observe equal values with no synchronisation, isolation annotation, or initialisation ordering concern

### Requirement: The algebra is lawful, not faithful

The `TypeSpace` algebra SHALL satisfy documented laws — sameness is an equivalence; assignability is
reflexive and transitive over declared edges; erasure is idempotent; boxing round-trips — verified by
example-based Spock specs over a spanning set of `TypeRef` shapes. The algebra SHALL NOT attempt JLS-faithful subtyping (wildcard calculus,
capture conversion); real Java type semantics remain validated by the feature-e2e layer through real
compiles.

#### Scenario: Laws hold across representative shapes
- **WHEN** the example specs exercise each `TypeRef` constructor kind (declared, array, primitive, variable; one- and two-argument; nested) and each declared edge in the fixture space
- **THEN** reflexivity, transitivity, erasure idempotence, boxing round-trip, and match→ground coherence hold on every case

#### Scenario: Wildcards are out of scope
- **WHEN** the v1 model surface is inspected
- **THEN** no wildcard representation exists, and no engine or built-in strategy code path requires one
