## Why

The expansion engine currently sources candidate input nodes from the global `MapperGraph` via `SourceReachability.candidateInputs(scope, graph)`, then locates source-parameter-roots from a globally ambient set. `ExpansionGroup` reifies a JGraphT `AsSubgraph` view per group, but the engine never consults that view during expansion. Two consequences fall out:

1. **Cross-group cycles.** Same-scope type-based scans pick up downstream nodes by type (e.g. `IterableUnwrap` at frontier `elem:HA` finds `elem:Set<HA>` allocated upstream by `SetCollect`), closing cycles across sibling sub-groups. We needed a multi-fire-aware engine guard to prevent this in `split-container-bridges` and ended up with a model whose correctness depends on per-edge cycle-prevention rather than on the structural model.

2. **Multi-parameter directive ambiguity.** Type-only candidate scans cannot tell which parameter a directive intended (`@Map(source = "person2.first")` vs. `@Map(source = "person.lastName")` with two `Person` parameters). The workaround was to bake typed source chains into the seed graph at seed time via `PathSegmentResolver`, with one path-segment `ExpansionGroup` per non-root segment. That fix solved the symptom but moved type resolution out of the expansion engine into a special seed-time pre-pass, leaving the engine itself globally-scoped.

Both bugs share one structural cause: **the engine treats groups as metadata, not as the unit of expansion.** When groups are made the actual scope of candidate search — and every bridge match spawns its own one-slot sub-group — cycles cannot form (sub-group views contain only `{root, slot, one edge}`; instance-identity `Node`s prevent structural collisions across sibling sub-groups) and per-directive disambiguation is structural (each path-segment group's slot is the specific parameter root the directive named). The seed-time path-resolution workaround becomes unnecessary: `PathSegmentResolver`s can fire during expansion within their seed-derived groups without re-introducing either bug.

## What Changes

- **BREAKING (engine internal)**: `ExpandGroupsPhase.expandFrontier` candidate search SHALL consult `currentGroup.getView().vertexSet()` (excluding the frontier itself), not `SourceReachability.candidateInputs(scope, graph)`. The global scan is removed.
- **BREAKING (engine internal)**: every matching `BridgeStep` during expansion SHALL register a fresh one-slot nested `ExpansionGroup` rooted at the current frontier with the allocated input as its sole slot — regardless of `ScopeTransition`. The PRESERVING/ENTERING/EXITING split in `commitBridgeStep` collapses to a single uniform branch. The "PRESERVING grows the current group" behaviour is retired.
- **BREAKING (seed-graph)**: `SeedGraph` SHALL register one `ExpansionGroup` per SEED edge — directive-bridging edges become directive-binding groups (`root = tgt[...]`, `slot = src[...]`); path-segment edges become path-segment groups (`root = src[...prefix.segment]`, `slot = src[...prefix]`). The seed graph stays **structurally untyped** at source-path segments: `SeedGraph` no longer invokes `PathSegmentResolver`s. The typed source chain produced by today's seed-time path-walking is removed.
- **BREAKING (engine internal)**: `PathSegmentResolver` invocation moves from `SeedGraph` to `ExpandGroupsPhase`. Path-segment groups (those whose root and slot both have `SourceLocation`, where the root's path is a one-segment extension of the slot's path) SHALL be expanded by iterating registered `PathSegmentResolver`s in `Class.getName()` order; the first non-empty `ResolvedSegment` types the root and emits a REALISED edge from slot to root. The resolver SPI surface is unchanged.
- **BREAKING (engine internal)**: `SourceReachability.sourceParameterRoots(...)` and the ambient-source-roots pattern are removed. A slot is base-case SAT iff its `Location` is a single-segment `SourceLocation` matching one of `currentMethod`'s parameters. No global set is consulted.
- **BREAKING (engine internal)**: `ExpandGroupsPhase` work-list becomes a cross-group fixed-point loop — iterate every registered group in registration order, repeat until a full pass produces no state changes (no group flipped to SAT, no sub-group spawned, no `Node.setType(...)` fired). Within-group expansion stays target-to-source. Convergence is guaranteed by state monotonicity + finite group count + DAG-acyclic boundary structure (typically 2–4 passes). A `MAX_OUTER_PASSES = 32` cap acts as a safety net.
- **BREAKING (engine internal)**: the cycle-prevention guard introduced/considered in `split-container-bridges` is dropped. Cycles are impossible by construction under sub-group isolation.
- `ExpansionGroup` gains a controlled mutator (`addVertex` / `addEdge` against `getView()`) used by the engine when imports of shared boundary nodes are needed (typically: a freshly-spawned sub-group's slot equals an existing node by structural equality — the existing instance is imported into the sub-group's view).
- `ExpansionGroup` view becomes the authoritative SAT input: a group SATs when every slot has at least one child sub-group SAT (outcome-propagated). The `slotReachable` REALISED-traversal stays as an implementation choice within a group's own view but is no longer a global query.
- Multi-fire (every matching bridge commits) stays; parallel sub-groups expanded independently; a slot SATs when any of its child sub-groups SATs. Dead branches remain in the graph as unsatisfied sub-groups.
- Integration acceptance: `~/Projects/joke/percolate-integration/mappers` compiles green; `PersonMapper.transforms.dot` shows the same linear alive chain as today (`src[person] → src[person.addresses]:List<Opt<PA>> → elem:Opt<PA> → elem:PA → elem:HA → elem:Set<HA> → tgt[addresses]:Optional<Set<HA>>`), but each hop is its own sub-group; no parallel dead branches close cycles; no cycle-prevention check in the engine.

## Capabilities

### New Capabilities

None. The change refactors the existing engine and seed-graph behaviour; no new SPI surface is introduced.

### Modified Capabilities

- `graph-expansion`: candidate search is sub-group-view-scoped; every bridge/resolver match spawns its own one-slot sub-group; cross-group fixed-point loop replaces FIFO single-pass; ambient source-parameter-roots and global cycle-prevention are removed; base-case SAT for parameter-root slots is determined structurally per-group.
- `graph-model`: `ExpansionGroup` gains controlled view-mutation (`addVertex`/`addEdge`) for boundary imports; `Node` instance-identity invariant is restated and load-bearing for cycle prevention.
- `seed-graph`: registers one `ExpansionGroup` per SEED edge (path-segment groups + directive-binding groups); no longer invokes `PathSegmentResolver`s at seed time; seed graph stays untyped at source-path segments.
- `source-path-resolution`: `PathSegmentResolver` invocation moves from `SeedGraph` to `ExpandGroupsPhase`; resolvers fire within path-segment groups during expansion (target-driven from the segment's typed-counterpart root, querying the typed slot — graph traversal stays backward, resolver type-inference is internal to the strategy).
- `realisation-validation`: outcome-driven semantics already in place; the spec gains a base-case SAT clause for parameter-root slots.

## Impact

- **Affected code**:
  - `processor/src/main/java/io/github/joke/percolate/processor/stages/expand/ExpandGroupsPhase.java` — candidate search, sub-group spawning, work-list ordering, resolver invocation.
  - `processor/src/main/java/io/github/joke/percolate/processor/stages/expand/SourceReachability.java` — drops `candidateInputs`/`sourceParameterRoots`; per-group `slotReachable` remains.
  - `processor/src/main/java/io/github/joke/percolate/processor/stages/seed/SeedGraph.java` — drops the seed-time path-walking algorithm; registers groups per SEED edge.
  - `processor/src/main/java/io/github/joke/percolate/processor/graph/ExpansionGroup.java` — view-mutation surface; boundary-import API.
  - `processor/src/main/java/io/github/joke/percolate/processor/stages/expand/ResolveTargetChainsPhase.java` — directive-binding group registration moves to `SeedGraph`; this phase reduces to `GroupTarget` matching at return roots.
- **Affected behaviour**:
  - `*.seed.dot` becomes structurally untyped at source-path segments (matching the 2026-05-08 architecture before `GetterPathResolver` was added).
  - `*.full.dot` and `*.transforms.dot` show more, smaller sub-group clusters; each container hop renders as its own DOT subgraph.
  - No engine code references `EdgeKind.SUB_SEED` / `EdgeKind.ELEMENT_SEED` (already absent) and no engine code runs a global cycle-prevention check.
- **Affected teams**: processor engine maintainers; built-in path-resolver authors (resolvers' contract is unchanged but invocation timing changes).
- **Risk**: medium-high. The engine refactor touches the realisation pipeline's core. Mitigation: the integration mapper (`PersonMapper.mapHuman` + `mapAddress`) is the acceptance signal — the alive chain must compose under the new rules without cycle-prevention. Reverting is a single revert of this change (no schema migration, no persisted state).
- **Dependencies**: requires `split-container-bridges` archived (already done). Supersedes the seed-time path-walking algorithm from `source-path-resolvers` (whose `PathSegmentResolver` SPI and built-ins are preserved; only the invocation site moves).
