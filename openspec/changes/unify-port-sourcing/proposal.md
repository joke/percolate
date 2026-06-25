## Why

The expansion driver reconstructs each port's *sourcing intent* from heuristics instead of reading it as a declared fact. `sourceForPort` decides "is this port a sub-target to assemble, a source to reuse, or an intermediate to mint?" by string-matching the port name against the goal spec's declared-children set **and** reading a `reuseOnly` boolean — a five-branch case analysis that also threads a `@Nullable` directive-pinned source through three call levels and hand-rolls a second, parallel landing path for accessor descent. The strategy already *knows* each port's intent (e.g. `ConstructorCall` fires only when its parameter names equal the declared children) but throws it away; the engine re-derives it. This is the last cluster of implicit case-switches in an otherwise mechanical, over-emit-and-prune engine — and it is exactly the surface the next wave of strategies (builders, captures, sub-class dispatch) will push on. Making intent explicit now is the foundation that lets those strategies drop in without growing new engine branches.

## What Changes

- **Add an explicit sourcing mode to `Port`** — a closed set `{SUBTARGET, REUSE, REUSE_OR_MINT}` a strategy stamps when it declares a port. The engine binds by dispatching on this mode, never by reconstructing intent.
- **Dissolve the declared-children name-match in the binding path.** `sourceForPort` collapses from a five-branch heuristic to a three-way dispatch on the declared mode; the `declaredChildren` set leaves the engine's binding path entirely and lives only in `ProduceDemand.declaredChildren()`, where assembly strategies gate on it.
- **Fold `reuseOnly` into the mode.** The `Port.reuseOnly` boolean and the `Port.reuse(...)` factory are restated as the `REUSE` mode. **BREAKING** (SPI source compatibility): `Port` construction changes shape.
- **Fold the directive-pinned source into source ranking.** Remove the `@Nullable Value pinnedSource` threaded through `expandFree → land → sourceForPort`; `SourceCandidates` ranks a directive-pinned source first, so one reuse lookup serves both "prefer the pinned source" and "any in-scope source of the port's type."
- **Share one landing primitive** behind the producer path and the accessor-descent path, so `AddOperation` construction is written once. The two control flows stay distinct (backward work-list vs forward target-bound descent); only the landing is shared.
- **Align `Location.Role` documentation to forward target-bound descent.** Confirm `ACCESS` remains the correct role for multi-segment source paths and that a producerless `ACCESS` can no longer arise; correct the stale "re-demanding the parent path" wording.
- **Reserve a future by-name binding mode (non-goal).** The mode set and `SourceCandidates` are shaped so a later `CAPTURE` mode (`@Context`-like — bound by name to an ambient source) slots in beside the three modes without reopening the closure. Not built here.

The bar is **semantic equivalence, not byte-identity**: every existing integration mapper graph and extracted plan MUST be unchanged. Invariants held non-negotiable: over-emit + cost-prune-to-one; strategies stay myopic and candidate-free; one cost fold subsumes SAT; the engine never chooses one strategy over another. Java 11 (no `sealed`, no pattern-`instanceof`).

## Capabilities

### New Capabilities

(none)

### Modified Capabilities

- `expansion-strategy-spi`: `Port` gains an explicit sourcing mode; a strategy SHALL declare each port's sourcing intent (`SUBTARGET` / `REUSE` / `REUSE_OR_MINT`); `Port.reuse` is restated as the `REUSE` mode; the mode set is documented as extensible (future by-name binding).
- `graph-expansion`: port binding dispatches on the declared sourcing mode; the declared-children name-match no longer participates in the binding path.
- `graph-model`: the `Location.Role` `ACCESS` description is aligned to forward target-bound descent — `ACCESS` values are produced forward and are base cases; a producerless `ACCESS` cannot arise.

> Threads B (fold the directive-pinned source into source ranking) and C (one shared landing primitive) are **implementation refactors with no requirement delta**: they preserve behavior already governed by `graph-expansion`'s "Forward target-bound descent walks a directive path" and "The driver is a pure work-list; the engine builds no Operations" requirements, and the `source-path-resolution` resolver requirements are unchanged.

## Impact

- **SPI (`spi`)**: `Port` (sourcing mode + factories) — source-incompatible for any third-party strategy constructing `Port`s directly; built-in and reactor strategies update in lockstep. `Demand` / `ProduceDemand` / `DescendDemand` surfaces unchanged.
- **Engine (`processor`)**: `ExpandStage` (`sourceForPort`, `land`, `descendSegment`, the `pinnedSource` threading), `SourceCandidates` (pinned-source ranking), `Location` (Role doc). No change to the cost fold, grounding-by-match, or the scope model.
- **Built-in strategies (`strategies-builtin`)**: `ConstructorCall` → `SUBTARGET`; `DirectAssign` / `NullnessCrossing` / `Container.unwrap` → `REUSE`; every other strategy relies on the `REUSE_OR_MINT` default.
- **Tests**: Spock specs over `ExpandStage` / `SourceCandidates` / `Port`; integration mapper graphs and extracted plans MUST be byte-unchanged (the semantic-equivalence bar).
- **Affected teams**: third-party `ExpansionStrategy` authors (the `Port` construction API changes).
