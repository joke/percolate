## Why

`ExpansionGroup` has accreted state that does not belong to a "group": a mutable `AsSubgraph`
view, per-slot `slotMetadata` (consumer-expected type + nullability contract), `conversionFrontiers`,
and a `GroupCodegen`. This forces the code generator to reach *into groups* for codegen and contracts
(`BuildMethodBodies` reads `group.getCodegen()`, `group.getSlots()`, `group.consumerContractFor(slot)`),
which contradicts the intended model: **a group is a logical grouping unit, never an active graph
component, and generated code must be determined solely by traversing the plan graph (nodes + edges).**
The accreted state also produces the inconsistencies seen in `SeedGraph` (two grouping mechanisms,
an "always untyped leaf" rule the code violates, a dead branch, a silent directive drop) and a
misleading `SeedGraph` name that implies it owns a graph artifact.

## What Changes

- **Node = typed variable.** `type` and `nullability` become independent, write-once attributes
  (unknown → determined → frozen). No node ever holds a per-consumer "expected type".
- **Edge = typed function.** Each REALISED edge carries its input `Slot`(s) — declared type **and**
  the consumer nullability contract (`AnnotatedConstruct`). `effectiveTypeFor` collapses to
  `node.getType()`; `consumerContractFor` is read off the consuming edge.
- **`ExpansionGroup` is dissolved as an active component.** **BREAKING (internal):** the class loses
  its view, `slots`, `slotMetadata`, `conversionFrontiers`, `codegen`, and SAT responsibilities.
  Group membership becomes a lightweight label (`GroupId`) carried on graph elements; group views are
  derived (`MaskSubgraph`) for expansion bookkeeping only. SAT stays structural and engine-memoized.
- **Generator is group-free.** **BREAKING (internal):** `BuildMethodBodies` / `PlanView` codegen
  paths touch only `Node` and `Edge`. N-ary assembly (`new P(a, b)`) is reconstructed from the
  output node's plan-edge fan-in, rendering once from `IncomingValues`. `GroupCodegen` folds into
  edge-carried `Codegen`. Invariant: no `group.*` reach-in remains in generation.
- **Seed registration unified to fan-in.** `registerSeedGroup` + `registerAssemblyGroups` collapse to
  one demand registration; the `targetChildren` accumulator and assembly post-pass are removed. The
  three structural "kinds" remain derivable from the graph (`GroupShapes`).
- **Variable identity owned by the graph.** A `MapperGraph` get-or-create (`variableFor(scope,
  location)`) replaces `SeedGraph`'s threaded `sourceCache` / `targetCache` / `targetChildren` maps.
- **Spec/robustness fixes.** The "bridging edge is always the untyped source leaf" rule is corrected
  (a single-segment source binds to its typed parameter root — code wins). The dead non-param
  first-segment branch and the silent empty-source drop are removed; `ValidateSourceParameters`
  becomes a hard precondition.
- **`*Stage` naming convention.** Every `implements Stage` class is renamed to end in `Stage`
  (`SeedGraph → SeedStage`, `ValidateSourceParameters → ValidateSourceParametersStage`,
  `DiscoverMappings`, `DiscoverAbstractMethods`, `DiscoverCallableMethods`,
  `ValidateNoDuplicateTargets`, `DumpPlan`/`DumpGraph`/`DumpTransforms`/`DumpFullGraph`). Internal
  `*Phase` orchestration classes keep their names.

## Capabilities

### New Capabilities
- None. This change modifies existing capabilities only.

### Modified Capabilities
- `graph-model`: `ExpansionGroup` is gutted to a non-traversable label (`GroupId` + `root`), losing
  its `AsSubgraph` view / `slots` / `codegen`; group membership is a label on `Node`; `Edge` carries
  the consumer `Slot` contract; `GroupCodegen` is removed (folded into `EdgeCodegen`); `MapperGraph`
  gains `variableFor(scope, location)`.
- `seed-graph`: single fan-in demand registration (no `targetChildren` post-pass, no placeholder
  group codegen); bridging edge binds from the deepest source variable (typed param root for
  single-segment sources); dead branch and silent drop removed; `*Stage` naming convention.
- `code-generation`: code is generated exclusively by traversing plan-graph nodes + edges; n-ary
  assembly derives from output-node fan-in; the consumer contract is read off the operand edge; the
  generator never reads `ExpansionGroup`.

> `graph-expansion` and `nullability` change at the **implementation** level only (view backing type
> `AsSubgraph → MaskSubgraph`, removed internal delta records, `conversionFrontiers` derived rather
> than stored) — their normative requirements already hold (contract not stored on the node;
> `setTyping` paired write-once), so no delta spec is needed for them.

## Impact

- **Graph model:** `Node`, `Edge`, `ExpansionGroup` (gutted/possibly deleted), `MapperGraph`.
- **Expansion pipeline (~24 files referencing `ExpansionGroup`):** `Applier`, `FrontierMatcher`,
  `ExpansionState(Impl)`, `GroupShapes`, the four `*Expander`s, `InputAllocator`, `SlotResolver`,
  `GroupOutcome`, `ExpansionSnapshot`, `GraphDelta` + delta records (`AddGroup`, `AddEdgeToView`,
  `RegisterConversionFrontier`).
- **Generation:** `PlanView`, `BuildMethodBodies`.
- **SPI:** `GroupCodegen` (folded into `Codegen`/`EdgeCodegen`), `Slot`.
- **Renames:** ~10 `Stage` classes + `Pipeline` / `ProcessorModule` wiring + `STRATEGY_FQN` constants.
- **Specs:** `graph-model`, `graph-expansion`, `seed-graph`, `code-generation`, `nullability`.
- **Tests:** `SeedGraphSpec` and the expansion/generation suites; behaviour (generated output) is
  unchanged — only the in-memory representation and stage names change.
- **Affected teams:** processor/codegen owners only; no public mapper-facing API change.
