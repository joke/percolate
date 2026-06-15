## 1. Preparation

- [ ] 1.1 Load the coding-convention skills (java + java11; groovy/spock for tests) before writing any code
- [ ] 1.2 Re-read `design.md` decisions D1–D6 and the migration order; confirm the existing Spock/jqwik suite is green at HEAD as the behavioural baseline

## 2. D6 — Per-mapper ResolveCtx; drop dead accessors; kill the ThreadLocal

- [ ] 2.1 Remove `mapperType()` and `currentMethod()` from `ResolveCtx` (spi)
- [ ] 2.2 Construct `ResolveCtx` per mapper, binding `callableMethods` at `Pipeline.process` time; delete the `ThreadLocal<MapperContext>` and the `mapperType`/`currentMethod` plumbing in `ProcessorModule` (`CompileResolveCtx`)
- [ ] 2.3 Confirm `MethodCallBridge` still compiles against the shrunk `ResolveCtx` (only `callableMethods()` is read)
- [ ] 2.4 Update test doubles: `HarnessResolveCtx`, `ResolveCtxBuilder` (+ `ResolveCtxBuilderSpec`), and inline `ResolveCtx` fakes in `ContainersSpec` / `ContainerSpec` — drop the removed accessors
- [ ] 2.5 Add/adjust a spec asserting `ResolveCtx` declares no `mapperType()`/`currentMethod()` and that `ProcessorModule` uses no `ThreadLocal`
- [ ] 2.6 `./gradlew check` green before proceeding

## 3. D1 — Resolution mode as the work-list dispatch key

- [ ] 3.1 Extend `Location.role()` to return the resolution mode `FREE`/`ACCESS`/`LEAF`/`CONSTANT`; implement per concrete `Location` (`TargetLocation`→FREE; `SourceLocation`→ACCESS when >1 segment else LEAF; `ElementLocation`→LEAF; `ConstantLocation`→CONSTANT)
- [ ] 3.2 Update `ExtractedPlan`/driver call sites that consult the old `SUPPLY`/`DEMAND` values to the new modes (mechanical), keeping behaviour unchanged for now
- [ ] 3.3 Add the single mode-dispatch branch to the work-list (`FREE` runs the full strategy set; `LEAF`/`CONSTANT` terminate) — initially leaving `descend` in place so the suite stays green
- [ ] 3.4 Unit spec: each `Location` reports the correct mode (multi- vs single-segment `SourceLocation`)
- [ ] 3.5 `./gradlew check` green

## 4. D2/D3 — Source paths as backward ACCESS demands; delete the second engine

- [ ] 4.1 Add a pure, non-mutating source-path type resolver (ResolveCtx type queries from the parameter type) used to type `SourceLocation` demands at creation — no graph mutation, no strategy dispatch
- [ ] 4.2 Make the work-list handle `ACCESS` demands: run only accessor strategies (`Getter`/`Method`/`Field` path resolvers via the normal dispatch), emit the last-segment accessor `Operation`, and enqueue the parent-path `SourceLocation` demand
- [ ] 4.3 Rework the directive path (D3): a `FREE` target demand with a directive source creates the typed `SourceLocation[P]` demand and binds the target's producer to it as the preferred source (collapse `pinnedSource`), preserving directive-pinned precedence over a same-typed sibling
- [ ] 4.4 Delete `descend`, `resolveAccessor`, `paramRoot`, and the `descended` memo from `ExpandStage`; ensure no driver-resident accessor emission remains
- [ ] 4.5 Source candidate enumeration (`candidates()`/`matchingSource()`) reads method-signature params + discovered source/intermediate Values instead of scanning pre-seeded SUPPLY vertices
- [ ] 4.6 Spec: a multi-segment source demand expands via the work-list (no `descend`/`resolveAccessor`); assembly does not fire on an `ACCESS` demand; the two-segment chain renders identically
- [ ] 4.7 `./gradlew check` green (codegen behaviour-equivalent; investigate any diff)

## 5. D5 — Tighten the cost base case to LEAF

- [ ] 5.1 Change `ExtractedPlan.isBaseCase` to `role() == LEAF` only (parameter / element roots); every other producerless Value is `Cost.INFINITE`
- [ ] 5.2 Spec: a multi-segment `ACCESS` demand with no matched accessor is unreachable (not vacuously `Cost.ZERO`); existing no-silent-sourcing / realisation diagnostics still pass
- [ ] 5.3 `./gradlew check` green

## 6. D4 — Delete SeedStage; self-seed; move GoalSpec to discovery

- [ ] 6.1 Move per-level `GoalSpec` derivation into the discovery phase; store it on the per-mapper context keyed by method scope
- [ ] 6.2 Self-seed at `ExpandStage` entry: enqueue one return-type demand per abstract method, created as a bare `AddValue` through the `Applier` (extend the `Applier` to land `AddValue` if needed); parameter `LEAF`s materialise lazily
- [ ] 6.3 Delete `SeedStage`; remove it from `ProcessorModule.stages()` and update the pipeline wiring; ensure dump-after-mutation ordering still holds
- [ ] 6.4 Confirm no stage calls `MapperGraph.valueFor(...)` directly — the `Applier` is the sole mutation site again
- [ ] 6.5 Migrate/retire `SeedStage` unit specs to discovery-owned goal-spec specs and the self-seed behaviour; update any harness that invoked seeding
- [ ] 6.6 Spec: graph starts empty and grows by demand; an unused parameter is never materialised; goal spec available to expansion without a seed stage
- [ ] 6.7 `./gradlew check` green

## 7. Verification & wrap-up

- [ ] 7.1 Grep for dangling references to `SeedStage`, `descend`, `resolveAccessor`, `descended`, `currentMethod`, `mapperType`, and the `ThreadLocal` across main + tests; remove leftovers
- [ ] 7.2 Run `openspec validate demand-driven-expansion --strict` and confirm the change still validates
- [ ] 7.3 Run `./gradlew check` (do not pipe to tail) and confirm zero violations — NEVER continue if there are violations
- [ ] 7.4 Commit the completed work with `/commit-commands:commit`
