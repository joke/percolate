# Builtin Strategy Unit Tests Spec

## Purpose

This spec defines the discipline contract for testing the strategies in `percolate-strategies-builtin` in isolation. It specifies that every built-in `ExpansionStrategy` has a corresponding Spock specification, where it lives, how it is tagged, what it must verify (preconditions, happy path, element scope, codegen surface), the test-time substrate it consumes (`TypeUniverse` + `HarnessResolveCtx` from `spi` `testFixtures`), and the boundary at which mocking is appropriate (`CallableMethods` and adjacent `ResolveCtx` surfaces; never `Types` / `Elements`).

The contract exists to make per-strategy regressions diagnosable in seconds (a failing spec names the strategy that broke), to establish the copyable pattern third-party strategy authors should follow, and to keep the test substrate identical to production (one javac, one element table) so identity-mismatch bugs cannot creep in.

## Requirements

### Requirement: Per-strategy unit spec presence, naming and location

Every concrete `@AutoService(io.github.joke.percolate.spi.ExpansionStrategy.class)` implementation shipped from the `percolate-strategies-builtin` module SHALL have a corresponding Spock specification named `<StrategyClassSimpleName>Spec.groovy`. (`ExpansionStrategy` is the single strategy SPI interface; the former `Bridge` / `GroupTarget` / `PathSegmentResolver` interfaces are removed.)

The spec SHALL reside at `strategies-builtin/src/test/groovy/io/github/joke/percolate/spi/builtins/<StrategyClassSimpleName>Spec.groovy` (mirroring the strategy's main-source package). The spec SHALL `extend spock.lang.Specification` and SHALL carry the annotation `@spock.lang.Tag('unit')` at the class level.

The shipped built-in strategies and their required specs are: `DirectAssignSpec`, `MethodCallBridgeSpec`, `ConstructorCallSpec`, `WidenPrimitiveSpec`, `PrimitiveWrapperConversionSpec`, `ConstantValueSpec`, `NullnessCrossingSpec`, `StreamMapSpec`, `OptionalContainerSpec`, `ListContainerSpec`, `SetContainerSpec`, `ArrayContainerSpec`, `GetterPathResolverSpec`, `FieldPathResolverSpec`, and `MethodPathResolverSpec`.

The superseded per-operation container specs (`IterableUnwrapSpec`, `OptionalUnwrapSpec`, `OptionalWrapSpec`, `ListWrapSpec`, `SetWrapSpec`, `OptionalCollectSpec`, `SetCollectSpec`, `ListCollectSpec`, `ArrayCollectSpec`, `SetMapSpec`, `ListMapSpec`, `OptionalMapSpec`), and `GetterReadSpec` / `SingletonSpec` / `RecordPathResolverSpec`, SHALL NOT exist — the corresponding strategies were folded into the one-class-per-container-type strategies or otherwise removed.

#### Scenario: Every builtin has a matching spec
- **WHEN** the contents of `strategies-builtin/src/main/java/io/github/joke/percolate/spi/builtins/` and `strategies-builtin/src/test/groovy/io/github/joke/percolate/spi/builtins/` are inspected
- **THEN** for every concrete class in the main tree annotated with `@AutoService(ExpansionStrategy.class)`, a sibling `<SimpleName>Spec.groovy` file exists in the test tree

#### Scenario: The built-in strategy specs are present
- **WHEN** the test tree is inspected
- **THEN** `DirectAssignSpec`, `MethodCallBridgeSpec`, `ConstructorCallSpec`, `WidenPrimitiveSpec`, `PrimitiveWrapperConversionSpec`, `ConstantValueSpec`, `NullnessCrossingSpec`, `StreamMapSpec`, `OptionalContainerSpec`, `ListContainerSpec`, `SetContainerSpec`, `ArrayContainerSpec`, `GetterPathResolverSpec`, `FieldPathResolverSpec`, and `MethodPathResolverSpec` are all present
- **AND** each extends `spock.lang.Specification` and carries `@spock.lang.Tag('unit')`

#### Scenario: Removed strategies have no specs
- **WHEN** the test tree is inspected
- **THEN** no file named `IterableUnwrapSpec.groovy`, `OptionalUnwrapSpec.groovy`, `OptionalWrapSpec.groovy`, `ListWrapSpec.groovy`, `SetWrapSpec.groovy`, `OptionalCollectSpec.groovy`, `SetCollectSpec.groovy`, `ListCollectSpec.groovy`, `ArrayCollectSpec.groovy`, `SetMapSpec.groovy`, `ListMapSpec.groovy`, `OptionalMapSpec.groovy`, `GetterReadSpec.groovy`, or `RecordPathResolverSpec.groovy` exists

#### Scenario: Specs are tagged as unit
- **WHEN** any one of the required strategy specs is inspected
- **THEN** the class is annotated with `@spock.lang.Tag('unit')` (the Spock-package `Tag`, not `org.junit.jupiter.api.Tag`)
- **AND** the class extends `spock.lang.Specification`

### Requirement: Single-substrate javac invariant

All `javax.lang.model.type.TypeMirror` instances flowing through any strategy **unit** spec SHALL originate from the single `com.sun.source.util.JavacTask` held inside `io.github.joke.percolate.spi.test.TypeUniverse` (the fixture published by `percolate-spi`'s `testFixtures` configuration). This invariant governs the per-strategy unit specs that live directly under `strategies-builtin/src/test/groovy/io/github/joke/percolate/spi/builtins/`; it does NOT govern the end-to-end compile specs under the `…/spi/builtins/e2e/` subpackage, which legitimately drive `PercolateProcessor` through the shared compile harness.

No strategy unit spec SHALL construct a second `JavacTask`, instantiate a separate `com.google.testing.compile.Compiler`, or otherwise introduce a parallel `Elements`/`Types` pair. Both JDK types and `strategies-builtin/src/test/java/.../fixtures/` user types SHALL resolve through that single substrate.

If a future build configuration prevents `TypeUniverse.of(...)`/`element(...)` from resolving user-defined classpath types, the remediation SHALL be a local change to `TypeUniverse` that wires an explicit `javax.tools.StandardJavaFileManager` with the test classpath. Introducing a second javac task to work around the visibility issue SHALL NOT be considered an acceptable remediation.

#### Scenario: No second JavacTask in any unit spec
- **WHEN** the imports of every per-strategy unit spec directly under `strategies-builtin/src/test/groovy/io/github/joke/percolate/spi/builtins/` (excluding the `e2e/` subpackage) are inspected
- **THEN** no such file imports `com.sun.source.util.JavacTask`, `com.google.testing.compile.Compiler`, or `com.google.testing.compile.JavaFileObjects`
- **AND** every `TypeMirror`-typed expression is sourced from `io.github.joke.percolate.spi.test.TypeUniverse` (directly or through `HarnessResolveCtx` / `ResolveCtxBuilder`)

#### Scenario: Fixture types resolve through TypeUniverse
- **WHEN** a strategy spec needs a user-defined fixture type
- **THEN** the spec obtains it via `TypeUniverse.of(<Fixture>.class).asType()` (a Class literal, not a fully-qualified string)
- **AND** `Types.isSameType(...)` between this type and any JDK type drawn from `TypeUniverse` behaves consistently (both come from the same javac element table)

### Requirement: ResolveCtxBuilder test helper

A class `io.github.joke.percolate.spi.builtins.test.ResolveCtxBuilder` SHALL exist under `strategies-builtin/src/test/groovy/`. It SHALL produce instances of `io.github.joke.percolate.spi.ResolveCtx` whose defaults are:

- `types()` returns `TypeUniverse.types()`,
- `elements()` returns `TypeUniverse.elements()`,
- `callableMethods()` returns a `CallableMethods` instance whose `producing(...)` returns an empty `Stream`.

(`ResolveCtx` declares only `types()`, `elements()`, and `callableMethods()`; there is no `mapperType()` or `currentMethod()`.) The builder SHALL expose a fluent `withCallableMethods(CallableMethods)` that returns a new builder so that a per-scenario override is one method chain. The builder SHALL be immutable per `build()` call.

Every per-strategy spec SHALL assemble its `ResolveCtx` through this builder. Specs SHALL NOT instantiate anonymous `ResolveCtx` implementations.

#### Scenario: Default builder produces a TypeUniverse-backed ctx
- **WHEN** `new ResolveCtxBuilder().build()` is called
- **THEN** the returned `ResolveCtx`'s `types()` equals `TypeUniverse.types()`
- **AND** its `elements()` equals `TypeUniverse.elements()`
- **AND** its `callableMethods().producing(<any type>)` returns an empty `Stream`

#### Scenario: Builder applies CallableMethods override
- **WHEN** a Spock-mocked `CallableMethods` is supplied via `.withCallableMethods(mock)` and the builder is built
- **THEN** the resulting `ResolveCtx.callableMethods()` returns the supplied mock

### Requirement: Mocking boundary

`javax.lang.model.util.Types` and `javax.lang.model.util.Elements` SHALL NOT be mocked, stubbed, or otherwise replaced in any strategy unit spec. Their behaviour is always provided by the real `JavacTask` held by `TypeUniverse`.

`io.github.joke.percolate.spi.CallableMethods` MAY be Spock-mocked (`Mock(CallableMethods)`) when a spec needs to control its behaviour. The other `ResolveCtx`-reachable surfaces (`types()`, `elements()`) MAY NOT be mocked.

The strategy under test (`DirectAssign`, `WidenPrimitive`, `OptionalContainer`, etc.) SHALL NOT be mocked, stubbed, partially-mocked, or spied. It SHALL be constructed via its public no-args constructor and exercised through its public interface methods.

#### Scenario: No Mockito stubbing of platform types
- **WHEN** the imports of any strategy spec are inspected
- **THEN** no `Mockito.mock(Types.class)`, `Mockito.mock(Elements.class)`, `Mock(Types)`, `Mock(Elements)`, or `Stub(Types)` / `Stub(Elements)` appears

#### Scenario: Strategy under test is plainly constructed
- **WHEN** any strategy spec invokes the strategy under test
- **THEN** the invocation site reads `new <StrategyClass>().<method>(...)` or holds a reference produced by such a call — no `Spy()`, `Mock()`, or `Stub()` wrapping the strategy itself

### Requirement: Shape fixtures for ConstructorCall and path resolvers

Java fixture types SHALL exist under `strategies-builtin/src/test/java/io/github/joke/percolate/spi/builtins/fixtures/`. They SHALL be plain Java sources compiled by the project's standard `compileTestJava` task into the test classpath; specs SHALL resolve them through `TypeUniverse.of(<Fixture>.class)` (a Class literal), not a fully-qualified string.

The fixture set SHALL include at minimum:

- A record with named parameters (e.g. `record PersonRecord(int age, String name) {}`) — exercises `ConstructorCall`'s per-constructor over-emit and `MethodPathResolver`'s canonical-record-accessor branch.
- A JavaBean with paired getter/setter accessors (e.g. `getName`/`setName`, `getAge`/`setAge`) — exercises `GetterPathResolver`'s `getX` path.
- A class exposing a boolean-returning accessor named `isFlag()` — exercises `GetterPathResolver`'s `isX` branch.
- A class with at least one public, non-static field whose name carries meaning (e.g. `public String value`) — exercises `FieldPathResolver`'s public-field-match path.
- A non-record fluent-style class with at least one zero-arg method whose name carries meaning (e.g. `class Address { private String street; public String street() { return street; } }`) — exercises `MethodPathResolver`'s non-record branch and the precedence rule that `MethodPathResolver` outranks `FieldPathResolver`.
- A class declaring more than one constructor (e.g. distinct parameter lists) — exercises `ConstructorCall`'s per-constructor over-emit.

#### Scenario: Fixtures are on the test classpath
- **WHEN** the six-or-more fixture classes exist under `strategies-builtin/src/test/java/io/github/joke/percolate/spi/builtins/fixtures/`
- **THEN** `TypeUniverse.of(PersonRecord.class)` returns a non-null `TypeElement` whose `asType()` is a `DeclaredType`
- **AND** the same is true for the JavaBean, boolean-accessor, public-field, fluent-method, and multi-constructor fixtures

### Requirement: Pinning policy for current-behaviour findings

Where a unit spec discovers behaviour that is unintentional or worth questioning but lies outside the scope of the current change to fix, the spec SHALL pin the current behaviour with a feature method whose name signals the pinning intent (e.g. `'pins current behaviour: MethodCallBridge accepts subtypeDistance == 0 for non-assignable inputs'`).

Adjacent to the feature method's `then:` block, the spec SHALL place a single-line source comment of the form `// FOLLOW-UP: <one-line summary>` so that the audit trail is discoverable by the next maintainer reading the test.

The `MethodCallBridge.subtypeDistance` finding pinned by `MethodCallBridgeSpec` SHALL be carried forward unchanged. (Findings tied to the retired per-operation container strategies — e.g. the former `ListMap`-accepts-`Optional` pin — are not carried forward, since those strategies were folded into the one-class-per-container-type strategies.)

#### Scenario: Known findings are pinned with FOLLOW-UP markers
- **WHEN** `MethodCallBridgeSpec` is inspected
- **THEN** at least one feature method demonstrates that `subtypeDistance` returns `0` for both same-type and non-assignable inputs, and pins the resulting `weight` in the returned `OperationSpec`
- **AND** a `// FOLLOW-UP:` source comment appears within twenty lines of that feature method

### Requirement: Per-resolver scenario coverage

Each `<ResolverClassSimpleName>Spec.groovy` SHALL cover, at minimum, the scenarios required by the corresponding `Requirement` in the `source-path-resolution` capability — specifically the resolver's positive-match scenarios and rejection scenarios. The spec SHALL exercise the resolver via the `ResolveCtxBuilder` test helper and the `TypeUniverse` substrate, consistent with the existing *Single-substrate javac invariant* and *ResolveCtxBuilder test helper* requirements.

For `GetterPathResolverSpec`, scenarios SHALL include: a positive match on a JavaBean `getX()` accessor, a positive match on an `isX()` accessor with boolean return, rejection of parameterized overloads, rejection of methods inherited from `java.lang.Object`, and rejection of non-declared parent types. The positive-match assertions SHALL pin `weight` equal to `Weights.STEP_GETTER`.

For `MethodPathResolverSpec`, scenarios SHALL include: a positive match on a canonical record component accessor, a positive match on a non-record fluent-style accessor, rejection of parameterized methods, rejection of methods inherited from `java.lang.Object`, and rejection of non-declared parent types. The positive-match assertions SHALL pin `weight` equal to `Weights.STEP_METHOD`. The spec SHALL NOT gate behaviour on `ElementKind.RECORD`.

For `FieldPathResolverSpec`, scenarios SHALL include: a positive match on a public field, rejection of private fields, and rejection of static fields. The positive-match assertions SHALL pin `weight` equal to `Weights.STEP_FIELD`.

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

The two `type-conversion` built-ins SHALL each have a corresponding Spock specification following the established per-strategy pattern: `PrimitiveWrapperConversionSpec.groovy` and `WidenPrimitiveSpec.groovy`, each residing at `strategies-builtin/src/test/groovy/io/github/joke/percolate/spi/builtins/<SimpleName>Spec.groovy`, extending `spock.lang.Specification`, carrying `@spock.lang.Tag('unit')`, assembling its `ResolveCtx` through `ResolveCtxBuilder`, and sourcing every `TypeMirror` from the single `TypeUniverse` substrate.

Assertions SHALL be metadata-only per the *Assertion scope is OperationSpec metadata only* requirement — specs SHALL assert on the emitted `OperationSpec`'s port type(s), output type, and weight, and on the presence/emptiness of the returned `Stream`. Specs SHALL NOT invoke codegen `render(...)` or pin `CodeBlock` output.

`PrimitiveWrapperConversionSpec` SHALL cover at minimum: a boxing happy path (target `Integer` ⇒ one `OperationSpec`, port `int`, output `Integer`, weight `Weights.STEP`), an unboxing happy path (target `int` ⇒ port `Integer`, output `int`), and an empty-return precondition (target neither a wrapper nor a primitive with a wrapper).

`WidenPrimitiveSpec` SHALL cover at minimum: a widening happy path asserting the narrower-source specs emitted for a numeric target (e.g. target `long` ⇒ one `OperationSpec` per `byte`, `short`, `char`, `int`, each output `long`, weight `Weights.STEP`), an IEEE precision-losing leg (e.g. a spec consuming `long` to produce `double` exists), a `boolean`-target empty-return, and a narrowing empty-return (no spec consuming `long` to produce `int`).

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

### Requirement: Assertion scope is OperationSpec metadata only

Strategy unit specs SHALL assert on the metadata of returned `OperationSpec` values: weight, port
signature (names, declared types, nullness), produced output type and nullness, child-scope
declaration (presence and element types), and emptiness of the returned stream for unmet
preconditions. Specs SHALL NOT invoke the spec's codegen `render(...)` or assert on rendered
`CodeBlock` output — codegen pinning remains out of scope.

#### Scenario: No codegen invocation in unit specs
- **WHEN** a strategy unit spec is inspected
- **THEN** it asserts on `OperationSpec` plain-data accessors and never calls `render`

### Requirement: Container strategies assert their child-scope declaration

For container strategies, at least one feature method SHALL assert the emitted spec's child-scope
declaration: element mapping declares a child scope with the expected element-in/element-out types;
wrap/unwrap emits a plain spec with no child scope.

#### Scenario: Element mapping declares a child scope
- **WHEN** the List strategy matches `List<A> → List<B>`
- **THEN** the unit spec asserts the `OperationSpec` declares a child scope with element types `A`
  and `B`

#### Scenario: Wrap declares none
- **WHEN** the Optional strategy emits a wrap for `T → Optional<T>`
- **THEN** the unit spec asserts the spec has no child-scope declaration
