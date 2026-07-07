## MODIFIED Requirements

### Requirement: Unit tests mock the ResolveCtx seam

Every `processor` and `spi` unit spec (`@Tag('unit')`) SHALL exercise its subject against a **mocked
`ResolveCtx`** — a Spock `Mock()` stubbing only the seam questions its subject asks — with **mocked
collaborators** for the subject's injected seams, and a **`Spy()` on the subject stubbing only a genuinely
self-recursive call** where a method recurses into its own type structure (unification, substitution).
`processor` SHALL contain **no `FakeResolveCtx`/`FakeType`**: once `Grounding` and `ExpandStage.Driver` are
decomposed into single-method collaborators, no `processor` unit spec drives a whole-pipeline pass, so none
needs a structural fake substrate. A hand-written, javac-free `FakeResolveCtx`/`FakeType` MAY remain only
for the small number of **`spi`** specs pinning the seam's own default-method composition (`ResolveCtxSpec`,
`ContainerSpec`), pending `spi`'s own decomposition. Either way, any `javax.lang.model` value is passed
through as a **never-stubbed opaque token** — the `ValidateNoDuplicateTargetsStageSpec` pattern. There SHALL
be **no javac in the unit path**: a unit spec SHALL NOT construct a `JavacTask`, compile a `@Mapper`, or
stand up any shared type substrate, so the suite is parallel-safe by construction. Specs SHALL be
**example-based Spock** (`where:` tables for algebra/laws); **jqwik SHALL NOT be used**.

Most specs stub only the 1-2 seam questions their subject asks (e.g. `ListContainerSpec`,
`SourceCandidatesSpec`, `AccessorSpec`). A decomposed algorithm such as `Grounding`'s unification is tested
by mocking its extracted collaborators (`Unifier`, `BindingEnumerator`, `SpecInstantiator`) and spying only
its irreducible self-recursion — not through a `FakeResolveCtx`.

#### Scenario: A processor unit spec mocks the seam and collaborators, adds no javac, and uses no fake
- **WHEN** a `processor` unit spec drives its subject
- **THEN** it constructs `ResolveCtx ctx = Mock()` (stubbing only the seam questions the subject asks) and mocks the subject's injected collaborators, never constructs a `JavacTask` or compiles a `@Mapper`, and imports no `FakeResolveCtx`/`FakeType`

#### Scenario: Self-recursion is isolated with a spy
- **WHEN** a decomposed engine method recurses into its own type structure (unification or substitution)
- **THEN** its spec spies the subject and stubs the recursive call, rather than exercising the full recursion or standing up a fake type-world

#### Scenario: Unit specs are example-based Spock
- **WHEN** the unit specs are inspected
- **THEN** no spec imports `net.jqwik`; property-shaped cases are covered by `where:` tables

### Requirement: Engine unit tests mock the seam; no shared type-system fixture module

Every `processor` and `spi` unit test SHALL exercise its subject against a mocked or faked `ResolveCtx`
(see "Unit tests mock the ResolveCtx seam"), needing no shared **javac** type-system fixture. No published
Gradle module SHALL exist whose sole purpose is to host engine-test infrastructure. `spi` SHALL export no
`testFixtures` type whose purpose is a shared javac substrate — `HarnessResolveCtx` (which handed strategy
specs a `ResolveCtx` backed by real `Types`/`Elements`) is deleted, and the shared-static **`TypeUniverse`
fixture is now also deleted** (see the REMOVED "TypeUniverse fixture"), its last code consumers — the
`strategies-builtin` unit specs — having migrated to the mocked seam. The per-spec `PrivateTypeUniverse`
`testFixtures` export **remains**, scoped to the `processor` compiler-boundary specs (`discover`,
`AssembleMapperType`) that genuinely need real mirrors.

Engine-bound helpers that a **compile-based** engine/e2e test still needs (`HarnessScope`, `TestFiler`, and the
Java `fixtures`) MAY remain co-located under `processor/src/test/` because they depend on engine internals
(`MapperContext`, `Diagnostics`, `ProcessorModule`, `MapperGraph`, `Scope`); they SHALL NOT be exported by any
other module. The e2e/doc compile tests continue to exercise the seam's real-javac implementation
(`CompileResolveCtx`) end-to-end.

#### Scenario: No separate test-support module
- **WHEN** `settings.gradle` is inspected
- **THEN** it does NOT include any module whose sole purpose is to host test infrastructure for the expansion engine

#### Scenario: HarnessResolveCtx and TypeUniverse are gone; PrivateTypeUniverse remains for the processor boundary
- **WHEN** `spi`'s `testFixtures` are inspected
- **THEN** neither `io.github.joke.percolate.spi.test.HarnessResolveCtx` nor `io.github.joke.percolate.spi.test.TypeUniverse` exists
- **AND** `io.github.joke.percolate.spi.test.PrivateTypeUniverse` still exists, consumed only by the `processor` `discover`/`AssembleMapperType` boundary specs

#### Scenario: Compile-bound engine helpers stay next to the engine tests
- **WHEN** the source tree is inspected
- **THEN** any `HarnessScope`, `TestFiler`, or Java `fixtures` a compile-based engine/e2e test needs live under `processor/src/test/` and are exported by no other module

## REMOVED Requirements

### Requirement: TypeUniverse fixture

**Reason**: The shared-static `TypeUniverse` (one JVM-lifetime `JavacTask`) existed to give the
`strategies-builtin` unit specs a real type world. Those specs now mock the `ResolveCtx` seam, removing
every code consumer, so `TypeUniverse` and its self-test `TypeUniverseSpec` are deleted from `spi`'s
`testFixtures`. This lands notes.md's original "delete `TypeUniverse`" goal that `type-query-seam` deferred.

**Migration**: `strategies-builtin` unit specs mock the seam (see `builtin-strategy-unit-tests`). The
`processor` compiler-boundary specs that need real mirrors use the surviving `PrivateTypeUniverse` (per-spec
javac), which is code-independent of `TypeUniverse` (its `{@link TypeUniverse}` javadoc references are
re-worded on deletion).

### Requirement: Type resolution by Class literal

**Reason**: This requirement specified `TypeUniverse.of(Class<?>)` and its `pool()`/constant hygiene. With
`TypeUniverse` deleted, it no longer has a subject.

**Migration**: Mock-based specs pass opaque `TypeMirror`/`Element` tokens (no `Class`-literal resolution
needed). The `processor` boundary specs resolve fixture types through `PrivateTypeUniverse`'s own API.
