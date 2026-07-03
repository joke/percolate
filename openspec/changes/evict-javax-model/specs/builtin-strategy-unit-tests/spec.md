## ADDED Requirements

### Requirement: Model-value type invariant

All types flowing through any strategy **unit** spec SHALL be plain `TypeRef`/`TypeSpace`/`TypeDecl` model
values, constructed via the `spi` testFixtures entry points (literal builders, the `TestTypes` reflection
mirror, or the prebuilt constants). No strategy unit spec SHALL import any `javax.lang.model` type, start a
javac task, or instantiate a `com.google.testing.compile.Compiler`. This invariant governs the per-strategy
unit specs that live directly under `strategies-builtin/src/test/groovy/io/github/joke/percolate/spi/builtins/`;
it does NOT govern the end-to-end compile specs under the `…/spi/builtins/e2e/` subpackage, which
legitimately drive `PercolateProcessor` through the shared compile harness.

Because every type is a value, strategy unit specs SHALL be safe under parallel execution and threaded
pitest without `@Isolated`, synchronisation, or execution-order constraints.

#### Scenario: No javac and no javax.lang.model in any unit spec
- **WHEN** the imports of every per-strategy unit spec directly under `strategies-builtin/src/test/groovy/io/github/joke/percolate/spi/builtins/` (excluding the `e2e/` subpackage) are inspected
- **THEN** no such file imports `javax.lang.model.*`, `com.sun.source.util.JavacTask`, `com.google.testing.compile.Compiler`, or `com.google.testing.compile.JavaFileObjects`
- **AND** every type-valued expression is a `TypeRef`/`TypeSpace` model value from the `spi` testFixtures entry points

#### Scenario: Fixture types resolve through the reflection mirror
- **WHEN** a strategy spec needs a user-defined fixture type
- **THEN** the spec obtains it via `TestTypes.of(<Fixture>.class)` (a Class literal, not a fully-qualified string)
- **AND** sameness/assignability queries against JDK constants behave consistently (all are values in one `TypeSpace`)

#### Scenario: Unit specs are parallel-safe
- **WHEN** the strategy unit suite runs with Spock parallel execution or threaded pitest
- **THEN** results are deterministic, and no spec carries `@Isolated`

## MODIFIED Requirements

### Requirement: ResolveCtxBuilder test helper

A class `io.github.joke.percolate.spi.builtins.test.ResolveCtxBuilder` SHALL exist under
`strategies-builtin/src/test/groovy/`. It SHALL produce instances of
`io.github.joke.percolate.spi.ResolveCtx` whose defaults are:

- `typeSpace()` returns the shared prebuilt default `TypeSpace` value (JDK constants plus the module's
  mirrored fixture types),
- `callableMethods()` returns a `CallableMethods` instance whose `producing(...)` returns an empty `Stream`.

(`ResolveCtx` declares only `typeSpace()` and `callableMethods()`; there is no `mapperType()` or
`currentMethod()`.) The builder SHALL expose a fluent `withCallableMethods(CallableMethods)` and a fluent
`withTypeSpace(TypeSpace)` that each return a new builder so that a per-scenario override is one method
chain. The builder SHALL be immutable per `build()` call.

Every per-strategy spec SHALL assemble its `ResolveCtx` through this builder. Specs SHALL NOT instantiate
anonymous `ResolveCtx` implementations.

#### Scenario: Default builder produces a model-backed ctx
- **WHEN** `new ResolveCtxBuilder().build()` is called
- **THEN** the returned `ResolveCtx`'s `typeSpace()` is the prebuilt default `TypeSpace` value
- **AND** its `callableMethods().producing(<any type>)` returns an empty `Stream`

#### Scenario: Builder applies CallableMethods override
- **WHEN** a Spock-mocked `CallableMethods` is supplied via `.withCallableMethods(mock)` and the builder is built
- **THEN** the resulting `ResolveCtx.callableMethods()` returns the supplied mock

#### Scenario: Builder applies TypeSpace override
- **WHEN** a spec-local `TypeSpace` is supplied via `.withTypeSpace(space)` and the builder is built
- **THEN** the resulting `ResolveCtx.typeSpace()` returns exactly that snapshot

### Requirement: Mocking boundary

`TypeSpace` SHALL NOT be mocked, stubbed, or otherwise replaced in any strategy unit spec: it is a plain
immutable value — a spec that needs different type relationships **constructs** a different snapshot via the
builders. `io.github.joke.percolate.spi.CallableMethods` MAY be Spock-mocked (`Mock(CallableMethods)`) when
a spec needs to control its behaviour.

The strategy under test (`DirectAssign`, `WidenPrimitive`, `OptionalContainer`, etc.) SHALL NOT be mocked,
stubbed, partially-mocked, or spied. It SHALL be constructed via its public no-args constructor and
exercised through its public interface methods.

#### Scenario: No mocking of the type space
- **WHEN** the source of any strategy spec is inspected
- **THEN** no `Mock(TypeSpace)`, `Stub(TypeSpace)`, or `Mockito.mock(TypeSpace.class)` appears; divergent type behaviour is expressed by constructing a different snapshot

#### Scenario: Strategy under test is plainly constructed
- **WHEN** any strategy spec invokes the strategy under test
- **THEN** the invocation site reads `new <StrategyClass>().<method>(...)` or holds a reference produced by such a call — no `Spy()`, `Mock()`, or `Stub()` wrapping the strategy itself

### Requirement: Shape fixtures for ConstructorCall and path resolvers

Java fixture types SHALL exist under
`strategies-builtin/src/test/java/io/github/joke/percolate/spi/builtins/fixtures/`. They SHALL be plain Java
sources compiled by the project's standard `compileTestJava` task into the test classpath; specs SHALL
mirror them into the model through `TestTypes.of(<Fixture>.class)` (a Class literal), not a fully-qualified
string.

The fixture set SHALL include at minimum:

- A record with named parameters (e.g. `record PersonRecord(int age, String name) {}`) — exercises `ConstructorCall`'s per-constructor over-emit and `MethodPathResolver`'s canonical-record-accessor branch.
- A JavaBean with paired getter/setter accessors (e.g. `getName`/`setName`, `getAge`/`setAge`) — exercises `GetterPathResolver`'s `getX` path.
- A class exposing a boolean-returning accessor named `isFlag()` — exercises `GetterPathResolver`'s `isX` branch.
- A class with at least one public, non-static field whose name carries meaning (e.g. `public String value`) — exercises `FieldPathResolver`'s public-field-match path.
- A non-record fluent-style class with at least one zero-arg method whose name carries meaning (e.g. `class Address { private String street; public String street() { return street; } }`) — exercises `MethodPathResolver`'s non-record branch and the precedence rule that `MethodPathResolver` outranks `FieldPathResolver`.
- A class declaring more than one constructor (e.g. distinct parameter lists) — exercises `ConstructorCall`'s per-constructor over-emit.

#### Scenario: Fixtures are on the test classpath
- **WHEN** the six-or-more fixture classes exist under `strategies-builtin/src/test/java/io/github/joke/percolate/spi/builtins/fixtures/`
- **THEN** `TestTypes.of(PersonRecord.class)` returns a non-null declaration whose reference is a declared `TypeRef`
- **AND** the same is true for the JavaBean, boolean-accessor, public-field, fluent-method, and multi-constructor fixtures

### Requirement: Per-resolver scenario coverage

Each `<ResolverClassSimpleName>Spec.groovy` SHALL cover, at minimum, the scenarios required by the
corresponding `Requirement` in the `source-path-resolution` capability — specifically the resolver's
positive-match scenarios and rejection scenarios. The spec SHALL exercise the resolver via the
`ResolveCtxBuilder` test helper and the model-value fixtures, consistent with the *Model-value type
invariant* and *ResolveCtxBuilder test helper* requirements.

For `GetterPathResolverSpec`, scenarios SHALL include: a positive match on a JavaBean `getX()` accessor, a
positive match on an `isX()` accessor with boolean return, rejection of parameterized overloads, rejection
of methods inherited from `java.lang.Object`, and rejection of non-declared parent types. The
positive-match assertions SHALL pin `weight` equal to `Weights.STEP_GETTER`.

For `MethodPathResolverSpec`, scenarios SHALL include: a positive match on a canonical record component
accessor, a positive match on a non-record fluent-style accessor, rejection of parameterized methods,
rejection of methods inherited from `java.lang.Object`, and rejection of non-declared parent types. The
positive-match assertions SHALL pin `weight` equal to `Weights.STEP_METHOD`. The spec SHALL NOT gate
behaviour on record-ness of the parent declaration.

For `FieldPathResolverSpec`, scenarios SHALL include: a positive match on a public field, rejection of
private fields, and rejection of static fields. The positive-match assertions SHALL pin `weight` equal to
`Weights.STEP_FIELD`.

#### Scenario: GetterPathResolverSpec covers JavaBean and boolean accessors
- **WHEN** `GetterPathResolverSpec.groovy` is inspected
- **THEN** the spec contains at least five feature methods covering: `getX` match, `isX` match, parameterized-overload rejection, Object-method rejection, non-declared-parent rejection
- **AND** the positive-match feature methods assert the returned `OperationSpec`'s `weight` equals `Weights.STEP_GETTER`

#### Scenario: MethodPathResolverSpec covers record and non-record parents
- **WHEN** `MethodPathResolverSpec.groovy` is inspected
- **THEN** the spec contains at least five feature methods covering: canonical record accessor match, non-record fluent method match, parameterized-method rejection, Object-method rejection, non-declared-parent rejection
- **AND** the positive-match feature methods assert the returned `OperationSpec`'s `weight` equals `Weights.STEP_METHOD`

#### Scenario: FieldPathResolverSpec covers visibility and modifiers
- **WHEN** `FieldPathResolverSpec.groovy` is inspected
- **THEN** the spec contains at least three feature methods covering: public-field match, private-field rejection, static-field rejection
- **AND** the positive-match feature method asserts the returned `OperationSpec`'s `weight` equals `Weights.STEP_FIELD`

### Requirement: Conversion strategy unit spec presence and coverage

The two `type-conversion` built-ins SHALL each have a corresponding Spock specification following the
established per-strategy pattern: `PrimitiveWrapperConversionSpec.groovy` and `WidenPrimitiveSpec.groovy`,
each residing at `strategies-builtin/src/test/groovy/io/github/joke/percolate/spi/builtins/<SimpleName>Spec.groovy`,
extending `spock.lang.Specification`, carrying `@spock.lang.Tag('unit')`, assembling its `ResolveCtx`
through `ResolveCtxBuilder`, and sourcing every type as a model value per the *Model-value type invariant*.

Assertions SHALL be metadata-only per the *Assertion scope is OperationSpec metadata only* requirement —
specs SHALL assert on the emitted `OperationSpec`'s port type(s), output type, and weight, and on the
presence/emptiness of the returned `Stream`. Specs SHALL NOT invoke codegen `render(...)` or pin
`CodeBlock` output.

`PrimitiveWrapperConversionSpec` SHALL cover at minimum: a boxing happy path (target `Integer` ⇒ one
`OperationSpec`, port `int`, output `Integer`, weight `Weights.STEP`), an unboxing happy path (target `int`
⇒ port `Integer`, output `int`), and an empty-return precondition (target neither a wrapper nor a primitive
with a wrapper).

`WidenPrimitiveSpec` SHALL cover at minimum: a widening happy path asserting the narrower-source specs
emitted for a numeric target (e.g. target `long` ⇒ one `OperationSpec` per `byte`, `short`, `char`, `int`,
each output `long`, weight `Weights.STEP`), an IEEE precision-losing leg (e.g. a spec consuming `long` to
produce `double` exists), a `boolean`-target empty-return, and a narrowing empty-return (no spec consuming
`long` to produce `int`).

#### Scenario: conversion strategy specs are present and tagged
- **WHEN** `strategies-builtin/src/test/groovy/io/github/joke/percolate/spi/builtins/` is inspected
- **THEN** `PrimitiveWrapperConversionSpec.groovy` and `WidenPrimitiveSpec.groovy` are both present
- **AND** each extends `spock.lang.Specification` and carries `@spock.lang.Tag('unit')`

#### Scenario: PrimitiveWrapperConversionSpec covers boxing, unboxing, and a precondition
- **WHEN** `PrimitiveWrapperConversionSpec.groovy` is inspected
- **THEN** a feature method asserts that a wrapper target emits one `OperationSpec` with the matching primitive port type and `Weights.STEP`
- **AND** a feature method asserts the unboxing direction (primitive target ⇒ wrapper port)
- **AND** a feature method asserts an empty `Stream` for a target that is neither a wrapper nor a primitive with a wrapper

#### Scenario: WidenPrimitiveSpec covers widening, an IEEE leg, and rejections
- **WHEN** `WidenPrimitiveSpec.groovy` is inspected
- **THEN** a feature method asserts the narrower-source `OperationSpec`s emitted for a numeric target with output and weight pinned
- **AND** a feature method asserts a precision-losing IEEE leg is emitted (e.g. `long → double`)
- **AND** a feature method asserts an empty `Stream` for a `boolean` target
- **AND** a feature method asserts no spec is emitted for a narrowing conversion (e.g. `long → int`)

## REMOVED Requirements

### Requirement: Single-substrate javac invariant
**Reason**: There is no javac substrate in unit tests any more — the model is plain values, so the
"one javac, one element table" identity discipline it protected is moot (see the ADDED *Model-value type
invariant*).
**Migration**: Specs replace `TypeUniverse`-sourced mirrors with `TypeSpace`/`TypeRef` values from the
`spi` testFixtures builders, `TestTypes.of(Class)`, and the prebuilt constants.
