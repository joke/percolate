# Builtin Strategy Unit Tests Spec

## Purpose

This spec defines the discipline contract for testing the strategies in `percolate-strategies-builtin` in isolation. It specifies that every built-in `ExpansionStrategy` has a corresponding Spock specification, where it lives, how it is tagged, what it must verify (preconditions, happy path, element scope, codegen surface), the test-time substrate it consumes (`TypeUniverse` + `HarnessResolveCtx` from `spi` `testFixtures`), and the boundary at which mocking is appropriate (`CallableMethods` and adjacent `ResolveCtx` surfaces; never `Types` / `Elements`).

The contract exists to make per-strategy regressions diagnosable in seconds (a failing spec names the strategy that broke), to establish the copyable pattern third-party strategy authors should follow, and to keep the test substrate identical to production (one javac, one element table) so identity-mismatch bugs cannot creep in.

## Requirements

### Requirement: Per-strategy unit spec presence, naming and location

Every concrete `@AutoService(io.github.joke.percolate.spi.ExpansionStrategy.class)` implementation shipped from the `percolate-strategies-builtin` module SHALL have a corresponding Spock specification named `<StrategyClassSimpleName>Spec.groovy`. (`ExpansionStrategy` is the single strategy SPI interface; the former `Bridge` / `GroupTarget` / `PathSegmentResolver` interfaces are removed.)

The spec SHALL reside at `strategies-builtin/src/test/groovy/io/github/joke/percolate/spi/builtins/<StrategyClassSimpleName>Spec.groovy` (mirroring the strategy's main-source package). The spec SHALL `extend spock.lang.Specification` and SHALL carry the annotation `@spock.lang.Tag('unit')` at the class level.

The shipped built-in strategies and their required specs are the twelve: `DirectAssignSpec`, `MethodCallBridgeSpec`, `ConstructorCallSpec`, `WidenPrimitiveSpec`, `PrimitiveWrapperConversionSpec`, `OptionalContainerSpec`, `ListContainerSpec`, `SetContainerSpec`, `ArrayContainerSpec`, `GetterPathResolverSpec`, `FieldPathResolverSpec`, and `MethodPathResolverSpec`.

The superseded per-operation container specs (`IterableUnwrapSpec`, `OptionalUnwrapSpec`, `OptionalWrapSpec`, `ListWrapSpec`, `SetWrapSpec`, `OptionalCollectSpec`, `SetCollectSpec`, `ListCollectSpec`, `ArrayCollectSpec`, `SetMapSpec`, `ListMapSpec`, `OptionalMapSpec`), and `GetterReadSpec` / `SingletonSpec` / `RecordPathResolverSpec`, SHALL NOT exist — the corresponding strategies were folded into the one-class-per-container-type strategies or otherwise removed.

#### Scenario: Every builtin has a matching spec
- **WHEN** the contents of `strategies-builtin/src/main/java/io/github/joke/percolate/spi/builtins/` and `strategies-builtin/src/test/groovy/io/github/joke/percolate/spi/builtins/` are inspected
- **THEN** for every concrete class in the main tree annotated with `@AutoService(ExpansionStrategy.class)`, a sibling `<SimpleName>Spec.groovy` file exists in the test tree

#### Scenario: The twelve built-in strategy specs are present
- **WHEN** the test tree is inspected
- **THEN** `DirectAssignSpec`, `MethodCallBridgeSpec`, `ConstructorCallSpec`, `WidenPrimitiveSpec`, `PrimitiveWrapperConversionSpec`, `OptionalContainerSpec`, `ListContainerSpec`, `SetContainerSpec`, `ArrayContainerSpec`, `GetterPathResolverSpec`, `FieldPathResolverSpec`, and `MethodPathResolverSpec` are all present
- **AND** each extends `spock.lang.Specification` and carries `@spock.lang.Tag('unit')`

#### Scenario: Removed strategies have no specs
- **WHEN** the test tree is inspected
- **THEN** no file named `IterableUnwrapSpec.groovy`, `OptionalUnwrapSpec.groovy`, `OptionalWrapSpec.groovy`, `ListWrapSpec.groovy`, `SetWrapSpec.groovy`, `OptionalCollectSpec.groovy`, `SetCollectSpec.groovy`, `ListCollectSpec.groovy`, `ArrayCollectSpec.groovy`, `SetMapSpec.groovy`, `ListMapSpec.groovy`, `OptionalMapSpec.groovy`, `GetterReadSpec.groovy`, or `RecordPathResolverSpec.groovy` exists

#### Scenario: Specs are tagged as unit
- **WHEN** any one of the twelve required strategy specs is inspected
- **THEN** the class is annotated with `@spock.lang.Tag('unit')` (the Spock-package `Tag`, not `org.junit.jupiter.api.Tag`)
- **AND** the class extends `spock.lang.Specification`

### Requirement: Single-substrate javac invariant

All `javax.lang.model.type.TypeMirror` instances flowing through any strategy unit spec SHALL originate from the single `com.sun.source.util.JavacTask` held inside `io.github.joke.percolate.test.TypeUniverse` (the fixture published by `percolate-spi`'s `testFixtures` configuration).

No strategy unit spec SHALL construct a second `JavacTask`, instantiate a separate `com.google.testing.compile.Compiler`, or otherwise introduce a parallel `Elements`/`Types` pair. Both JDK types and `strategies-builtin/src/test/java/.../fixtures/` user types SHALL resolve through that single substrate.

If a future build configuration prevents `TypeUniverse.element('…fixtures.PersonRecord')` from resolving user-defined classpath types, the remediation SHALL be a local change to `TypeUniverse` that wires an explicit `javax.tools.StandardJavaFileManager` with the test classpath. Introducing a second javac task to work around the visibility issue SHALL NOT be considered an acceptable remediation.

#### Scenario: No second JavacTask in any spec
- **WHEN** the imports of every file under `strategies-builtin/src/test/groovy/io/github/joke/percolate/spi/builtins/` are inspected
- **THEN** no file imports `com.sun.source.util.JavacTask`, `com.google.testing.compile.Compiler`, or `com.google.testing.compile.JavaFileObjects`
- **AND** every `TypeMirror`-typed expression is sourced from `io.github.joke.percolate.test.TypeUniverse` (directly or through `HarnessResolveCtx` / `ResolveCtxBuilder`)

#### Scenario: Fixture types resolve through TypeUniverse
- **WHEN** a strategy spec needs a user-defined fixture type
- **THEN** the spec obtains it via `TypeUniverse.element('io.github.joke.percolate.spi.builtins.fixtures.<FixtureSimpleName>').asType()`
- **AND** `Types.isSameType(...)` between this type and any JDK type drawn from `TypeUniverse` behaves consistently (both come from the same javac element table)

### Requirement: ResolveCtxBuilder test helper

A class `io.github.joke.percolate.spi.builtins.test.ResolveCtxBuilder` SHALL exist under `strategies-builtin/src/test/groovy/`. It SHALL produce instances of `io.github.joke.percolate.spi.ResolveCtx` whose defaults are:

- `types()` returns `TypeUniverse.types()`,
- `elements()` returns `TypeUniverse.elements()`,
- `mapperType()` returns `null`,
- `currentMethod()` returns `null`,
- `callableMethods()` returns a `CallableMethods` instance whose `producing(...)` returns an empty `Stream`.

The builder SHALL expose fluent `with*` methods for each surface — `withCallableMethods(CallableMethods)`, `withMapperType(TypeElement)`, `withCurrentMethod(ExecutableElement)` — so that per-scenario overrides are one method chain. The builder SHALL be immutable per `build()` call (each `with*` returns a new builder or stages state safely for one terminal `build()`).

Every per-strategy spec SHALL assemble its `ResolveCtx` through this builder. Specs SHALL NOT instantiate anonymous `ResolveCtx` implementations.

#### Scenario: Default builder produces a HarnessResolveCtx-equivalent ctx
- **WHEN** `new ResolveCtxBuilder().build()` is called
- **THEN** the returned `ResolveCtx`'s `types()` equals `TypeUniverse.types()`
- **AND** its `elements()` equals `TypeUniverse.elements()`
- **AND** its `mapperType()` is `null`
- **AND** its `currentMethod()` is `null`
- **AND** its `callableMethods().producing(<any type>)` returns an empty `Stream`

#### Scenario: Builder applies CallableMethods override
- **WHEN** a Spock-mocked `CallableMethods` is supplied via `.withCallableMethods(mock)` and the builder is built
- **THEN** the resulting `ResolveCtx.callableMethods()` returns the supplied mock

### Requirement: Mocking boundary

`javax.lang.model.util.Types` and `javax.lang.model.util.Elements` SHALL NOT be mocked, stubbed, or otherwise replaced in any strategy unit spec. Their behaviour is always provided by the real `JavacTask` held by `TypeUniverse`.

`io.github.joke.percolate.spi.CallableMethods`, and the optional ctx surfaces `mapperType()` and `currentMethod()`, MAY be Spock-mocked (`Mock(CallableMethods)`, etc.) when a spec needs to control their behaviour. Other `ResolveCtx`-reachable surfaces (`types()`, `elements()`) MAY NOT be mocked.

The strategy under test (`DirectAssign`, `IterableUnwrap`, `SetCollect`, etc.) SHALL NOT be mocked, stubbed, partially-mocked, or spied. It SHALL be constructed via its public no-args constructor and exercised through its public interface methods.

#### Scenario: No Mockito stubbing of platform types
- **WHEN** the imports of any strategy spec are inspected
- **THEN** no `Mockito.mock(Types.class)`, `Mockito.mock(Elements.class)`, `Mock(Types)`, `Mock(Elements)`, or `Stub(Types)` / `Stub(Elements)` appears

#### Scenario: Strategy under test is plainly constructed
- **WHEN** any strategy spec invokes the strategy under test
- **THEN** the invocation site reads `new <StrategyClass>().<method>(...)` or holds a reference produced by such a call — no `Spy()`, `Mock()`, or `Stub()` wrapping the strategy itself

### Requirement: Assertion scope is metadata-only

Strategy unit specs SHALL assert on the metadata of returned `ExpansionStep` values: the step's `intent` (`CONVERSION` / `BOUNDARY`), input slot types, output type, weight, element scope (presence and `ENTERING`/`EXITING` value), the presence or emptiness of the returned `Stream`, and any other plain-data accessor exposed by `ExpansionStep` and `Slot`.

Strategy unit specs SHALL NOT invoke `io.github.joke.percolate.spi.EdgeCodegen.render(...)` or `io.github.joke.percolate.spi.GroupCodegen.render(...)`. They SHALL NOT assert on `com.palantir.javapoet.CodeBlock.toString()` output or otherwise pin the rendered Java source produced by a strategy's codegen. Codegen pinning is the scope of a separate future change and is explicitly excluded from this contract.

#### Scenario: No codegen invocation in unit specs
- **WHEN** the source of every strategy spec is inspected
- **THEN** no method call to `EdgeCodegen.render`, `GroupCodegen.render`, or `CodeBlock.toString()` appears
- **AND** no `import com.palantir.javapoet.CodeBlock` appears in the strategy specs (the test helpers are free to use CodeBlock internally if needed)

### Requirement: Per-strategy scenario coverage

For each of the twelve strategy specs, the spec SHALL include at minimum:

- **Empty-return scenarios.** One Spock feature method per declared precondition demonstrating that the strategy returns an empty `Stream<ExpansionStep>` when that precondition is not met. Examples: a container strategy returns empty when the target is not its container type; `ConstructorCall` returns empty when the target type is not a `DECLARED` type with accessible constructors; `DirectAssign` returns empty when `isSameType` is false.

- **Happy-path scenarios.** At least one feature method that exercises the strategy on inputs that satisfy all preconditions, asserting on the returned step's metadata (`intent`, input slot type(s), `output`, `weight`, and `elementScope` where applicable).

- **Branch scenarios.** For strategies whose behaviour differs across multiple accepted input shapes or member-resolution paths, one feature method per distinguishable branch. Examples: `ConstructorCall` over-emits one `BOUNDARY` step per accessible constructor (cover a target with multiple constructors); a path resolver covers each member-resolution kind it supports.

- **Element-scope scenarios.** For container strategies that emit a scope-crossing (`BOUNDARY`) step, at least one feature method asserting the step's `elementScope` value (`ENTERING` when entering the element scope, `EXITING` when collecting back out). Scalar steps carry empty `elementScope`.

#### Scenario: Every strategy spec exercises at least one precondition failure
- **WHEN** any strategy spec is inspected
- **THEN** at least one feature method named to indicate a precondition asserts an empty `Stream<ExpansionStep>` result

#### Scenario: ConstructorCall covers multi-constructor over-emit
- **WHEN** `ConstructorCallSpec` is inspected
- **THEN** there exists a feature method exercising a target type with more than one accessible constructor
- **AND** it asserts one `BOUNDARY` `ExpansionStep` is emitted per accessible constructor

#### Scenario: Scope-bearing container strategies assert their element scope
- **WHEN** a container strategy spec that emits a scope-crossing step is inspected
- **THEN** at least one feature method asserts the returned `ExpansionStep`'s `elementScope` equals the expected `ENTERING` / `EXITING` value

### Requirement: Shape fixtures for ConstructorCall and path resolvers

Java fixture types SHALL exist under `strategies-builtin/src/test/java/io/github/joke/percolate/spi/builtins/fixtures/`. They SHALL be plain Java sources compiled by the project's standard `compileTestJava` task into the test classpath; specs SHALL resolve them through `TypeUniverse.element('io.github.joke.percolate.spi.builtins.fixtures.<SimpleName>')`.

The fixture set SHALL include at minimum:

- A record with named parameters (e.g. `record PersonRecord(int age, String name) {}`) — exercises `ConstructorCall`'s per-constructor over-emit and `MethodPathResolver`'s canonical-record-accessor branch.
- A JavaBean with paired getter/setter accessors (e.g. `getName`/`setName`, `getAge`/`setAge`) — exercises `GetterPathResolver`'s `getX` path.
- A class exposing a boolean-returning accessor named `isFlag()` — exercises `GetterPathResolver`'s `isX` branch.
- A class with at least one public, non-static field whose name carries meaning (e.g. `public String value`) — exercises `FieldPathResolver`'s public-field-match path.
- A non-record fluent-style class with at least one zero-arg method whose name carries meaning (e.g. `class Address { private String street; public String street() { return street; } }`) — exercises `MethodPathResolver`'s non-record branch and the precedence rule that `MethodPathResolver` outranks `FieldPathResolver`.
- A class declaring more than one constructor (e.g. distinct parameter lists) — exercises `ConstructorCall`'s per-constructor over-emit.

#### Scenario: Fixtures are on the test classpath
- **WHEN** the six-or-more fixture classes exist under `strategies-builtin/src/test/java/io/github/joke/percolate/spi/builtins/fixtures/`
- **THEN** `TypeUniverse.element('io.github.joke.percolate.spi.builtins.fixtures.PersonRecord')` returns a non-null `TypeElement` whose `asType()` is a `DeclaredType`
- **AND** the same is true for the JavaBean, boolean-accessor, public-field, fluent-method, and multi-constructor fixtures

### Requirement: Pinning policy for current-behaviour findings

Where a unit spec discovers behaviour that is unintentional or worth questioning but lies outside the scope of the current change to fix, the spec SHALL pin the current behaviour with a feature method whose name signals the pinning intent (e.g. `'pins current behaviour: MethodCallBridge accepts subtypeDistance == 0 for non-assignable inputs'`).

Adjacent to the feature method's `then:` block, the spec SHALL place a single-line source comment of the form `// FOLLOW-UP: <one-line summary>` so that the audit trail is discoverable by the next maintainer reading the test.

The `ListMap`-accepts-`Optional`-inputs finding pinned by the prior `ListMapSpec` SHALL NOT be carried forward — `ListMap` is deleted by `split-container-bridges`; the same shape pattern is now expressed through the orthogonal `IterableUnwrap` + `*Collect` chain, and `IterableUnwrap` explicitly declines `Optional` input (see the per-strategy coverage requirement). The `MethodCallBridge.subtypeDistance` finding pinned by `MethodCallBridgeSpec` SHALL be carried forward unchanged.

#### Scenario: Known findings are pinned with FOLLOW-UP markers
- **WHEN** `MethodCallBridgeSpec` is inspected
- **THEN** at least one feature method demonstrates that `subtypeDistance` returns `0` for both same-type and non-assignable inputs, and pins the resulting `weight` in the returned `ExpansionStep`
- **AND** a `// FOLLOW-UP:` source comment appears within twenty lines of that feature method

### Requirement: Per-resolver scenario coverage

Each `<ResolverClassSimpleName>Spec.groovy` SHALL cover, at minimum, the scenarios required by the corresponding `Requirement` in the `source-path-resolution` capability — specifically the resolver's positive-match scenarios and rejection scenarios. The spec SHALL exercise the resolver via the `ResolveCtxBuilder` test helper and the `TypeUniverse` substrate, consistent with the existing *Single-substrate javac invariant* and *ResolveCtxBuilder test helper* requirements.

For `GetterPathResolverSpec`, scenarios SHALL include: a positive match on a JavaBean `getX()` accessor, a positive match on an `isX()` accessor with boolean return, rejection of parameterized overloads, rejection of methods inherited from `java.lang.Object`, and rejection of non-declared parent types. The positive-match assertions SHALL pin `weight` equal to `Weights.STEP_GETTER`.

For `MethodPathResolverSpec`, scenarios SHALL include: a positive match on a canonical record component accessor, a positive match on a non-record fluent-style accessor, rejection of parameterized methods, rejection of methods inherited from `java.lang.Object`, and rejection of non-declared parent types. The positive-match assertions SHALL pin `weight` equal to `Weights.STEP_METHOD`. The spec SHALL NOT gate behaviour on `ElementKind.RECORD`.

For `FieldPathResolverSpec`, scenarios SHALL include: a positive match on a public field, rejection of private fields, and rejection of static fields. The positive-match assertions SHALL pin `weight` equal to `Weights.STEP_FIELD`.

#### Scenario: GetterPathResolverSpec covers JavaBean and boolean accessors
- **WHEN** `GetterPathResolverSpec.groovy` is inspected
- **THEN** the spec contains at least five feature methods covering: `getX` match, `isX` match, parameterized-overload rejection, Object-method rejection, non-declared-parent rejection
- **AND** the positive-match feature methods assert the returned `ExpansionStep`'s `weight` equals `Weights.STEP_GETTER`

#### Scenario: MethodPathResolverSpec covers record and non-record parents
- **WHEN** `MethodPathResolverSpec.groovy` is inspected
- **THEN** the spec contains at least five feature methods covering: canonical record accessor match, non-record fluent method match, parameterized-method rejection, Object-method rejection, non-declared-parent rejection
- **AND** the positive-match feature methods assert the returned `ExpansionStep`'s `weight` equals `Weights.STEP_METHOD`

#### Scenario: FieldPathResolverSpec covers visibility and modifiers
- **WHEN** `FieldPathResolverSpec.groovy` is inspected
- **THEN** the spec contains at least three feature methods covering: public-field match, private-field rejection, static-field rejection
- **AND** the positive-match feature method asserts the returned `ExpansionStep`'s `weight` equals `Weights.STEP_FIELD`

### Requirement: Conversion strategy unit spec presence and coverage

The two `type-conversion` built-ins SHALL each have a corresponding Spock specification following the established per-strategy pattern: `PrimitiveWrapperConversionSpec.groovy` and `WidenPrimitiveSpec.groovy`, each residing at `strategies-builtin/src/test/groovy/io/github/joke/percolate/spi/builtins/<SimpleName>Spec.groovy`, extending `spock.lang.Specification`, carrying `@spock.lang.Tag('unit')`, assembling its `ResolveCtx` through `ResolveCtxBuilder`, and sourcing every `TypeMirror` from the single `TypeUniverse` substrate.

Assertions SHALL be metadata-only per the existing *Assertion scope is metadata-only* requirement — specs SHALL assert on the emitted `ExpansionStep`'s `intent`, input type(s), output type, and weight, and on the presence/emptiness of the returned `Stream`. Specs SHALL NOT invoke codegen `render(...)` or pin `CodeBlock` output.

`PrimitiveWrapperConversionSpec` SHALL cover at minimum: a boxing happy path (target `Integer` ⇒ one `CONVERSION` step, input `int`, output `Integer`, weight `Weights.STEP`), an unboxing happy path (target `int` ⇒ input `Integer`, output `int`), and an empty-return precondition (target neither a wrapper nor a primitive with a wrapper).

`WidenPrimitiveSpec` SHALL cover at minimum: a widening happy path asserting the narrower-source steps emitted for a numeric target (e.g. target `long` ⇒ steps consuming `byte`, `short`, `char`, `int`, each output `long`, weight `Weights.STEP`), an IEEE precision-losing leg (e.g. a step consuming `long` to produce `double` exists), a `boolean`-target empty-return, and a narrowing empty-return (no step consuming `long` to produce `int`).

#### Scenario: conversion strategy specs are present and tagged
- **WHEN** `strategies-builtin/src/test/groovy/io/github/joke/percolate/spi/builtins/` is inspected
- **THEN** `PrimitiveWrapperConversionSpec.groovy` and `WidenPrimitiveSpec.groovy` are both present
- **AND** each extends `spock.lang.Specification` and carries `@spock.lang.Tag('unit')`

#### Scenario: PrimitiveWrapperConversionSpec covers boxing, unboxing, and a precondition
- **WHEN** `PrimitiveWrapperConversionSpec.groovy` is inspected
- **THEN** a feature method asserts that a wrapper target emits one `CONVERSION` step with the matching primitive input type and `Weights.STEP`
- **AND** a feature method asserts the unboxing direction (primitive target ⇒ wrapper input)
- **AND** a feature method asserts an empty `Stream` for a target that is neither a wrapper nor a primitive with a wrapper

#### Scenario: WidenPrimitiveSpec covers widening, an IEEE leg, and rejections
- **WHEN** `WidenPrimitiveSpec.groovy` is inspected
- **THEN** a feature method asserts the narrower-source `CONVERSION` steps emitted for a numeric target with output and weight pinned
- **AND** a feature method asserts a precision-losing IEEE leg is emitted (e.g. `long → double`)
- **AND** a feature method asserts an empty `Stream` for a `boolean` target
- **AND** a feature method asserts no step is emitted for a narrowing conversion (e.g. `long → int`)
