## Context

`BuildValueGraphStage` runs a fixpoint that asks each `TypeTransformStrategy` to propose edges between typed value nodes in a per-method `DefaultDirectedGraph<ValueNode, ValueEdge>`. The value-graph refactor declared the graph a DAG and enforced this with two guards:

- `wouldCreateCycle(graph, from, to)` — blocks edge add if a path `to → … → from` already exists. Called inline in the fixpoint and again when materialising post-fixpoint `LiftEdge`s.
- `CycleDetector.detectCycles()` in `assertInvariants` — final structural check.

The original motivation was to stop bidirectional strategies (`TemporalToString` ↔ `StringToTemporal`) from forming X↔Y 2-cycles that "violate the DAG". In practice the DAG invariant is not used by any downstream consumer — `BFSShortestPath` (used by `ResolvePathStage` and by `BuildValueGraphStage` itself to resolve lift inner paths) maintains a visited set and traverses cyclic graphs correctly.

`OptionalWrap` / `OptionalUnwrap` form the same inverse pair. When a `StreamMap` lift proposes an inner `Optional<X> → Y` transformation, both strategies try to contribute edges between the `Optional<X>` and `X` typed nodes. Whichever is proposed first wins; the other is dropped by `wouldCreateCycle`. `PersonMapper.mapHuman` (which needs `Optional<Person.Address> → Person.Address → Human.Address` inside a stream lift) hits exactly this case: `OptionalWrap` adds `PA → Opt<PA>` first, then `OptionalUnwrap`'s `Opt<PA> → PA` is blocked, the lift's inner BFS returns null, no `LiftEdge` is materialised, and the main BFS fails to reach the target slot.

## Goals / Non-Goals

**Goals:**
- Allow inverse-strategy 2-cycles in the `ValueGraph` so BFS can select whichever direction a given path needs.
- Restore the pre-regression behaviour for `percolate-integration`'s `PersonMapper.mapHuman` and any similar mapper that mixes wrap/unwrap (or temporal conversion) inside a container lift.
- Keep every other `ValueGraph` invariant intact: target-slot leaves, `PropertyReadEdge` endpoint types, `TypeTransformEdge` endpoint types, `LiftEdge.innerPath` vertex/edge membership.

**Non-Goals:**
- Reworking strategy proposal semantics, path scoring, or BFS algorithm selection.
- Adding "tagged" typed nodes (e.g. wrapped vs unwrapped variants of the same `TypeMirror`).
- Changing `ResolvePathStage`, `OptimizePathStage`, `GenerateStage`, or any SPI.

## Decisions

### Decision: Drop the DAG invariant entirely rather than special-casing inverse pairs

Two alternatives were considered:

1. **Special-case inverse strategy pairs.** Introduce a marker (e.g. `TypeTransformStrategy#inverseOf`) and let `wouldCreateCycle` allow reverse edges when the existing forward edge belongs to the declared inverse. Requires changes to the SPI, updates to every inverse pair, and still leaves the implementation fragile: any new bidirectional strategy has to be opted into the allow-list or it silently regresses.

2. **Drop the DAG invariant.** Remove `wouldCreateCycle` and the `CycleDetector` check. The fixpoint's "add edge if not already present" rule already bounds the iteration (finite vertex count × finite strategy count). `BFSShortestPath` is cycle-safe by construction.

We pick (2). It is strictly smaller, removes code rather than adding it, and matches the semantic reality: the graph is a proposal space where multiple transformation directions can coexist, and the selected path is the source-of-truth — not the graph's topology.

### Decision: Keep all non-cycle invariants

`assertInvariants` still checks:

- `TargetSlotNode` has no outgoing edges (paths terminate at slots).
- `PropertyReadEdge` source ∈ `{SourceParamNode, PropertyNode}`, target ∈ `PropertyNode`.
- `LiftEdge.innerPath` vertices and edges are present in the parent graph.

These enforce structural contracts that downstream stages rely on. Only the DAG bullet goes away.

### Decision: Leave `sortBySourceTargetReachability` in place

The sort still helps: it controls the order in which `(from, to)` pairs are offered to strategies during the fixpoint, so forward-flowing edges tend to be proposed before backward ones. With cycles now allowed, the sort becomes a performance/ordering heuristic rather than a correctness guarantee. Its docstring is updated to reflect this.

## Risks / Trade-offs

- **Risk**: A future strategy introduces an unbounded chain of proposals through a cycle, causing the fixpoint to iterate until the `MAX_ITERATIONS` budget is exhausted. **Mitigation**: The fixpoint already enforces `!graph.containsEdge(inputNode, outputNode)` before adding, and the 30-iteration cap remains. Every strategy's proposal is keyed by `(from, to)` pair so no strategy can add the same edge twice. Worst-case growth is polynomial in the vertex set.
- **Risk**: Diagnostic noise — the debug DOT export will show cycles, which looks odd at first glance. **Mitigation**: The new behaviour is documented in the spec and class-level javadoc.
- **Trade-off**: We lose a cheap structural sanity check. In exchange we get correctness for inverse strategy pairs and a simpler implementation.

## Migration Plan

No runtime migration. The change is internal to the annotation processor; existing generated code for all green fixtures is unchanged (verified by the golden-output harness). Previously-failing mappers that exercise inverse-strategy chains inside lifts now compile.

## Open Questions

None.
