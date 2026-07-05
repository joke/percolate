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

- [ ] 3.1 Narrow `ResolveCtx` into the ~13-question type-query seam (remove `types()` / `elements()`); passing `TypeMirror` through as an opaque token
- [ ] 3.2 Implement `CompileResolveCtx` delegating each seam method to real javac (`Types` / `Elements`)
- [ ] 3.3 Route ONE engine stage (`SourceCandidates`) through the seam
- [ ] 3.4 Route ONE strategy (`ListContainer`) through the seam; carve `Containers` / `TypeProbe` mockable over the seam for the list case
- [ ] 3.5 Rewrite `SourceCandidatesSpec` and the `ListContainer` spec from scratch against a mocked `ResolveCtx` (copy `ValidateNoDuplicateTargetsStageSpec`); each stubs 1–2 seam questions with zero javac
- [ ] 3.6 Run pitest on the slice; confirm clean, deterministic, no `@Isolated` — **go/no-go gate** before horizontal rollout

## 4. Phase 3 — Seam across all production + ArchUnit confinement

- [ ] 4.1 Route every remaining engine type-interrogation site (graph / stages / plan) through the seam
- [ ] 4.2 Route every remaining strategy type-interrogation site (`strategies-builtin`, `reactor`, `reactor-blocking`) through the seam; finish the `Containers` / `TypeProbe` carving
- [ ] 4.3 Delete all direct `Types` / `Elements` / mirror-method calls outside the seam impl and the enumerated boundary packages
- [ ] 4.4 Add the ArchUnit rule confining `javax.lang.model` to the seam impl, discovery adapter, codegen emit, diagnostics, and the nullability resolver
- [ ] 4.5 Gate: e2e / doc compile-tests + `percolate-smoke:smokeRun` green

## 5. Phase 4 — Rewrite processor engine unit specs; restore threaded pitest

- [ ] 5.1 Rewrite the `processor` engine unit specs from scratch against a mocked `ResolveCtx` (first targets `GroundingSpec`, `ExpandStageDriverSpec`, and the remaining real-javac specs); each stubs 1–2 seam questions, no compile
- [ ] 5.2 Delete `HarnessResolveCtx` and the 17 real-javac specs' `PrivateTypeUniverse` dependencies from `processor`
- [ ] 5.3 Restore threaded pitest config: delete `processor/spock-pitest.groovy`, the `spock.configuration` jvmArg, and the `pitestTargetClasses` / `pitestTargetTests` blocks; set `threads = availableProcessors()`
- [ ] 5.4 Ratchet the `processor` mutation floor up on the honest suite; confirm identical scores across two cleared-history runs

## 6. Phase 5 — Rewrite spi unit specs; delete TypeUniverse; pitest on spi

- [ ] 6.1 Rewrite the 11 `spi` real-javac unit specs from scratch against mocked `ResolveCtx`
- [ ] 6.2 Delete the shared static `TypeUniverse` and `TypeUniverseSpec`
- [ ] 6.3 Add pitest to `spi/build.gradle` (threaded `availableProcessors()`, all mutators, incremental analysis with the `org.pitest:pitest-history-plugin` dep); measure, set a ratchet floor, wire into `check`
- [ ] 6.4 Confirm `spi` pitest is deterministic across two cleared-history runs

## 7. Acceptance verification

- [ ] 7.1 Confirm each rewritten module's unit suite runs threaded pitest deterministically — no `@Isolated`, no `threads = 1`, no `spock-pitest.groovy` — holding its ratchet floor, with two cleared-history runs matching
- [ ] 7.2 Confirm the e2e / doc compile-tests + `percolate-smoke:smokeRun` stay green
- [ ] 7.3 Confirm the ArchUnit rule proves `javax.lang.model` appears only in the seam impl and the enumerated boundary packages
- [ ] 7.4 Confirm the spike criterion holds suite-wide: rewritten specs stub 1–2 seam methods with zero javac in the unit path
