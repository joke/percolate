## ADDED Requirements

### Requirement: Discovery stages separate javax reading from pure logic

The discovery stages (`DiscoverAbstractMethodsStage`, `DiscoverMappingsStage`, `DiscoverCallableMethodsStage`) SHALL each be decomposed so that the raw `javax.lang.model` read (member enumeration via `getLocalAndInheritedMethods`/`getAllMembers`, `@Map`/`@MapList` `AnnotationMirror` reading) lives in a **thin reader** collaborator, and the stage's **pure decision logic** (`Map.UNSET`-sentinel directive presence, `@MapList` ordering, `MappingDirective` assembly, `isAbstract`/`isObjectMethod` filtering, single-parameter/return-type filtering and assignability) lives in a collaborator unit-testable **without javac**. The pure core SHALL be exercised on **plain data** (raw strings, plain method/candidate descriptors), passing any `javax.lang.model` value (`AnnotationValue`, `TypeMirror`, `Element`) through as a **never-stubbed opaque token**. The thin reader SHALL be covered by the compile-based feature-e2e layer (real `CompileResolveCtx`), not by a unit-test javac substrate. No discovery-stage unit spec SHALL construct a `JavacTask`, a `PrivateTypeUniverse`, or a `FakeResolveCtx`/`FakeType`.

#### Scenario: A discovery stage's pure core is unit-tested on plain data

- **WHEN** a discovery stage's pure decision logic (e.g. `Map.UNSET` presence, method filtering, assignability) is unit-tested
- **THEN** the spec drives the pure collaborator with plain data, constructs no `JavacTask`/`PrivateTypeUniverse`/`FakeType`, and treats any passed `AnnotationValue`/`TypeMirror`/`Element` as an opaque never-stubbed token

#### Scenario: A discovery stage's javax reader is covered by compile e2e

- **WHEN** the thin javax reader (member enumeration, `@Map`/`@MapList` mirror reading) is exercised
- **THEN** it is covered by the compile-based feature-e2e layer compiling a real `@Mapper` through `CompileResolveCtx`, with no unit-test javac substrate

## MODIFIED Requirements

### Requirement: Codegen unit tests need no shared javac substrate

The codegen stages (`AssembleMapperType`, `BuildMethodBodies`) are decomposed, and their pure assembly logic SHALL be unit-tested against mocked seams. The only residue requiring a genuinely compiler-backed `TypeMirror` — a JavaPoet `TypeName.get(mirror)` rendering leaf — SHALL be covered by the compile-based feature-e2e layer (driving the real `CompileResolveCtx`), not by any unit-test javac substrate. `PrivateTypeUniverse` is **removed outright**: no `processor` unit spec — engine-logic or codegen-boundary — SHALL construct a `PrivateTypeUniverse`, a `JavacTask`, or any `Types`/`Elements` pair.

#### Scenario: No processor unit spec constructs PrivateTypeUniverse

- **WHEN** the `processor` unit specs (`@Tag('unit')`) are inspected
- **THEN** none of them imports or constructs `io.github.joke.percolate.spi.test.PrivateTypeUniverse`, and none stands up a `JavacTask`
- **AND** the real-mirror `TypeName.get(mirror)` rendering leaf is exercised only by the compile-based feature-e2e layer

#### Scenario: Codegen assembly logic is mock-tested

- **WHEN** a decomposed codegen unit (an extracted method-signature or method-body assembler) is unit-tested
- **THEN** it is exercised against mocked seams, with real-mirror rendering left to the feature-e2e compile tests

### Requirement: Engine unit tests mock the seam; no shared type-system fixture module

Every `processor` and `spi` unit test SHALL exercise its subject against a mocked or faked `ResolveCtx`
(see "Unit tests mock the ResolveCtx seam"), needing no shared **javac** type-system fixture. No published
Gradle module SHALL exist whose sole purpose is to host engine-test infrastructure. `spi` SHALL export no
`testFixtures` type whose purpose is a javac substrate: `HarnessResolveCtx` (which handed strategy specs a
`ResolveCtx` backed by real `Types`/`Elements`) is deleted, the shared-static `TypeUniverse` fixture is
deleted, and the per-spec **`PrivateTypeUniverse` fixture is now also deleted** — its last consumers, the
four `processor` compiler-boundary specs (`DiscoverAbstractMethodsStageSpec`, `DiscoverMappingsStageSpec`,
`DiscoverCallableMethodsStageSpec`, `AssembleMapperTypeSpec`), having migrated to the mocked/faked seam,
with the sole genuinely-compiler-backed `TypeName.get(mirror)` leaf covered by the compile-based
feature-e2e layer instead. `spi`'s `testFixtures` export **no** javac-backed type after this change.

Engine-bound helpers that a **compile-based** engine/e2e test still needs (`HarnessScope`, `TestFiler`, and the
Java `fixtures`) MAY remain co-located under `processor/src/test/` because they depend on engine internals
(`MapperContext`, `Diagnostics`, `ProcessorModule`, `MapperGraph`, `Scope`); they SHALL NOT be exported by any
other module. The e2e/doc compile tests continue to exercise the seam's real-javac implementation
(`CompileResolveCtx`) end-to-end.

#### Scenario: No separate test-support module

- **WHEN** `settings.gradle` is inspected
- **THEN** it does NOT include any module whose sole purpose is to host test infrastructure for the expansion engine

#### Scenario: HarnessResolveCtx, TypeUniverse, and PrivateTypeUniverse are all gone

- **WHEN** `spi`'s `testFixtures` are inspected
- **THEN** none of `io.github.joke.percolate.spi.test.HarnessResolveCtx`, `io.github.joke.percolate.spi.test.TypeUniverse`, or `io.github.joke.percolate.spi.test.PrivateTypeUniverse` exists
- **AND** `spi`'s `testFixtures` export no javac-backed (`JavacTask`/`Types`/`Elements`) type at all

#### Scenario: Compile-bound engine helpers stay next to the engine tests

- **WHEN** the source tree is inspected
- **THEN** any `HarnessScope`, `TestFiler`, or Java `fixtures` a compile-based engine/e2e test needs live under `processor/src/test/` and are exported by no other module
