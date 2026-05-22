## Why

The percolate engine has, across multiple implementation rounds, regressed to a **forward-driven** bridge phase: `BridgeSourceToTargetPhase` iterates user-directive SEED edges and asks every `Bridge` "given `(F.type → T.type)`, what step takes me there?" applying the unified emission rule. This was never the intent. Three concrete consequences of the forward direction show up in the integration project's `PersonMapper.full.dot`:

1. **Dead REALISED edges and dead intermediate nodes.** `SetWrap` is queried for "produce `Set<Human.Address>`" and answers "from `Human.Address`." The engine commits a `REALISED H.A → Set<H.A>` edge and an `H.A` intermediate node even when nothing produces `H.A` from any source parameter. The intermediate sits in the graph unreachable.
2. **Orphan element-scope clusters.** Element-location phantom nodes (`elem(parent=…):type`) are connected to each other by `ELEMENT_SEED` and per-element `REALISED` edges, but the JGraphT graph has no edge connecting them to their parent containers — the `parent` field on `Node` is metadata, not a graph edge.
3. **Forced workarounds in `satisfy()`.** The in-flight `expansion-pruning` change had to add (a) a same-strategy filter on `SUB_SEED` promises, (b) a source-parameter-root check in the base case, and (c) deeper-miss-propagation in `satisfyEdge` to bubble leaf misses up correctly. None of these are design choices — they are corrections forced by forward expansion's debris.

Earlier rounds of this change explored a per-SEED-edge target-driven driver and exhaustive multigraph alternatives. Both ran into structural mismatches with how strategies actually compose:

- **Per-SEED drives the wrong unit.** A constructor needs *every* slot reached simultaneously; treating each directive's SEED as an independent subgraph cannot express "this constructor only works if all three of its inputs reach a source." Per-SEED also stumbled on untyped source-side seed endpoints (no MARKER counterpart) and required a SEED-chain-walk fallback in `resolveTypedCounterpart`.
- **Exhaustive multigraph search is overkill.** Strategies *can* offer parallel producers for the same slot. Keeping all of them and letting codegen Dijkstra pick the cheapest is conceptually clean, but the AND-join at a constructor's fan-in is not a vanilla shortest-path problem — it requires per-group cost summation. The implementation complexity is not justified by current mapper sizes or strategy counts.

The intended model is **per-expansion-group, target-driven, greedy expansion**:

- An **expansion group** is the natural unit of strategy work: one root (the target node a strategy produces), a list of slots (the strategy's inputs), and a codegen function (how the slot values combine). `ConstructorCall` emits one expansion group per registered constructor; `OptionalUnwrap`-style single-input bridges emit no group (just a single REALISED edge).
- Expansion groups are **persistent subgraph objects** — JGraphT `AsSubgraph` views over the shared `MapperGraph` — not metadata-on-edges. Each group's subgraph contains its root, its slots, and the REALISED edges joining them. Groups link **naturally via shared nodes**: a slot of an outer group is the root of an inner group when a multi-input strategy fills that slot.
- For each group, the driver walks **target → source**, asking `Bridge` strategies "what produces this slot?" The first matching producer commits one REALISED edge and (if multi-input) registers a nested expansion group on the slot's root. Nested groups join the work list.
- **Greedy:** each slot is filled exactly once. Alternative producers are not retained. The graph is a recipe, not a search space. Codegen reads the graph and emits the code; no Dijkstra is needed at codegen time.
- Source-side reach (the gap between the deepest slot and a source parameter root) is a `Bridge` concern. `GetterRead` is rewritten as a `Bridge` and queried alongside every other strategy. One SPI for the entire expansion work; no special-case engine logic.
- After each group is filled, `ConnectivityInspector` over the group's REALISED-only subgraph view checks that every slot is reachable from a source-parameter root. Connectivity is the SAT verdict.

This makes the data model honest about three things:

- **Groups are first-class.** `groupId` on edges and `groupCodegens` keyed map collapse into `ExpansionGroup` objects with their own subgraph views.
- **Three edge kinds, not five.** `SEED`, `REALISED`, `MARKER`. `SUB_SEED` and `ELEMENT_SEED` collapse into nested expansion groups at element scope.
- **SEED edges become framing.** They describe the original directive structure for diagnostics and the `.seed.dot` debug view. They do **not** drive expansion.

## What Changes

- **BREAKING** Replace `BridgeSourceToTargetPhase` with a **per-group, greedy, target-driven expansion driver** named `ExpandGroupsPhase`. The driver maintains a work list of `ExpansionGroup` objects (initially populated by `ResolveTargetChainsPhase` plus any other group-emitting phase). For each group, it iterates the group's slot list, walks each slot target-to-source through `Bridge` queries until a source-parameter-root is reached (or rounds are exhausted), and commits exactly one REALISED edge per slot. Multi-input strategies queried during this walk emit nested `ExpansionGroup` objects on the slot's root, which join the work list and are filled the same way.
- **BREAKING** Introduce an `ExpansionGroup` type in `processor/.../graph/`. Each group has `Node root`, `List<Node> slots`, `GroupCodegen codegen`, `String strategyClassFqn`, and an `AsSubgraph<Node, Edge>` view over the underlying graph. `MapperGraph` carries a `List<ExpansionGroup>` registry; group lookups (codegen, slot membership, per-group connectivity) read this registry directly.
- **BREAKING** Drop `Edge.groupId : Optional<String>`. Group membership of a REALISED edge is determined by membership in the group's subgraph view (or by a reverse `Map<Edge, ExpansionGroup>` index rebuilt from the registry — implementation choice). Drop `MapperGraph.groupCodegens : Map<String, GroupCodegen>` and `GroupRegistration` — the codegen lives on the `ExpansionGroup` object.
- **BREAKING** **Delete the `SourceStep` SPI** (`spi/SourceStep.java`) and `ResolveSourceChainsPhase` (`processor/.../stages/expand/ResolveSourceChainsPhase.java`). The only existing `SourceStep` implementation (`strategies-builtin/.../GetterRead.java`) is rewritten as a `Bridge` implementation. Source-side reach becomes a `Bridge` concern queried during slot expansion.
- **BREAKING** Collapse `EdgeKind.SUB_SEED` and `EdgeKind.ELEMENT_SEED` into `EdgeKind.SEED`. Element-scope nested seeds become nested `ExpansionGroup` objects at element scope, distinguished by `Node.loc instanceof ElementLocation`.
- **BREAKING** Remove `Node.parent`. Node identity becomes JVM object identity. `(scope, loc, type)` are presentation labels for the renderer. The "always allocate fresh" pattern applies inside the expansion driver: when a slot is filled by a multi-input strategy, the strategy's nested group brings fresh intermediate nodes for its own slots.
- **BREAKING** `MapperGraph.transformsView()` becomes a `MaskSubgraph` over the underlying graph filtered to `kind == REALISED`. The view is the codegen substrate. Under greedy expansion every slot has at most one producing REALISED edge, so the view is a DAG (or forest of group-joined DAGs), not a multigraph search space.
- **REPLACED** `ValidateRealisationStage`'s satisfy() walk is replaced by reading per-group SAT/UNSAT outcomes recorded by `ExpandGroupsPhase`. The stage is **renamed to `RealisationDiagnosticsStage`** and narrows to "format closest-miss diagnostics from UNSAT group records." The closest-miss message format from `expansion-pruning` is preserved.
- **SEED edges are pure framing.** `ExpandGroupsPhase` does not consult `SEED` edges. They remain in the graph for the `.seed.dot` debug view and for diagnostic origin-tracking (a directive's `@Map` `AnnotationMirror` is carried on the user-emitted SEED edge). `MARKER` edges similarly remain for connecting untyped seed leaves to typed slot nodes.
- **`expansion-pruning` reconciliation.** This change supersedes most of `expansion-pruning`'s substantive work: forward-direction emission rules, `SUB_SEED` / `ELEMENT_SEED` machinery, the standalone satisfy() algorithm, and the workaround logic in `SatisfySearch`. The surviving pieces (closest-miss diagnostic message format, three-file debug-output split, the `transformsView` skeleton, the `RealisedSubgraph` view) are folded into this change's specs. `expansion-pruning` is archived **without applying its remaining integration-test tasks** since its implementation never converged; this change subsumes them.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `graph-expansion`: Replace the forward `BridgeSourceToTargetPhase` requirement and the unified emission rule. New requirements: per-group target-driven expansion driver, slot resolution semantics, `ConnectivityInspector`-based SAT check per group's subgraph view, bounded iteration (per-slot rounds), per-group independence and error accumulation, nested-group composition via shared nodes.
- `graph-model`: Update `EdgeKind` to `{SEED, REALISED, MARKER}` — delete `SUB_SEED` and `ELEMENT_SEED`. Update `Edge` factories: delete `Edge.subSeed(...)` and `Edge.elementSeed(...)`. Drop `Edge.groupId` field. Update `Node` to drop the `parent` field; identity is JVM object identity. Add `ExpansionGroup` value type. Add `MapperGraph.addGroup(...)` and `MapperGraph.groups()` accessors; drop `MapperGraph.groupCodegens` / `addGroupCodegen(...)`. The append-only invariant on `MapperGraph` is retained.
- `realisation-validation`: Rename the stage; narrow its responsibility to diagnostic emission only. The satisfy() algorithm requirement is removed (folded into expansion). Outcomes are recorded per `ExpansionGroup`, not per SEED edge. The closest-miss diagnostic message format requirement is preserved verbatim. Diagnostic anchoring at the originating directive's `@Map` `AnnotationMirror` (carried on the seed-graph framing) is preserved.
- `graph-debug-output`: Simplify the `transformsView` requirement — "hide non-REALISED edges; keep REALISED edges and incident nodes." `DotRenderer` rendering of `SUB_SEED` and `ELEMENT_SEED` kinds is removed. The renderer iterates `MapperGraph.groups()` and renders each group as a DOT cluster (so the group's edges visually cohere). Group identification in the DOT output no longer needs an explicit `groupId` attribute — the cluster boundary is the group.
- `expansion-strategy-spi`: **Delete** the `SourceStep` interface. `Bridge`, `BridgeStep`, `ElementSeed`, `GroupTarget`, `GroupBuild`, `ResolveCtx` are unchanged. `GetterRead` is rewritten as a `Bridge` (annotated `@AutoService(Bridge.class)`).

### Unchanged

`callable-method-discovery`, `container-expansion`, `mapper-discovery`, `mapping-discovery`, `mapping-validation`, `processor`, `processor-options`, `seed-graph`, `expansion-test-harness` are unaffected. The `Bridge` / `BridgeStep` / `ElementSeed` / `GroupTarget` / `GroupBuild` / `Slot` SPI types are unchanged in signature; only their consumer (the expansion driver) changes.

## Impact

**Affected teams** — solo project; no cross-team coordination.

**Code — additions:**
- New per-group driver implementation: `processor/.../stages/expand/ExpandGroupsPhase.java`.
- New value type: `processor/.../graph/ExpansionGroup.java`.
- Renamed diagnostic stage: `processor/.../stages/validate/RealisationDiagnosticsStage.java` (replaces `ValidateRealisationStage.java`).
- `GetterRead` rewritten as a `Bridge` implementation in `strategies-builtin` (replaces its `SourceStep` form).

**Code — modifications:**
- `processor/.../stages/expand/ExpandStage.java`: outer loop semantics change to "invoke each phase exactly once"; the phase list becomes `[ResolveTargetChainsPhase, ExpandGroupsPhase]`.
- `processor/.../stages/expand/ResolveTargetChainsPhase.java`: emits `ExpansionGroup` objects via `MapperGraph.addGroup(...)` instead of edges-with-groupId; per-slot REALISED edges still emitted but as part of the group's edge set, with no `groupId` field on the edge.
- `processor/.../graph/MapperGraph.java`: replace `groupCodegens : Map<String, GroupCodegen>` with `groups : List<ExpansionGroup>`; replace `addGroupCodegen(...)` with `addGroup(ExpansionGroup)`; expose `groups()` accessor; `transformsView()` filter simplified to `kind == REALISED`.
- `processor/.../graph/Edge.java`: drop `groupId` field; drop the `groupId` parameter from `Edge.realised(...)` (now takes only `from, to, weight, codegen, strategyClassFqn`).
- `processor/.../graph/DotRenderer.java`: drop `SUB_SEED` / `ELEMENT_SEED` styling; iterate `graph.groups()` to emit group clusters; emit edges without `groupId` attributes (cluster boundary identifies the group).
- `processor/.../graph/Node.java`: delete `parent` field; `id()` returns `"node@" + System.identityHashCode(this)`.

**Code — deletions:**
- `spi/.../SourceStep.java`
- `spi/.../Step.java`
- `processor/.../stages/expand/ResolveSourceChainsPhase.java`
- `processor/.../graph/GroupRegistration.java` (replaced by `ExpansionGroup`)
- `processor/.../graph/SatisfySearch.java`, `SatisfyResult.java`, `SatisfyOutcome.java` (from `expansion-pruning`'s implementation)
- `processor/.../graph/BridgeGraphQuery.java` (forward-driver helper)
- `processor/.../stages/expand/BridgeSourceToTargetPhase.java`

**Generated outputs** — `.seed.dot` unchanged (still shows the user-directive framing). `.full.dot` shows the underlying graph including SEED framing, MARKER edges, every REALISED edge committed during expansion, and renders each `ExpansionGroup` as a DOT cluster. `.transforms.dot` is exactly the codegen substrate: REALISED edges + incident nodes, grouped by `ExpansionGroup` clusters. Under greedy expansion no orphan element clusters or `SetWrap`-style dead intermediates appear — each slot has exactly one producer.

**APIs / SPI** — `SourceStep` is gone. `Bridge`, `BridgeStep`, `ElementSeed`, `GroupTarget`, `GroupBuild`, `Slot`, `ResolveCtx` signatures are unchanged. `Edge.groupId` accessor removed; consumers retrieve group membership via `MapperGraph.groups()`.

**Tests** — major rework in `processor/src/test/groovy/`:
- `ExpansionFailureModesSpec` — rewritten against per-group driver outcomes.
- The property tests under `stages/expand/properties/` — rewritten with the new SAT/UNSAT verdict at group granularity.
- `SatisfyAlgorithmSpec`, `RealisationErrorMessagesSpec` (from `expansion-pruning`) — adapted: the satisfy() class is deleted, message-shape assertions move to a spec against the diagnostic-emission stage.
- `MapperGraphAppendOnlySpec`, `TransformsViewSpec` — adapted for the simplified `transformsView`, the smaller `EdgeKind` set, and the `ExpansionGroup` registry.
- New: `ExpansionGroupSpec` covering `AsSubgraph` view semantics, slot membership, nested-group composition via shared nodes.
- Strategy specs in `strategies-builtin/src/test/groovy/` — `GetterReadSpec` adapted for the `Bridge` rewrite; others unaffected.

**Sequencing** — this change supersedes `expansion-pruning` in flight. Archive `expansion-pruning` (without applying its remaining tasks 33–36) before starting implementation here. Use `expansion-pruning`'s implementation-work commits as the baseline; surviving artifacts (`.transforms.dot` rename, debug-output split, closest-miss message format) live in this change's specs.

**Systems** — CI runs `./gradlew check` on the rewrite. The integration project (`~/Projects/joke/percolate-integration`) is the primary acceptance signal: `PersonMapper` must compile when its directives are reachable, fail with the canonical closest-miss when a directive cannot be realised, produce a `.full.dot` with **clean group clusters and no `SetWrap`-style dead intermediates**, and produce a `.transforms.dot` that is consumable by codegen.
