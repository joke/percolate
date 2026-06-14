## 1. Foundations (additive)

- [x] 1.1 Add a comparable `Cost` value type in `processor.graph`: either `Cost.INFINITE` or a finite `(partials:int, weight:double)`, with `compareTo` ordering `partials` then `weight`; `Cost.ZERO`; `plus` (`⊗`, INFINITE-absorbing componentwise add) and `min` (`⊕`); `isReachable()`.
- [x] 1.2 Add `Location.Role` enum (`SUPPLY`, `DEMAND`, `ELEMENT`, `CONSTANT`) and `Location.role()`; implement on `SourceLocation`→SUPPLY, `TargetLocation`→DEMAND, `ElementLocation`→ELEMENT, `ConstantLocation`→CONSTANT.
- [x] 1.3 Spock spec for `Cost`: lexicographic order (totality dominates weight), INFINITE absorbing in `plus`, `min` semantics, ZERO base, reachability=finiteness.

## 2. Unified fold in ExtractedPlan

- [x] 2.1 Reimplement `ExtractedPlan` over `Cost`: one memoized `cost(Value):Cost` / `cost(Operation):Cost` DFS (keep the cycle guard writing INFINITE before recursing), replacing the separate `costOf`(double) and `partialOf`(int) recursions.
- [x] 2.2 `cheapestProducer` selects `argmin` over producers by `Cost` with `Operation.id()` as the selection-local tie-break, returning empty when no producer is reachable (drop the `filter(isSat)`).
- [x] 2.3 Add public `reachable(GraphVertex)` (`cost < INFINITE`) and `cost(Value)` queries; base case via `Location.role()` (`SUPPLY`/`ELEMENT`).
- [x] 2.4 Extend `ExtractedPlanSpec`: reachability-from-cost (supply-root reachable, orphan not), zero-weight cycle unreachable, scope-owning op unreachable when child return-root has none; existing OR/cost-sum/totality cases retained.

## 3. Repoint consumers off the SAT store

- [x] 3.1 `RealisationDiagnosticsStage`: build an `ExtractedPlan` and diagnose a method whose return-root is `!reachable` (was `!isSat`); closest-miss walk uses `!plan.reachable(op/source)`; message unchanged.
- [x] 3.2 `DumpTransformsStage` (the SAT-filtered dump): include reachable vertices via `ExtractedPlan.reachable` instead of `graph.isSat`. The full dump (`vertex -> true`) and `DotRenderer` needed no SAT change.
- [x] 3.3 Test harness: no change required — `ExpansionResult`/`ExpansionAssertions` held no `graph.isSat` reference (grep-confirmed); reachability assertions already route through the plan.

## 4. Delete the SAT machinery

- [x] 4.1 Delete `stages/expand/HornSat` and drop the `HornSat.propagate` call from `ExpandStage`.
- [x] 4.2 Remove `markSat`/`isSat`/`clearSat`/`satVertices` (and now-unused imports) from `MapperGraph`.
- [x] 4.3 Replace the driver demand-gate `instanceof TargetLocation` with `Location.role() == DEMAND` (the named Driver.expand site; ExtractedPlan base case uses `role()` too). grep-verified: no `isSat`/`HornSat`/SAT-store references remain in main or test.
- [x] 4.4 Retire `HornSatSpec` (its cycle / all-ports cases folded into `ExtractedPlanSpec` as cost/reachability cases).

## 5. Verify

- [x] 5.1 `./gradlew check` green — passes (unit + integration + spotless + PMD + CodeNarc).
- [x] 5.2 Integration suite green confirms compiles + semantically-equivalent output; byte-identity holds by construction (the new `min` over `Cost=(partials,weight)` then `id` is the same lexicographic key as the old `(partialOf, costOf, id)`, and `filter(reachable)` selects the same set as `filter(isSat)`).
- [x] 5.3 Committed (654797d) with /commit-commands:commit.
