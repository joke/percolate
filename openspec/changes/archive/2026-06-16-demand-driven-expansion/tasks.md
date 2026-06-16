## 1. Preparation

- [x] 1.1 Load the coding-convention skills (java + java11; groovy/spock for tests) before writing any code
- [x] 1.2 Re-read `design.md` decisions D1–D6 and the migration order; confirm the existing Spock/jqwik suite is green at HEAD as the behavioural baseline

## 2. D6 — Per-mapper ResolveCtx; drop dead accessors; kill the ThreadLocal

- [x] 2.1 Remove `mapperType()` and `currentMethod()` from `ResolveCtx` (spi)
- [x] 2.2 Construct `ResolveCtx` per mapper, binding `callableMethods` at `Pipeline.process` time; delete the `ThreadLocal<MapperContext>` and the `mapperType`/`currentMethod` plumbing in `ProcessorModule` (`CompileResolveCtx`)
- [x] 2.3 Confirm `MethodCallBridge` still compiles against the shrunk `ResolveCtx` (only `callableMethods()` is read)
- [x] 2.4 Update test doubles: `HarnessResolveCtx`, `ResolveCtxBuilder` (+ `ResolveCtxBuilderSpec`), and inline `ResolveCtx` fakes in `ContainersSpec` / `ContainerSpec` — drop the removed accessors
- [x] 2.5 Add/adjust a spec asserting `ResolveCtx` declares no `mapperType()`/`currentMethod()` and that `ProcessorModule` uses no `ThreadLocal`
- [x] 2.6 `./gradlew check` green before proceeding

## 3. D1 — Resolution mode as the work-list dispatch key

- [x] 3.1 Extend `Location.role()` to return the resolution mode `FREE`/`ACCESS`/`LEAF`/`CONSTANT`; implement per concrete `Location` (`TargetLocation`→FREE; `SourceLocation`→ACCESS when >1 segment else LEAF; `ElementLocation`→LEAF; `ConstantLocation`→CONSTANT)
- [x] 3.2 Update `ExtractedPlan`/driver call sites that consult the old `SUPPLY`/`DEMAND` values to the new modes (mechanical), keeping behaviour unchanged for now
- [x] 3.3 Add the single mode-dispatch branch to the work-list (`FREE` runs the full strategy set; `LEAF`/`CONSTANT` terminate) — initially leaving `descend` in place so the suite stays green
- [x] 3.4 Unit spec: each `Location` reports the correct mode (multi- vs single-segment `SourceLocation`)
- [x] 3.5 `./gradlew check` green

## 4. D2/D3 — Source paths as backward ACCESS demands; delete the second engine

- [x] 4.1 (decision A) Add a memoized, non-mutating forward type-walk `typing(scope, segments)` that types a `SourceLocation` demand at creation by reusing the accessor-resolution helper (base case: the parameter's declared `(type, nullness)` from the method signature; step: `resolveAccessor(parentType, segment).output{Type,Nullness}`) — no graph mutation, one shared dispatch helper [extracted to `AccessorResolver`]
- [x] 4.2 Make the work-list handle `ACCESS` demands: resolve the last segment's accessor on `parentType = typing(parent).type` (via the accessor strategies), land the accessor `Operation` through the `Applier`, and enqueue the parent-path `SourceLocation` demand
- [x] 4.3 Rework the directive path (D3): a `FREE` target demand with a directive source creates the typed leaf `SourceLocation[P]` Value (preferred candidate) instead of eager descent, binds the target's producer to it (collapse `pinnedSource`, preserving directive-pinned precedence over a same-typed sibling), and enqueues it so the work-list builds the accessor chain backward
- [x] 4.4 Delete the eager `descend` recursion and the `descended` memo; keep the accessor-resolution helper (`resolveAccessor`, repurposed into `AccessorResolver`) and ensure all accessor `Operation` emission flows through the work-list `ACCESS` handler + `Applier` — no driver-resident eager descent remains
- [x] 4.5 Source candidate enumeration (`candidates()`/`matchingSource()`) reads method-signature params + discovered source/intermediate Values instead of scanning pre-seeded SUPPLY vertices — DEFERRED to D4 (task 6.x): required only once seeding is removed; params are still seeded during D2 so the graph scan suffices [delivered in 6.3 via `SourceCandidates`]
- [x] 4.6 Spec: a multi-segment source demand expands via the work-list (no `descend`/`resolveAccessor`); assembly does not fire on an `ACCESS` demand; the two-segment chain renders identically [added `SourcePathChainEndToEndSpec` (3-segment); existing 2-segment end-to-end specs already cover the rest]
- [x] 4.7 `./gradlew check` green (codegen behaviour-equivalent; investigate any diff)

## 5. D5 — Tighten the cost base case to LEAF

- [x] 5.1 Change `ExtractedPlan.isBaseCase` to `role() == LEAF` only (parameter / element roots); every other producerless Value is `Cost.INFINITE`
- [x] 5.2 Spec: a multi-segment `ACCESS` demand with no matched accessor is unreachable (not vacuously `Cost.ZERO`); existing no-silent-sourcing / realisation diagnostics still pass
- [x] 5.3 `./gradlew check` green

## 6. D4 — Delete SeedStage; self-seed; move GoalSpec to discovery

- [x] 6.1 Move per-level `GoalSpec` derivation into the discovery phase (`DiscoverMappingsStage`); store it on the per-mapper context keyed by method scope
- [x] 6.2 `ExpandStage` creates the empty `MapperGraph` (`ctx.setGraph`) and self-seeds at entry: enqueue one return-type demand per abstract method, created as a bare `AddValue` through the `Applier` (extend the `Applier` to land `AddValue`); parameter `LEAF`s materialise lazily
- [x] 6.3 (was 4.5) Source candidate enumeration (`candidates()`/`matchingSource()`) reads method-signature params + discovered graph source/intermediate Values, materialising a referenced param `LEAF` on demand (params are no longer pre-seeded) [extracted into `SourceCandidates` collaborator, mirroring `AccessorResolver`]
- [x] 6.4 Delete `SeedStage` and `DumpGraphStage` (the dropped `seed` view, decision A); remove both from `ProcessorModule.stages()`; the `full`/`transforms`/`plan` dumps remain, all after expansion
- [x] 6.5 Confirm no stage calls `MapperGraph.valueFor(...)` directly — the `Applier` is the sole mutation site again
- [x] 6.6 Migrate/retire `SeedStageSpec` to discovery-owned goal-spec specs and the self-seed behaviour; update any harness/spec that invoked seeding or asserted the `seed` dump [goal-spec attachment migrated to `DiscoverMappingsStageSpec`; self-seed to `SelfSeedExpansionSpec`]
- [x] 6.7 Spec: graph starts empty and grows by demand; an unused parameter is never materialised; goal spec available to expansion without a seed stage [`SelfSeedExpansionSpec`]
- [x] 6.8 `./gradlew check` green

## 7. Verification & wrap-up

- [x] 7.1 Grep for dangling references to `SeedStage`, `descend`, `resolveAccessor`, `descended`, `currentMethod`, `mapperType`, and the `ThreadLocal` across main + tests; remove leftovers
- [x] 7.2 Run `openspec validate demand-driven-expansion --strict` and confirm the change still validates
- [x] 7.3 Run `./gradlew check` (do not pipe to tail) and confirm zero violations — NEVER continue if there are violations
- [x] 7.4 Commit the completed work with `/commit-commands:commit`
