## Why

The DOT debug graphs hand-roll their own DOT serialization and lean on two cluster layers, one of which is silently dead: group clusters (`cluster_group_*`) reference nodes already owned by scope clusters, and Graphviz assigns each node to exactly one cluster, so the group clusters render as empty and never appear in any `.svg`. The surviving scope clusters cram every mapper method into one crowded file, the element nodes use a diamond shape that is hard to read, and seed edges compete visually with the realised edges that actually matter. The four dump stages are also near-identical copies that differ only in a filename suffix and a view selector. Since the output is now a flat, per-scope, attributed graph, JGraphT's `DOTExporter` fits exactly — and replacing the bespoke renderer removes the dead cluster code and the duplicated stages in one pass.

## What Changes

- **Remove the dead group-cluster rendering.** Delete `DotGroupClusterRenderer` and the `instanceof MapperGraph` seam in `DotRenderer`. These emit `cluster_group_*` subgraphs that Graphviz drops (a node can belong to only one cluster, and these nodes are already in their scope cluster), so removal changes no rendered output. **BREAKING** for any tooling that parses `cluster_group_*` out of the `.dot` text (none known; the clusters are invisible).
- **Split debug output one file per scope.** Instead of one `<FQN>.<view>.dot` holding every `MethodScope` in stacked `subgraph cluster_*` blocks, emit one file per `(scope, view)`: `<FQN>.<method>.<view>.dot`, disambiguated to `<method>-<n>` only when overloaded method simple names collide. The scope is conveyed by the file and by a graph-level `label=` caption rather than by a cluster box. The four views (`seed`, `full`, `transforms`, `plan`) are unchanged. **BREAKING** file-naming change for anyone consuming the old single-file-per-view names.
- **Switch the renderer to JGraphT `DOTExporter`.** Replace the hand-rolled digraph header/footer, `escapeDot`, `appendAttributes`, and `TreeMap` attribute assembly with `org.jgrapht.nio.dot.DOTExporter` over an `AsSubgraph` filtered to one scope. Vertex/edge/graph attributes are supplied via `setVertexIdProvider` / `setVertexAttributeProvider` / `setEdgeAttributeProvider` / `setGraphAttributeProvider`. Library handles escaping and statement structure.
- **Distinguish node roles by colour, not shape.** Render every node as a filled `box`; encode source / target / element role via `fillcolor` (source light blue, target light green, element light amber) instead of box / oval / diamond. The diamond is removed.
- **Recede seed edges, keep realised edges dominant.** Render `SEED` edges (line and label) in grey so they read as background; `REALISED` edges keep the heaviest stroke (black, elevated `penwidth`). `MARKER` edges keep a neutral fallback.
- **Deduplicate the dump stages.** Extract a single `GraphDumpWriter` collaborator that owns the debug-graphs gate, the empty-graph skip, the per-scope partition + `DOTExporter` pass, the `Filer` write, and the `IOException`→warning handling. The four `Dump*` stages become thin scheduling points (each keeps its pipeline position; `DumpGraph`/seed still fires before expansion) that delegate to the writer with their suffix and view selector.
- **Cross-scope edge fallback.** Cross-scope edges do not arise by construction (cross-method calls are by-name codegen bindings, not graph edges). The per-scope partition SHALL place any edge in its `from`-node's scope file so an edge is never silently lost; no dedicated invariant test is required.

## Capabilities

### New Capabilities
<!-- none -->

### Modified Capabilities

- `graph-debug-output`: Replace cluster-based grouping (both the dead group clusters and the scope clusters) with one-file-per-scope output named `<FQN>.<method>[-n].<view>.dot`; the DOT renderer is reimplemented over JGraphT `DOTExporter`; node roles are encoded by `fillcolor` on a uniform `box` shape rather than by distinct shapes (the diamond is removed); `SEED` edges render grey/background while `REALISED` edges remain dominant; the four dump stages delegate their IO to a shared `GraphDumpWriter`.

## Impact

- **Code**: `DotRenderer` reimplemented over `DOTExporter`; `DotGroupClusterRenderer` deleted; new `GraphDumpWriter` collaborator; `DumpGraph`, `DumpFullGraph`, `DumpTransforms`, `DumpPlan` reduced to thin delegators; per-scope `AsSubgraph` partition + `<method>[-n]` filename derivation. All under `io.github.joke.percolate.processor.graph` and `…processor.stages.dump`.
- **APIs**: No SPI or public API change. `MapperGraph` views (`transformsView()`, `planView()`) are consumed as-is; an internal `GraphSource → org.jgrapht.Graph` adapter (or direct `AsSubgraph` over the underlying graph) feeds `DOTExporter`.
- **Dependencies**: None new — `jgrapht-io` (home of `DOTExporter`) and `jgrapht-core` (`AsSubgraph`) are already on the processor classpath; `AsSubgraph` is already used in `ExpansionGroup`.
- **Output / determinism**: File names and count change (N scopes × 4 views instead of 4 files). Rendered SVGs change appearance (colours replace shapes, grey seed edges, no cluster boxes). Output remains off by default (`ProcessorOptions.debugGraphs`); it is a development/support diagnostic only, so build-time cost of multiple exporter passes is irrelevant.
- **Teams**: Maintainers and support engineers who read the debug graphs — the new per-scope, colour-coded files are the artifact they will work with. Flag the file-naming and visual change in dev/support notes. No end-user (generated-code) impact.
