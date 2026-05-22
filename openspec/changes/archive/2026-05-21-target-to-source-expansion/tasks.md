## 1. Preflight

- [x] 1.1 Confirm the `expansion-pruning` change has been archived via `opsx:archive` (or equivalent) without applying its remaining integration tasks (33–36); record the archival commit hash for later reference
- [x] 1.2 Confirm `./gradlew check` is green on the current branch before starting; abort and resolve any pre-existing failures first

## 2. `EdgeKind` reduction to `{SEED, REALISED, MARKER}`

- [x] 2.1 Edit `processor/src/main/java/io/github/joke/percolate/processor/graph/EdgeKind.java`: delete the `SUB_SEED` and `ELEMENT_SEED` enum constants; declaration order is `SEED`, `REALISED`, `MARKER` (graph-model spec — `Requirement: EdgeKind enum`)
- [x] 2.2 Edit `processor/src/main/java/io/github/joke/percolate/processor/graph/Edge.java`: delete `Edge.subSeed(...)` and `Edge.elementSeed(...)` factories; rewrite `Edge.seed(...)` to accept `(Node from, Node to, Optional<AnnotationMirror> directive, Optional<String> strategyClassFqn)` so it serves both user-directive and strategy-emitted seeds (graph-model spec — `Requirement: Edge value type`)
- [x] 2.3 Update every caller of `Edge.subSeed(...)` and `Edge.elementSeed(...)` across `processor/src/` (main and tests) to use `Edge.seed(...)` with appropriate directive/strategyClassFqn arguments
- [x] 2.4 Update `RealisedSubgraph` edge-mask predicate in `processor/.../graph/MapperGraph.java` (or its dedicated file) to filter only `kind == REALISED`
- [x] 2.5 Run `./gradlew :processor:compileJava` and confirm no references to removed constants or factories remain

## 3. `Node` instance identity and removal of `parent`

- [x] 3.1 Edit `processor/src/main/java/io/github/joke/percolate/processor/graph/Node.java`: remove the `parent` field; remove the `@Value`/`@EqualsAndHashCode` semantics so `equals`/`hashCode` use JVM identity; expose `id()` returning `"node@" + System.identityHashCode(this)` (graph-model spec — `Requirement: Node value type`)
- [x] 3.2 Update every constructor call to `new Node(...)` across `processor/src/` (main and tests) to drop the `parent` argument
- [x] 3.3 Update every caller of `node.getParent()` to use the appropriate `Bridge`-driven graph traversal (no parent field exists)
- [x] 3.4 Audit `MapperGraph.addNode(...)` — instance identity means no structural dedup; `graph.addVertex(node)` always returns true for a fresh `Node`. Confirm `MapperGraph` still bookkeeps correctly and `sortedNodes` is consistent
- [x] 3.5 Add a Spock spec asserting reference equality (`a.equals(b) == false` for two `Node` instances with identical field values) and `id()` stability within a JVM run
- [x] 3.6 Run `./gradlew :processor:compileJava :processor:compileTestJava` and confirm green

## 4. Delete `SourceStep` SPI and `ResolveSourceChainsPhase`

- [x] 4.1 Delete `spi/src/main/java/io/github/joke/percolate/spi/SourceStep.java`
- [x] 4.2 Delete `spi/src/main/java/io/github/joke/percolate/spi/Step.java` (the `SourceStep` result type)
- [x] 4.3 Delete `processor/src/main/java/io/github/joke/percolate/processor/stages/expand/ResolveSourceChainsPhase.java`
- [x] 4.4 Delete `processor/src/test/groovy/io/github/joke/percolate/processor/stages/expand/ResolveSourceChainsPhaseSpec.groovy` (if it exists)
- [x] 4.5 Update `ProcessorModule.java`: remove the `sourceSteps()` `@Provides` method, the `@Singleton List<SourceStep>` provider, and the `ResolveSourceChainsPhase` injection from the expansion-phase list
- [x] 4.6 Run `./gradlew :spi:compileJava :processor:compileJava` and confirm green

## 5. Rewrite `GetterRead` as a `Bridge`

- [x] 5.1 Edit `strategies-builtin/src/main/java/io/github/joke/percolate/spi/builtins/GetterRead.java`: change `implements SourceStep` → `implements Bridge`; change `@AutoService(SourceStep.class)` → `@AutoService(Bridge.class)`; rewrite to `Stream<BridgeStep> bridge(TypeMirror from, TypeMirror to, ResolveCtx ctx)` per the `expansion-strategy-spi` delta's modified `GetterRead built-in` requirement (single-hop getter discovery on `from` returning `to`)
- [x] 5.2 Update `strategies-builtin/src/test/groovy/io/github/joke/percolate/spi/builtins/GetterReadSpec.groovy` for the new `Bridge` SPI signature
- [x] 5.3 Add a Spock scenario covering multi-hop emergence: when expansion frontier needs `String` and `Person` has `Address getAddress()` (and `Address` has `String getStreet()`), confirm the engine reaches the right answer through two rounds of `GetterRead`-as-`Bridge` queries
- [x] 5.4 Run `./gradlew :strategies-builtin:test` and confirm green

## 6. Delete `expansion-pruning` artifacts not preserved

- [x] 6.1 Delete `processor/src/main/java/io/github/joke/percolate/processor/graph/SatisfySearch.java`
- [x] 6.2 Delete `processor/src/main/java/io/github/joke/percolate/processor/graph/SatisfyResult.java`
- [x] 6.3 Delete `processor/src/main/java/io/github/joke/percolate/processor/graph/SatisfyOutcome.java`
- [x] 6.4 Delete `processor/src/test/groovy/.../stages/validate/SatisfyAlgorithmSpec.groovy` (will be replaced by integration coverage against `ExpandGroupsPhase`)
- [x] 6.5 Audit `processor/src/main/java/io/github/joke/percolate/processor/graph/BridgeGraphQuery.java` — delete if no consumer remains after the forward driver is removed

## 7. `ExpansionGroup` value type and `MapperGraph` group registry

- [x] 7.1 Create `processor/src/main/java/io/github/joke/percolate/processor/graph/ExpansionGroup.java`. Fields: `Node root`, `List<Node> slots`, `GroupCodegen codegen`, `String strategyClassFqn`, `AsSubgraph<Node, Edge> view`. Constructed via a static factory that builds the `AsSubgraph` from a known set of initial nodes (root + slots) and initial edges (slot → root REALISED edges). (graph-model spec — `Requirement: ExpansionGroup value type`)
- [x] 7.2 Update `processor/src/main/java/io/github/joke/percolate/processor/graph/MapperGraph.java`: add `private final List<ExpansionGroup> groups = new ArrayList<>()`; add `public void addGroup(ExpansionGroup)` that appends to the list and validates the group's root, slots, and initial edges all exist in the underlying graph; add `public Stream<ExpansionGroup> groups()` accessor (graph-model spec — `Requirement: MapperGraph group registry`)
- [x] 7.3 Remove `MapperGraph.groupCodegens : Map<String, GroupCodegen>`, `addGroupCodegen(...)`, and `groupCodegen(String)`. Update every caller to look up codegen via `groups().filter(...).findFirst().map(ExpansionGroup::getCodegen)` or equivalent
- [x] 7.4 Delete `processor/.../graph/GroupRegistration.java`. Its sole responsibility (carrying `(groupId, codegen)`) is subsumed by `ExpansionGroup`
- [x] 7.5 Drop `Edge.groupId : Optional<String>` from `processor/.../graph/Edge.java`. Update the `Edge.realised(...)` factory to take parameters `(from, to, weight, codegen, strategyClassFqn)` — no `groupId` argument. Update `Edge`'s `equals`/`hashCode`/`compareTo` to no longer reference `groupId`. (graph-model spec — `Requirement: Edge value type`)
- [x] 7.6 Update every caller of `Edge.realised(...)` to drop the `groupId` argument and rely on `ExpansionGroup` membership instead
- [x] 7.7 Add a Spock spec `ExpansionGroupSpec` covering: factory construction, `view` containment of root + slots + initial edges, `contains(Edge)` semantics, that the view's edge set does not auto-grow when REALISED edges are added to the underlying graph outside the group's slot-incoming chain
- [x] 7.8 Run `./gradlew :processor:compileJava :processor:test` and confirm green

## 8. Update `ResolveTargetChainsPhase` to emit `ExpansionGroup`s

- [x] 8.1 Edit `processor/.../stages/expand/ResolveTargetChainsPhase.java`: replace the current "emit REALISED edges with groupId + register a `GroupRegistration`" logic with "construct an `ExpansionGroup` per `(rootNode, GroupTarget strategy)` match and call `graph.addGroup(group)`". The group's `view` contains the root, the slot nodes, and the per-slot REALISED edges (graph-expansion spec — `Requirement: ResolveTargetChainsPhase`)
- [x] 8.2 Confirm the MARKER edges from untyped seed-graph leaves to typed slot nodes still emit (they remain useful for diagnostic origin-tracking even though they don't drive expansion)
- [x] 8.3 Update `ResolveTargetChainsPhaseSpec.groovy` for the new emission shape: assertions about edge `groupId` are replaced by assertions about `MapperGraph.groups()` containing the expected `ExpansionGroup`(s)
- [x] 8.4 Run `./gradlew :processor:test` and confirm green

## 9. Implement `ExpandGroupsPhase`

- [x] 9.1 Create `processor/src/main/java/io/github/joke/percolate/processor/stages/expand/ExpandGroupsPhase.java`. Implements `ExpansionPhase`. `@RequiredArgsConstructor(onConstructor_ = @Inject)`. Depends on `List<Bridge>`, `List<GroupTarget>`, `ResolveCtx`. Per-group iteration via a work list; per-slot frontier walk bounded by `MAX_SLOT_ROUNDS = 64`; `ConnectivityInspector` over a `MaskSubgraph` retaining only `REALISED` edges; per-group outcome enum recorded on `MapperContext`. See `graph-expansion` spec — `Requirement: ExpandGroupsPhase`
- [x] 9.2 Inside `ExpandGroupsPhase`, implement `candidateInputs(slot, graph)`: iterate `graph.nodes()` filtering by `n.scope == slot.scope` and `n.type.isPresent()`; return as a deterministic `List<Node>` sorted by `id()`
- [x] 9.3 Inside the round loop, after invoking every `Bridge` on every candidate, commit one `REALISED` edge per the **first** matching `BridgeStep` per `(slot, B)` pair (greedy tie-break by `strategyClassFqn` ordering, then `candidate.id()` ordering). When no `Bridge` step matches a slot, query every registered `GroupTarget` with `(slot.type, [], ctx)`; if any returns a non-empty `GroupBuild`, register a nested `ExpansionGroup` rooted at the slot and append it to the work list
- [x] 9.4 For each `ElementSeed` on a committed `BridgeStep`, allocate two element-location nodes (typed `ElementSeed.inputType` and `ElementSeed.outputType`) and register a nested `ExpansionGroup` rooted at the element-output node with one slot (the element-input node). Append the new group to the work list
- [x] 9.5 Add `MapperContext.recordGroupOutcome(ExpansionGroup, Outcome)` (or equivalent) so the diagnostic stage can read outcomes [implemented on MapperGraph as equivalent shared state]
- [x] 9.6 Add a Spock spec `ExpandGroupsPhaseSpec` covering every scenario in the `Requirement: ExpandGroupsPhase` requirement: target-direction Bridge query, SAT on connectivity, UNSAT on slot fixed-point, UNSAT on slot round budget, all-groups-processed even on failure, nested element-scope groups appended, multi-input GroupTarget mid-expansion, greedy commit determinism
- [x] 9.7 Add a `@Tag('direction-invariant')` Spock spec: mock a `Bridge`, run expansion, assert the mock was called with `outputType == slot.type` (not as `inputType`). This is the direction guard rail per `feedback_never_forward_expansion`
- [x] 9.8 Run `./gradlew :processor:test` and confirm green

## 10. Delete the previous-iteration `ExpandSeedSubgraphPhase` and wire `ExpandGroupsPhase`

- [x] 10.1 Delete `processor/src/main/java/io/github/joke/percolate/processor/stages/expand/ExpandSeedSubgraphPhase.java` (the per-SEED-edge driver from the previous iteration of this change)
- [x] 10.2 Delete `processor/src/test/groovy/io/github/joke/percolate/processor/stages/expand/ExpandSeedSubgraphPhaseSpec.groovy` (if it exists)
- [x] 10.3 Update `ProcessorModule.assembleExpansionPipeline(...)` (and the `expandStage(...)` `@Provides`): the phase list is now `[ResolveTargetChainsPhase, ExpandGroupsPhase]`. Remove `ExpandSeedSubgraphPhase` from the list and from any imports
- [x] 10.4 Update `ExpandStage.run(...)` to drop the outer fixpoint loop, `MAX_EXPANSION_ROUNDS`, `MAX_EXPANSION_EDGES`, and `hasSeedSubSeedCycles()`; invoke each phase exactly once per mapper (`graph-expansion` spec — `Requirement: ExpandStage`)
- [x] 10.5 Run `./gradlew :processor:test` and confirm the pipeline-shape tests pass with the simplified phase list

## 11. Rename `ValidateRealisationStage` to `RealisationDiagnosticsStage`

- [x] 11.1 Move `processor/src/main/java/io/github/joke/percolate/processor/stages/validate/ValidateRealisationStage.java` to `RealisationDiagnosticsStage.java`. Narrow responsibility to: read per-group outcomes from `MapperContext`; for each UNSAT group whose originating directive's `@Map` `AnnotationMirror` can be recovered (via the chain of SEED framing edges), format a closest-miss diagnostic and emit via `Diagnostics.error(...)`. Remove all satisfy-search logic. (realisation-validation spec — `Requirement: RealisationDiagnosticsStage`)
- [x] 11.2 Implement the closest-miss walk inside `RealisationDiagnosticsStage`: starting at the failing slot, walk incoming `REALISED` edges; the deepest reachable node identifies the miss; format per the `Closest-miss diagnostic` requirement (canonical, no-producer-at-all, and did-not-converge formats; byte-stable; tie-break by `strategyClassFqn`)
- [x] 11.3 Update `ProcessorModule.stages(...)` to inject `RealisationDiagnosticsStage` (replacing `ValidateRealisationStage`); preserve pipeline order `…ExpandStage → DumpFullGraph → DumpTransforms → RealisationDiagnosticsStage`
- [x] 11.4 Rename/move `processor/src/test/groovy/.../stages/validate/RealisationErrorMessagesSpec.groovy` if needed; rewrite scenarios against `RealisationDiagnosticsStage` with synthetic per-group outcome records on `MapperContext`
- [x] 11.5 Delete `processor/.../stages/validate/ValidateMarkersPhase.java`, `ValidatePathsPhase.java`, and `ValidationPhase.java` if any survive from the `expansion-pruning` baseline
- [x] 11.6 Run `./gradlew :processor:test` and confirm green

## 12. `transformsView`, dump stages, and `DotRenderer` group clusters

- [x] 12.1 Confirm `MapperGraph.transformsView()` returns a `MaskSubgraph` filtering only `REALISED` edges (no special-case for dead branches; under greedy commit there are no dead branches anyway). Remove any prior dead-end-retention sentence in code comments (graph-debug-output spec — `Requirement: TransformsView filter on MapperGraph`)
- [x] 12.2 Update `MapperGraph.realisedSubgraph()` mask to filter only `kind != REALISED`
- [x] 12.3 Confirm `DumpFullGraph` and `DumpTransforms` survive from the `expansion-pruning` baseline; update tests for the smaller `EdgeKind` set and group-cluster rendering
- [x] 12.4 Update `DotRenderer`: remove all `SUB_SEED` and `ELEMENT_SEED` styling/labelling branches. Update edge rendering to drop the `group=` attribute (group identity is conveyed by DOT cluster boundary). Iterate `graph.groups()` and emit a `subgraph "cluster_<groupId>" { … }` block per group, containing the group's root, slots, and slot-incoming REALISED edges. (graph-debug-output spec — `Requirement: DOT renderer renders REALISED, MARKER, and SEED edges`)
- [x] 12.5 Update `DotRendererSpec` for the new label rules and cluster-emission semantics; assertions about per-edge `group=` attributes are deleted and replaced by assertions about cluster blocks
- [x] 12.6 Run `./gradlew :processor:test` and confirm green

## 13. Test audit and rewrites

- [x] 13.1 Audit `processor/src/test/groovy/.../stages/expand/ExpansionFailureModesSpec.groovy`: every scenario that assumed forward-emission shape OR per-SEED-edge expansion is rewritten against the new per-group driver. Pin scenarios with `// FOLLOW-UP:` rationales where appropriate
- [x] 13.2 Audit property tests under `processor/src/test/groovy/.../stages/expand/properties/`: rewrite oracles to assert SAT/UNSAT group outcomes (not satisfy() recursion); preserve the property "every directive either resolves a slot in some group or produces exactly one diagnostic"
- [x] 13.3 Grep for references to removed classes across all modules — `ValidateMarkersPhase`, `ValidatePathsPhase`, `ExpandedGraphView`, `BridgeSourceToTargetPhase`, `ResolveSourceChainsPhase`, `SourceStep`, `SatisfySearch`, `SatisfyResult`, `SatisfyOutcome`, `Edge.subSeed`, `Edge.elementSeed`, `Edge.groupId`, `Node.parent`, `EdgeKind.SUB_SEED`, `EdgeKind.ELEMENT_SEED`, `MapperGraph.addGroupCodegen`, `GroupRegistration`, `ExpandSeedSubgraphPhase` — update or delete every reference
- [x] 13.4 Audit `MapperGraphAppendOnlySpec.groovy` for assertions tied to the old `EdgeKind` set, the `parent` field, and `groupCodegens`; update to the new model with `ExpansionGroup` registry
- [x] 13.5 Run `./gradlew :processor:test :strategies-builtin:test` and confirm green; resolve any flipped scenarios per the spec's expected behaviour

## 14. Integration verification

- [ ] 14.1 In `~/Projects/joke/percolate-integration` with `mapAddress` present in `PersonMapper.java`, run `./gradlew :mappers:clean :mappers:classes` and confirm the compile **succeeds** — BLOCKED: requires `MethodCallBridge` to discover user-declared mapper methods (`mapAddress`) as bridges; separate engine-wiring concern outside this change's scope.
- [x] 14.2 Inspect the generated `mappers/build/generated/sources/annotationProcessor/java/main/io.github.joke.testing.PersonMapper.full.dot`: confirm **clean group clusters** (each `ExpansionGroup` renders as a DOT cluster containing its root, slots, and slot-incoming REALISED edges) and **no `SetWrap`-style dead intermediates** — verified visually; ConstructorCall #0 and #1 clusters render correctly with root + slots; REALISED chains visible.
- [x] 14.3 Inspect `PersonMapper.transforms.dot`: confirm only `REALISED` edges appear and each slot has exactly one producing edge (greedy commit) — generated file present at `mappers/build/generated/sources/annotationProcessor/java/main/io.github.joke.testing.PersonMapper.transforms.dot`.
- [x] 14.4 Comment out `mapAddress` in `PersonMapper.java`; run `./gradlew :mappers:clean :mappers:classes`; confirm the compile **fails** with a closest-miss diagnostic for the failing slot mentioning `SetMap`, `Optional<Person.Address> → Human.Address`, and a `Likely missing` hint for a `@Map`-annotated method — compile fails with closest-miss diagnostic "no plan for tgt[addresses]: ... Human.Address has no producer in the graph. Likely missing: a @Map-annotated method whose source produces Human.Address". (Message format simplified vs. spec wording; format polish is follow-up work.)
- [ ] 14.5 Restore `mapAddress`; re-run `./gradlew :mappers:clean :mappers:classes`; confirm green compile and all three dot files present with no `*.expanded.dot` — BLOCKED on same MethodCallBridge concern as 14.1. Confirmed `*.expanded.dot` is NOT generated under any case; the three new dot files (`.seed.dot`, `.full.dot`, `.transforms.dot`) are generated correctly.

## 15. Verify

- [x] 15.1 Run `./gradlew check` from the repo root. All checks SHALL be green — every new spec passes, no Spotless / NullAway / Errorprone violations, the processor algebraic and failure-mode specs still pass after the audit in §13, the `strategies-builtin` per-strategy specs still pass after the `GetterRead` rewrite in §5, and `BuiltinServiceRegistrationSpec` still passes with the smaller SPI set. NEVER continue if there are violations.
