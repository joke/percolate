## Why

Several spec capabilities still describe **expansion/assembly architecture that has already been deleted from the code** — a drift accumulated across multiple superseded cutovers that shipped without spec updates. The specs reference a deleted `ResolveTargetChainsPhase`, a deleted `GroupTarget` SPI interface, and (in `expansion-test-harness`) an even older `ResolveSourceChainsPhase`/`BridgeSourceToTargetPhase` pipeline — none of which exist in the shipped processor. This makes the specs an unreliable contract and, concretely, blocks the `fix-overloaded-constructor-assembly` change: its deltas cannot layer onto requirements that describe a phase the code no longer has. This change realigns the specs to the **shipped code** so there is a correct baseline to build on.

## What Changes

This is a **spec-to-shipped-code realignment only — no code changes.** The code is the ground truth; the specs are updated to match it.

- Replace all references to the deleted **`ResolveTargetChainsPhase`** with the shipped reality: the directive-binding group's declared target type is pinned by **`Applier.pinExpectedTypesOnProducers`** during `AddGroup` application (driven by the over-emitting assembly producer), not by a dedicated pre-pass.
- Replace the deleted **`GroupTarget`** SPI vocabulary with the shipped **`AssemblyStrategy`** marker and its sole implementor **`ConstructorCall`**, which over-emits one `BOUNDARY` step per accessible constructor at an assembly root.
- Realign **seed-graph**: per-edge "target-chain groups (SAT-by-construction once `ResolveTargetChainsPhase` allocates typed slots)" → **one umbrella assembly group per parent target node** (`SeedGraph.registerAssemblyGroups`, `slots = targetChildren` = all directive-decomposed child leaves).
- Realign **expansion-test-harness**: the superseded three-phase wiring (`ResolveSourceChainsPhase`/`ResolveTargetChainsPhase`/`BridgeSourceToTargetPhase`, `List<GroupTarget>`/`List<SourceStep>`) → the shipped **`ExpandStage` running `ExpansionPhase`s** with **`ExpandGroupsPhase`** driving the cross-group fixed-point loop over **`GroupExpander`** implementations.
- Sweep residual **`GroupTarget`** references in `code-generation`, `callable-method-discovery`, `builtin-strategy-unit-tests`, `graph-model`, and `expansion-strategy-spi` to the shipped vocabulary (or remove them where the concept no longer exists), keeping each requirement's intent but stated against shipped types.
- **BREAKING (spec only):** the `GroupTarget` SPI interface and the `ResolveTargetChainsPhase`/older-pipeline contracts are removed from the specs, matching their prior removal from code.

## Capabilities

### New Capabilities
<!-- none — this change introduces no new capability -->

### Modified Capabilities

- `graph-expansion`: requirements referencing `ResolveTargetChainsPhase` (slot typing lifecycle, directive-binding pin, phase ordering) restated against the shipped `ExpandGroupsPhase` + `Applier.pinExpectedTypesOnProducers` + `AssemblyStrategy`/`ConstructorCall`.
- `seed-graph`: target-chain group structure restated as the umbrella assembly group (`registerAssemblyGroups`).
- `expansion-test-harness`: harness wiring restated against `ExpandStage`/`ExpandGroupsPhase`/`GroupExpander`, dropping the deleted phase and `GroupTarget`/`SourceStep` list arguments.
- `expansion-strategy-spi`: the `GroupTarget` interface contract removed; assembly is covered by the `AssemblyStrategy` marker over `ExpansionStrategy`.
- `code-generation`: `renderGroupTarget`/`GroupTarget` references restated against the shipped group-target rendering of `ExpansionGroup` roots.
- `callable-method-discovery`: `GroupTarget` references realigned to `AssemblyStrategy`/`ConstructorCall` discovery context.
- `builtin-strategy-unit-tests`: `GroupTarget` unit-test references realigned to the shipped built-in strategies.
- `graph-model`: residual `GroupTarget` vocabulary realigned to shipped graph/group types.

## Impact

- **Specs**: 8 capability spec files updated; no new capabilities. Each delta is a `MODIFIED`/`REMOVED` requirement set bringing wording to shipped types.
- **Code**: none. This change asserts nothing new about behavior; it documents what already ships. Each realigned requirement SHALL be verified against the current `processor`/`spi`/`strategies-builtin` source before being written.
- **APIs**: spec-level removal of the `GroupTarget` SPI surface (already absent from code).
- **Unblocks**: `fix-overloaded-constructor-assembly` rebases onto the corrected `graph-expansion`/`seed-graph` baseline.
- **Affected teams / readers**: processor & codegen maintainers (the `graph-expansion`, `seed-graph`, `code-generation`, `graph-model` specs), strategy authors (the `expansion-strategy-spi`, `callable-method-discovery`, `builtin-strategy-unit-tests` specs), and test-harness maintainers (`expansion-test-harness`). No external consumers.
