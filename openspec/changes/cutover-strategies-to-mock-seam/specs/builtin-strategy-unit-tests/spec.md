## ADDED Requirements

### Requirement: Builtin unit specs are mock-based over the ResolveCtx seam

Every per-strategy **unit** spec directly under `strategies-builtin/src/test/groovy/io/github/joke/percolate/spi/builtins/` (excluding the `e2e/` subpackage) SHALL exercise its strategy against a **Spock-mocked** `io.github.joke.percolate.spi.ResolveCtx`, stubbing the seam questions the strategy asks (`isList`/`isSet`/`isOptional`/`isAssignable`/`isSameType`/`membersOf`/`superclassOf`/`callableMethods`/…) per scenario. No unit spec SHALL construct a `com.sun.source.util.JavacTask`, a `com.google.testing.compile.Compiler`, a `TypeUniverse`, a `PrivateTypeUniverse`, or any `Types`/`Elements` pair — there SHALL be **no javac** on the unit path.

Every `javax.lang.model.type.TypeMirror` and `javax.lang.model.element.Element` a unit spec passes SHALL be an **opaque, never-stubbed token** (a bare `Mock()` or a distinct object identity): the spec SHALL NOT stub a `TypeMirror`/`Element` method to return type-internal behaviour, because the seam — not the mirror — answers every type question.

#### Scenario: No javac substrate in any builtin unit spec
- **WHEN** the imports of every per-strategy unit spec under `…/spi/builtins/` (excluding `e2e/`) are inspected
- **THEN** none imports `com.sun.source.util.JavacTask`, `com.google.testing.compile.Compiler`, `com.google.testing.compile.JavaFileObjects`, `io.github.joke.percolate.spi.test.TypeUniverse`, or `io.github.joke.percolate.spi.test.PrivateTypeUniverse`

#### Scenario: The seam is mocked and its questions are stubbed
- **WHEN** a builtin unit spec exercises its strategy
- **THEN** it holds a `ResolveCtx ctx = Mock()` and stubs the seam questions the scenario needs (e.g. `ctx.isList(type) >> true`)
- **AND** it asserts the returned `OperationSpec` metadata, never invoking `render(...)`

#### Scenario: TypeMirror is an opaque never-stubbed token
- **WHEN** a builtin unit spec passes a `TypeMirror` or `Element` to its strategy
- **THEN** that value is a bare `Mock()` (or a distinct identity) with no stubbed `TypeMirror`/`Element` method
- **AND** the type answers the strategy needs come from the mocked `ResolveCtx`, not from the mirror

### Requirement: Builtin strategies with hidden logic are decomposed into individually testable units

A built-in strategy that fuses a sub-algorithm or a matching decision behind a `private` member SHALL have that logic extracted into a package-private collaborator that is individually unit-testable over the mocked `ResolveCtx` seam. Specifically: `MethodCallBridge`'s subtype-distance walk SHALL be extracted to a `SubtypeDistance` collaborator whose surface is the seam (`isSameType`/`isAssignable`/`superclassOf`/`isDeclared`); `GetterPathResolver`'s getter / boolean-`is` matching predicates SHALL be extracted to an individually-testable form. Each extracted collaborator SHALL have its own `@Tag('unit')` mock-based spec. The extraction SHALL satisfy the `decompose-engine-stages` litmus (every method describable in one sentence without "and"; no `private` methods).

#### Scenario: SubtypeDistance is a separate collaborator unit-tested mock-only
- **WHEN** the `strategies-builtin` test tree is inspected
- **THEN** a `SubtypeDistanceSpec` exists that drives `SubtypeDistance` against a mocked `ResolveCtx`, with no javac
- **AND** `MethodCallBridge` no longer declares the `bfsDistance`/`subtypeDistance` private walk

#### Scenario: A matching predicate is isolable
- **WHEN** `GetterPathResolver`'s getter / boolean-`is` matching is unit-tested
- **THEN** it is asserted through a mocked seam over an opaque member token, without a real type hierarchy

### Requirement: The builtin unit suite is mutation-tested under threaded pitest

The `strategies-builtin` module SHALL run pitest mutation testing over its `@Tag('unit')` suite as part of `check`, threaded (`threads = availableProcessors()`), using the `pitest-history-plugin` for incremental analysis, with a mutation floor that tolerates measured run-to-run variance. pitest SHALL be scoped to the unit suite only and SHALL NOT run against the `e2e/` compile-test suite. This requirement SHALL be satisfied only after the decomposition above, so mutation coverage measures individually addressable units rather than a coarse seam.

#### Scenario: pitest runs on the strategies-builtin unit suite under check
- **WHEN** `check` runs for `strategies-builtin`
- **THEN** pitest executes against the `@Tag('unit')` specs and enforces the configured mutation floor

#### Scenario: pitest is scoped away from the e2e suite
- **WHEN** the `strategies-builtin` pitest configuration is inspected
- **THEN** it targets the unit suite and excludes the `…/spi/builtins/e2e/` compile tests

## MODIFIED Requirements

### Requirement: Mocking boundary

The `io.github.joke.percolate.spi.ResolveCtx` seam SHALL be Spock-mocked (`ResolveCtx ctx = Mock()`) in every strategy unit spec, and the seam questions the strategy asks SHALL be stubbed per scenario. `javax.lang.model.util.Types` and `javax.lang.model.util.Elements` SHALL NOT appear in any strategy unit spec at all — there is no javac on the unit path, so there is nothing to mock or supply.

`javax.lang.model.type.TypeMirror` and `javax.lang.model.element.Element` values SHALL be opaque pass-through tokens (bare `Mock()` or distinct identities) and SHALL NOT be stubbed to return type-internal behaviour. `io.github.joke.percolate.spi.CallableMethods` MAY be Spock-mocked when a spec controls the callable-method index, or stubbed directly on the mocked `ResolveCtx` via `ctx.callableMethods() >> …`.

The strategy under test (`DirectAssign`, `WidenPrimitive`, `OptionalContainer`, etc.) SHALL NOT be mocked, stubbed, partially-mocked, or spied. It SHALL be constructed via its public no-args constructor and exercised through its public interface methods.

#### Scenario: The seam is mocked, platform types are absent
- **WHEN** the imports and mocks of any strategy unit spec are inspected
- **THEN** the `ResolveCtx` seam is a `Mock()` whose questions are stubbed, and no `Mock(Types)`, `Mock(Elements)`, `Stub(Types)`, or `Stub(Elements)` appears — nor any `Types`/`Elements` reference

#### Scenario: Strategy under test is plainly constructed
- **WHEN** any strategy spec invokes the strategy under test
- **THEN** the invocation site reads `new <StrategyClass>().<method>(...)` or holds a reference produced by such a call — no `Spy()`, `Mock()`, or `Stub()` wrapping the strategy itself

### Requirement: Pinning policy for current-behaviour findings

Where a unit spec discovers behaviour that is unintentional or worth questioning but lies outside the scope of the current change to fix, the spec SHALL pin the current behaviour with a feature method whose name signals the pinning intent. Adjacent to the feature method's `then:` block, the spec SHALL place a single-line source comment of the form `// FOLLOW-UP: <one-line summary>` so that the audit trail is discoverable by the next maintainer reading the test.

The `MethodCallBridge.subtypeDistance` finding — that `subtypeDistance` returns `0` for **both** same-type and non-assignable inputs — SHALL be carried forward unchanged, now pinned **mock-only** in `SubtypeDistanceSpec` (against a stubbed `ResolveCtx`) rather than driven through a real type hierarchy in `MethodCallBridgeSpec`. (Findings tied to the retired per-operation container strategies are not carried forward.)

#### Scenario: The subtype-distance finding is pinned mock-only
- **WHEN** `SubtypeDistanceSpec` is inspected
- **THEN** at least one feature method demonstrates that the distance is `0` for both same-type and non-assignable inputs, stubbing those answers on a mocked `ResolveCtx`, and pins the resulting `weight` on the `OperationSpec` built by `MethodCallBridge`
- **AND** a `// FOLLOW-UP:` source comment appears within twenty lines of that feature method

### Requirement: Per-resolver scenario coverage

Each `<ResolverClassSimpleName>Spec.groovy` SHALL cover, at minimum, the scenarios required by the corresponding `Requirement` in the `source-path-resolution` capability — specifically the resolver's positive-match scenarios and rejection scenarios. The spec SHALL exercise the resolver against a **mocked `ResolveCtx`** seam, stubbing the member-reflection questions (`membersOf`, `isMethod`, `isField`, `kind`, `qualifiedName`, …) over opaque member tokens; it SHALL use no javac, `TypeUniverse`, or `ResolveCtxBuilder`.

For `GetterPathResolverSpec`, scenarios SHALL include: a positive match on a JavaBean `getX()` accessor, a positive match on an `isX()` accessor with boolean return, rejection of parameterized overloads, rejection of methods inherited from `java.lang.Object`, and rejection of non-declared parent types. The positive-match assertions SHALL pin `weight` equal to `Weights.STEP_GETTER`.

For `MethodPathResolverSpec`, scenarios SHALL include: a positive match on a canonical record component accessor, a positive match on a non-record fluent-style accessor, rejection of parameterized methods, rejection of methods inherited from `java.lang.Object`, and rejection of non-declared parent types. The positive-match assertions SHALL pin `weight` equal to `Weights.STEP_METHOD`. The spec SHALL NOT gate behaviour on `ElementKind.RECORD`.

For `FieldPathResolverSpec`, scenarios SHALL include: a positive match on a public field, rejection of private fields, and rejection of static fields. The positive-match assertions SHALL pin `weight` equal to `Weights.STEP_FIELD`.

#### Scenario: GetterPathResolverSpec covers JavaBean and boolean accessors
- **WHEN** `GetterPathResolverSpec.groovy` is inspected
- **THEN** the spec contains at least five feature methods covering: `getX` match, `isX` match, parameterized-overload rejection, Object-method rejection, non-declared-parent rejection
- **AND** the positive-match feature methods assert the returned `OperationSpec`'s `weight` equals `Weights.STEP_GETTER`
- **AND** the resolver is driven against a mocked `ResolveCtx`, not a real type hierarchy

#### Scenario: MethodPathResolverSpec covers record and non-record parents
- **WHEN** `MethodPathResolverSpec.groovy` is inspected
- **THEN** the spec contains at least five feature methods covering: canonical record accessor match, non-record fluent method match, parameterized-method rejection, Object-method rejection, non-declared-parent rejection
- **AND** the positive-match feature methods assert the returned `OperationSpec`'s `weight` equals `Weights.STEP_METHOD`

#### Scenario: FieldPathResolverSpec covers visibility and modifiers
- **WHEN** `FieldPathResolverSpec.groovy` is inspected
- **THEN** the spec contains at least three feature methods covering: public-field match, private-field rejection, static-field rejection
- **AND** the positive-match feature method asserts the returned `OperationSpec`'s `weight` equals `Weights.STEP_FIELD`

### Requirement: Conversion strategy unit spec presence and coverage

The two `type-conversion` built-ins SHALL each have a corresponding Spock specification following the established per-strategy pattern: `PrimitiveWrapperConversionSpec.groovy` and `WidenPrimitiveSpec.groovy`, each residing at `strategies-builtin/src/test/groovy/io/github/joke/percolate/spi/builtins/<SimpleName>Spec.groovy`, extending `spock.lang.Specification`, carrying `@spock.lang.Tag('unit')`, exercising its strategy against a **mocked `ResolveCtx`** seam (stubbing `isPrimitive`/`boxed`/`unboxed`/`kind`/`asTypeElement`/… as the scenario needs) over opaque `TypeMirror` tokens, with no javac substrate.

Assertions SHALL be metadata-only per the *Assertion scope is OperationSpec metadata only* requirement — specs SHALL assert on the emitted `OperationSpec`'s port type(s), output type, and weight, and on the presence/emptiness of the returned `Stream`. Specs SHALL NOT invoke codegen `render(...)` or pin `CodeBlock` output.

`PrimitiveWrapperConversionSpec` SHALL cover at minimum: a boxing happy path (target `Integer` ⇒ one `OperationSpec`, port `int`, output `Integer`, weight `Weights.STEP`), an unboxing happy path (target `int` ⇒ port `Integer`, output `int`), and an empty-return precondition (target neither a wrapper nor a primitive with a wrapper).

`WidenPrimitiveSpec` SHALL cover at minimum: a widening happy path asserting the narrower-source specs emitted for a numeric target (e.g. target `long` ⇒ one `OperationSpec` per `byte`, `short`, `char`, `int`, each output `long`, weight `Weights.STEP`), an IEEE precision-losing leg (e.g. a spec consuming `long` to produce `double` exists), a `boolean`-target empty-return, and a narrowing empty-return (no spec consuming `long` to produce `int`).

#### Scenario: conversion strategy specs are present and tagged
- **WHEN** `strategies-builtin/src/test/groovy/io/github/joke/percolate/spi/builtins/` is inspected
- **THEN** `PrimitiveWrapperConversionSpec.groovy` and `WidenPrimitiveSpec.groovy` are both present
- **AND** each extends `spock.lang.Specification`, carries `@spock.lang.Tag('unit')`, and drives its strategy against a mocked `ResolveCtx`

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

**Reason**: The unit specs are now mock-based over the `ResolveCtx` seam (see the ADDED "Builtin unit specs are mock-based over the ResolveCtx seam"), so there is **no** javac on the unit path — no `JavacTask`, no shared `Types`/`Elements`, no `TypeUniverse`. A single-substrate invariant governing `TypeMirror` provenance is therefore vacuous. (The `e2e/` compile suite it always excluded is untouched, and the surviving `processor` compiler-boundary specs are governed by `expansion-test-harness`, not this capability.)

**Migration**: Replace every `TypeUniverse.of(...)`/`ResolveCtxBuilder`-sourced `TypeMirror` with an opaque `Mock()` token and stub the type answer on the mocked `ResolveCtx` (e.g. `ctx.isAssignable(a, b) >> true`).

### Requirement: ResolveCtxBuilder test helper

**Reason**: `ResolveCtxBuilder` constructed a `ResolveCtx` over a real `Types`/`Elements` pair; unit specs now mock `ResolveCtx` directly. The helper (and `ResolveCtxBuilderSpec`) is deleted.

**Migration**: Replace `new ResolveCtxBuilder().build()` with `ResolveCtx ctx = Mock()`; replace `.withCallableMethods(mock)` with `ctx.callableMethods() >> mock` (or a `Mock(CallableMethods)` stubbed on the seam).

### Requirement: Shape fixtures for ConstructorCall and path resolvers

**Reason**: The Java shape fixtures existed only so specs could resolve real record/JavaBean/field/constructor shapes through a live javac. With member reflection now stubbed on the mocked seam, no real classpath types are needed; the fixtures under `strategies-builtin/src/test/java/.../fixtures/` are deleted.

**Migration**: Stub the member-reflection questions on the mocked `ResolveCtx` over opaque `Element`/`ExecutableElement` tokens — e.g. `ctx.membersOf(typeElement) >> Stream.of(ctorToken)`, `ctx.isConstructor(ctorToken) >> true`, `ctx.isMethod(memberToken) >> true` — shaping exactly the member set each scenario requires.
