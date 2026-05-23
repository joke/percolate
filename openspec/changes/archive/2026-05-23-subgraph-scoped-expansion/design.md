## Context

The realisation engine emerged from a multi-iteration design path. `add-graph-expansion` (2026-05-08) shipped a forward-driven three-phase pipeline with a `SourceStep` SPI; `target-to-source-expansion` (2026-05-21) inverted the driver to target-driven; `source-path-resolvers` (2026-05-21) added a seed-time path-walking algorithm via `PathSegmentResolver`; `split-container-bridges` (2026-05-22) replaced the fused `*Map` diamonds with orthogonal `*Unwrap` / `*Wrap` / `*Collect` bridges and introduced multi-fire per frontier.

After all four changes, two structural smells remain:

- `ExpansionGroup` reifies a JGraphT `AsSubgraph` view per group (`MapperGraph.addGroup(...)`), but `ExpandGroupsPhase.expandFrontier` sources candidates from `SourceReachability.candidateInputs(scope, graph)` — a global scan over `MapperGraph.nodes()`. The view exists but is never consulted by the engine. The "group is a subgraph" model is implemented as metadata, not as the scope of expansion.
- Type-only candidate matching across the global graph produced two bug classes — cross-group cycles (`elem:HA ↔ elem:Set<HA>` formed by sibling sub-groups whose downstream-of-frontier nodes leak as candidates) and multi-parameter directive ambiguity (two `Person` parameters with directives targeting different paths get confused at expansion time). The remediation introduced cycle-prevention guards and moved path-resolution to a seed-time pre-pass — both workarounds for the underlying scope leak.

Strict subgraph isolation collapses both classes by construction:

1. **Per-sub-group view scoping** — candidate search inside expansion is restricted to the current group's `view.vertexSet()`; downstream sibling-group nodes are invisible.
2. **Instance-identity `Node`s** — fresh allocations create distinct objects even when `(scope, loc, type, parent)` would equal an existing node, so cycles cannot form via structural collision (`project_expansion_mental_model.md`).
3. **One sub-group per bridge match** — every match's REALISED edge lives in its own minimal view (`{root, slot, one edge}`), so cycle detection is structurally impossible at the per-group level.

Under that model, `PathSegmentResolver`'s seed-time pre-pass becomes redundant — resolvers can fire during expansion within path-segment groups without re-introducing the multi-parameter ambiguity that motivated the seed-time fix (each path-segment group's slot is the specific parameter root the directive named; per-group local lookup cannot shuffle parameters between directives).

## Goals / Non-Goals

**Goals:**
- Make `ExpansionGroup.view` the authoritative scope of candidate search during expansion.
- Collapse the PRESERVING / ENTERING / EXITING split in `commitBridgeStep` to a single uniform branch: every match spawns its own one-slot sub-group, regardless of `ScopeTransition`.
- Restore the seed graph to a purely structural artifact (untyped at source-path segments). Move `PathSegmentResolver` invocation from `SeedGraph` into `ExpandGroupsPhase`, firing inside path-segment groups.
- Make group SAT outcome-driven and structurally local: a group SATs when every slot has at least one SAT child sub-group; base-case SAT for parameter-root slots is determined per-group from the slot's `Location` matching `currentMethod`'s parameters — no global ambient set.
- Process the group work-list in topological order over the shared-boundary group-dependency DAG (`G` depends on `H` iff `H.root ∈ G.slots`), so dependents see typed boundaries when expanded.
- Drop all cycle-prevention guards; cycles are impossible by construction.

**Non-Goals:**
- Codegen. The change preserves enough structure for a future codegen pass to walk the sub-group tree, but does not specify the walk or emit Java. Expansion produces the structure; codegen reads it later.
- `Bridge` SPI changes. The `BridgeStep` shape stays as `split-container-bridges` left it; resolvers and bridges live behind the existing surfaces.
- `PathSegmentResolver` SPI changes. The SPI surface is unchanged; only the invocation site (engine vs. seed) moves.
- `MapperGraph` schema migration. `EdgeKind` stays `{SEED, REALISED, MARKER}`; no edge types added or removed.
- Performance optimisation. The model is correctness-first; perf tuning (e.g., reverse boundary-node index for topological ordering) is a follow-up.

## Decisions

### D1. Sub-group per bridge match, regardless of `ScopeTransition`

**Decision:** `ExpandGroupsPhase.commitBridgeStep` SHALL register a fresh one-slot `ExpansionGroup` for **every** matching `BridgeStep`, with the frontier as root and the allocated input as the sole slot. `PRESERVING` matches no longer grow the parent group's view; `ENTERING` / `EXITING` matches no longer take a different code path. One rule.

**Why:** Uniform sub-group structure makes the view-scoped candidate-search rule (D2) trivially correct: every group is the smallest unit of expansion, `{root, slot, one edge}`. Cycles cannot form across sub-groups because each sibling's candidate scope is independent and instance-identity prevents structural collisions. The `PRESERVING-grows-view` rule was the engine pattern that allowed cross-group leakage today; uniform spawning removes that affordance.

**Alternatives considered:**
- *Keep PRESERVING growing the current group; only scope-changing matches spawn.* Smaller refactor (closer to `split-container-bridges`), but reintroduces the multi-chain-within-one-view complication that motivated the engine guard. Rejected as continuing the pattern we're trying to retire.
- *No sub-groups; engine maintains a global "current chain" set per slot.* Closer to the original `target-to-source-expansion` design pre-`ExpansionGroup`. Rejected as it eliminates the structural-DAG property and re-introduces global state.

### D2. Candidate search scope = `currentGroup.getView().vertexSet()`

**Decision:** When `expandFrontier(frontier, group, ...)` is invoked, candidate inputs SHALL come from `group.getView().vertexSet()` excluding the frontier itself and `TargetLocation` nodes. `SourceReachability.candidateInputs(scope, graph)` and the global `MapperGraph.nodes()` scan are removed from the expansion path.

**Why:** This is the structural fix for cross-group cycles and multi-parameter ambiguity. Under uniform sub-grouping (D1), each sub-group's view contains `{root, slot, one edge}` — candidate search trivially scopes to "the slot, maybe the root". Sibling sub-groups' nodes are invisible. Combined with instance-identity (D3), no candidate scan can pick up a downstream-of-frontier node by type.

**Alternatives considered:**
- *Global scan with cycle-prevention filter (`!reachableViaRealised(frontier → candidate)`).* The `split-container-bridges` guard. Rejected as a workaround for the missing scope rather than a fix.
- *View-scoped scan + exposed-root index across groups (boundary-import on type-match).* Adds an "exposes" semantics to groups so target-side chains can find typed source roots without a global scan. Rejected for v1 — the topological work-list (D6) achieves the same effect because dependent groups are processed after their dependencies, so shared-boundary nodes are already members of the dependent group's view by the time expansion sees them.

### D3. `Node` is instance-identity; `MapperGraph.addNode` is the only convergence point

**Decision:** `Node.equals` returns `this == other`; `Node.hashCode` returns `System.identityHashCode(this)`. `MapperGraph.addNode(Node)` is idempotent on equal (instance-equal) nodes — i.e., adding the same instance twice is a no-op. `SeedGraph` is responsible for emitting **one instance per directive-segment-root key** (it already does this via prefix-sharing). Bridge expansion creates **fresh instances** for every allocated input.

**Why:** Instance-identity is the documented model in `project_expansion_mental_model.md` ("Two `Node` instances with identical `(scope, loc, type)` are distinct nodes"). It is the structural property that makes sub-grouping (D1) cycle-free: a freshly allocated `elem:Set<HA>` in sub-group `sg1` is a different `Node` object from an `elem:Set<HA>` previously allocated by sibling `sg2`, even though their presentation attributes are equal. Cycles require shared identity; instance-identity removes that channel.

**Alternatives considered:**
- *Structural identity via `(scope, loc, type, parent)`.* Earlier design iteration. Rejected because it forced same-shape allocations to converge — exactly the channel cross-group cycles ride.

### D4. `SeedGraph` registers one group per SEED edge; seed graph is untyped at source-path segments

**Decision:** `SeedGraph` SHALL register one one-slot `ExpansionGroup` per SEED edge. Directive-bridging edges become directive-binding groups (`root = tgt[full-target-path]`, `slot = src[full-source-path]`); path-segment edges become path-segment groups (`root = src[prefix.segment]`, `slot = src[prefix]`). `SeedGraph` SHALL NOT invoke `PathSegmentResolver`s; the typed source chain produced by today's seed-time path-walking is removed. The directive-bridging edge's `from` is always the untyped seed leaf (no typed-source-prefix preference).

**Why:** The user's mental model — every SEED edge is a sub-group at seed time — restores the architecture to the 2026-05-08 layout (where source paths were untyped after seed) while keeping the structural disambiguation that `source-path-resolvers` introduced (each path-segment group's slot is the specific parameter root). Type resolution moves into expansion (D5) where it is naturally target-driven and per-group local.

**Alternatives considered:**
- *Keep seed-time path resolution; only retrofit expansion to be subgraph-scoped.* Smaller change; safer. Rejected after analysis showed seed-time path resolution is a workaround for global candidate search (the very thing this change fixes). Once the engine is subgraph-scoped, the workaround becomes redundant; carrying it as legacy creates two ways to type a source chain.

### D5. `PathSegmentResolver` fires inside path-segment groups during expansion

**Decision:** `ExpandGroupsPhase` SHALL invoke registered `PathSegmentResolver`s when expanding a **path-segment group** — a group whose `root` and `slot` both carry `SourceLocation` and whose root's path is a single-segment extension of the slot's path. For such groups, the engine iterates resolvers in `Class.getName()` ascending order, calling `resolve(slot.type.get(), segmentName, ctx)`. The first non-empty `ResolvedSegment` types the root (`Node.type` updated in place, since instance-identity preserves identity-by-object) and emits a single REALISED edge from slot to root with the resolver-provided codegen and weight. The path-segment group SATs.

The resolver SPI surface is unchanged — `resolve(parentType, segment, ctx)` returns `Optional<ResolvedSegment>`. The traversal stays target-driven: the engine starts at the group's root (the untyped segment), asks "what produces this from my slot?", and the resolver answers. Resolver-internal type inference is forward (Person.getAddresses() → List<Opt<PA>>), but the engine's graph traversal is backward. Per `feedback_never_forward_expansion.md`, this distinction is preserved.

**Why:** Path resolution is the answer to "what produces this single-segment access from its parent?" — naturally per-group local. Moving it into expansion makes the resolver another participant in the same model as bridges, not a special seed-time pre-pass. The disambiguation correctness is structural (D4 ensures the slot is the directive's specific parameter root).

**Alternatives considered:**
- *Resolvers stay at seed time; engine ignores them during expansion.* Today's behaviour. Rejected per D4 rationale.
- *Resolver-as-Bridge: collapse `PathSegmentResolver` into the `Bridge` SPI.* Rejected because resolvers need the segment name (not a type pair); shoehorning a name field into `Bridge` widens the SPI for a single use case. Two narrow SPIs are clearer than one wide one.

### D6. Cross-group fixed-point loop

**Decision:** `ExpandGroupsPhase` SHALL iterate over registered `ExpansionGroup`s in registration order, expanding each one, and SHALL repeat the iteration until a full pass produces no state changes. State changes include: a group transitioning from pending to `SAT`, a new sub-group registered during expansion, an in-place `Node.setType(...)` call. A `MAX_OUTER_PASSES = 32` cap MUST hold as a safety net (a trip indicates a bug, not a slow chain).

Within-group expansion stays target-to-source (root frontier asks "what produces this?"); the cross-group loop is independent of and orthogonal to the within-group rule. A group whose expansion cannot proceed because a slot is still untyped SHALL be marked as **pending** (not `unsatNoPlan`) and retried in the next outer pass. Only when the outer fixed-point converges with the group still pending is the final outcome recorded as `unsatNoPlan` (or `unsatDidNotConverge` if the budget was exhausted).

**Why:** Source-side path-segment groups type their roots during their own expansion; downstream directive-binding groups need to see the typed boundary. In an earlier sketch we used a topological pre-sort over the boundary DAG to ensure source-side runs first — but the fixed-point loop arrives at the same effective order naturally, with two structural advantages:

1. **Mirrors the within-group bounded iteration pattern.** `resolveSlot` already iterates rounds within a slot. `fillGroup` iterates slots within a group. The cross-group fixed-point is the same shape at the next level up — uniform "iterate until quiescent" across all three scopes.
2. **Handles dynamic sub-group registration without special-case insertion logic.** Sub-groups registered during expansion just get picked up in the next pass. No "insert at the head of the work-list" rule needed.

Convergence is guaranteed because the state space is monotonic — nodes go untyped → typed (one-way via `setType`), groups go pending → `SAT` (one-way), sub-groups are only added. Combined with finite group count and DAG-acyclic boundary structure (guaranteed by target-to-source group dependency), termination is bounded at O(depth) passes (typically 2–4 for realistic mappers).

The earlier "no fixed-point loop" note in `project_expansion_direction.md` (since updated) referred to forward-sweep retries that the 2026-05-08 design rejected. A backward-only fixed-point loop is distinct: each pass is still target-to-source per group; only the orchestration is iterative.

**Alternatives considered:**
- *Topological pre-sort over the boundary DAG.* O(n) vs O(n × depth) of the fixed-point. Saves a handful of redundant passes for realistic mappers — not worth the static-analysis machinery (compute DAG, handle dynamic sub-group insertion at the right position). The fixed-point loop is simpler.
- *FIFO with one-pass + "did not yet sat" diagnostics.* Risk of false-negative diagnostics when slot typing arrives via a later group in the same pass. Rejected: the diagnostic correctness requires deferring outcome recording until quiescence, which is exactly the fixed-point.
- *Per-group lazy re-queue when a dependency types.* Adds reverse-dependency tracking (when X SATs, notify dependents). Same correctness as fixed-point, more bookkeeping. Rejected.

### D7. SAT propagation: outcome-driven, structural base case

**Decision:** Group SAT is outcome-driven: `G` SATs iff every slot in `G.slots` has at least one child sub-group rooted at it whose outcome is `SAT`. Base case: a slot SATs trivially when its `Location` is a single-segment `SourceLocation` whose first segment matches a parameter name of `currentMethod`. No global `sourceParameterRoots` set is consulted. `MapperGraph.recordGroupOutcome(...)` continues to register outcomes; the outcome is computed during expansion and read by `realisation-validation`.

**Why:** Structural base case keeps SAT determination local to each group's view. The diagnostic stage already consumes outcomes (`realisation-validation`); no double accounting between "REALISED-reachability" and "outcome-state".

**Alternatives considered:**
- *Keep `SourceReachability.slotReachable(slot, graph, sourceRoots)` as the SAT check.* Works but couples SAT to a global reachability query, re-introducing the global state we're removing.

### D8. Drop ambient source-roots; cycles dropped via CycleDetector rollback

**Decision:** `SourceReachability.sourceParameterRoots(...)` and the `sourceRoots` parameter threaded through `resolveSlot` / `expandFrontier` SHALL be removed. The engine SHALL drop cycle-attempting REALISED edges via `MapperGraph.addEdgeIfAcyclic(edge)`: speculatively add the edge, run JGraphT `CycleDetector` over the REALISED `MaskSubgraph`, roll back on cycle. The candidate sub-group SHALL NOT be registered when its edge is rolled back; `tryBridgeOnCandidate` proceeds to the next step.

**Why:** Ambient source-roots are unnecessary — parameter-root SAT is structural (D7) and per-group. Sibling-derived nodes can't leak into a sub-group's view (instance-identity D3 + narrow boundary import restricted to `SourceLocation` nodes), so the cross-group leakage that motivated the original cycle-prevention guard from `split-container-bridges` is gone. But inverse-bridge pairs (Wrap↔Unwrap, Collect↔Unwrap) at the same `Location` still produce cycle-attempting matches: when expanding a sub-group's slot, `findCandidateByInputType` finds the parent group's root (same loc, same type as the bridge's input), and committing reuses it as input — creating a back-edge that closes the group's slot→root REALISED edge into a cycle. Letting those commits stand produces visible cycles in `transforms.dot`. Excluding root from candidates instead produces exponential dead-branch trees because each inverse match fresh-allocates a new slot rather than folding back (integration mapper grew from 28 to 1935 nodes when this was tried). Post-hoc CycleDetector + rollback gives a bounded acyclic graph: the alive chain composes via source-typed reuse (`findCandidateByInputType` returns the imported `src[…]` node), dead inverse-bridge branches drop at the first commit attempt.

**Alternatives considered:**
- *Pre-emptive reachability check via hand-rolled BFS in `commitBridgeStep`.* Rejected: duplicates JGraphT primitives. `MapperGraph` already uses `CycleDetector` via `isAcyclic()`; the same primitive is the right tool for the per-commit check.
- *Exclude the parent group's root from candidates.* Eliminates inverse-bridge cycles at source but causes exponential dead-branch expansion. Rejected after integration mapper exploded ~70× in node count.
- *Keep cycle prevention as a separate "guard" method.* Rejected. Encapsulating the speculative-add + cycle-check + rollback inside `MapperGraph.addEdgeIfAcyclic(...)` keeps the append-only public API invariant (`MapperGraph` exposes no `removeEdge` / `remove` / `clear` / `delete` / `evict`) while letting the engine treat "would close a cycle" as a normal SHALL-skip case.

## Group shape under the new model (worked example)

For `Human map(Person person)` with directives `@Map(target = "addresses", source = "person.addresses")` and `@Map(target = "firstName", source = "person.firstName")` and a constructor `Human(Optional<Set<HA>> addresses, String firstName)`:

```mermaid
flowchart TD
    g1["g1: Constructor<br>root=tgt[]:Human<br>slots=[tgt[addresses], tgt[firstName]]"]
    g2["g2: Directive binding<br>root=tgt[addresses]:Optional&lt;Set&lt;HA&gt;&gt;<br>slot=src[person.addresses]:?"]
    g3["g3: Path segment<br>root=src[person.addresses]:?<br>slot=src[person]:Person"]
    g4["g4: Directive binding<br>root=tgt[firstName]:String<br>slot=src[person.firstName]:?"]
    g5["g5: Path segment<br>root=src[person.firstName]:?<br>slot=src[person]:Person"]

    g1 -- shared boundary tgt[addresses] --> g2
    g1 -- shared boundary tgt[firstName] --> g4
    g2 -- shared boundary src[person.addresses] --> g3
    g4 -- shared boundary src[person.firstName] --> g5

    classDef seed fill:#ffe5e5,stroke:#a83c3c
    classDef ctor fill:#dff5d8,stroke:#3c803c
    class g1 ctor
    class g2,g3,g4,g5 seed
```

`src[person]` is base-case SAT (parameter root) for both g3 and g5. Cross-group fixed-point loop:

- **Pass 1**: g1 cannot proceed (slots untyped further down); g2 cannot proceed (slot src[person.addresses] still untyped); g3 SATs by invoking `GetterPathResolver` against `Person.getAddresses()` → types src[person.addresses] to `List<Optional<PA>>`; g4 cannot proceed (slot untyped); g5 SATs similarly, typing src[person.firstName] to String. "State changed" — run another pass.
- **Pass 2**: g2 now sees a typed slot, expands container-bridge tree (List<Opt<PA>> → … → Optional<Set<HA>>) as a sub-group tree beneath g2; g4 trivially SATs (typed slot of type String, root tgt[firstName]:String — DirectAssign matches); g1 still pending until its child sub-groups SAT. "State changed" — run another pass.
- **Subsequent passes**: each container-chain sub-group beneath g2 SATs one or two passes after its parent (chain depth bounded by `MAX_OUTER_PASSES`). Eventually g2 SATs, then g1 SATs.
- **Final pass**: no state changes; loop terminates.

Each chain hop is its own one-slot sub-group rooted at the current frontier. The fixed-point loop converges in O(chain-depth) passes (typically 2–4 for realistic mappers).

## Risks / Trade-offs

- **[Engine refactor blast radius]** → Mitigation: the integration mapper (`PersonMapper.mapHuman` + `mapAddress`) stays as the green-build acceptance signal. The change is bounded to `processor/stages/expand/` plus `SeedGraph`; SPI surfaces are untouched. Reverting is a single revert (no schema migration).
- **[Sub-group count explosion in dumps]** → Mitigation: each container chain produces ~5 sub-groups per directive; for the integration mapper, this means ~40 nested groups total. The DOT renderer's cluster grouping handles arbitrary nesting; readability is comparable to today's `*.full.dot`. Codegen is a future concern that walks the tree.
- **[Path-segment-group recognition is structural, not declared]** → Mitigation: the engine identifies path-segment groups by `(root.loc, slot.loc)` shape — both `SourceLocation`, root path is slot path + one segment. This is a heuristic; a future change can add an explicit `groupKind` field to `ExpansionGroup` if confusion arises.
- **[Topological ordering with dynamic group registration]** → Mitigation: every nested sub-group registered during expansion has its root in the current group's view (a known set), so the new group is a leaf in the dependency-DAG (no group depends on it yet). Inserting into a topo order at the next iteration is O(1) — process it next in the next iteration without re-sorting.
- **[`PathSegmentResolver` order changes from `Class.getName()` to first-match-wins]** → Mitigation: same ordering, semantics unchanged. The only behavioural shift is *when* the resolver is invoked (expansion vs. seed). Existing resolver specs from `source-path-resolvers` remain valid.

## Open Questions

- **Sub-group registration during multi-fire** — when two bridges match the same frontier (e.g. `OptionalCollect` and `OptionalWrap` for `tgt[addresses]:Optional<Set<HA>>`), each spawns its own sub-group. The two sub-groups share the same root but different slots. Open: should the multi-fire siblings carry any metadata linking them (e.g. a shared `alternativeGroupId`) for diagnostic dumps, or is "same root" enough? Leaning toward "same root is enough" — codegen and diagnostics can detect siblings by root identity.
- **Path-segment groups for `record` accessors and field reads** — same structural shape as `GetterPathResolver`; the new model treats them uniformly. Confirm no resolver-specific work is needed during the migration.
- **`SeedGraph` removal of typed nodes** — directive-binding groups whose source/target types match exactly today get a "directive-binding REALISED edge" pass-through (per `graph-expansion` spec). Under D4, the seed slot is untyped, so the same-type pass-through can no longer fire at seed time. Open: does it move into expansion as a `DirectAssign`-equivalent bridge, or is the typed pass-through detected during path-segment-group SAT (since the slot becomes typed after expansion)? Leaning toward "detect after typing" — it's a natural consequence of expansion ordering.
