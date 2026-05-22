## 1. Preflight

- [x] 1.1 Confirm `./gradlew check` is green on the current branch before any change in this task list; abort if not — this change must not be blended with unrelated failures
- [x] 1.2 Confirm the in-progress change `unit-test-builtin-strategies` has either landed or is at a state where its per-strategy specs are available, so that test failures triggered by the symmetric-emission fix (task 3) can be pinned in the right location

## 2. `EdgeKind.ELEMENT_SEED` and cascade

- [x] 2.1 Add the constant `ELEMENT_SEED` to `processor/src/main/java/io/github/joke/percolate/processor/graph/EdgeKind.java` (graph-model spec — `Requirement: EdgeKind enum`)
- [x] 2.2 Update `Edge.elementSeed(...)` in `processor/src/main/java/io/github/joke/percolate/processor/graph/Edge.java` to construct edges with `kind = EdgeKind.ELEMENT_SEED` instead of `EdgeKind.SEED` (graph-model spec — `Requirement: Edge value type`)
- [x] 2.3 Update `RealisedSubgraph` in `processor/src/main/java/io/github/joke/percolate/processor/graph/MapperGraph.java` (or its dedicated file) so its edge mask excludes `EdgeKind.ELEMENT_SEED` in addition to the existing exclusions (graph-model spec — `Requirement: RealisedSubgraph view`)
- [x] 2.4 Update `BridgeGraphQuery.collectElementScopeSeedEdges` and `BridgeSourceToTargetPhase.seeds(...)` to iterate `EdgeKind.ELEMENT_SEED` edges between `ElementLocation` endpoints (instead of `EdgeKind.SEED` edges with that endpoint shape) (graph-expansion spec — `Requirement: BridgeSourceToTargetPhase`)
- [x] 2.5 Update `DotRenderer` to register a distinct style for `EdgeKind.ELEMENT_SEED` (proposal: solid line, low penwidth, color `#3366aa`) and emit the literal label token `ELEMENT_SEED` for these edges, per `Requirement: Node and edge visual distinction` and `Requirement: Edge label includes EdgeKind marker`
- [x] 2.6 Update existing Spock specs that asserted `EdgeKind.SEED` for element-seed edges to assert `EdgeKind.ELEMENT_SEED`; update spec asserts for `ExpansionFailureModesSpec`, the property base, and any test fixtures that constructed element-seed edges via the SEED factory
- [x] 2.7 Add a Spock spec asserting the append-only invariant on `MapperGraph` (graph-model spec — `Requirement: MapperGraph is append-only after construction`): no public method removes nodes or edges; `MARKER` edges produced during expansion are still present in `MapperGraph.edges()` after `ExpandStage`
- [x] 2.8 Run `./gradlew :processor:test :strategies-builtin:test` and confirm green before proceeding

## 3. Symmetric SUB_SEED emission

- [x] 3.1 Refactor `BridgeSourceToTargetPhase`: collapse `applyUnifiedEmissionRule` and `applySubSeedEmissionRule` into one shared private helper that implements the six-step rule in `graph-expansion` spec `Requirement: Bridge edge-emission rule (unified)`; both top-level-seed and SUB_SEED-driven code paths delegate to the helper; the helper processes `step.getElementSeeds()` unconditionally
- [x] 3.2 Add a Spock spec in `processor/src/test/groovy/.../stages/expand/` asserting the `Scenario: SUB_SEED-triggered bridge query emits element seeds` from the graph-expansion delta: given a SUB_SEED triggering a bridge query whose returned step has element seeds, both phantom nodes are materialised and the `ELEMENT_SEED` edge is emitted
- [x] 3.3 Run `./gradlew :processor:test` and confirm green before proceeding

## 4. `TransformsView` and `MapperGraph.transformsView()`

- [x] 4.1 Add a `TransformsView` class in `processor/src/main/java/io/github/joke/percolate/processor/graph/` implementing the filter contract in graph-debug-output spec `Requirement: TransformsView filter on MapperGraph`: edge mask `kind == EdgeKind.REALISED`; vertex mask "incident on at least one retained REALISED edge"; implemented over JGraphT `MaskSubgraph`
- [x] 4.2 Add a `MapperGraph.transformsView()` method returning the new view; existing `expandedView()` method MAY remain temporarily but SHALL be deleted in task 6.4
- [x] 4.3 Add a `TransformsViewSpec` in `processor/src/test/groovy/.../graph/` covering every scenario in `Requirement: TransformsView filter on MapperGraph`: only REALISED edges pass; only nodes incident on REALISED pass; dead-end transformations retained; ElementLocation phantoms retained when REALISED-incident; no mutation of the underlying graph

## 5. `DumpFullGraph` and renamed `DumpTransforms`

- [x] 5.1 Add `DumpFullGraph` stage in `processor/src/main/java/io/github/joke/percolate/processor/stages/dump/` per graph-debug-output spec `Requirement: DumpFullGraph stage`: renders the underlying `MapperGraph` directly (no view), writes to `<MapperFQN>.full.dot`, respects `ProcessorOptions.debugGraphs` and empty-graph short-circuit, reports filer failure as a warning
- [x] 5.2 Add a `DumpFullGraphSpec` covering every scenario in the `DumpFullGraph stage` and `Full DOT file naming` requirements
- [x] 5.3 Rename `DumpExpandedGraph` → `DumpTransforms` (package unchanged); its filename changes to `<MapperFQN>.transforms.dot`; it consumes `MapperGraph.transformsView()` (graph-debug-output spec — `Requirement: DumpTransforms stage`, `Requirement: Transforms DOT file naming`)
- [x] 5.4 Rename `DumpExpandedGraphSpec` → `DumpTransformsSpec`; update assertions for the new filename and view; cover every scenario in `Requirement: DumpTransforms stage`
- [x] 5.5 Run `./gradlew :processor:test` and confirm green before proceeding

## 6. `ValidateRealisationStage` rewrite

- [x] 6.1 Implement the `satisfy(Node)` algorithm per realisation-validation spec `Requirement: Per-directive satisfy() search`: recursive AND-OR search; base case at source-parameter nodes; cycle handling via per-invocation `visited` set; promises are `SUB_SEED` edges rooted at `E.source` and `ELEMENT_SEED` edges whose source's parent equals `E.source`; first SAT wins; deepest miss tracked across alternatives; tie-break by `strategyClassFqn`
- [x] 6.2 Implement the closest-miss diagnostic formatter per realisation-validation spec `Requirement: Closest-miss diagnostic`: one-line headline plus multi-line detail; format strings for element-conversion miss, no-producer-at-all, and cycle cases; byte-stable output (no timestamps, deterministic ordering)
- [x] 6.3 Rewrite `ValidateRealisationStage` to invoke `satisfy(directive.realisedTarget)` per directive and emit diagnostics via the formatter (anchored at the `@Map` `AnnotationMirror` per `Requirement: Diagnostic anchoring at @Map AnnotationMirror`); remove the Tier-2/Tier-3 phase split
- [x] 6.4 Delete `ValidateMarkersPhase.java`, `ValidatePathsPhase.java`, and `ExpandedGraphView.java`; delete the previous `MapperGraph.expandedView()` method
- [x] 6.5 Add `SatisfyAlgorithmSpec` in `processor/src/test/groovy/.../stages/validate/` covering every scenario in `Requirement: Per-directive satisfy() search`: base case, single-hop SAT, missing producer UNSAT, unsatisfiable SUB_SEED promise UNSAT, unsatisfiable ELEMENT_SEED promise UNSAT, first SAT wins, deepest miss reported, tie-break by FQN, cycle UNSAT, visited set per-invocation
- [x] 6.6 Add `RealisationErrorMessagesSpec` in `processor/src/test/groovy/.../stages/validate/` covering every scenario in `Requirement: Closest-miss diagnostic`: element-conversion canonical message, no-producer-at-all message, byte-stable across runs

## 7. Pipeline wiring

- [x] 7.1 Update `ProcessorModule.stages(...)` to the order in design.md §D9: `…SeedGraph → DumpGraph → ExpandStage → DumpFullGraph → DumpTransforms → ValidateRealisationStage`; the three dump stages run before validation so files land on disk regardless of the verdict; remove the existing `DumpExpandedGraph` injection (replaced by `DumpTransforms`) and the `ValidateRealisationStage` injection wiring for the removed phases
- [x] 7.2 Run `./gradlew :processor:test` and confirm the wiring change does not break existing pipeline tests; pin any flipped scenario per task 8.1

## 8. Test audit

- [x] 8.1 Audit `processor/src/test/groovy/.../stages/expand/ExpansionFailureModesSpec.groovy`: for each scenario that now flips red, either (a) update the assertion to the new closest-miss error shape if the original scenario was about a legitimate failure mode, or (b) pin under `// FOLLOW-UP:` with a one-line rationale if the scenario was green-by-accident under the old validation (i.e. unsoundness that the old `ValidatePathsPhase` did not catch). Document each pin
- [x] 8.2 Audit property tests under `processor/src/test/groovy/.../stages/expand/properties/`: any oracle that compared against the old Tier-2/Tier-3 output SHALL be rewritten in terms of the satisfy() verdict (SAT vs. UNSAT) and the closest-miss message shape; add the new property "every directive either succeeds satisfy() or produces exactly one error diagnostic"
- [x] 8.3 Grep for references to removed classes across all modules — `ValidateMarkersPhase`, `ValidatePathsPhase`, `DumpExpandedGraph`, `ExpandedGraphView`, `MapperGraph.expandedView` — and update or delete every reference

## 9. Integration verification

- [x] 9.1 In `~/Projects/joke/percolate-integration` with `mapAddress` commented out in `PersonMapper.java`, run `./gradlew :mappers:classes` and confirm the compile **fails** with a closest-miss error message that (a) names `SetMap` as the considered strategy, (b) identifies the missing element conversion `Optional<Person.Address>` → `Human.Address` (or `Person.Address` → `Human.Address`), and (c) suggests the likely missing `@Map`-annotated method
- [x] 9.2 Confirm that all three dot files are present under `mappers/build/generated/sources/annotationProcessor/java/main/`: `io.github.joke.testing.PersonMapper.seed.dot`, `io.github.joke.testing.PersonMapper.full.dot`, `io.github.joke.testing.PersonMapper.transforms.dot`; confirm `*.expanded.dot` is absent
- [x] 9.3 Restore `mapAddress` in `PersonMapper.java` and re-run `./gradlew :mappers:classes`; confirm the compile succeeds and the three dot files reflect the satisfiable graph

## 10. Verify

- [x] 10.1 Run `./gradlew check` from the repo root and confirm all checks green: every new spec passes, no Spotless / NullAway / Errorprone violations introduced, existing `processor`-module algebraic and failure-mode specs still pass (after the audit in task 8.1), existing `strategies-builtin` per-strategy specs still pass (after the kind-name update in task 2.6), `BuiltinServiceRegistrationSpec` still passes. NEVER continue if there are violations.
