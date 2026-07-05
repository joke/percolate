## RENAMED Requirements

- FROM: `### Requirement: Engine tests live in `processor/src/test/`; type-system fixtures live in `spi` testFixtures`
- TO: `### Requirement: Engine unit tests mock the seam; no shared type-system fixture module`

## ADDED Requirements

### Requirement: Unit tests mock the ResolveCtx seam

Every `processor` and `spi` unit spec (`@Tag('unit')`) SHALL exercise its subject against a **mocked
`ResolveCtx`** (a Spock `Mock()`), stubbing only the **1–2** type questions that subject asks and passing any
`javax.lang.model` value through as a **never-stubbed opaque token** — the `ValidateNoDuplicateTargetsStageSpec`
pattern. There SHALL be **no javac in the unit path**: a unit spec SHALL NOT construct a `JavacTask`, compile a
`@Mapper`, or stand up any shared type substrate, so the suite is parallel-safe by construction. Specs SHALL be
**example-based Spock** (`where:` tables for algebra/laws); **jqwik SHALL NOT be used**.

#### Scenario: A rewritten unit spec mocks the seam and adds no javac
- **WHEN** a rewritten `processor` or `spi` unit spec drives its subject
- **THEN** it constructs `ResolveCtx ctx = Mock()`, stubs only the 1–2 seam questions the subject asks, and never constructs a `JavacTask` or compiles a `@Mapper`

#### Scenario: Unit specs are example-based Spock
- **WHEN** the rewritten unit specs are inspected
- **THEN** no spec imports `net.jqwik`; property-shaped cases are covered by `where:` tables

## REMOVED Requirements

### Requirement: TypeUniverse fixture

**Reason**: The shared static `TypeUniverse` (a JVM-lifetime `JavacTask`) is the thread-hostile substrate the
narrow `ResolveCtx` seam eliminates. With type questions routed through a mockable seam, unit specs stub the
1–2 questions their subject asks instead of resolving real mirrors, so no shared javac fixture is needed and the
`@Isolated` / `threads = 1` race workaround is deleted, not bridged.

**Migration**: Replace `TypeUniverse`-backed specs with a mocked `ResolveCtx` (`ResolveCtx ctx = Mock()`),
stubbing the seam questions the subject asks and passing any `TypeMirror` through as a never-stubbed opaque
token. The transitional per-spec `PrivateTypeUniverse` scaffold remains **only** for the `strategies-builtin`
specs deferred to `features-as-documentation` (#3); the shared static `TypeUniverse` and its `TypeUniverseSpec`
are deleted.

### Requirement: HarnessResolveCtx fixture

**Reason**: `HarnessResolveCtx` existed only to hand strategy specs a `ResolveCtx` backed by the shared
`TypeUniverse`'s real `Types`/`Elements`. Once unit specs mock the seam, a real-javac-backed `ResolveCtx`
fixture has no consumer in the `processor`/`spi` unit path.

**Migration**: Use `ResolveCtx ctx = Mock()` and stub the seam directly; delete `HarnessResolveCtx` and its
`testFixtures` export.

### Requirement: Type resolution by Class literal

**Reason**: `TypeUniverse.of(Class)` and `element(String)` are members of the deleted shared `TypeUniverse`
fixture; they vanish with it.

**Migration**: None — unit specs no longer resolve types from `Class` literals or names; they stub seam answers
on a mocked `ResolveCtx`.

## MODIFIED Requirements

### Requirement: Engine unit tests mock the seam; no shared type-system fixture module

Every `processor` and `spi` unit test SHALL exercise its subject against a mocked `ResolveCtx`, needing **no**
shared type-system fixture. No published Gradle module SHALL exist whose sole purpose is to host engine-test
infrastructure, and `spi` SHALL export no `testFixtures` type whose purpose is a shared javac substrate
(`TypeUniverse`, `HarnessResolveCtx`).

Engine-bound helpers that a **compile-based** engine/e2e test still needs (`HarnessScope`, `TestFiler`, and the
Java `fixtures`) MAY remain co-located under `processor/src/test/` because they depend on engine internals
(`MapperContext`, `Diagnostics`, `ProcessorModule`, `MapperGraph`, `Scope`); they SHALL NOT be exported by any
other module. The e2e/doc compile tests continue to exercise the seam's real-javac implementation
(`CompileResolveCtx`) end-to-end.

#### Scenario: No separate test-support module and no shared type fixture
- **WHEN** `settings.gradle` and `spi`'s `testFixtures` are inspected
- **THEN** no module exists solely to host expansion-engine test infrastructure, and `spi` exports no shared javac substrate (`TypeUniverse`, `HarnessResolveCtx`)

#### Scenario: Compile-bound engine helpers stay next to the engine tests
- **WHEN** the source tree is inspected
- **THEN** any `HarnessScope`, `TestFiler`, or Java `fixtures` a compile-based engine/e2e test needs live under `processor/src/test/` and are exported by no other module
