# Tasks

This change is **spec-only** — it realigns capability specs with already-shipped code. There is **no production code to write**. "Implementation" means a final verification pass that each delta matches the current `processor` / `spi` / `strategies-builtin` source, then syncing the deltas into the main specs.

## 1. Verify each delta against shipped code

- [x] 1.1 `seed-graph` — confirm `SeedGraph.registerAssemblyGroups` creates one umbrella per parent (slots = all child leaves) and `registerSeedGroup` covers source path-segment + directive-bridging edges only; confirm `SourceDescentExpander` / `DirectiveBindingExpander` / `AssemblyExpander` are the live expanders.
- [x] 1.2 `graph-expansion` — confirm `ExpandStage` runs `[ExpandGroupsPhase]` only; `Applier.pinExpectedTypesOnProducers` performs the pin; no `ResolveTargetChainsPhase`; slot-typing lifecycle uses `ExpansionStep.getOutput()` / `TypeNode` / `Node.setTyping`.
- [x] 1.3 `expansion-test-harness` — confirm `ProcessorModule.assembleExpansionPipeline(List<ExpansionStrategy>, ResolveCtx, NullabilityResolver)` and `ExpansionHarness.expand(MapperGraph, List<ExpansionStrategy>)`; confirm `ValidatePathsPhase` and the `Bridge`/`SourceStep`/`GroupTarget` lists no longer exist.
- [x] 1.4 `graph-model` — confirm `Edge` fields are `from, to, weight, kind, directive, codegen, elementScope, strategyClassFqn` with `@EqualsAndHashCode(exclude = {"codegen","elementScope","strategyClassFqn"})`; confirm `EdgeKind{SEED,REALISED,MARKER}` and the container `realised(...)` factory takes `ElementScope`.
- [x] 1.5 `callable-method-discovery` — confirm the multi-parameter filter wording no longer names the deleted `MethodCallGroupTarget`.
- [x] 1.6 `expansion-strategy-spi` — confirm `ConstructorCall` (`AssemblyStrategy`) over-emits per accessible constructor with no match-filter; `DirectAssign` (`CombinatorialMatch`) emits a `CONVERSION` step; `MethodCallBridge` (`ExpansionStrategy`) emits `BOUNDARY` steps; all built-ins register `@AutoService(ExpansionStrategy.class)`; the 12-class roster is accurate.
- [x] 1.7 `builtin-strategy-unit-tests` — confirm the 12 strategy specs listed match the test tree, the value-type accessors are `ExpansionStep`/`Slot`/`ElementScope`, and no superseded per-operation container specs remain.
- [x] 1.8 `code-generation` — confirm no delta is needed (its "group-target composition" refers to the live `BuildMethodBodies.renderGroupTarget`, not the deleted `GroupTarget` SPI). Record the no-op decision.

## 2. Cross-check for residual drift in touched specs

- [x] 2.1 Re-grep each touched spec for `ResolveTargetChainsPhase`, `GroupTarget`, `Bridge` (SPI), `BridgeStep`, `ScopeTransition`, `PathSegmentResolver`, `ResolvedSegment`, `SourceStep` to ensure no stale token survives in a rewritten requirement block.
- [x] 2.2 Confirm `openspec validate "realign-assembly-cutover-specs"` passes (delta format: 4-hashtag scenarios, full `MODIFIED` blocks, `REMOVED` with Reason/Migration).

## 3. Sync and unblock

- [x] 3.1 Run `openspec sync` (or `/opsx:sync`) to apply the deltas into `openspec/specs/`, and verify the main specs no longer reference the deleted phases/types.
- [ ] 3.2 Rebase the parked `fix-overloaded-constructor-assembly` change onto the corrected `graph-expansion` / `seed-graph` baseline and continue its `specs` artifact.
