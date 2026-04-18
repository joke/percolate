## Why

The value-graph refactor imposed a DAG invariant on the per-method `ValueGraph`, enforced by a `wouldCreateCycle` guard in `BuildValueGraphStage` and a `CycleDetector` assertion. Inverse `TypeTransformStrategy` pairs (`OptionalWrap`/`OptionalUnwrap`, `TemporalToString`/`StringToTemporal`) fundamentally produce 2-cycles between the same two typed nodes, so the guard drops whichever direction is queried second. As a result, `Stream<Optional<X>> → Stream<Y>` lifts can no longer form their inner `Optional<X> → X → Y` path, and mappings like `percolate-integration`'s `PersonMapper.mapHuman` that used to compile now fail with a spurious "no mapping method found" diagnostic.

## What Changes

- Remove the DAG invariant from `value-graph/spec.md` — the graph is allowed to contain cycles between inverse transform edges.
- **BREAKING (internal)**: `BuildValueGraphStage` no longer calls `wouldCreateCycle`; inverse strategy pairs coexist as 2-cycles in the graph.
- `BuildValueGraphStage.assertInvariants` drops the `CycleDetector.detectCycles()` check; all other invariants (target-slot leaves, edge type constraints, lift inner-path membership) remain.
- `sortBySourceTargetReachability` docstring no longer claims the sort preserves DAG flow.
- `ValueGraphSpec` drops the "well-formed ValueGraph is acyclic" test.

Downstream stages already tolerate cycles: `BFSShortestPath` uses a visited set, and `OptimizePathStage` / `GenerateStage` only walk the resolved `GraphPath`, never the raw graph.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `value-graph`: drop the DAG invariant requirement and its "Graph is acyclic" scenario; keep all other invariants.

## Impact

- `processor/src/main/java/io/github/joke/percolate/processor/stage/BuildValueGraphStage.java` — remove `wouldCreateCycle` helper and its two call sites (fixpoint edge add + post-fixpoint lift materialisation); remove `CycleDetector` import and its check in `assertInvariants`; update class-level javadoc.
- `processor/src/test/groovy/io/github/joke/percolate/processor/graph/ValueGraphSpec.groovy` — remove the DAG scenario test and the `CycleDetector` import.
- `openspec/specs/value-graph/spec.md` — remove the DAG bullet from the invariants requirement and delete the "Graph is acyclic" scenario.
- No consumer-facing API change. No generated-code change for existing green fixtures; newly unblocks mappings that use inverse wrap/unwrap chains inside lifts.
