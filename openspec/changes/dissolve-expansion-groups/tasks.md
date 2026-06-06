## 1. Stage rename (isolated mechanical commit)

- [x] 1.1 Rename every `implements Stage` class to end in `Stage` (`SeedGraph → SeedStage`, `ValidateSourceParameters → ValidateSourceParametersStage`, `DiscoverMappings`, `DiscoverAbstractMethods`, `DiscoverCallableMethods`, `ValidateNoDuplicateTargets`, `DumpPlan`, `DumpGraph`, `DumpTransforms`, `DumpFullGraph`); leave `*Phase` classes unchanged
- [x] 1.2 Update `Pipeline` / `ProcessorModule` wiring and any Dagger bindings for the renamed stages
- [x] 1.3 Update `STRATEGY_FQN` constants and `GroupShapes.SEED_PACKAGE_PREFIX` references to the new `SeedStage` FQN
- [x] 1.4 Rename `SeedGraphSpec` → `SeedStageSpec` and any other stage specs; build green

## 2. Graph model — new attributes & identity

- [ ] 2.1 Add `GroupId` thin value type (wrapper over a monotonic `int`) with cheap equality/ordering
- [ ] 2.2 Add insertion-ordered `Set<GroupId> groups()` membership to `Node`, mutated only by the `Applier`; exclude from `equals`/`hashCode`
- [ ] 2.3 Add the consumer `Slot` to `REALISED` `Edge` (declared type + `AnnotatedConstruct producedFrom`); update `Edge.realised(...)` factories
- [ ] 2.4 Add `MapperGraph.variableFor(scope, location)` get-or-create keyed by `(scope, location)`; document the boundary (seed-time structural only; expansion mints fresh)

## 3. Gut ExpansionGroup to a label

- [ ] 3.1 Reduce `ExpansionGroup` to `{GroupId id, Node root}` + a derived `view()` returning a `MaskSubgraph(underlying, v -> !v.groups().contains(id), e -> e.kind != REALISED)`
- [ ] 3.2 Remove `slots`, `codegen`, `strategyClassFqn`, `AsSubgraph` view, `slotMetadata`, `expectedTypeFor`, `consumerContractFor`, `conversionFrontiers`, `addVertexToView`, `addEdgeToView`, `initialEdges`/`validateInitialEdge`
- [ ] 3.3 Delete the `AddEdgeToView` and `RegisterConversionFrontier` delta records; update `GraphDelta` and the `Applier` accordingly
- [ ] 3.4 Remove `GroupCodegen` SPI; migrate its render usage to edge-carried `Codegen`/`EdgeCodegen`

## 4. Expansion engine — tag-based membership

- [ ] 4.1 `Applier`: replace `addVertexToView`/`addEdgeToView` with node group-tagging at the single mutation site; attach the `Slot` onto the produced `REALISED` edge
- [ ] 4.2 `FrontierMatcher` / `InputAllocator` / expanders: stop populating `slotMetadata`; carry the `Slot` on the edge; keep per-`(name,type)` leaf duplication for competing producers (D3)
- [ ] 4.3 `ExpansionStateImpl.effectiveTypeFor` collapses to `node.getType()`; remove `expectedTypeFor` fallback
- [ ] 4.4 Derive `conversionFrontiers` behaviour from competing-producer fan-in (no stored set); keep "stops at SAT" / type-keyed bounds
- [ ] 4.5 Keep SAT engine-memoized (`satGroups`); ensure `GroupShapes` dispatch reads tagged inputs, not `getSlots()`

## 5. Plan selection & generation — group-free

- [ ] 5.1 `PlanView`: select cheapest REALISED edges using demand labels + engine SAT; emit a pure node+edge `MaskSubgraph`; re-express `group.contains(edge)` / `getRoot` / `getSlots` cost & pruning over fan-in
- [ ] 5.2 `BuildMethodBodies`: replace the group-target case with the **assembly fan-in** case (operands = output node's incoming REALISED plan edges, producer `Codegen` from the edges); delete the group-by-root index
- [ ] 5.3 Nullability-aware wiring: read the consumer contract from the operand **edge's** `Slot.producedFrom`, not from a group
- [ ] 5.4 Assert the invariant: no `ExpansionGroup`/`getCodegen`/`getSlots` reference remains in `BuildMethodBodies` or the composer

## 6. SeedStage — unified registration & robustness

- [ ] 6.1 Replace `registerSeedGroup` + `registerAssemblyGroups` + `targetChildren` with one `registerDemand(root, inputs)` that tags nodes with a shared `GroupId`
- [ ] 6.2 Route seed-time node creation through `MapperGraph.variableFor(...)`; remove `sourceCache` / `targetCache` / `targetChildren` threading
- [ ] 6.3 Bridge from the deepest source variable: typed param root for single-segment sources (drop the untyped-leaf mint)
- [ ] 6.4 Remove the dead non-parameter first-segment branch and the silent empty-source drop; make `ValidateSourceParametersStage` a hard precondition (reject, not just diagnose)

## 7. Specs sync & verification

- [ ] 7.1 Run `./gradlew :processor:test`; green across `SeedStageSpec`, expansion, and generation suites
- [ ] 7.2 Add a test: group `view()` contains exactly its REALISED edges on a shared boundary node (`person.address`) — no cross-group leak
- [ ] 7.3 Add a test: two type-divergent leaves at one `(scope, location)` stay distinct (not obtained via `variableFor`)
- [ ] 7.4 Diff debug DOT dumps (`.full.dot` / `.transforms.dot`) before/after for a representative mapper — topology unchanged
- [ ] 7.5 `openspec validate dissolve-expansion-groups`; `opsx:verify`; then `opsx:sync` the delta specs and `opsx:archive`
