## Why

`MapperGraph` and `Edge` are meant to be a thin, typed layer over a JGraphT `DirectedMultigraph<Node, Edge>`, but they have accreted three pieces of logic that belong elsewhere or nowhere:

1. **A vestigial edge kind.** `EdgeKind.MARKER` has zero production call sites. It once meant a `realises` edge linking an untyped seed node to its typed counterpart, but that role vanished when typing became in-place `Node.setTyping`. It is the last survivor of a collapsed taxonomy (`SUB_SEED`/`ELEMENT_SEED` are already gone) and is a recurring "what is this for?" speed bump.

2. **Seed-time logic baked into the container.** `MapperGraph` owns node canonicalization (`variableFor`/`registerVariable`/`variableIndex`) and edge deduplication (`edgeIndex` + the `addEdge` "reject duplicate by structural equality" gate). Both exist only to collapse the shared path prefixes `SeedStage` produces — and both are used by `SeedStage` alone (expansion mints fresh instance-identity nodes; `Applier` ignores `addEdge`'s boolean). The `graph-model` spec *already* says SeedGraph should own its dedup map; the code put it on the graph.

3. **A hand-maintained topology mirror.** `Edge` stores `from`/`to` and 37 sites read them, while **zero** sites ask JGraphT via `getEdgeSource`/`getEdgeTarget` — even though JGraphT already maintains the topology and `SlotResolver` already half-uses it (`incomingEdgesOf(node)` then `edge.getFrom()`). `from`/`to` on the `Edge` value also force the manual `edgeIndex` and `copyWithEndpoints`.

The unifying goal: make the graph "stupid" — a typed JGraphT view that holds no knowledge of how seeding or expansion work. Producers (SeedStage, the expansion driver) own their own idempotency, exactly as expansion already does.

## What Changes

**A. Remove `EdgeKind.MARKER`**
- Drop the `MARKER` enum constant (leaving `SEED`, `REALISED`), the `Edge.marker(...)` factory, and the `DotRenderer` MARKER branch.
- Simplify the orphan-detection traversable lattice in the test harness to `REALISED | SEED`.
- No behavior change: nothing ever minted a MARKER edge.

**B. Move canonicalization + seed idempotency into `SeedStage` (dumb graph)**
- Move the `(scope, location) → Node` canonical map out of `MapperGraph` into a per-`apply` helper inside `SeedStage`; `SeedStage` calls only `addNode`.
- `SeedStage` gates segment-edge + demand creation on "node was freshly created" (a chain edge into a node is new iff the node is new), never *offering* a duplicate — matching how expanders guard with `incomingEdgesOf`.
- Remove `variableFor`/`registerVariable`/`variableIndex` and the `edgeIndex` dedup from `MapperGraph`.

**C. Lean on JGraphT for topology**
- Remove `Edge.from`/`Edge.to`; read endpoints via `getEdgeSource`/`getEdgeTarget` (and existing `incomingEdgesOf`/`outgoingEdgesOf`) at the ~37 call sites. `copyWithEndpoints` becomes a plain re-add between the new vertices.
- `Edge` becomes identity-keyed payload (`weight`, `kind`, codegen/scope/slot, and — for now — `directive`/`strategyClassFqn`); it no longer carries or compares endpoints. **BREAKING (internal):** `Edge.equals`/`hashCode` change from structural to identity.
- The multigraph's "no duplicate parallel edge" invariant moves to the **single mutation sites**: `SeedStage` (already idempotent via B) and `Applier` (dedups its `AddEdge` deltas against the live graph via `getAllEdges(from, to)` before adding). The graph itself stops owning a value-dedup index.

## Capabilities

### New Capabilities

<!-- none -->

### Modified Capabilities

- `graph-model`: `EdgeKind` drops to two values and loses the `Edge.marker(...)` factory + scenario; `Edge` loses `from`/`to` and its `equals`/`hashCode` become identity-based (endpoints are obtained from the graph, not the edge); `variableFor`/`registerVariable` are removed and the "one Node per prefix" ownership moves to the seed stage; `addEdge` is restated as a thin append with the dedup index removed; the per-kind weight/field documentation drops MARKER and the endpoint fields.
- `seed-graph`: "shared prefix is not duplicated" is restated as producer-side (SeedStage canonicalization + create-gate) with the same observable outcome; MARKER drops from the "emits only SEED" enumeration.
- `graph-debug-output`: remove the requirement/scenario mandating a literal `MARKER` token in the DOT label.
- `expansion-test-harness`: orphan-detection reachability is defined over `REALISED | SEED` only; the MARKER-path scenario is updated.

## Impact

- **Code**: `EdgeKind`, `Edge` (remove `marker`, `from`/`to`, structural equality), `DotRenderer`, `MapperGraph` (remove `variableIndex`/`variableFor`/`registerVariable`, `edgeIndex`, dedup branch; thin `addEdge`), `SeedStage` (own canonical map + create-gate), `Applier` (own expansion-time edge non-duplication), and the ~37 endpoint-read sites (`PlanView`, `SlotResolver`, `DotRenderer`, `RealisedSubgraph`/`TransformsView`, `GraphDumpWriter`, expanders) re-routed through JGraphT, plus the test-harness orphan lattice.
- **APIs**: all internal to the processor module. `Edge.marker`, `Edge.from/to`, `MapperGraph.variableFor`/`registerVariable` are removed. No SPI change — strategy authors never saw `EdgeKind`, `Edge`, or `MapperGraph`.
- **Behavior**: graph/plan/generated source are identical for every existing mapper; this is a representation + ownership refactor. Flag the internal `Edge` identity-equality change for processor maintainers.
- **Dependencies**: none.
- **Teams**: processor maintainers only.
