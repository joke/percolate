## 1. Preflight

- [x] 1.1 Load the Java 11 + Lombok + null-safety coding-convention skills before editing any Java.
- [x] 1.2 Resolve open question: is `MapperGraph.addEdgeIfAcyclic` reachable from production code, or only tests? **Answer: dead** — zero call sites anywhere (only its own definition in `MapperGraph`); `Applier` uses a separate `wouldBeAcyclic` probe + plain `addEdge`. → delete it in 4.6.
- [x] 1.3 Resolve open question: does the expansion same-pass cross-group duplicate edge (design D5) actually occur? **Decision: implement the `Applier` guard as a real skip** (conservative — correct whether or not the duplicate ever occurs, and cannot destabilize the suite), rather than a bare assertion that would crash if the case arises. No standing graph index.

## 2. Step A — Remove EdgeKind.MARKER

- [x] 2.1 Remove the `MARKER` constant from `EdgeKind` (leaving `SEED`, `REALISED`).
- [x] 2.2 Remove the `Edge.marker(Node, Node, String)` factory.
- [x] 2.3 Remove the `MARKER` branch from `DotRenderer` (label emission).
- [x] 2.4 Simplify the orphan-detection traversable lattice in `ExpansionResult` (`isTraversable`) to `REALISED | SEED`.
- [x] 2.5 Remove/retarget tests that fabricate MARKER edges (`DotRendererSpec`, `TransformsViewSpec`, `MapperGraphAppendOnlySpec`) so they no longer reference `Edge.marker`/`EdgeKind.MARKER`.
- [x] 2.6 Build + run the processor test suite; confirm green (no behavior change expected).

## 3. Step B — Dumb graph: SeedStage owns canonicalization + seed idempotency

- [x] 3.1 Add a per-`apply` canonicalizer to `SeedStage` holding the `(scope, location) → Node` map and the graph; expose `canonical(scope, loc)` (get-or-create + `addNode`, reporting created-vs-reused) and `register(node)`.
- [x] 3.2 Thread the canonicalizer through `seedMethod`/`seedDirective`/`buildSourceChain`/`buildTargetChain`; replace `graph.variableFor`/`graph.registerVariable` calls with it.
- [x] 3.3 Gate segment-edge + `registerDemand`/`tagUmbrellaChild` on "canonical node freshly created" (design D3); guard the bridging edge's degenerate duplicate with a single `getAllEdges(from, to)` check.
- [x] 3.4 Remove `variableIndex`, `variableFor`, `registerVariable`, and `VariableKey` from `MapperGraph`.
- [x] 3.5 Remove `edgeIndex` and the duplicate-rejection branch from `MapperGraph.addEdge`; per 1.2, either delete `addEdgeIfAcyclic` or drop its `edgeIndex.remove` line. (Deleted `addEdgeIfAcyclic` — dead.)
- [x] 3.6 Move/retarget graph specs/tests that asserted `variableFor` canonicalization or `addEdge` structural dedup to `SeedStage`-level specs asserting producer-side non-duplication (one node per prefix, one edge per segment, one demand per edge). (`SeedStageSpec` "two directives sharing a source prefix…" already covers it; stripped `variableFor` from `ExpansionGroupSpec` 7.3.)
- [x] 3.7 Build + run the suite; confirm identical generated output across existing mapper fixtures.

## 4. Step C — Lean on JGraphT for topology

- [x] 4.1 Remove `from`/`to` fields from `Edge`; make `Edge.equals`/`hashCode` instance-identity; drop the endpoint-based `Comparable`. (Already in tree from prior session; added `@ToString` for diagnostic concatenation.)
- [x] 4.2 Change edge factories to construct endpoint-less payload (`Edge.seed(directive)`, `Edge.realised(weight, codegen, fqn[, slot])`, container variant); remove `copyWithEndpoints` (replace with a re-add of the payload between new vertices).
- [x] 4.3 Change `MapperGraph.addEdge` to `addEdge(Node from, Node to, Edge edge)`; add `getEdgeSource(Edge)`/`getEdgeTarget(Edge)`; compute `edges()` ordering from `getEdgeSource/Target` ids + `weight` + `kind` (new `EdgeOrder`).
- [x] 4.4 Update `GraphDelta` (and the expand-side `AddEdge` delta) to carry `(from, to, edge)` edge-entries instead of endpoint-bearing `Edge`s; update `apply`/`Applier.visitAddEdge` accordingly. (`GraphDelta.EdgeEntry`; `AddEdge` already carried endpoints.)
- [x] 4.5 Reroute the endpoint-read sites (`PlanView`, `SlotResolver`, `RealisationDiagnosticsStage`, `TransformsView`/`RealisedSubgraph`, `DotRenderer`, `GraphDumpWriter`, `ExpansionGroup`, `ExpandStage`, `BuildMethodBodies`, `Applier.wouldBeAcyclic`/`CycleProbe`) from `edge.getFrom()`/`getTo()` to `getEdgeSource`/`getEdgeTarget` (or the view they already hold).
- [x] 4.6 Add the `Applier` edge non-duplication guard (per 1.3): before applying an `AddEdge`, skip when `getAllEdges(from, to)` holds a payload-equal edge (same `kind`, `weight`). Implemented as a real skip via `MapperGraph.getAllEdges`.
- [x] 4.7 Build + run the full suite; confirm identical generated output across existing mapper fixtures. (`:processor:test` + `:processor:integrationTest` green; test helpers + specs migrated to the endpoint-less API.)

## 5. Spec sync & verification

- [x] 5.1 Run `openspec validate clean-graph-model` and confirm the four delta specs match the implemented code. (`Change 'clean-graph-model' is valid`.)
- [x] 5.2 Confirm the three debug graph outputs (seed/full/transforms) still render and contain no `MARKER` token. (`DotRendererSpec`/`GraphDumpWriterSpec` green; `DotRendererSpec` asserts `!dot.contains('MARKER')`.)
- [x] 5.3 Full processor + builtin-strategy build green; spot-check a multi-`@Map` shared-prefix mapper and a container mapper for byte-identical generated source vs. `main`. (`./gradlew build` green incl. `:processor:integrationTest` compile-testing fixtures + PMD/CodeNarc/spotless; fixed pre-existing PMD `Node.groups`/CodeNarc unused-imports and removed the now-unnecessary `BuildMethodBodies` GodClass suppression.)
