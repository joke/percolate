# Tasks: evict-javax-model

Phases follow design.md D9. Phase 0 is the go/no-go gate; Phase 2 is the atomic SPI flip; the old
`strategies-builtin` e2e compile-tests stay green from Phase 2 on and gate the adapter against real javac.

## 1. Spike (go/no-go gate)

- [x] 1.1 Prototype `TypeRef` value hierarchy + minimal `TypeSpace` (sameness, erasure, declared-edge assignability with substitution, boxing table) in a scratch package in `spi`
- [x] 1.2 Port ONE container strategy (`ListContainer` or `StreamMap`) + its unit spec to the prototype — validates generics substitution, functor-lift type-variable matching ergonomics, and the nested type-use nullness question (design open question)
- [x] 1.3 Port ONE engine stage seam (`SourceCandidates` or `SelfCallGuard`) + its spec to the prototype — validates value-equality keying replacing `toString` keys
- [x] 1.4 Prototype `TypeRef → TypeName` emission for the spike's shapes and golden-compare against `TypeName.get(mirror)` output
- [x] 1.5 Go/no-go: record spike findings (final names, package, nested-nullness representation, `VariableRef` bounds y/n) in design.md; delete scratch code or promote it — **GO**, promoted in place (see design.md Spike Findings)

## 2. Phase 1 — model lands in spi (zero consumers)

- [x] 2.1 Implement the `TypeRef` hierarchy (declared/primitive/array/variable/none) with value equality and source-form `toString`
- [x] 2.2 Implement `TypeDecl`/`MethodSig`/`FieldSig` member model with resolved-nullness fields and `Origin` token
- [x] 2.3 Implement `TypeSpace`: decl lookup, declared/array construction, sameness, erasure, boxing, assignability walk with type-argument substitution
- [x] 2.4 Unit-test the algebra with example-based Spock specs covering the laws (reflexivity, transitivity, erasure idempotence, boxing round-trips, match→ground substitution coherence) — TypeSpaceSpec + StreamMapPortSpec
- [x] 2.5 Build the `spi` testFixtures: `TypeSpace` literal builders, `TestTypes` reflection mirror (methods/fields/ctors/generic supertypes via `java.lang.reflect`), prebuilt constants (`STRING`, `INT`, `LIST_OF_STRING`, …)
- [x] 2.6 Unit-test the fixtures (builder shapes, reflection mirror against the existing fixture classes, constants) — all parallel-safe, no javac

## 3. Phase 2 — the flip (adapter, SPI, engine, strategies, codegen, all unit specs)

- [x] 3.1 Build the discovery adapter (`TypeSpaceAdapter`): eager cycle-safe closure walk materialising `TypeSpace` + model values from `javax.lang.model` (JDK types edge-only), member nullness resolved at the boundary; tested against real javac. Bridge: `ResolveCtx.typeSpace()` added alongside `types()`/`elements()`. Deferred — `Origin` registry stubbed to `Origin.none()` (until diagnostics migrate) and production `ResolveCtx` wiring lands with the first consumer increment
- [ ] 3.2 Flip the SPI signatures: `ResolveCtx.typeSpace()`, `Demand`/`OperationSpec`/`CallableMethods`/`MethodCandidate`/`Containers`/`TypeProbe`/`Port` et al. to `TypeRef`/`MethodSig`; move callable nullability resolution to the adapter (resolved data on `MethodSig`)
- [ ] 3.3 Port the engine internals: `Value` (`TypeRef` + value-equality dedup), `MethodScope`/`SelfCallGuard` (drop `toString` keys), `ExpandStage` driver, grounding/matching, validate + dump stages (DOT rendering from the model walk)
- [ ] 3.4 Implement the `TypeRef → TypeName` emitter (`TypeNames`) and golden-spec it vs JavaPoet rendering — done. Route the JavaPoet sites through it — **amended (2026-07-04, design D7)**: only 1 of 7 sites is safe (`ConstructorCall`'s bare-class-name `new $T(...)`, wildcard-free by construction) and is routed; the other 6 (`AssembleMapperType` ×5, `BuildMethodBodies` ×1) require exact JLS fidelity (override signatures, hoisted-local declarations) that v1's wildcard-free `TypeRef` can't provide, and stay on `TypeName.get(TypeMirror)` permanently — see design.md D7 amendment. Type-use `@Nullable` emission not started (separate, unblocked by the wildcard question — a future increment)
- [ ] 3.5 Port `strategies-builtin`, `reactor`, `reactor-blocking` strategies to the model
- [x] 3.6 Migrate all unit specs off `TypeUniverse`/mirrors — **16 `processor` specs done** (task 4.2) **+ 11 `spi` specs done** (`PrivateTypeUniverse`, see design.md D8 amendment) **+ 19 `strategies-builtin` specs done** (`PrivateTypeUniverse`; `ResolveCtxBuilder` retargeted from the static `TypeUniverse` to a `PrivateTypeUniverse` constructor argument). **`reactor`/`reactor-blocking` confirmed N/A**: every spec in both modules is `@Tag('integration')`, none reference `TypeUniverse`/`HarnessResolveCtx`/`ResolveCtxBuilder` — they test exclusively via full real-compiles (`PercolateCompiler`/google-compile-testing), a different strategy that never touched the shared-mirror hazard. **Found + fixed during the `strategies-builtin` migration**: `PrivateTypeUniverse` itself was not thread-safe (plain `HashMap`/`HashSet`, no lock) — fine for the 2 specs it was designed for (single real-mirror call each) but several `strategies-builtin` specs share one `@Shared PrivateTypeUniverse` across many feature methods, and the developer's global `~/.spock/SpockConfig.groovy` (`parallel { enabled true }`) actually runs Spock in parallel — `strategies-builtin/build.gradle`'s attempted opt-out (`junit.jupiter.execution.parallel.enabled=false`, `spock.parallel.threads=0`) does not touch Spock's own runner (verified: `spock.parallel.threads` is not a real Spock property; Jupiter's property is ignored by Spock's own JUnit Platform engine), so real concurrent feature-method execution against the shared instance produced `ConcurrentModificationException`. Fixed by giving `PrivateTypeUniverse` the same guards `TypeUniverse` already has — an instance-scoped synchronized lookup plus a `SynchronizedElements` wrapper — rather than trying to suppress parallelism; confirmed via 5 repeated clean `:strategies-builtin:test` runs. Note: `spi`'s module-wide `maxParallelForks=1`/`spock.parallel.threads=0` override is NOT lifted yet — deliberately deferred to Phase 4 (task 5), gated on `TypeUniverse`'s actual deletion (task 4.1), not on individual spec migration
- [ ] 3.7 Green gate: full build — unit suites, the old `strategies-builtin` e2e compile-tests, `percolate-smoke`

## 4. Phase 3 — demolition and confinement

- [ ] 4.1 Delete `TypeUniverse` and the javac substrate of `HarnessResolveCtx` (with `SynchronizedElements`, `completeClosure`, priming lists); delete `TypeUniverseSpec`
- [x] 4.2 Remove `@Isolated` from all 16 specs — **done** via `PrivateTypeUniverse` (see design.md D8 amendment 2026-07-04), including giving `HarnessResolveCtx` a `PrivateTypeUniverse`-backed constructor (its 3 remaining consumers — `SourceCandidatesSpec`, `ExpandStageDriverSpec`, `GroundingSpec` — were the last blocker, since it was itself still bound to the shared static `TypeUniverse`). All 16 processor specs now construct their own private, non-shared javac substrate. Note: `TypeUniverse` itself is NOT deleted (task 4.1) — it is still the active substrate for the `spi`/`strategies-builtin` specs outside this 16-spec set (those modules' own `TypeUniverse` migration is unstarted, see task 3.6)
- [ ] 4.3 Add the ArchUnit rule: `javax.lang.model` imports confined to the boundary packages (adapter/discovery, processor entry points, nullability resolver, diagnostics); fix any stragglers it finds
- [ ] 4.4 Update `openspec/notes.md` open-topics entry to SHIPPED state

## 5. Phase 4 — pitest rollout + config hygiene

- [ ] 5.1 Root `build.gradle`: `threads = availableProcessors()`; delete the `pitestThreads`/`pitestTargetClasses`/`pitestTargetTests` property blocks; delete the `spock.configuration` jvmArg
- [ ] 5.2 Delete `processor/spock-pitest.groovy`
- [ ] 5.3 Move the processor-specific `excludedClasses` (Dagger*, `stages.dump.*`, `DotRenderer`) from root into `processor/build.gradle`; root keeps only module-agnostic mechanics
- [ ] 5.4 Apply the pitest plugin to `spi`, `strategies-builtin`, `reactor`, `reactor-blocking`; measure clean scores, set per-module ratchet floors below measured, wire into `check`
- [ ] 5.5 Verify determinism: two clean pitest runs (cleared history) produce identical scores; verify no `@Isolated`, no `spock-pitest.groovy`, threaded execution

## 6. Verification

- [ ] 6.1 Run `./gradlew check` — everything green (unit, e2e, smoke, ArchUnit, jacoco 95%, all pitest gates). NEVER continue if there are violations!
