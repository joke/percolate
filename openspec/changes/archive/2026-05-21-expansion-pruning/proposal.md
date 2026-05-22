## Why

Today's `.expanded.dot` claims to be the codegen-walkable graph but contains speculative dead branches the engine never reconciled, and `ValidatePathsPhase` only audits top-level `SourceLocation → TargetLocation` SEED reachability — it never inspects `SUB_SEED` closure or element-seed promises. The combined effect: an integration mapper missing its `mapAddress(Person.Address)` method silently produces a "successful" expanded graph with an orphan `Human.Address` node, no error is emitted, and (once codegen lands) broken code would be generated. The root cause is architectural — the graph and the verdict on the graph live in two artifacts that disagree. This change makes the verdict a property of the graph: realisation validation becomes a per-directive satisfiability search that walks `REALISED` edges and recursively closes every promise (`SUB_SEED`, `ELEMENT_SEED`), then emits a closest-miss compile error when a directive target has no satisfying assignment. The same restructure splits the debug outputs into three files that each mean exactly one thing.

## What Changes

- **BREAKING** Add `EdgeKind.ELEMENT_SEED` so a strategy's per-element promise (today encoded as `EdgeKind.SEED` and indistinguishable from a user directive) is first-class. The satisfiability search distinguishes promises from directives by edge kind; views and renderers also benefit from the cleaner taxonomy.
- Make `BridgeSourceToTargetPhase.applySubSeedEmissionRule` symmetric with `applyUnifiedEmissionRule` (collapse into a shared helper): both must iterate `step.getElementSeeds()` and materialise ElementLocation phantom nodes + element-seed edges. Today the SUB_SEED path drops element seeds, losing a strategy's per-element promise whenever the strategy is invoked via a SUB_SEED (the common nested-container case). Without this fix, the satisfiability search would not see the promises strategies make.
- Rewrite `ValidateRealisationStage` (today `ValidatePathsPhase` + `ValidateMarkersPhase`) as a per-directive `satisfy()` search on `MapperGraph`. For each user directive, the search walks back from the directive's realised target through `REALISED` edges, picking one whose source is itself satisfiable and whose every promise (`SUB_SEED`, `ELEMENT_SEED`) is also satisfiable. The first satisfying assignment wins; if none exists, a closest-miss compile error is emitted naming the strategy whose promise could not be closed (via `Edge.strategyClassFqn`) and the type of the unclosable promise. `ValidateMarkersPhase` is folded in (an untyped seed endpoint with no marker-resolved counterpart is, by construction, not satisfiable). The `realisation-validation` capability stays; only the implementation changes.
- **BREAKING** Rename the debug output `.expanded.dot` to `.transforms.dot`. The new file is a *structural* filter over the underlying graph: only `REALISED` edges and their endpoints (typed `SourceLocation` / `TargetLocation` nodes plus `ElementLocation` phantoms involved as endpoints). Dead-end transformations are included — this is the menu of transformations the engine produced, not the subset that any directive actually uses. The choice of which transformations a directive will use is a future codegen-time concern (Dijkstra over weights), and a fourth `.plan.dot` dump can be added then.
- Introduce a new `.full.dot` debug output that is a pure dump of the underlying `MapperGraph` — every kind of edge, every kind of node, no styling tricks, no analysis. Its purpose is diagnostic: when a user wants to see what the engine actually emitted (including promises and markers), this file is the source of truth. The existing `.seed.dot` is unchanged. The three files now read as a story: *what you asked for → what we tried → what we will pick from*.

Strategies themselves do not change. The `Bridge` / `SourceStep` / `GroupTarget` SPI is unchanged. `BridgeStep.elementSeeds` already exists and remains the strategy-side surface — the change is in how the driver materialises what strategies emit and in how the engine confirms a satisfying assignment exists.

## Capabilities

### New Capabilities

None. The restructure is implementable as modifications to four existing capabilities.

### Modified Capabilities

- `graph-model`: add `EdgeKind.ELEMENT_SEED` and the invariant that element-seed edges connect two `ElementLocation` phantom nodes. The earlier conflation of element seeds and directive seeds under `EdgeKind.SEED` is no longer permitted.
- `graph-expansion`: require `applySubSeedEmissionRule` to process `step.getElementSeeds()` identically to `applyUnifiedEmissionRule` (collapsed into a single shared helper). Element-seed edges produced via either rule must use `EdgeKind.ELEMENT_SEED`.
- `graph-debug-output`: three outputs per mapper (`.seed.dot`, `.full.dot`, `.transforms.dot`) with the names and contents defined above. `.full.dot` is a pure dump of the underlying graph; `.transforms.dot` is a structural filter (only `REALISED` edges + their endpoints + `ElementLocation` phantoms involved as endpoints; promises and markers excluded). `.seed.dot` is unchanged. A future `.plan.dot` (Dijkstra-selected subset) is not part of this change.
- `realisation-validation`: requirement-level changes only — directive realisation is now defined as "a per-directive `satisfy()` search starting at the directive's realised target finds a satisfying assignment of `REALISED` edges that closes every promise (`SUB_SEED`, `ELEMENT_SEED`) along the way." Diagnostics carry closest-miss provenance: the deepest `REALISED` edge whose source is satisfiable but whose promises include at least one unsatisfiable target, the strategy that emitted that edge, and a suggestion for the likely missing piece. The marker-presence check that lived in `ValidateMarkersPhase` is subsumed (an untyped seed endpoint with no marker-resolved counterpart is structurally unsatisfiable). Implementation classes change; the capability's responsibility does not.

### Unchanged

`callable-method-discovery`, `container-expansion`, `expansion-strategy-spi`, `expansion-test-harness`, `mapper-discovery`, `mapping-discovery`, `mapping-validation`, `processor`, `processor-options`, `seed-graph` are unaffected. Strategies and their tests do not change.

## Impact

**Affected teams** — solo project; no cross-team coordination needed.

**Code** — modified: `graph/EdgeKind.java` (new constant), `graph/Edge.java` (`elementSeed` factory's kind), `graph/MapperGraph.java` (update view methods: rename / repurpose `expandedView` → `transformsView` with structural filter semantics; the `fullView` is a pass-through), `graph/ExpandedGraphView.java` (either renamed to `TransformsView` with new filter semantics or replaced; the current "filter SEED/MARKER" implementation is wrong under the new model), `graph/DotRenderer.java` (handle `ELEMENT_SEED` edges with a distinct color; no alive/dead styling needed), `stages/expand/BridgeSourceToTargetPhase.java` (symmetric SUB_SEED emission via shared helper), `stages/validate/ValidateRealisationStage.java` (rewritten as per-directive `satisfy()` search with closest-miss mining), `stages/dump/DumpExpandedGraph.java` (renamed to `DumpTransforms`), `ProcessorModule.java` (wiring). Net delete: `stages/validate/ValidateMarkersPhase.java` (subsumed). New sibling: `stages/dump/DumpFullGraph.java`. `ValidatePathsPhase` either evaporates into `ValidateRealisationStage`'s body or is rewritten as a thin helper — implementation detail.

**Generated outputs** — `<MapperFqn>.expanded.dot` no longer produced. New: `<MapperFqn>.transforms.dot` (structural filter; `REALISED` edges + endpoints) and `<MapperFqn>.full.dot` (pure dump of the underlying graph). `<MapperFqn>.seed.dot` unchanged. Developer tooling or scripts that grep for `*.expanded.dot` must update; this is BREAKING for debug-output filenames.

**APIs / SPI / dependencies** — none externally. `Bridge`, `BridgeStep`, `ElementSeed`, `SourceStep`, `GroupTarget` are unchanged. `EdgeKind` is processor-internal and not part of the strategy SPI. No new third-party dependencies.

**Tests** — `processor/src/test/groovy/.../stages/expand/ExpansionFailureModesSpec.groovy` likely contains scenarios that pass green today only because validation never inspected promises; expect a small number of those to flip red and need either pinning under `// FOLLOW-UP:` or migration to assert the new error messages. `BuiltinServiceRegistrationSpec` and the per-strategy specs added by `unit-test-builtin-strategies` are unaffected (strategies don't change). Property tests under `stages/expand/properties/` may need their oracles updated to account for the new satisfy semantics.

**Sequencing** — independent of the in-progress `unit-test-builtin-strategies` change. Ideally lands after it so the per-strategy specs already exist and can host pinning scenarios for the symmetric-emission fix.

**Systems** — CI: `./gradlew check` picks up the new stage + tests automatically. No infra changes.
