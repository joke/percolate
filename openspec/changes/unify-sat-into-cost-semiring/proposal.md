## Why

Satisfaction, cost, and totality are three names for one bottom-up fold over the same AND/OR graph,
but the engine computes them in three places: `HornSat` (a boolean reachability fixpoint, recomputed
from scratch in `ExpandStage`), `ExtractedPlan.costOf` (`min` / `weight + Σ`), and
`ExtractedPlan.partialOf` (the totality count). `ExtractedPlan.cheapestProducer` already selects with
a single lexicographic comparison — `(partiality, cost, id)` — and already encodes `unreachable ⟺
cost = +∞` (`UNREACHABLE`). `HornSat` is therefore a redundant pass: the selection comparator *is* the
reachability predicate. Unifying the three into one cost recursion removes a whole stage and the
graph's SAT store, and — because selection becomes a lexicographically-ordered cost vector — makes
every future selection preference (prefer non-deprecated, prefer fewer allocations, prefer
user-authored producers) a new vector component that the algorithm consumes for free, with no engine
change. The project is in evaluation with no releases, so consolidating now is cheapest.

## What Changes

- Replace the separate `HornSat` reachability pass and the two separate `ExtractedPlan` recursions
  with **one bottom-up minimum-cost hyperpath fold** over a **lexicographically-ordered cost vector**
  (Knuth's minimum-cost-hyperpath shape): `⊕ = min` over a `Value`'s producers, `⊗ = componentwise
  combine` at an `Operation` (`weight: w + Σ`, `partial: p + Σ`), with `0 = (0,0)` at supply roots and
  zero-port Operations and `∞` for unreachable. **Reachability is derived, not stored**:
  `reachable(v) ⟺ cost(v) < ∞`.
- Define the cost vector as `(partiality, weight, id-tiebreak)` — totality dominance is the most
  significant component, exactly the current `cheapestProducer` ordering — and make the vector the
  documented extension point: a new preference is a new component, not a new pass.
- **Remove** the standalone "Horn SAT propagation" computation from the expansion phase: expansion
  still over-emits candidate Operations and drains the work-list; it no longer computes or records a
  SAT predicate. SAT is computed lazily during plan extraction.
- **Remove** the SAT predicate store from `MapperGraph` (`markSat` / `isSat` / `clearSat` /
  `satVertices`); reachability queries route through the extraction cost fold.
- Restate "unsatisfied" everywhere downstream as "infinite extraction cost": realisation diagnostics
  walk infinite-cost demands; the full debug dump annotates reachability (finite-cost) rather than a
  stored SAT bit; the test harness exposes the plan/cost view rather than a SAT predicate.
- **Fold in the `Location` role cleanup (Thread D):** replace the `instanceof SourceLocation /
  TargetLocation / ElementLocation` dispatch — duplicated across the deleted `HornSat.isSupplyRoot`,
  `ExtractedPlan.isSupplyRoot`, and `Driver.expand` — with a single `Location.role()`
  (`SUPPLY` / `DEMAND` / `ELEMENT` / `CONSTANT`). The supply-root base case of the cost fold becomes
  one predicate in one place. This rides along because deleting `HornSat` already removes one of the
  two duplicated `isSupplyRoot` copies.
- **Not changing:** plan *selection results*. The selection comparator is already `(partiality, cost,
  id)`; moving reachability into the same fold does not change which producer wins, so generated
  mapper output is expected to be unchanged (the integration suite arbitrates). The target-to-source
  work-list, the bipartite model, scopes, deltas, and the SPI are untouched.
- **BREAKING (internal only):** the `MapperGraph` SAT-store methods are removed; their internal
  callers (`HornSat`, `ExtractedPlan`, `RealisationDiagnosticsStage`, dump, harness) move to the cost
  fold. No public `@Map` surface or generated-code change.

## Capabilities

### New Capabilities

<!-- none — this is a consolidation of existing selection/reachability requirements -->

### Modified Capabilities

- `plan-extraction`: the cost recursion, totality dominance, and reachability are restated as one
  lexicographic cost-vector hyperpath fold; `unreachable ⟺ cost = ∞`; the cost vector is the named
  extension point for future selection preferences.
- `graph-expansion`: the "Horn SAT propagation" requirement is removed — expansion no longer computes
  a SAT predicate; satisfaction is derived during extraction (well-foundedness via the cost cycle
  guard, unchanged).
- `graph-model`: the `MapperGraph` wrapper requirement drops the SAT predicate store from its surface;
  `Location` gains a `role()` (`SUPPLY` / `DEMAND` / `ELEMENT` / `CONSTANT`) that replaces `instanceof`
  dispatch for the supply-root / demand / element / constant distinction.
- `realisation-validation`: an unsatisfied demand is one with infinite extraction cost (no
  finite-cost producer) rather than a stored `¬SAT`; the closest-miss walk is otherwise unchanged.
- `graph-debug-output`: the full dump annotates reachability (finite extraction cost) instead of a
  stored SAT bit.
- `expansion-test-harness`: harness results expose the extracted plan / cost-reachability view
  instead of a SAT predicate.

## Impact

- **`processor` module** — delete `stages/expand/HornSat`; remove the SAT store from
  `graph/MapperGraph`; fold reachability + cost + totality into `graph/ExtractedPlan` as one
  vector-valued memoized DFS (it already has the cycle guard); repoint `stages/validate/
  RealisationDiagnosticsStage`, `stages/dump/*`, and the test harness at the cost fold; drop the
  `HornSat.propagate` call from `ExpandStage`.
- **`spi` module** — none. The strategy surface (`ExpansionStrategy`, `OperationSpec`, `Demand`) is
  unchanged.
- **Tests** — `HornSatSpec` retired or folded into the extraction spec; `ExtractedPlanSpec` extended
  with reachability-via-cost cases; dump goldens unchanged (structure) but SAT annotation reworded;
  integration suite must remain green with unchanged generated output.
- **`Location` role (Thread D)** — add `Location.role()` and repoint the supply-root/demand/element
  checks in `ExtractedPlan` and `Driver` at it; the `ElementLocation`/`SourceLocation`/`TargetLocation`/
  `ConstantLocation` implementations gain the role.
- **Out of scope** — the larger "pure-pull / uniform-demand driver" restructure (`uniform-demand-engine`).
- **Affected teams** — single-maintainer project; `processor` only.
- **Dependencies** — none added; JGraphT remains the substrate (`MaskSubgraph` views retained, no new
  use).
