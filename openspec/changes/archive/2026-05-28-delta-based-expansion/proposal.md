## Why

`ExpandGroupsPhase` has grown to 696 lines and is already annotated `@SuppressWarnings("PMD.GodClass")`. Its imperative shape — pervasive in-place graph mutation, a cross-cutting `producerScopes` side-map, a `ChangeTracker` accumulator threaded through every method, and three group-kind branches sharing a dense frontier-expansion engine — makes the phase hard to read, hard to test in isolation, and hides one minor correctness issue (cycle-rejected bridge steps leave orphan input nodes in the graph). Refactoring it to a delta-driven pipeline with pure expanders + a single applier centralises mutation, eliminates the side-map and accumulator, fixes the orphan leak, and unlocks expander-level unit tests that need no real `MapperGraph`.

## What Changes

- Introduce an internal `Delta` taxonomy (visitor-pattern sum type — Java 11 idiomatic, Lombok `@Value`) describing every graph mutation the phase can request: `AddNode`, `AddEdge`, `AddEdgeToView`, `ImportToView`, `TypeNode`, `AddGroup`.
- Introduce `DeltaBundle { String origin; List<Delta> deltas }` for atomic application — a bundle is accepted as a whole or rejected as a whole (cycle reject drops the entire bundle, fixing the orphan-node leak).
- Introduce `ExpansionState` (mutable) and `ExpansionSnapshot` (read-only facade); expanders only ever see the snapshot.
- Introduce `GroupExpander` interface with `boolean appliesTo(group)` + `GroupStepResult step(group, snapshot)` returning `(List<DeltaBundle>, List<Node> pendingSlots)`. SAT is signalled by `pendingSlots.isEmpty()` — no explicit `MarkSat` delta.
- Split the three branches into DI-bound expanders: `PathSegmentExpander`, `DirectiveBindingExpander`, `BridgeExpander` (the last delegating to a shared `FrontierMatcher` + `InputAllocator`).
- Introduce `Applier implements Delta.Visitor<Void>` as the **only** call site that mutates `MapperGraph`, calls `Node.setTyping`, mutates group views, or runs the cycle check; it owns the `producerScopes` `IdentityHashMap` as private state.
- Convergence becomes per-pass batched-end-of-pass: each pass collects bundles from all non-SAT groups against a snapshot, then applies them. Loop terminates when a pass produces no bundles and no new SATs. `ChangeTracker` is deleted.
- **BREAKING** (internal only — no public API): the imperative entry-points named in `graph-expansion`'s requirements (`commitBridgeStep`, `tryGroupTargets`, `expandFrontier`, `findCandidateByInputType`, `tryBridgeOnCandidate`, `resolveSlot`) cease to exist; the equivalent semantics live in the expander/applier split. The `Bridge` / `GroupTarget` / `PathSegmentResolver` SPI is unchanged.
- Replace `ExpandGroupsPhaseSpec` with fresh expander-level Spock specs that assert on `GroupStepResult` directly (no live `MapperGraph` required).

Observable behaviour preserved: SAT/UNSAT outcomes, edge emission rules, bridge match semantics, multi-fire siblings, candidate scoping to current view, cycle rollback, base-case SAT, `MAX_OUTER_PASSES`, all per-slot semantics. The only behavioural changes are (a) the orphan-node fix, and (b) cross-group SAT visibility is now batched at end-of-pass rather than visible mid-pass — functionally equivalent under fixed-point, may add one extra pass in degenerate cases (well within the 32-pass budget).

## Capabilities

### New Capabilities
None.

### Modified Capabilities
- `graph-expansion`: requirements that name internal methods (`commitBridgeStep`, `tryGroupTargets`, `expandFrontier`, `findCandidateByInputType`, `tryBridgeOnCandidate`, `resolveSlot`) are restated against the new expander/applier split. The "Cross-group fixed-point loop" requirement's state-change signal is restated from "SAT transition OR new sub-group OR `Node.setType` call" to "applier accepted at least one bundle OR a group's `pendingSlots` became empty". The "Bridge edge-emission rule" requirement adds atomic-bundle semantics (cycle-rejected step leaves no orphan input node). All behavioural scenarios remain valid.

## Impact

- **Affected code**: `processor/src/main/java/io/github/joke/percolate/processor/stages/expand/` — `ExpandGroupsPhase` shrinks to ~80 lines; new files for `Delta` + variants, `DeltaBundle`, `ExpansionState`, `ExpansionSnapshot`, `Applier`, `GroupExpander`, three expander impls, `FrontierMatcher`, `InputAllocator`. `MapperGraph.addEdgeIfAcyclic` semantics still used (called by `Applier`).
- **Affected tests**: `processor/src/test/groovy/.../ExpandGroupsPhaseSpec.groovy` deleted and replaced with per-expander Spock specs. Acceptance specs (the harness-driven end-to-end tests in `expansion-test-harness`) remain unchanged and serve as the regression net.
- **DI wiring**: Dagger module additions for the three `GroupExpander` impls (likely `@IntoSet` providers) and the `Applier`. No consumer-side changes.
- **SPI**: `Bridge`, `GroupTarget`, `PathSegmentResolver`, `ResolveCtx` — all unchanged. Strategies-stay-myopic rule preserved.
- **Dependencies**: no new libraries. JGraphT's `Graphs.unmodifiableGraph` (already on classpath) wraps the view in the snapshot.
- **Affected teams**: processor engine (sole owner of this code path); the change is invisible to mapper authors and to other percolate consumers.
