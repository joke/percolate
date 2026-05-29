## Why

`BuildMethodBodies` renders over `realisedSubgraph()`, which contains *every* committed `REALISED` edge — including dead multi-fire sibling branches that never reached source. The render pass selects a producing group per root via `putIfAbsent`, ignoring `GroupOutcome`, so it can descend into a dead `UNSAT_NO_PLAN` sibling and fail (`leaf node is not a SourceLocation`). The integration `PersonMapper.mapHuman` (`tgt[addresses]:Optional<Set<HA>>`) hits exactly this. Codegen must render only the *satisfied, cheapest* plan — selection that the architecture explicitly assigns to the render-time consumer, not to an a-priori commit in the engine ([[project_expansion_direction]]).

## What Changes

- Introduce a dedicated `PlanView` (a `GraphSource`) exposing only the edges of the chosen plan: `REALISED` edges that belong to a `SAT`-outcome group, with multi-fire OR-choices resolved to the cheapest branch.
- Resolve the cheapest branch using JGraphT `DijkstraShortestPath` as a **cost oracle**: a virtual super-source (weight-0 edges to every source-parameter leaf) yields `d(n)` = cheapest cost from source to each node over the SAT subgraph, with edge weights supplied via `AsWeightedGraph(graph, e -> (double) e.weight)`. The plan is then built by a top-down (target→source) guided walk: AND nodes (single group, e.g. `ConstructorCall`) keep all slot edges; OR nodes (multiple SAT single-slot sibling groups, e.g. multi-fire containers) pick the group minimising `weight(slot→root) + d(slot)`.
- Point `BuildMethodBodies` at `planView()` instead of `realisedSubgraph()`. Every node then has exactly one producer, so the `putIfAbsent` group guessing and the multi-inbound-edge ambiguity disappear.
- Add a `DumpPlan` stage emitting `<mapper>.plan.dot`, debug-gated by `processorOptions.isDebugGraphs()` like the other dumps.
- Leave `transformsView()` / `.transforms.dot` unchanged: it intentionally retains dead siblings — its purpose is debugging, and dead branches are part of that picture.

## Capabilities

### New Capabilities

<!-- none -->

### Modified Capabilities

- `code-generation`: The method-body composition algorithm consumes the chosen plan (`planView`) rather than the raw realised subgraph; defines cheapest-plan selection (SAT-group filtering + Dijkstra cost oracle + AND/OR guided walk) so each node has a single producer.
- `graph-debug-output`: Add the `planView()` accessor and the `.plan.dot` debug output; record that `.transforms.dot` deliberately retains dead (UNSAT) sibling branches.

## Impact

- **Code**: New `PlanView` (`GraphSource`) + its builder, `MapperGraph.planView()`, new `DumpPlan` stage wired into the pipeline after expansion and before/around the other dumps. `BuildMethodBodies` switches its input view. No `Bridge`/`GroupTarget`/expansion/`Applier` change.
- **Dependencies**: First use of JGraphT `org.jgrapht.alg.shortestpath.DijkstraShortestPath` and `org.jgrapht.graph.AsWeightedGraph` (already on the 1.5.2 classpath).
- **Behaviour**: Mappers with multi-fire container/conversion targets generate correctly (cheapest SAT plan). New `.plan.dot` artifact appears only under `-Apercolate.debugGraphs` (or whatever `isDebugGraphs` reads).
- **Teams**: Anyone mapping container/Optional fields where multiple bridges match — previously a hard codegen failure, now resolved to the cheapest plan.
