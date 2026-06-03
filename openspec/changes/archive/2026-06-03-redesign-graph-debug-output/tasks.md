## 1. Remove dead cluster code

- [x] 1.1 Delete `DotGroupClusterRenderer` and remove the `if (source instanceof MapperGraph)` group-cluster branch from `DotRenderer.render`
- [x] 1.2 Remove scope-cluster emission (`subgraph cluster_*`, `renderCluster`, `buildNodesByScope`/phantom-by-parent helpers) from `DotRenderer`
- [x] 1.3 Confirm nothing else references `DotGroupClusterRenderer` or the removed cluster helpers (search + compile)

## 2. Per-scope graph slicing

- [x] 2.1 Add a scope-partition helper that yields, per `Scope`, an `org.jgrapht.Graph<Node, Edge>` (`AsSubgraph`/`MaskSubgraph` over the underlying graph) restricted to that scope's nodes and the view's edges; assign each edge to its `from`-node scope
- [x] 2.2 Derive the deterministic file infix `<methodSimpleName>` per scope, disambiguating colliding overloads as `<methodSimpleName>-<n>` in a stable order

## 3. Reimplement DotRenderer over DOTExporter

- [x] 3.1 Rewrite `DotRenderer` to render a single-scope slice via `org.jgrapht.nio.dot.DOTExporter`, removing hand-rolled header/footer, `escapeDot`, and `appendAttributes`
- [x] 3.2 Wire `setVertexIdProvider` (`Node.id()`) and `setVertexAttributeProvider` (label per existing simple-type rule; `shape=box`, `style=filled`, `fillcolor` by location kind — source/target/element distinct colours; remove the diamond)
- [x] 3.3 Wire `setEdgeAttributeProvider`: REALISED solid black + elevated `penwidth`; SEED grey `color` + grey `fontcolor`; MARKER neutral; preserve existing label content (strategy short name + weight, `∞` sentinel, SEED token/directive marker)
- [x] 3.4 Wire `setGraphAttributeProvider` to emit a graph-level `label` caption with the human-readable scope description; verify it renders as a visible caption (fall back to id-only if not)

## 4. Shared GraphDumpWriter + thin stages

- [x] 4.1 Add `GraphDumpWriter` (`@Inject`-constructed; depends on `Filer`, `Diagnostics`, `ProcessorOptions`, `DotRenderer`) owning the debug-graphs gate, empty-graph skip, per-scope partition loop, one `Filer.createResource(SOURCE_OUTPUT, …)` write per scope, and `IOException`→warning handling
- [x] 4.2 Reduce `DumpGraph`, `DumpFullGraph`, `DumpTransforms`, `DumpPlan` to thin delegators passing their `<view>` infix and view selector (`g -> g`, `g -> g.transformsView()`, `g -> g.planView()`); keep each stage's pipeline position (DumpGraph before `expandStage`)
- [x] 4.3 Update `ProcessorModule` provider wiring for the new collaborator and the slimmed stages

## 5. Tests

- [x] 5.1 Update/replace renderer specs: assert no `cluster_` token, all nodes `shape=box`+`style=filled`, distinct `fillcolor` per role, no diamond/oval, graph `label` caption present
- [x] 5.2 Update edge-styling specs: REALISED black + elevated penwidth; SEED grey `color` + grey `fontcolor`; label content preserved (strategy name + weight, `∞`)
- [x] 5.3 Update dump-stage specs for one-file-per-scope naming (`<FQN>.<method>[-n].<view>.dot`), one write per scope, option-off/empty-graph skips, `IOException`→warning, overloaded-method disambiguation
- [x] 5.4 Remove specs asserting phantom-in-parent-cluster placement (requirement removed)

## 6. Verify

- [x] 6.1 Rebuild the integration mapper (`~/Projects/joke/percolate-integration/mappers`) with `debugGraphs` on; confirm per-scope `.dot`/`.svg` files render with colour-coded boxes, grey seed edges, scope captions, and no empty group clusters
- [x] 6.2 Run `./gradlew check` and resolve every violation before completing
- [x] 6.3 Commit the completed change with `/commit-commands:commit`
