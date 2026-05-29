## 1. PlanView + cost oracle

- [x] 1.1 Add `PlanView` (implements `GraphSource`) in `processor/.../graph/` exposing `nodes()`, `edges()`, `nodesByScope(Scope)` over a chosen-plan edge set, mirroring `TransformsView`.
- [x] 1.2 Add a plan builder: compute eligible edges (`REALISED` ∧ contained in a `SAT`-outcome group via `groupOutcomes()` + `group.contains(edge)`).
- [x] 1.3 Build the cost oracle: weighted projection of the eligible subgraph via `AsWeightedGraph(eligible, e -> (double) e.weight)`, a virtual super-source with weight-0 edges to every source-parameter-leaf node, then `DijkstraShortestPath.getPaths(superSource)` for `d(n)`. Use a throwaway copy; never mutate `MapperGraph`.
- [x] 1.4 Assemble the plan by a target-to-source walk from each method return-root: AND node (single eligible group) keeps all slot edges + recurses all slots; OR node (>1 SAT group) keeps the group minimising `weight(slot→node) + d(slot)` (tiebreak by `Node.id()`), recurses only it.
- [x] 1.5 Add `MapperGraph.planView()` returning the built `PlanView`.

## 2. Codegen consumes the plan

- [x] 2.1 Point `BuildMethodBodies` at `graph.planView()` instead of `realisedSubgraph()`; update `findReturnRoot` / `indexGroupRootsByNode` / `inboundRealisedEdges` to read the plan view.
- [x] 2.2 Remove the `putIfAbsent` arbitrary group pick — in the plan view at most one group is rooted at any node; keep a loud failure if a render-reachable node has no producer.
- [x] 2.3 Unit test in `BuildMethodBodiesSpec`: a node with one `SAT` and one `UNSAT_NO_PLAN` sibling group renders the SAT branch and never touches the dead branch.
- [x] 2.4 Unit test `PlanView` directly: dead-sibling exclusion, AND keeps all slots, cheapest-of-two-SAT selection.

## 3. DumpPlan stage

- [x] 3.1 Add `DumpPlan` in `stages/dump/` mirroring `DumpTransforms` (`@Inject` deps, `isDebugGraphs()` gate, empty-graph skip, warning-on-IOException), rendering `planView()` to `<fqn>.plan.dot`.
- [x] 3.2 Wire `DumpPlan` into the pipeline (Dagger module + stage ordering) after validation, alongside the other dumps.

## 4. Verification

- [x] 4.1 Ran `:processor:test` + `:processor:integrationTest` — all green (new `PlanViewSpec`, `BuildMethodBodiesSpec` incl. dead-sibling scenario, and existing `EndToEndCodegenSpec` pass).
- [x] 4.2 Ran `./gradlew :mappers:classes` in `percolate-integration`. The `code generation failed` processor diagnostic is **gone** — `mapHuman` now emits a generated source (plan-view selection + slot-naming work). NOTE: also extended `slotName` to handle `SourceLocation` slots (container-unwrap groups), exposed once dead-sibling pruning let render reach those groups.
- [x] 4.3 With `percolate.debug.graphs=true`, `PersonMapper.plan.dot` (5 edges) is written and is a strict subset of `PersonMapper.transforms.dot` (which retains the dead `OptionalWrap` + extra `OptionalUnwrap` siblings). Dead-sibling pruning confirmed.

## 5. Out of scope — follow-up gap

- [x] 5.1 The generated `mapHuman` body does not yet compile: the chosen plan picked `SetWrap` (wrap a single element) over `SetCollect`, and no `.stream().map(...).collect(...)` iteration is emitted for EXITING/ENTERING scope transitions — generating `Set.of(this.mapAddress(person.getAddresses()))` (a `List` passed to `mapAddress(Address)`). This is **container Wrap-vs-Collect cardinality + element-iteration codegen**: a distinct gap that cheapest-cost cannot disambiguate (both `SetWrap` and `SetCollect` chains are SAT). Belongs in a follow-up change, not plan-view selection.
