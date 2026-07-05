## 1. Phase 0 — Scaffold & withdraw

- [x] 1.1 Create the change via `opsx:new` and generate proposal/design/specs/tasks (this artifact set)
- [x] 1.2 Record the supersession: mark `evict-javax-model` withdrawn in `openspec/notes.md` open-topics, pointing to `type-query-seam`
- [x] 1.3 Strike the last jqwik reference from the live `openspec/specs/expansion-test-harness/spec.md`; confirm `grep -ri jqwik openspec/specs/` shows no standing jqwik mandate
- [x] 1.4 Confirm the `project_evict_javax_model` and `project_typeuniverse_concurrency` memories record the pivot to this change

## 2. Phase 1 — Revert the owned model to the javax-native baseline

- [x] 2.1 Delete the owned `spi …/types` package (`TypeRef`, `TypeSpace`, `TypeDecl`, `MethodSig`, `FieldSig`, `ParamSig`, `DeclKind`, `MemberFlag`, `Origin`, `TypeRefs`, `TypeNames`) and `spi …/test/TestTypes`
- [x] 2.2 Delete `processor …/discover/TypeSpaceAdapter`
- [x] 2.3 Un-dual-type `Port` / `OperationSpec` / `Demand`: drop the `typeRef` / `outputTypeRef` / `targetTypeRef` fields, restoring the `TypeMirror`-native shape
- [x] 2.4 Restore `PortType` and the pre-fold `Grounding`
- [x] 2.5 Restore `TypeMirror`-keyed `Value.id()` / `MapperGraph.valueKey` / `MethodScope` / `SelfCallGuard`, and remove the transitional `ResolveCtx.typeSpace()` bridge
- [x] 2.6 Keep `PrivateTypeUniverse` and the non-owned-model `strategies-builtin` PrivateTypeUniverse migrations intact; owned-model-coupled specs reverted to their `main` baseline (rewritten later in Phases 4–5 / #3), with `HarnessResolveCtx`/`ResolveCtxBuilder` carrying both substrate modes transitionally
- [x] 2.7 Gate: full `check` green on the restored javax-native baseline (tests + pitest 95% line / 86% mutation + PMD/spotless/coverage)

## 3. Phase 2 — SPIKE (go/no-go), one vertical slice

- [x] 3.1 Introduce the type-query seam on `ResolveCtx` (`isSameType`, `isList` as default methods over `types()`/`elements()`), `TypeMirror` an opaque token — full narrowing (removing `types()`/`elements()`) rolls out in Phase 3
- [x] 3.2 `CompileResolveCtx` backs the seam via real javac (through the `types()`/`elements()` the defaults delegate to); explicit per-method overrides land in Phase 3
- [x] 3.3 Route the engine stage `SourceCandidates.matches` through the seam (`ctx.isSameType`)
- [x] 3.4 Route the strategy `ListContainer.matches` through the seam (`ctx.isList`); `Containers`/`TypeProbe` full carve-over rolls out in Phase 3
- [x] 3.5 Rewrite `SourceCandidatesSpec` fully mock-only (real in-memory graph, seam stubbed, opaque tokens, no javac) and add `ListContainerSeamSpec` mock-only; each stubs 1 seam question with zero javac
- [x] 3.6 Full `check` green: pitest clean & deterministic on the routed slice (processor 95% line / 86% mutation), no `@Isolated` — **go/no-go gate = GO**

## 4. Phase 3 — Seam across all production + ArchUnit confinement

- [x] 4.1 Route every remaining engine type-interrogation site (graph / stages / plan) through the seam
- [x] 4.2 Route every remaining strategy type-interrogation site (`strategies-builtin`, `reactor`, `reactor-blocking`) through the seam; finish the `Containers` / `TypeProbe` carving
- [x] 4.3 Delete all direct `Types` / `Elements` / mirror-method calls outside the seam impl and the enumerated boundary packages
- [x] 4.4 Add the ArchUnit rule confining `javax.lang.model` to the seam impl, discovery adapter, codegen emit, diagnostics, and the nullability resolver
- [x] 4.5 Gate: e2e / doc compile-tests + `percolate-smoke:smokeRun` green

## 5. Phase 4 — Rewrite processor engine unit specs; restore threaded pitest

- [x] 5.1 Rewrite the `processor` engine unit specs from scratch against a mocked `ResolveCtx` (first targets `GroundingSpec`, `ExpandStageDriverSpec`, and the remaining real-javac specs); each stubs 1–2 seam questions, no compile
- [x] 5.2 Delete `HarnessResolveCtx` and the 17 real-javac specs' `PrivateTypeUniverse` dependencies from `processor`
- [x] 5.3 Restore threaded pitest config: delete `processor/spock-pitest.groovy`, the `spock.configuration` jvmArg, and the `pitestTargetClasses` / `pitestTargetTests` blocks; set `threads = availableProcessors()`
- [x] 5.4 Ratchet the `processor` mutation floor up on the honest suite; confirm identical scores across two cleared-history runs

## 6. Phase 5 — Rewrite spi unit specs; delete TypeUniverse; pitest on spi

- [x] 6.1 Rewrite the 8 `spi` real-javac unit specs from scratch against mocked/faked `ResolveCtx` (`AccessorSpec`, `CandidateFreeSurfaceSpec` trimmed, `ContainerSpec`, `ContainersSpec`, `ConversionSpec`, `LiteralCoercionSpec`, `PortSpec`, `ResolveCtxSpec`) — the measured count was 8, not 11
- [x] 6.2 **Scope note (discovered during apply):** `TypeUniverse`/`TypeUniverseSpec` are **not** deleted here. `strategies-builtin` (`StreamMapSpec`, `OptionalContainerSpec`, `MethodCallBridgeSpec`, `ResolveCtxBuilder`/`ResolveCtxBuilderSpec`) depends on `TypeUniverse` directly, and rewriting those specs is this change's own explicit Non-Goal (deferred to `features-as-documentation` #3) — deleting `TypeUniverse` now would break that module's build. `spi` itself no longer references `TypeUniverse` anywhere (confirmed: no unit spec in `spi` imports it); `HarnessResolveCtx` — which had no remaining consumer once `processor` (Phase 4) and `spi` (this phase) went mock-only — **is** deleted, per the `expansion-test-harness` delta spec. `TypeUniverse`/`TypeUniverseSpec` deletion is deferred to change #3, alongside the `strategies-builtin` unit-spec rewrite.
- [x] 6.3 Add pitest to `spi/build.gradle` (threaded `availableProcessors()`, all mutators, incremental analysis with the `org.pitest:pitest-history-plugin` dep — inherited from the shared root block); measured (see 6.4) and set a ratchet floor (`mutationThreshold = 10`, `coverageThreshold = 60`, `testStrengthThreshold = 15`, well below the observed range with margin); wired into `check` (inherited)
- [x] 6.4 **Confirmed non-identical, but stable within margin — see finding below.** Two cleared-history runs of `spi`'s pitest do *not* reproduce byte-identical scores (unlike `processor`'s, confirmed identical in Phase 4): line coverage held at 70-73% across 4 clean-build runs, but mutation-kill/test-strength varied (16-26% killed, 24-40% test strength) — reproduced even with `enableDefaultIncrementalAnalysis = false` and `threads = 1`, ruling out both incremental-analysis staleness and minion-thread races. Plain `./gradlew :spi:test` is 100% reproducible (8/8 identical clean runs), so this is not a shared-substrate race like the one this change targets — it's PIT's own test-to-mutant attribution when multiple tests cover the same line with differing assertion strength. Floors set with margin below the worst observed case so `check` never flaps; tightening further (stronger assertions, or a direct investigation of the PIT attribution variance) is flagged as follow-up, not blocking.

## 7. Acceptance verification

- [x] 7.1 Each rewritten module's unit suite runs threaded pitest — no `@Isolated`, no `threads = 1`, no `spock-pitest.groovy` — holding its ratchet floor. `processor` reproduces identically across cleared-history runs; `spi` does not reproduce identically (see 6.4) but stays comfortably above its floor across repeated clean-build runs.
- [x] 7.2 Confirmed: `strategies-builtin:integrationTest` (e2e/doc compile-tests) and `percolate-smoke:smokeRun` both green.
- [x] 7.3 Confirmed: `architecture-tests:test` (`ModuleBoundariesSpec`) green.
- [x] 7.4 Holds for the strategy/engine-boundary specs (`ListContainerSeamSpec`, `SourceCandidatesSpec`, `AccessorSpec`, `ConversionSpec`, `ContainersSpec`, `TypeProbeSpec`: 1-2 stubs each). Does **not** hold for `ResolveCtxSpec` (and, to a lesser extent, `ContainerSpec`) by design: those specs pin the seam's *own* default-method composition (`isType`/`isCollection`/`membersOf`-consumers/…), so they necessarily exercise multiple leaf seam questions per test through a hand-written javac-free `FakeResolveCtx`/`FakeType` — the same shape as `processor`'s `GroundingSpec`. Zero javac either way.
