## ADDED Requirements

### Requirement: All graph mutation flows through the Applier

`ExpandGroupsPhase` SHALL centralise all `MapperGraph` mutation in a single `Applier` collaborator. The `Applier` is the **only** call site that invokes `MapperGraph.addNode`, `MapperGraph.addEdge`, `MapperGraph.addEdgeIfAcyclic`, `MapperGraph.addGroup`, `MapperGraph.recordGroupOutcome`, `Node.setTyping`, `ExpansionGroup.addVertexToView`, or `ExpansionGroup.addEdgeToView`. No other class in package `io.github.joke.percolate.processor.stages.expand` SHALL call these mutators directly during `apply(graph)` execution.

The `Applier` SHALL implement `Delta.Visitor<Void>` and SHALL own the `producerScopes : IdentityHashMap<Node, Element>` map as private state. The map records, for each `Node` typed via `setTyping` during `apply(graph)`, the `Element` scope that drove the resolver call. Reads of the map are exposed only through `ExpansionSnapshot.producerScopeOf(node)`.

#### Scenario: Expanders never mutate the graph directly
- **WHEN** the source of `PathSegmentExpander`, `DirectiveBindingExpander`, `BridgeExpander`, `FrontierMatcher`, and `InputAllocator` is inspected
- **THEN** no source line invokes `MapperGraph.addNode`, `MapperGraph.addEdge`, `MapperGraph.addEdgeIfAcyclic`, `MapperGraph.addGroup`, `MapperGraph.recordGroupOutcome`, `Node.setTyping`, `ExpansionGroup.addVertexToView`, or `ExpansionGroup.addEdgeToView`

#### Scenario: producerScopes is encapsulated in the Applier
- **WHEN** the source of `ExpandGroupsPhase`, the three `GroupExpander` impls, and helper classes (`FrontierMatcher`, `InputAllocator`) is inspected
- **THEN** none of them declare or expose an `IdentityHashMap<Node, Element>` field
- **AND** only the `Applier` declares the `producerScopes` map as a private field

### Requirement: GroupExpander interface contract

The phase SHALL define a `GroupExpander` interface with two methods:

- `boolean appliesTo(ExpansionGroup group)` — pure structural predicate; expanders SHALL recognise their group kind by inspecting the group's slots and locations.
- `GroupStepResult step(ExpansionGroup group, ExpansionSnapshot snapshot)` — pure function returning `(List<DeltaBundle> bundles, List<Node> pendingSlots)`. The implementation SHALL NOT mutate the graph, the group, the snapshot, or any other shared state. The `bundles` describe intended mutations; `pendingSlots` lists the group's slots that are not yet satisfied after applying the bundles (empty list ⇔ the expander considers the group SAT).

Expanders are supplied to the driver as an ordered `List<GroupExpander>` assembled by the `ExpandGroupsPhase.create` factory and passed via constructor injection. The driver SHALL dispatch each non-SAT group to the **first** expander whose `appliesTo` predicate returns true. The `appliesTo` predicates SHALL be mutually exclusive by construction (path-segment, directive-binding, and bridge-group structural shapes do not overlap).

#### Scenario: Expander step is pure
- **WHEN** `GroupExpander.step(group, snapshot)` is invoked
- **THEN** the method returns a `GroupStepResult` without mutating `group`, `snapshot`, the underlying `MapperGraph`, or any `Node` or `Edge` reachable from them
- **AND** invoking the method a second time with the same arguments returns an equivalent `GroupStepResult` (referential transparency modulo identity of created `Node` / `Edge` instances inside emitted deltas)

#### Scenario: appliesTo predicates do not overlap
- **WHEN** any `ExpansionGroup` is presented to the registered expander list
- **THEN** at most one expander's `appliesTo` returns true

#### Scenario: GroupExpander list is constructor-injected
- **WHEN** `ExpandGroupsPhase` is constructed via its `create` factory
- **THEN** it receives an ordered `List<GroupExpander>` via constructor injection
- **AND** dispatch picks the first expander whose `appliesTo` returns true for the given group

### Requirement: DeltaBundle atomicity

`Applier.apply(state, List<DeltaBundle> bundles)` SHALL apply each bundle as an atomic unit. For each `DeltaBundle b`:

1. The applier SHALL dry-check whether every `AddEdge` delta in `b` would be acyclic given the current REALISED projection of `state` plus all preceding accepted deltas in `b`.
2. If every `AddEdge` would be acyclic, the applier SHALL apply **every** delta in `b` in order via the visitor.
3. If any `AddEdge` would close a cycle, the applier SHALL apply **none** of `b`'s deltas; the bundle is dropped without partial application.

No delta in a rejected bundle SHALL leave residual state in the graph — in particular, an `AddNode` delta that precedes a cycle-rejected `AddEdge` SHALL NOT add the node to the graph. This requirement supersedes the today-observable behaviour where `MapperGraph.addEdgeIfAcyclic` rolling back an edge leaves the freshly-allocated input `Node` in the graph as an orphan.

#### Scenario: Cycle-rejected bundle leaves no orphan node
- **WHEN** a `BridgeExpander` emits a bundle `{ AddNode(fresh), AddEdge(fresh → frontier), AddGroup(nested) }`
- **AND** the `AddEdge` would close a cycle in the REALISED projection
- **THEN** the bundle is dropped in its entirety
- **AND** `fresh` does NOT appear in `MapperGraph.nodes()` after the applier returns
- **AND** `nested` does NOT appear in `MapperGraph.groups()` after the applier returns

#### Scenario: Accepted bundle applies all deltas in order
- **WHEN** a bundle `{ AddNode(n), AddEdge(n → frontier), TypeNode(frontier, T, scope), AddGroup(g) }` is presented and the `AddEdge` is acyclic
- **THEN** `n` is added to `MapperGraph.nodes()`
- **AND** the edge `n → frontier` is added to the REALISED projection
- **AND** `frontier.setTyping(T, NullabilityResolver.resolve(T, scope))` is invoked exactly once
- **AND** `g` is added to `MapperGraph.groups()`

### Requirement: Per-pass snapshot semantics

`ExpandGroupsPhase.apply` SHALL run a cross-group fixed-point loop where each outer pass operates against a snapshot of the state taken at pass start. All expanders in a single pass SHALL see the same snapshot; bundles emitted by one expander in pass `p` are NOT visible to other expanders in pass `p`. The applier applies all collected bundles at the end of pass `p`; pass `p+1` operates against a new snapshot reflecting those applications.

The `ExpansionSnapshot` interface SHALL expose only read methods (`groups()`, `viewOf(group)`, `typeOf(node)`, `isSat(group)`, `effectiveTypeFor(node, group)`, `producerScopeOf(node)`, `currentMethod()`). The snapshot's view of a group SHALL be an `AsUnmodifiableGraph` wrapper over the group's underlying JGraphT subgraph (JGraphT exposes no `Graphs.unmodifiableGraph`; `AsUnmodifiableGraph` gives the same read-only guarantee). Calls to mutator methods on the returned wrapper SHALL throw `UnsupportedOperationException`.

#### Scenario: Mid-pass mutations are invisible within the same pass
- **WHEN** a pass dispatches group `A` first and group `B` second
- **AND** the expander for `A` emits a bundle that, when applied, would type a node also referenced by `B`'s slot
- **THEN** the expander for `B` (in the same pass) sees the node still untyped via its snapshot
- **AND** the next pass's snapshot reflects the applied bundle, and `B`'s expander observes the typed node in pass `p+1`

#### Scenario: Snapshot views are read-only
- **WHEN** an expander obtains `snapshot.viewOf(group)` and attempts to call `addVertex`, `addEdge`, `removeVertex`, or `removeEdge` on the returned graph
- **THEN** the call throws `UnsupportedOperationException`

## MODIFIED Requirements

### Requirement: ExpandGroupsPhase drives per-group expansion

`ExpandGroupsPhase` SHALL process registered `ExpansionGroup`s via a cross-group fixed-point loop (see the "Cross-group fixed-point loop" requirement): iterate every non-SAT group, dispatch each to its `GroupExpander`, collect emitted bundles, apply them, repeat until a full pass produces no changes. Initial group set is the groups present after `SeedGraph` and `ResolveTargetChainsPhase` (per-SEED-edge groups + parent constructor groups). Groups registered during expansion (via `AddGroup` deltas) join the set and are processed in subsequent passes.

The phase SHALL enforce a single budget constant:

- `MAX_OUTER_PASSES = 32` — the maximum number of cross-group passes. Exceeding this records `unsatDidNotConverge` on every still-pending group.

No per-slot round budget exists. Each `GroupExpander.step` call describes the group's intended progress for one pass; further progress on a pending group happens in subsequent passes. The cross-group fixed-point loop subsumes what an in-pass round budget would protect against, and the cycle-rejection-in-applier semantics (see the "Bridge edge-emission rule" and "DeltaBundle atomicity" requirements) bound inverse-bridge dead-branch expansion at the structural level.

For each group visited in an outer pass, the phase SHALL invoke `GroupExpander.step(group, snapshot)` which produces `(bundles, pendingSlots)`. If `pendingSlots.isEmpty()` after the pass's applier run, the driver SHALL mark the group SAT on the state and SHALL invoke `recordGroupOutcome(GroupOutcome.sat(group))` on the underlying graph via the applier. Otherwise the group remains pending; outcome recording is deferred to outer-loop convergence (see "Cross-group fixed-point loop").

The legacy phase methods `fillGroup`, `resolveSlot`, `expandFrontier`, `tryBridges`, `tryBridgeOnCandidate`, `commitBridgeStep`, `tryGroupTargets`, `registerNestedGroupTarget`, `allocateInputNode`, `findCandidateByInputType`, `allocateFresh`, `importBoundaryNodes` SHALL NOT exist on `ExpandGroupsPhase`. Their semantic equivalents live in `GroupExpander` implementations and the `Applier`.

#### Scenario: Slot is base-case SAT for parameter root
- **WHEN** a group's slot is `src[person]:Person` and `currentMethod` has parameter `person`
- **THEN** the `BridgeExpander.step` for the group returns the slot in `pendingSlots = []` without emitting any bundle for that slot

#### Scenario: Slot SATs when any child sub-group SATs
- **WHEN** a slot's expansion has spawned three sibling sub-groups (multi-fire) and exactly one of them has outcome SAT in the snapshot
- **THEN** the `BridgeExpander.step` for the parent group considers that slot satisfied
- **AND** the slot is NOT included in the returned `pendingSlots`

#### Scenario: Slot exhausts plan in a pass ⇒ left pending for next pass
- **WHEN** an expander emits no bundles for a slot and the slot is not SAT
- **THEN** the slot is included in the returned `pendingSlots`
- **AND** the group remains pending for the next outer pass
- **AND** `unsatNoPlan` is recorded ONLY when the cross-group fixed-point converges with this slot still unsatisfied

#### Scenario: Outer-pass budget exhaustion records remaining groups
- **WHEN** more than `MAX_OUTER_PASSES` cross-group passes complete without convergence
- **THEN** every still-pending group is recorded `unsatDidNotConverge`

#### Scenario: No legacy phase methods remain
- **WHEN** the source of `ExpandGroupsPhase` is inspected
- **THEN** no methods named `fillGroup`, `resolveSlot`, `expandFrontier`, `tryBridges`, `tryBridgeOnCandidate`, `commitBridgeStep`, `tryGroupTargets`, `registerNestedGroupTarget`, `allocateInputNode`, `findCandidateByInputType`, `allocateFresh`, or `importBoundaryNodes` are declared

### Requirement: Cross-group fixed-point loop

`ExpandGroupsPhase` SHALL iterate over registered `ExpansionGroup`s, dispatching each non-SAT group to its `GroupExpander` against a per-pass snapshot, collecting emitted `DeltaBundle`s, applying them via the `Applier`, and repeating until a full pass produces no state changes. A "state change" is any of:

- the `Applier` accepted at least one `DeltaBundle` during the pass (one or more deltas were applied), OR
- at least one group's `GroupExpander.step` returned `pendingSlots.isEmpty()` and was promoted to SAT during the pass.

Within-group expansion stays target-to-source per the "Bridge edge-emission rule" requirement; the outer fixed-point loop is independent of and orthogonal to the within-group traversal direction.

A group whose expander in the current pass returns non-empty `pendingSlots` and no bundles capable of progressing those slots SHALL be left in **pending** state — NOT recorded as `unsatNoPlan` — and retried in the next outer pass. The group's outcome is recorded only when the outer fixed-point converges:
- If a group is still pending at convergence and at least one slot remains unsatisfied, `unsatNoPlan(group, failingSlot)` is recorded with `failingSlot` taken from the group's last-pass `pendingSlots`.
- If the outer-pass budget tripped (more than `MAX_OUTER_PASSES` passes without convergence), `unsatDidNotConverge(group, failingSlot)` is recorded.
- Otherwise (every slot satisfied), `sat(group)` is recorded.

The phase SHALL enforce a `MAX_OUTER_PASSES = 32` cap as a safety net. Exceeding it indicates a bug (state monotonicity guarantees convergence in O(depth) passes, typically 2–4 for realistic mappers); the phase SHALL record `unsatDidNotConverge` on every still-pending group and stop.

The legacy `ChangeTracker` class SHALL NOT exist; the state-change signal is computed inline by the driver from the applier's return count and the SAT transition count.

#### Scenario: Source-side path-segment group eventually types the directive-binding group's slot
- **WHEN** two groups exist with `pathGroup.root = src[person.addresses]:?` and `bindingGroup.slot = src[person.addresses]:?` (same node)
- **AND** pass 1's snapshot shows `bindingGroup` cannot proceed (slot is untyped) but `pathGroup` can SAT (its expander emits a `TypeNode` bundle for `src[person.addresses]`)
- **THEN** the applier applies the `pathGroup`'s bundle at end of pass 1; `bindingGroup` remains pending
- **AND** pass 2's snapshot shows the typed slot; `bindingGroup`'s expander emits bundles to expand its container chain
- **AND** the outer loop converges after further passes resolve the sub-groups produced

#### Scenario: Pending state is not surfaced as unsatNoPlan
- **WHEN** a group cannot proceed in pass 1 because its slot is untyped
- **THEN** the group's outcome remains pending after pass 1
- **AND** the outer loop does another pass
- **AND** `unsatNoPlan` is NOT recorded for this group at the end of pass 1

#### Scenario: Convergence after a fixed-point pass with no state changes
- **WHEN** an outer pass completes with the applier accepting zero bundles AND no group transitioning to SAT
- **THEN** the outer loop terminates
- **AND** any group still pending is recorded as `unsatNoPlan(group, failingSlot)` (or `unsatDidNotConverge` if the per-slot budget tripped)

#### Scenario: MAX_OUTER_PASSES safety net
- **WHEN** the outer loop has run `MAX_OUTER_PASSES` times without convergence
- **THEN** every still-pending group is recorded as `unsatDidNotConverge`
- **AND** the phase returns without further iteration

#### Scenario: ChangeTracker class does not exist
- **WHEN** the source of package `io.github.joke.percolate.processor.stages.expand` is inspected
- **THEN** no class named `ChangeTracker` is declared

### Requirement: Path-segment-group resolution via PathSegmentResolver

`PathSegmentExpander` SHALL recognise **path-segment groups** by their structural shape and SHALL invoke `PathSegmentResolver` strategies to type and SAT them. A group `G` is a path-segment group iff:
- `G.root.loc` is a `SourceLocation`, AND
- `G.slots.size() == 1` and `G.slots[0].loc` is a `SourceLocation`, AND
- `G.root.loc.path.segments` equals `G.slots[0].loc.path.segments` with exactly one additional segment appended.

`PathSegmentExpander.appliesTo(group)` SHALL return true iff `G` is a path-segment group by the above shape.

`PathSegmentExpander.step(group, snapshot)` SHALL iterate registered `PathSegmentResolver`s in `Class.getName()` ascending order and call `resolve(slot.type.get(), <appendedSegment>, ctx)`. The first non-empty `ResolvedSegment` `rs` SHALL produce a single `DeltaBundle` containing:

1. `TypeNode(root, rs.getReturnType(), rs.getProducedFrom())` — types the root via the applier (`Node.setTyping` is invoked there).
2. `AddEdge(slot → root REALISED)` with `weight = rs.getWeight()`, `codegen = rs.getCodegen()`, `strategyClassFqn = <resolver-class-FQN>`.
3. `AddEdgeToView(group, <the just-added edge>)`.

After applying, `PathSegmentExpander.step` SHALL return `pendingSlots = []` (group SAT). If no resolver matches, the expander SHALL return `pendingSlots = [slot]` and emit no bundles; eventual fixed-point convergence records `unsatNoPlan` for the group.

`PathSegmentResolver`s SHALL NOT be invoked at seed time; `SeedGraph` retires the seed-time path-walking algorithm.

#### Scenario: GetterPathResolver types a path-segment group's root
- **WHEN** a path-segment group has `root = src[person.addresses]:?`, `slot = src[person]:Person`
- **AND** `GetterPathResolver.resolve(Person, "addresses", ctx)` returns `Optional.of(ResolvedSegment(List<Optional<PA>>, codegen, Weights.STEP, getterMethod))`
- **THEN** `PathSegmentExpander.step` emits a bundle containing `TypeNode(root, List<Opt<PA>>, getterMethod)`, `AddEdge(slot → root)`, `AddEdgeToView(group, edge)`
- **AND** after applier run, the root's type is `List<Optional<PA>>` and a REALISED edge exists from `src[person]` to `src[person.addresses]`

#### Scenario: No resolver matches; path-segment group is UNSAT_NO_PLAN
- **WHEN** a path-segment group has `root = src[person.weirdSegment]:?`, `slot = src[person]:Person`
- **AND** no registered resolver matches `(Person, "weirdSegment", ctx)`
- **THEN** `PathSegmentExpander.step` returns `(bundles = [], pendingSlots = [slot])`
- **AND** at fixed-point convergence the group's outcome is `unsatNoPlan`

#### Scenario: Path-segment recognition by structural shape
- **WHEN** an `ExpansionGroup` has `root.loc = SourceLocation(["person", "addresses"])`, `slot.loc = SourceLocation(["person"])`
- **THEN** `PathSegmentExpander.appliesTo(group)` returns true

### Requirement: Candidate search scoped to current group's view

`FrontierMatcher` SHALL source candidate input nodes from `snapshot.viewOf(group).vertexSet()`, excluding the frontier itself and excluding any node whose `Location` is a `TargetLocation`. No global scan over `snapshot.allNodes()` or any equivalent SHALL occur during candidate search.

The view-scoped search is the structural fix for sibling-leak cross-group cycles and multi-parameter directive ambiguity. Combined with `Node` instance-identity (see `graph-model`) and narrow boundary import (only `SourceLocation` nodes inherit from the parent group's view), it prevents *sibling-derived nodes* from being picked up as candidates. Inverse-bridge same-loc reuse (e.g. `OptionalWrap` after `OptionalUnwrap`) can still attempt cycle-closing matches by reusing the current group's root via input-type matching; those are rejected at applier-time via the cycle check in the "DeltaBundle atomicity" requirement.

#### Scenario: Candidates come only from the current group's view
- **WHEN** `FrontierMatcher.matchAt(frontier, group, snapshot)` is invoked
- **THEN** the candidate stream contains only nodes from `snapshot.viewOf(group).vertexSet()`
- **AND** the candidate stream excludes the frontier
- **AND** the candidate stream excludes any node whose `Location` is a `TargetLocation`
- **AND** no candidate is sourced from `snapshot.allNodes()` or `MapperGraph.nodes()` directly

#### Scenario: Sibling sub-group nodes are invisible
- **WHEN** the underlying `MapperGraph` contains a `Node` `X` that is a member of some sibling sub-group `S` but not of `snapshot.viewOf(currentGroup).vertexSet()`
- **AND** `FrontierMatcher.matchAt(frontier, currentGroup, snapshot)` is invoked
- **THEN** `X` is not a candidate considered by any `Bridge` query in that frontier's match

### Requirement: Bridge edge-emission rule

For every `BridgeStep` returned by every `Bridge` query during `FrontierMatcher`'s candidate matching, the matcher SHALL apply the following rule and emit a single atomic `DeltaBundle` describing the commit:

Let `F` be the frontier node (the node demanding an input) and `C` be a candidate node from `snapshot.viewOf(group).vertexSet()` (excluding `F`, excluding `TargetLocation` nodes). Given a `BridgeStep(inputType, outputType, weight, codegen, scopeTransition, elementRole, producedFrom)`:

1. The matcher SHALL verify `step.outputType` equals `F`'s effective type (the producer-stamped type, or the group-expected type if the slot is still untyped). If not, the step is skipped.

2. The matcher SHALL verify `step.scopeTransition` is compatible with the frontier's location: `PRESERVING` always matches; `ENTERING` matches only when `F.loc instanceof ElementLocation`; `EXITING` matches only when `F.loc` is NOT an `ElementLocation`. Incompatible steps are skipped.

3. `InputAllocator` SHALL determine `inputNode`:
   - For `PRESERVING`: if any candidate `C` in `snapshot.viewOf(group).vertexSet()` has `C.type` equal to `step.inputType` and `C.loc.equals(F.loc)`, use `C` (no `AddNode` delta). Otherwise, allocate a fresh `Node` and emit an `AddNode` delta for it.
   - For `ENTERING`: if any candidate `C` exists with `C.type` equal to `step.inputType` and `C.loc` NOT an `ElementLocation`, use `C`. Otherwise, allocate fresh at `F.loc` (which is an `ElementLocation`) and emit an `AddNode` delta.
   - For `EXITING`: allocate fresh at `ElementLocation(step.elementRole)` in `F.scope` and emit an `AddNode` delta.

4. If `inputNode.equals(F)` (would degenerate to a no-op), the step is skipped.

5. The matcher SHALL emit one `AddEdge` delta for a `REALISED` edge from `inputNode` to `F`, carrying `step.weight`, `step.codegen`, and the bridge's class FQN.

6. If `F.type` is empty (Path B untyped slot), the matcher SHALL emit a `TypeNode(F, step.outputType, <scope>)` delta. The `<scope>` is the bridge step's `producedFrom` (`Element`) when present; otherwise it falls back to `snapshot.currentMethod()` as a best-effort anchor.

7. The matcher SHALL emit an `AddGroup` delta for a fresh one-slot nested `ExpansionGroup` (per the "Every bridge match spawns a one-slot nested ExpansionGroup" requirement). The nested group's initial view contains exactly `{F, inputNode, the just-emitted REALISED edge}`. Boundary import of parent `SourceLocation` nodes is carried on the `AddGroup` delta's `boundaryImports` field, not a separate `ImportToView` delta (a pure expander cannot reference a not-yet-built nested group, so boundary import ships as `AddGroup` ingredients — the taxonomy has five delta variants).

All deltas in steps 3, 5, 6, 7 are emitted as a single atomic `DeltaBundle`. The applier accepts or rejects the bundle as a unit per the "DeltaBundle atomicity" requirement; cycle rejection of the `AddEdge` in step 5 drops the whole bundle, leaving no orphan input `Node` in the graph.

Fresh allocation uses instance-identity (per `graph-model`): every fresh-allocated input is a distinct `Node` object even when `(scope, loc, type)` would equal an existing graph node. Combined with narrow boundary import (only `SourceLocation` nodes inherit into spawned sub-groups), this prevents *sibling-derived* nodes from being picked up as candidates across groups. Inverse-bridge same-loc reuse (Wrap↔Unwrap pairs that reuse the parent group's root via input-type matching) still attempts cycle-closing matches; the applier's cycle check drops them.

#### Scenario: Every match emits one REALISED edge + one sub-group as one bundle
- **WHEN** a `BridgeStep` matches at frontier `F`
- **THEN** the matcher emits one `DeltaBundle` containing one `AddEdge` delta (for the input → F REALISED edge) and one `AddGroup` delta (for the one-slot sub-group rooted at `F`)
- **AND** the bundle may also contain `AddNode` (fresh input), `TypeNode` (if F was untyped), and `AddEdgeToView` deltas; boundary `SourceLocation` nodes ride on the `AddGroup` delta's `boundaryImports` field

#### Scenario: PRESERVING reuses same-loc candidate from current view
- **WHEN** a `PRESERVING` step's `inputType` matches a candidate `C` in `snapshot.viewOf(group).vertexSet()` with `C.loc.equals(F.loc)`
- **THEN** `inputNode = C` (no `AddNode` delta is emitted)
- **AND** the bundle's `AddGroup` delta describes a one-slot sub-group with `C` as the slot

#### Scenario: ENTERING fresh-allocates at frontier's loc
- **WHEN** an `ENTERING` step matches and no same-element-scope candidate exists in the current view
- **THEN** the bundle includes an `AddNode` delta for the fresh input at `F.loc`
- **AND** an `AddGroup` delta for a one-slot sub-group with the fresh node as the slot

#### Scenario: EXITING fresh-allocates at ElementLocation(elementRole)
- **WHEN** an `EXITING` step matches at a regular-scope frontier
- **THEN** the bundle includes an `AddNode` delta for the fresh input at `ElementLocation(step.elementRole)` in `F.scope`
- **AND** an `AddGroup` delta for a one-slot sub-group

#### Scenario: Cycle-attempting inverse-bridge match is dropped without orphan
- **WHEN** the matcher emits a bundle `{ AddNode(X), AddEdge(X → Y REALISED), AddGroup(nested) }` for a sub-group with `root = Y` where an existing REALISED edge `Y → X` is present
- **AND** the applier's cycle check detects the cycle on the `AddEdge`
- **THEN** the applier drops the entire bundle
- **AND** `X` does NOT remain in `MapperGraph.nodes()` after the pass completes
- **AND** the matcher proceeds to the next step in subsequent passes

### Requirement: Every bridge match spawns a one-slot nested ExpansionGroup

`BridgeExpander` (via `FrontierMatcher`) SHALL emit an `AddGroup` delta for **every** matching `BridgeStep`, regardless of `BridgeStep.scopeTransition`. The new group described by the delta SHALL have `root = frontier`, `slots = [inputNode]`, `initialEdges = {inputNode → frontier REALISED}`, `codegen = step.getCodegen()` lifted to a `GroupCodegen`, and `strategyClassFqn = <originating-bridge-FQN>`. The new group joins the snapshot of the next pass after the applier accepts the bundle.

The PRESERVING / ENTERING / EXITING split in input allocation is encapsulated in `InputAllocator`; the matcher's emission rule is uniform: every match becomes its own sub-group.

#### Scenario: PRESERVING bridge match spawns a sub-group
- **WHEN** `FrontierMatcher` matches a `BridgeStep` with `scopeTransition == PRESERVING`
- **THEN** the emitted bundle contains an `AddGroup` delta with the frontier as root and the input as the sole slot
- **AND** the parent group's view is NOT mutated to add the input node directly (the input node is not listed in the `AddGroup` delta's `boundaryImports`)

#### Scenario: ENTERING bridge match spawns a sub-group
- **WHEN** `FrontierMatcher` matches a `BridgeStep` with `scopeTransition == ENTERING`
- **THEN** the emitted bundle contains an `AddGroup` delta with the frontier as root and the allocated element-scope input as its sole slot

#### Scenario: EXITING bridge match spawns a sub-group
- **WHEN** `FrontierMatcher` matches a `BridgeStep` with `scopeTransition == EXITING`
- **THEN** the emitted bundle contains an `AddGroup` delta with the frontier as root and the allocated element-scope input as its sole slot

### Requirement: GroupTarget matches register nested groups via tryGroupTargets

When `FrontierMatcher` produces no bridge match for a frontier, `BridgeExpander` SHALL fall back to `GroupTarget` strategies. For each registered `GroupTarget`, the expander SHALL call `groupTarget.buildFor(frontier.effectiveType, List.of(), ctx)`. The first non-empty `GroupBuild` SHALL produce a single atomic `DeltaBundle` containing:

1. If `frontier.type` is empty (Path B nested slot), one `TypeNode(frontier, rootType, <scope>)` delta. The `<scope>` falls back to `snapshot.currentMethod()` since GroupTargets do not carry an `Element`.
2. One `AddNode` delta per `Slot`, allocating a slot node with `Location = ElementLocation(slot.name)` and `Scope = frontier.scope`. Allocation uses instance-identity (fresh `Node` objects).
3. One `AddEdge` delta per slot, for a REALISED edge from the slot to the frontier.
4. One `AddGroup` delta for a multi-slot `ExpansionGroup` with `root = frontier`, `slots = the allocated nodes`, `initialEdges = the just-emitted REALISED edges`, including the slot-metadata mapping (`IdentityHashMap<Node, Slot>`).

This is the multi-slot exception to the "every bridge match spawns a one-slot sub-group" rule — `GroupTarget` matches produce multi-slot groups because their codegen reads multiple inputs. The bundle is atomic per the "DeltaBundle atomicity" requirement.

#### Scenario: GroupTarget match at frontier with no bridge match
- **WHEN** the frontier is `elem:Human` and `FrontierMatcher` returns no match, but `ConstructorCall.buildFor(Human, [], ctx)` returns a non-empty `GroupBuild`
- **THEN** `BridgeExpander` emits a bundle containing one `AddNode` delta per `Slot` (at `ElementLocation(slot.name)` in the frontier's scope), one `AddEdge` delta per slot (slot → frontier REALISED), and one `AddGroup` delta with the frontier as root and the slot nodes as slots
- **AND** if `frontier.type` was empty before the match, the bundle also contains a `TypeNode(frontier, Human, currentMethod)` delta
