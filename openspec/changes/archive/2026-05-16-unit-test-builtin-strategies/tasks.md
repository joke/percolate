## 1. Preflight

- [x] 1.1 Confirm `extract-spi-and-builtins` has been applied: `percolate-strategies-builtin` exists, depends on `percolate-spi`, and its `build.gradle` declares `testImplementation testFixtures(project(':spi'))` plus Spock + Mockito + byte-buddy + junit-platform-launcher on the test classpath
- [x] 1.2 Confirm `percolate-spi`'s `testFixtures` configuration publishes `TypeUniverse` and `HarnessResolveCtx` and that both resolve from `strategies-builtin`'s test sources

## 2. Test helpers

- [x] 2.1 Create `strategies-builtin/src/test/groovy/io/github/joke/percolate/spi/builtins/test/ResolveCtxBuilder.groovy` with defaults sourced from `TypeUniverse` and fluent `withCallableMethods` / `withMapperType` / `withCurrentMethod` overrides, per design §D3 and spec requirement "ResolveCtxBuilder test helper"
- [x] 2.2 Write `ResolveCtxBuilderSpec.groovy` covering the spec's two scenarios: default ctx matches `HarnessResolveCtx`, and `withCallableMethods` override is honoured
- [x] 2.3 Run `./gradlew :strategies-builtin:test --tests '*ResolveCtxBuilderSpec'` and confirm green before proceeding

## 3. Shape fixtures

- [x] 3.1 Create `strategies-builtin/src/test/java/io/github/joke/percolate/spi/builtins/fixtures/PersonRecord.java` as `record PersonRecord(int age, String name) {}`
- [x] 3.2 Create `strategies-builtin/src/test/java/io/github/joke/percolate/spi/builtins/fixtures/PersonBean.java` with paired `getName` / `setName` and `getAge` / `setAge`
- [x] 3.3 Create `strategies-builtin/src/test/java/io/github/joke/percolate/spi/builtins/fixtures/BooleanBean.java` exposing `isFlag()` returning `boolean`
- [x] 3.4 Create `strategies-builtin/src/test/java/io/github/joke/percolate/spi/builtins/fixtures/PersonByFieldOrder.java` whose constructor parameters are positional (e.g. `(int, String)`) but whose fields carry named identifiers (`age`, `name`)
- [x] 3.5 Add a smoke spec (or extend an existing helper spec) confirming `TypeUniverse.element('…fixtures.PersonRecord')` returns a non-null `TypeElement`; if it returns null, fix `TypeUniverse`'s file manager to expose the test classpath (per design §D2 — do NOT introduce a second `JavacTask`)

## 4. Simple bridges (no element seeds)

- [x] 4.1 Write `DirectAssignSpec.groovy` covering the same-type happy path (assert `inputType`, `outputType`, `weight == Weights.NOOP`, empty element seeds), plus an empty-return scenario for distinct types
- [x] 4.2 Write `OptionalUnwrapSpec.groovy` covering the `Optional<E>` happy path (assert `inputType`, `outputType == E`, `weight == Weights.CONTAINER`), plus empty-return scenarios for non-Optional input and (if applicable) Optional with mismatched downstream

## 5. Container bridges with element seeds

- [x] 5.1 Write `OptionalWrapSpec.groovy` covering the wrap happy path, asserting the produced `BridgeStep.elementSeeds` carries a single seed with correct inner-from / inner-to types
- [x] 5.2 Write `OptionalMapSpec.groovy` covering its precondition checks plus a happy-path scenario asserting element-seed shape
- [x] 5.3 Write `ListWrapSpec.groovy` covering the wrap happy path and element-seed shape
- [x] 5.4 Write `ListMapSpec.groovy` covering: (a) empty when target is not a `List`, (b) empty when source shape is unsupported, (c) happy path for `Iterable<E>` input with element-seed assertion, (d) happy path for array input with element-seed assertion, (e) `// FOLLOW-UP:` pinning scenario for `Optional<E>` input acceptance
- [x] 5.5 Write `SetWrapSpec.groovy` covering the wrap happy path and element-seed shape
- [x] 5.6 Write `SetMapSpec.groovy` covering its precondition checks plus a happy-path scenario asserting element-seed shape

## 6. Introspection-heavy strategies (use shape fixtures)

- [x] 6.1 Write `GetterReadSpec.groovy` covering: empty when `pathTail` is null/empty, empty when source kind is not declared, happy path through `getX` accessor (`PersonBean`), `isX` accessor branch (`BooleanBean`), and field-named-only accessor branch — each asserting returned `Step.type` and `weight == Weights.STEP`
- [x] 6.2 Write `ConstructorCallSpec.groovy` covering: empty when `targetTails` is empty/null, empty when target type is not a `DeclaredType`, happy path through constructor-by-parameter-name (`PersonRecord`), happy path through constructor-by-arity-and-fields (`PersonByFieldOrder`), and `GroupBuild` slot shape (names, types, weights)

## 7. CallableMethods-driven strategy

- [x] 7.1 Write `MethodCallBridgeSpec.groovy` using Spock `Mock(CallableMethods)` configured per scenario: empty when `callableMethods()` returns null or produces an empty stream, happy path for a single-parameter method whose return type is assignable to the target, exact-type-match scenario, and a `// FOLLOW-UP:` pinning scenario covering `subtypeDistance == 0` for both same-type and non-assignable inputs (asserting on the resulting `weight`)

## 8. Wire-up checks

- [x] 8.1 Verify the eleven required specs all exist and carry `@spock.lang.Tag('unit')` (spec requirement "Per-strategy unit spec presence")
- [x] 8.2 Verify no spec under `strategies-builtin/src/test/groovy/io/github/joke/percolate/spi/builtins/` imports `com.sun.source.util.JavacTask`, `com.google.testing.compile.*`, or invokes `Mockito.mock(Types|Elements)` / `Mock(Types|Elements)` / `Stub(Types|Elements)` (spec requirements "Single-substrate javac invariant" and "Mocking boundary")
- [x] 8.3 Verify no spec imports `com.palantir.javapoet.CodeBlock` or invokes `EdgeCodegen.render` / `GroupCodegen.render` / `CodeBlock.toString()` (spec requirement "Assertion scope is metadata-only in this change")

## 9. Verify

- [x] 9.1 Run `./gradlew check` from the repo root and confirm all checks green: every new spec passes, no Spotless / NullAway / Errorprone violations introduced, existing `processor`-module algebraic and failure-mode specs still pass, `BuiltinServiceRegistrationSpec` still passes. NEVER continue if there are violations.
