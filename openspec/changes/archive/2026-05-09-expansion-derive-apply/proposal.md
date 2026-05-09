## Why

The expansion fixpoint loop currently depends on each `ExpansionPhase` self-reporting whether it mutated the graph (the `boolean` return of `apply`). Both loop termination and the per-seed expansion budget tick are downstream of that single signal. A recent bug — a phase mutating the graph but returning `false` — let the loop run forever without the budget ever firing, because the addition counter was conditioned on the very signal that lied. The cooperation-dependent design has no defense-in-depth: when the boolean is wrong, every safety mechanism fails at once.

The algorithmic core is also imperative in style: phases interleave reads of the graph with `graph.addNode` / `graph.addEdge` calls scattered through inner loops, threaded through `boolean anyAdded` accumulators. Helpers cannot be tested in isolation because each one both reads the graph and mutates it, and it is easy for new phase code to reintroduce the same class of fixpoint bug.

## What Changes

- **BREAKING (internal SPI):** `ExpansionPhase.apply(MapperGraph)` returns `void` instead of `boolean`. Phases no longer self-report change; the outer driver observes graph growth directly.
- Phases adopt the **derive → apply** discipline. Helpers compute `GraphDelta` values from the current graph state without mutating it; the phase commits all deltas through a single call to `MapperGraph.apply(GraphDelta)` per pass. No helper that returns a value SHALL also mutate the graph.
- Introduce `GraphDelta` immutable value type in `io.github.joke.percolate.processor.graph` carrying the nodes and edges to add. Add `MapperGraph.apply(GraphDelta)` as the single mutation entry point used by phases.
- Internal per-phase fixpoints collapse. The `while (changed)` loop inside `ResolveSourceChainsPhase.processUntypedEdges` is removed; the outer `ExpandStage` round picks up newly typed nodes on the next iteration. The outer round is the only fixpoint in the system.
- **BREAKING (internal SPI):** Replace the per-seed expansion budget (`EXPANSION_BUDGET = 100`, ticked by additions reported via the boolean return) with an unforgeable mapper-level round budget (`MAX_EXPANSION_ROUNDS`). The counter ticks once per iteration of the outer fixpoint loop, independent of any phase-reported signal. Convergence is detected externally by comparing `graph.edgeCount()` before and after each round.
- When the round cap fires, `ExpandStage` emits one generic `Diagnostics.error` against the mapper type ("expansion did not converge after N rounds"). The heuristic that tries to attribute the failure to a specific originating seed is removed. Cycle detection over `SEED + SUB_SEED` keeps its directive-specific diagnostic and remains the informative path for the diagnosable case.
- Cycle detection itself is unchanged in behaviour.

`MapperContext` remains mutable (stages still set fields on it). `ExpansionPhase` strategy SPI (`SourceStep`, `Bridge`, `GroupTarget`, `Step`, `BridgeStep`) is unchanged — those interfaces already return values rather than mutating.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `graph-expansion`: `ExpansionPhase` contract changes from `boolean apply(MapperGraph)` to `void apply(MapperGraph)`; outer-loop termination uses `edgeCount()` delta rather than aggregated boolean returns; per-seed addition budget replaced with mapper-level round budget; round-cap diagnostic is mapper-level and generic.
- `graph-model`: introduces `GraphDelta` value type and `MapperGraph.apply(GraphDelta)`. Existing `addNode` / `addEdge` remain (still used by `SeedGraph` and internally by `apply`); no removal.

## Impact

- **Production code:** `ExpandStage`, `ExpansionPhase`, `BridgeSourceToTargetPhase`, `ResolveSourceChainsPhase`, `ResolveTargetChainsPhase`, `MapperGraph` (new method), new `GraphDelta` class. All changes are confined to `processor.graph` and `processor.stages.expand`.
- **Tests:** Existing phase-level tests continue to assert end-state graphs and remain valid. The boolean-return assertions disappear. New pure-function tests of `derive`/`deltaFor` helpers become possible without constructing a `MapperGraph` fixture.
- **External SPI (`processor.spi.*`):** Unchanged. Custom strategy implementers are unaffected.
- **Diagnostics:** Round-cap message is mapper-level and generic; lineage-specific cycle diagnostic unchanged.
- **Dependencies:** None added or removed; no Java version change.
- **Maintainers:** Single-maintainer project.
