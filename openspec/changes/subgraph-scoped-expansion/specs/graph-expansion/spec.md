## ADDED Requirements

### Requirement: Candidate search scoped to current group's view

`ExpandGroupsPhase.expandFrontier(frontier, group, ...)` SHALL source candidate input nodes from `group.getView().vertexSet()`, excluding the frontier itself and excluding any node whose `Location` is a `TargetLocation`. No global scan over `MapperGraph.nodes()` SHALL occur during expansion.

The view-scoped search is the structural fix for cross-group cycles and multi-parameter directive ambiguity. Combined with `Node` instance-identity (see `graph-model`) it makes it impossible for a sibling sub-group's downstream-of-frontier node to be picked up as a candidate.

#### Scenario: Candidates come only from the current group's view
- **WHEN** `expandFrontier(frontier, currentGroup, graph)` is invoked
- **THEN** the candidate stream contains only nodes from `currentGroup.getView().vertexSet()`
- **AND** the candidate stream excludes the frontier
- **AND** the candidate stream excludes any node whose `Location` is a `TargetLocation`
- **AND** no candidate is sourced from `MapperGraph.nodes()` directly

#### Scenario: Sibling sub-group nodes are invisible
- **WHEN** the underlying `MapperGraph` contains a `Node` `X` that is a member of some sibling sub-group `S` but not of `currentGroup.getView().vertexSet()`
- **AND** `expandFrontier(frontier, currentGroup, graph)` is invoked
- **THEN** `X` is not a candidate considered by any `Bridge` query in that frontier's expansion

### Requirement: Every bridge match spawns a one-slot nested ExpansionGroup

`ExpandGroupsPhase.commitBridgeStep` SHALL register one fresh one-slot `ExpansionGroup` for **every** matching `BridgeStep`, regardless of `BridgeStep.scopeTransition`. The new group SHALL have `root = frontier`, `slots = [inputNode]`, `initialEdges = {inputNode → frontier REALISED}`, `codegen = step.getCodegen()` lifted to a `GroupCodegen`, and `strategyClassFqn = <originating-bridge-FQN>`. The new group joins the work-list.

The PRESERVING / ENTERING / EXITING split in `commitBridgeStep` is collapsed: there is no longer a code path that grows the parent group's view in place. Every match becomes its own sub-group.

#### Scenario: PRESERVING bridge match spawns a sub-group
- **WHEN** `commitBridgeStep` runs for a `BridgeStep` with `scopeTransition == PRESERVING`
- **THEN** a fresh one-slot `ExpansionGroup` is registered with the frontier as root
- **AND** the new group joins the work-list
- **AND** the parent group's view is NOT mutated to add the input node directly

#### Scenario: ENTERING bridge match spawns a sub-group
- **WHEN** `commitBridgeStep` runs for a `BridgeStep` with `scopeTransition == ENTERING`
- **THEN** a fresh one-slot `ExpansionGroup` is registered with the frontier as root and the allocated element-scope input as its sole slot

#### Scenario: EXITING bridge match spawns a sub-group
- **WHEN** `commitBridgeStep` runs for a `BridgeStep` with `scopeTransition == EXITING`
- **THEN** a fresh one-slot `ExpansionGroup` is registered with the frontier as root and the allocated element-scope input as its sole slot

### Requirement: Cross-group fixed-point loop

`ExpandGroupsPhase` SHALL iterate over registered `ExpansionGroup`s in registration order, calling `fillGroup` on each, and SHALL repeat the iteration until a full pass completes with no state changes. A "state change" is any of:

- a group's outcome transitioning from pending to `SAT`,
- a new sub-group registered via `MapperGraph.addGroup(...)` during expansion,
- an in-place `Node.setType(...)` call (path-segment groups type their root via this mutator).

Within-group expansion stays target-to-source per the "Bridge edge-emission rule" requirement; the outer fixed-point loop is independent of and orthogonal to the within-group traversal direction.

A group whose expansion in the current pass cannot proceed because a slot is still untyped (or no candidate matches) SHALL be left in **pending** state — NOT recorded as `unsatNoPlan` — and retried in the next outer pass. The group's outcome is recorded only when the outer fixed-point converges:
- If a group is still pending at convergence and at least one slot remains unsatisfied, `unsatNoPlan(group, failingSlot)` is recorded.
- If the per-slot round budget tripped, `unsatDidNotConverge(group, failingSlot)` is recorded.
- Otherwise (every slot satisfied), `sat(group)` is recorded.

The phase SHALL enforce a `MAX_OUTER_PASSES = 32` cap as a safety net. Exceeding it indicates a bug (state monotonicity guarantees convergence in O(depth) passes, typically 2–4 for realistic mappers); the phase SHALL record `unsatDidNotConverge` on every still-pending group and stop.

#### Scenario: Source-side path-segment group eventually types the directive-binding group's slot
- **WHEN** two groups exist with `pathGroup.root = src[person.addresses]:?` and `bindingGroup.slot = src[person.addresses]:?` (same node)
- **AND** pass 1 processes `bindingGroup` first (it cannot proceed because its slot is untyped) then `pathGroup` (which SATs, typing `src[person.addresses]` to `List<Opt<PA>>`)
- **THEN** the outer loop registers "state changed" and runs pass 2
- **AND** pass 2 processes `bindingGroup` with a typed slot and is able to expand its container chain
- **AND** the outer loop converges after pass 2 produces no further changes (or the chain spawns sub-groups that resolve in subsequent passes)

#### Scenario: Pending state is not surfaced as unsatNoPlan
- **WHEN** a group cannot proceed in pass 1 because its slot is untyped
- **THEN** the group's outcome remains pending after pass 1
- **AND** the outer loop does another pass
- **AND** `unsatNoPlan` is NOT recorded for this group at the end of pass 1

#### Scenario: Convergence after a fixed-point pass with no state changes
- **WHEN** an outer pass completes with no group transitioning to SAT, no new sub-group registered, and no `Node.setType(...)` call
- **THEN** the outer loop terminates
- **AND** any group still pending is recorded as `unsatNoPlan(group, failingSlot)` (or `unsatDidNotConverge` if the per-slot budget tripped)

#### Scenario: MAX_OUTER_PASSES safety net
- **WHEN** the outer loop has run `MAX_OUTER_PASSES` times without convergence
- **THEN** every still-pending group is recorded as `unsatDidNotConverge`
- **AND** the phase returns without further iteration

### Requirement: Base-case SAT for parameter-root slots

A slot `S` of any group SHALL satisfy as a structural base case (without requiring a child sub-group) iff `S.loc` is a `SourceLocation` with exactly one segment AND that segment matches one of `currentMethod`'s declared parameter names.

This rule replaces the global `SourceReachability.sourceParameterRoots(graph)` ambient set. No `MapperGraph`-wide query is performed for SAT determination; the slot's `Location` is the sole input.

#### Scenario: Slot at single-segment SourceLocation matching a parameter is base-case SAT
- **WHEN** a group has slot `src[person]:Person` and `currentMethod` declares a parameter named `person`
- **THEN** the slot is structurally SAT
- **AND** no child sub-group is required to satisfy it

#### Scenario: Slot at multi-segment SourceLocation is not base-case SAT
- **WHEN** a group has slot `src[person.addresses]:?`
- **THEN** the slot is NOT base-case SAT
- **AND** SAT requires at least one SAT child sub-group rooted at this slot

#### Scenario: Slot whose Location is not a SourceLocation is not base-case SAT
- **WHEN** a group has slot at `TargetLocation` or `ElementLocation`
- **THEN** the slot is NOT base-case SAT regardless of its segments

### Requirement: Path-segment-group resolution via PathSegmentResolver

`ExpandGroupsPhase` SHALL recognise **path-segment groups** by their structural shape and SHALL invoke `PathSegmentResolver` strategies to type and SAT them. A group `G` is a path-segment group iff:
- `G.root.loc` is a `SourceLocation`, AND
- `G.slots.size() == 1` and `G.slots[0].loc` is a `SourceLocation`, AND
- `G.root.loc.path.segments` equals `G.slots[0].loc.path.segments` with exactly one additional segment appended.

For each path-segment group, the phase SHALL iterate registered `PathSegmentResolver`s in `Class.getName()` ascending order and call `resolve(slot.type.get(), <appendedSegment>, ctx)`. The first non-empty `ResolvedSegment` `rs` SHALL:

1. Set the root's `type` to `Optional.of(rs.getReturnType())` (in-place; preserved by `Node` instance identity).
2. Emit a REALISED edge `slot → root` with `weight = rs.getWeight()`, `codegen = rs.getCodegen()`, `strategyClassFqn = <resolver-class-FQN>`.
3. Mark the path-segment group SAT.

If no resolver matches, the group records `unsatNoPlan` and the dependent directive-binding group remains untyped (and likely UNSAT as a consequence).

`PathSegmentResolver`s SHALL NOT be invoked at seed time; `SeedGraph` retires the seed-time path-walking algorithm.

#### Scenario: GetterPathResolver types a path-segment group's root
- **WHEN** a path-segment group has `root = src[person.addresses]:?`, `slot = src[person]:Person`
- **AND** `GetterPathResolver.resolve(Person, "addresses", ctx)` returns `Optional.of(ResolvedSegment(List<Optional<PA>>, codegen, Weights.STEP))`
- **THEN** the root's type becomes `Optional.of(List<Optional<PA>>)`
- **AND** a REALISED edge `src[person] → src[person.addresses]:List<Opt<PA>>` is emitted with the resolver's codegen
- **AND** the path-segment group's outcome is SAT

#### Scenario: No resolver matches; path-segment group is UNSAT_NO_PLAN
- **WHEN** a path-segment group has `root = src[person.weirdSegment]:?`, `slot = src[person]:Person`
- **AND** no registered resolver matches `(Person, "weirdSegment", ctx)`
- **THEN** the group's outcome is `unsatNoPlan`
- **AND** the root remains untyped

#### Scenario: Path-segment recognition by structural shape
- **WHEN** an `ExpansionGroup` has `root.loc = SourceLocation(["person", "addresses"])`, `slot.loc = SourceLocation(["person"])`
- **THEN** it is recognised as a path-segment group
- **AND** `PathSegmentResolver`s are invoked for its expansion

## MODIFIED Requirements

### Requirement: ResolveTargetChainsPhase scaffolds target chains and ExpansionGroups

`ResolveTargetChainsPhase` SHALL iterate every `Node` in the graph whose `Location` is a `TargetLocation` with empty path segments and whose `type` is present — the return-root nodes (one per `@Map`-target return). For each return root, the phase SHALL:

1. Find leaf target nodes by BFS over incoming SEED edges within `TargetLocation` space, collecting nodes with no further incoming SEED edges and whose `type` is empty.
2. Extract `targetTails` — the deepest path segments of each leaf — to feed into `GroupTarget.buildFor(returnType, targetTails, ctx)`.
3. For every registered `GroupTarget` whose `buildFor` returns a non-empty `GroupBuild`, allocate one typed slot `Node` per `Slot` and emit a REALISED edge from each slot to the return root.
4. Register one parent `ExpansionGroup` per `GroupTarget` match, rooted at the return root, with the slots as members and the slot→root REALISED edges in `initialEdges`. The parent group joins the graph's group list.

`ResolveTargetChainsPhase` SHALL NOT emit MARKER edges from typed slots to the corresponding untyped seed leaves; `SeedGraph` is responsible for the directive-binding group that links the typed slot to the untyped source-leaf side (see `seed-graph`).

`ResolveTargetChainsPhase` SHALL NOT emit any directive-binding REALISED edge or register any directive-binding sub-group; both are now registered by `SeedGraph` as one group per SEED edge.

Phase mutation SHALL be committed via `MapperGraph.apply(GraphDelta)` for node/edge additions and `MapperGraph.addGroup(...)` for group registrations. Groups SHALL be registered AFTER all nodes/edges are added.

#### Scenario: Return root with one constructor candidate registers parent group only
- **WHEN** the graph contains return root `tgt[]:Human` and `ConstructorCall.buildFor(Human, [addresses, firstName, lastName], ctx)` emits a `GroupBuild` with three slots
- **THEN** three slot nodes are allocated and added (with `TargetLocation` paths matching the slot names)
- **AND** three REALISED edges are emitted from each slot to the return root
- **AND** one parent `ExpansionGroup` is registered with the return root as root, three slots, and three initial REALISED edges
- **AND** no directive-binding REALISED edge is emitted by this phase
- **AND** no nested directive-binding `ExpansionGroup` is registered by this phase

### Requirement: ExpandGroupsPhase drives per-group expansion

`ExpandGroupsPhase` SHALL process registered `ExpansionGroup`s via a cross-group fixed-point loop (see the "Cross-group fixed-point loop" requirement): iterate every group in registration order, repeat until a full pass produces no state changes. Initial group set is the groups present after `SeedGraph` and `ResolveTargetChainsPhase` (per-SEED-edge groups + parent constructor groups). Sub-groups registered during expansion join the set and are processed in subsequent passes.

The phase SHALL enforce two budget constants:

- `MAX_OUTER_PASSES = 32` — the maximum number of cross-group passes. Exceeding this records `unsatDidNotConverge` on every still-pending group.
- `MAX_SLOT_ROUNDS = 64` — the maximum number of expansion rounds per slot's `resolveSlot` call within a single outer pass. Exceeding this records `unsatDidNotConverge` for that slot's group.

For each group visited in an outer pass, the phase SHALL invoke `fillGroup(group)` which:

1. If the group's outcome is already `SAT`, return without re-processing.
2. If the group is a path-segment group, invoke `PathSegmentResolver`s per the "Path-segment-group resolution via PathSegmentResolver" requirement. On resolver match: type the root via `Node.setType(...)`, emit the REALISED edge, mark the group SAT, signal "state changed" to the outer loop. On no match in the current pass: leave the group pending and signal "no change for this group".
3. Otherwise (a bridge-expanded group), for each slot in `group.getSlots()`:
   - If the slot is base-case SAT (per the "Base-case SAT for parameter-root slots" requirement), the slot is satisfied.
   - Else if the slot is already typed and has at least one SAT child sub-group, the slot is satisfied.
   - Else if the slot is typed but has no SAT child sub-group, call `resolveSlot(slot, group)`. The call may spawn new sub-groups (signal "state changed") and may SAT the slot via an existing child.
   - Else (slot is untyped), leave the group pending — its typing depends on another group's expansion in a future outer pass.
4. If every slot is satisfied at the end of this pass, mark the group `SAT` and signal "state changed".
5. Otherwise leave the group pending; outcome recording is deferred to outer-loop convergence (see "Cross-group fixed-point loop").

`resolveSlot` SHALL implement bounded greedy expansion within one outer pass: starting with `frontier = [slot]`, iterate up to `MAX_SLOT_ROUNDS` rounds. Each round calls `expandFrontier(f, group, newNodes)` for every `f` in the current frontier. Sub-groups spawned during the round are added to the graph's group set (and signal "state changed" so the outer loop runs another pass). The frontier becomes the freshly allocated `newNodes`. After each round, check whether the slot has at least one SAT child sub-group; if true, return `SAT`. If `newNodes` is empty before the slot SATs, return without recording an outcome (the slot is left for the next outer pass — sibling groups may type a candidate by then). If the round budget is exhausted within this pass, signal `UNSAT_DID_NOT_CONVERGE` for the group (final outcome on convergence).

#### Scenario: Slot is base-case SAT for parameter root
- **WHEN** a group's slot is `src[person]:Person` and `currentMethod` has parameter `person`
- **THEN** `fillGroup` recognises the slot as base-case SAT without running `resolveSlot`

#### Scenario: Slot SATs when any child sub-group SATs
- **WHEN** a slot's expansion has spawned three sibling sub-groups (multi-fire) and exactly one of them has outcome SAT
- **THEN** `resolveSlot` returns SAT for that slot
- **AND** the group's outcome is determined by the remaining slots' outcomes

#### Scenario: Slot exhausts plan in a pass ⇒ left pending for next pass
- **WHEN** an expansion round produces no new sub-groups and the slot is still not SAT
- **THEN** `resolveSlot` returns without recording an outcome
- **AND** the group remains pending for the next outer pass
- **AND** `unsatNoPlan` is recorded ONLY when the cross-group fixed-point converges with this slot still unsatisfied

#### Scenario: Round budget exhaustion within a pass ⇒ flag UNSAT_DID_NOT_CONVERGE for this group
- **WHEN** `MAX_SLOT_ROUNDS` rounds elapse within a single outer pass without the slot reaching SAT or running out of work
- **THEN** the group is flagged `UNSAT_DID_NOT_CONVERGE`; the final outcome is recorded on cross-group convergence

#### Scenario: Outer-pass budget exhaustion records remaining groups
- **WHEN** more than `MAX_OUTER_PASSES` cross-group passes complete without convergence
- **THEN** every still-pending group is recorded `unsatDidNotConverge`

### Requirement: Bridge edge-emission rule

For every `BridgeStep` returned by every `Bridge` query during frontier expansion, the phase SHALL apply the following rule:

Let `F` be the frontier node (the node demanding an input) and `C` be a candidate node from `currentGroup.getView().vertexSet()` (excluding `F`, excluding `TargetLocation` nodes). Given a `BridgeStep(inputType, outputType, weight, codegen, scopeTransition, elementRole)`:

1. The phase SHALL verify `step.outputType` equals `F.type` (the bridge produces the frontier's type). If not, the step is skipped.

2. The phase SHALL verify `step.scopeTransition` is compatible with the frontier's location: `PRESERVING` always matches; `ENTERING` matches only when `F.loc instanceof ElementLocation`; `EXITING` matches only when `F.loc` is NOT an `ElementLocation`. Incompatible steps are skipped.

3. The phase SHALL determine `inputNode`:
   - For `PRESERVING`: if any candidate `C` in `currentGroup.getView().vertexSet()` has `C.type` equal to `step.inputType` and `C.loc.equals(F.loc)`, use `C`. Otherwise, fresh-allocate at `F.loc`.
   - For `ENTERING`: fresh-allocate at `F.loc` (which is an `ElementLocation`). Same-element-scope candidate reuse from the current view is permitted when a typed `ElementLocation` node already exists in the view.
   - For `EXITING`: fresh-allocate at `ElementLocation(step.elementRole)` in `F.scope`.

4. If `inputNode.equals(F)` (would degenerate to a no-op), the step is skipped.

5. The phase SHALL emit one `REALISED` edge from `inputNode` to `F`, carrying `step.weight`, `step.codegen`, and the bridge's class FQN.

6. The phase SHALL register a fresh one-slot nested `ExpansionGroup` (per the "Every bridge match spawns a one-slot nested ExpansionGroup" requirement). The new sub-group's view contains exactly `{F, inputNode, the just-emitted REALISED edge}`. The new group joins the work-list.

7. If `inputNode` was freshly allocated, it is added to `newNodes` for the next expansion round.

Fresh allocation uses instance-identity (per `graph-model`): every fresh-allocated input is a distinct `Node` object even when `(scope, loc, type)` would equal an existing graph node. This is the structural property that makes cross-sub-group cycles impossible.

#### Scenario: Every match emits one REALISED edge + one sub-group
- **WHEN** a `BridgeStep` matches at frontier `F`
- **THEN** one REALISED edge is emitted from the input to `F`
- **AND** exactly one one-slot `ExpansionGroup` is registered with `root = F`, `slots = [inputNode]`

#### Scenario: PRESERVING reuses same-loc candidate from current view
- **WHEN** a `PRESERVING` step's `inputType` matches a candidate `C` in `currentGroup.getView().vertexSet()` with `C.loc.equals(F.loc)`
- **THEN** `inputNode = C` (no fresh allocation)
- **AND** a one-slot sub-group is registered with `C` as the slot

#### Scenario: ENTERING fresh-allocates at frontier's loc
- **WHEN** an `ENTERING` step matches and no same-element-scope candidate exists in the current view
- **THEN** `inputNode` is fresh-allocated at `F.loc`
- **AND** a one-slot sub-group is registered with the fresh node as the slot

#### Scenario: EXITING fresh-allocates at ElementLocation(elementRole)
- **WHEN** an `EXITING` step matches at a regular-scope frontier
- **THEN** `inputNode` is fresh-allocated at `ElementLocation(step.elementRole)` in `F.scope`
- **AND** a one-slot sub-group is registered

### Requirement: Multi-fire per frontier; parallel sub-groups coexist

`ExpandGroupsPhase` SHALL commit every matching `BridgeStep` at each frontier. Multiple bridges that produce the frontier's type SHALL each emit their own REALISED edge and register their own one-slot sub-group. The siblings share the same root (the frontier) but have different slots and expand independently in subsequent work-list iterations.

A slot SATs iff at least one of its child sub-groups has outcome SAT. Dead branches (sub-groups that never reach SAT) remain in the graph as unsatisfied sub-groups; they do not contribute to the alive chain via outcome propagation.

Within one `Bridge`, the first matching candidate wins — a single `Bridge` MAY return multiple `BridgeStep`s describing alternative shapes; the engine commits the first that matches and stops within that bridge.

#### Scenario: Two matching bridges spawn two sibling sub-groups
- **WHEN** the frontier is `tgt[addresses]:Optional<Set<HA>>` and both `OptionalCollect` (EXITING) and `OptionalWrap` (PRESERVING) match
- **THEN** two one-slot `ExpansionGroup`s are registered, both with `root = tgt[addresses]`
- **AND** the two siblings have different slots and join the work-list as DAG leaves

#### Scenario: Dead sibling sub-groups don't block the alive chain
- **WHEN** two sibling sub-groups exist at the same root and one's outcome is SAT, the other's is `unsatNoPlan`
- **THEN** the parent slot is SAT via the alive sibling
- **AND** the dead sibling remains in the graph with its outcome unchanged

### Requirement: GroupTarget matches register nested groups via tryGroupTargets

`ExpandGroupsPhase.tryGroupTargets` SHALL run for every frontier that no bridge matched. For each registered `GroupTarget`, the phase SHALL call `groupTarget.buildFor(frontier.type, List.of(), ctx)`. The first non-empty `GroupBuild` SHALL:

1. Allocate one slot node per `Slot`, with `Location = ElementLocation(slot.name)` and `Scope = frontier.scope`. Allocation uses instance-identity (fresh `Node` objects).
2. Emit one REALISED edge per slot, from the slot to the frontier.
3. Register one multi-slot `ExpansionGroup` with `root = frontier`, `slots = the allocated nodes`, `initialEdges = the just-emitted REALISED edges`.

The new group joins the work-list. This is the multi-slot exception to the "every bridge match spawns a one-slot sub-group" rule — `GroupTarget` matches produce multi-slot groups because their codegen reads multiple inputs.

#### Scenario: GroupTarget match at frontier with no bridge match
- **WHEN** the frontier is `elem:Human` and no `Bridge` matches but `ConstructorCall.buildFor(Human, [], ctx)` returns a non-empty `GroupBuild`
- **THEN** the phase allocates one slot node per `Slot` at `ElementLocation(slot.name)` in the frontier's scope
- **AND** emits one REALISED edge from each slot to the frontier
- **AND** registers a multi-slot `ExpansionGroup` with the frontier as root and the slot nodes as slots

### Requirement: GroupOutcome records per-group SAT/UNSAT verdicts

The graph SHALL retain a `GroupOutcome` for every registered `ExpansionGroup`, recorded via `MapperGraph.recordGroupOutcome(...)` during `fillGroup`. Outcomes are one of:

- `GroupOutcome.sat(group)` — every slot SAT'd via either base-case SAT or at least one SAT child sub-group.
- `GroupOutcome.unsatNoPlan(group, failingSlot)` — `resolveSlot(failingSlot)` exhausted its expansion without producing new sub-groups, AND no existing child sub-group is SAT.
- `GroupOutcome.unsatDidNotConverge(group, failingSlot)` — `resolveSlot(failingSlot)` exceeded `MAX_SLOT_ROUNDS` or the work-list budget was exhausted.

Outcomes propagate up the dependency DAG: a parent group's slot is SAT iff at least one child sub-group rooted at that slot has outcome SAT. No global REALISED-traversal is performed at SAT time; the outcome of each group is the SAT input for its parent.

Outcomes are consumed by downstream validation phases (see `realisation-validation`) to surface closest-miss diagnostics.

#### Scenario: SAT outcome recorded when all slots SAT
- **WHEN** the cross-group fixed-point converges with `group` having every slot either base-case SAT or having at least one SAT child sub-group
- **THEN** `MapperGraph.recordGroupOutcome(GroupOutcome.sat(group))` is called

#### Scenario: UNSAT_NO_PLAN recorded with the failing slot
- **WHEN** a slot's `resolveSlot` returns `UNSAT_NO_PLAN`
- **THEN** `fillGroup` records `GroupOutcome.unsatNoPlan(group, slot)` and returns early without processing remaining slots

#### Scenario: Parent SAT requires at least one SAT child per slot
- **WHEN** a parent group has slot `S` with three child sub-groups rooted at `S`, all of whose outcomes are `unsatNoPlan`
- **THEN** the parent's slot `S` is NOT SAT
- **AND** the parent's outcome is `unsatNoPlan`

## REMOVED Requirements

### Requirement: Source reachability gates slot SAT

**Reason:** Slot SAT is now determined per-group via outcome propagation and a structural base case for parameter-root slots (see "Base-case SAT for parameter-root slots" and "GroupOutcome records per-group SAT/UNSAT verdicts" under ADDED/MODIFIED). The global REALISED-traversal query is removed because (a) it required an ambient `sourceParameterRoots` set whose scope leaked across the directive structure and contributed to multi-parameter ambiguity bugs, and (b) under per-sub-group expansion, the parent-slot-to-source connection is naturally encoded by the dependency DAG.

**Migration:** Replace `SourceReachability.slotReachable(slot, graph, sourceRoots)` call sites with: (a) for parameter-root slots, the structural base-case check (`slot.loc.path.segments.size() == 1 && currentMethod.parameters.contains(slot.loc.path.segments[0])`); (b) for other slots, propagation from child sub-group outcomes (the parent's slot SATs iff at least one child sub-group rooted at it has outcome SAT).

### Requirement: Candidate inputs are scope-filtered, non-target, non-frontier

**Reason:** Candidate search is now scoped to `currentGroup.getView().vertexSet()` (see "Candidate search scoped to current group's view" under ADDED), not to the global graph. The scope filter is implicit (a group's view contains only nodes within the relevant scope by construction); the target/frontier filter remains but is applied to the view rather than to `MapperGraph.nodes()`.

**Migration:** Replace `SourceReachability.candidateInputs(scope, graph)` call sites with `currentGroup.getView().vertexSet().stream().filter(n -> !n.equals(frontier) && !(n.getLoc() instanceof TargetLocation)).sorted(Comparator.comparing(Node::id)).collect(toList())`.

### Requirement: Scope-changing bridges register per-match nested groups

**Reason:** Superseded by "Every bridge match spawns a one-slot nested ExpansionGroup" (ADDED) which broadens the rule to every match regardless of `ScopeTransition`. The PRESERVING/ENTERING/EXITING split in `commitBridgeStep` is collapsed.

**Migration:** Remove the conditional `if (scopeTransition != PRESERVING) registerSubGroup(...)` in `commitBridgeStep`; register a sub-group unconditionally for every committed `BridgeStep`.

### Requirement: Scope inheritance under target-to-source expansion

**Reason:** The scope-aware behaviour of `allocateOrReuseInputNode` collapses into the uniform Bridge edge-emission rule (MODIFIED) under sub-group spawning. The "sole scope-aware engine surface" invariant is preserved naturally: each sub-group's view is bound to a single scope context, so scope inheritance is structural rather than algorithmic.

**Migration:** Strategies continue to declare `scopeTransition` on `BridgeStep`. The engine consumes it within the unified rule. No SPI change.
