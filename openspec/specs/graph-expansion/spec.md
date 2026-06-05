# Graph Expansion Spec

## Purpose

This spec defines the expansion engine that turns a `MapperGraph` populated with `SEED` edges into a graph augmented with `REALISED` edges, organised as a forest of `ExpansionGroup` sub-graphs. Expansion proceeds target-to-source within each group: target slots demand inputs, and the engine drives chains backwards through `ExpansionStrategy` matches. SAT propagates from leaf groups (parameter-root base-case SAT) up through the dependency DAG via per-group `GroupOutcome` records.

The realisation engine is a **cross-group fixed-point loop**: each pass dispatches every non-SAT group to its `GroupExpander` against a per-pass snapshot, collects the emitted `DeltaBundle`s, applies them via the single `Applier`, and re-runs until a full pass produces no state changes (the applier accepted no bundle AND no group was promoted to SAT). There is no SUB_SEED edge kind, no element-seed/diamond machinery, and no per-slot round budget. Element scope is declared per `ExpansionStep` via its optional `ElementScope` (`ENTERING`/`EXITING`); expanders emit `AddNode` deltas for the step's slot nodes at the right `Location` and an `AddGroup` delta for the nested `ExpansionGroup` opened by each `BOUNDARY` step (its `slots` are the step's `inputs`, `0..N`; regardless of whether an `ElementScope` is present).

Candidate search during expansion is scoped to the **current group's view** (`ExpansionGroup.getView().vertexSet()`), not the global `MapperGraph`. This structural rule prevents sibling-derived nodes from leaking as candidates. Combined with `Node` instance-identity and narrow boundary import (only `SourceLocation` parents inherit), cross-sibling cycles can't form by leakage. Inverse-bridge same-loc reuse (Wrap↔Unwrap pairs) can still attempt cycle-closing matches via the parent group's root; those are caught at apply time by the `Applier`'s per-bundle dry-cycle-check (JGraphT `CycleDetector` over a temporary REALISED `DirectedMultigraph`), which drops the entire `DeltaBundle` so no orphan node survives (see "DeltaBundle atomicity").

## Requirements

### Requirement: ExpandStage runs ExpansionPhases in declared order

The processor SHALL define a stage `ExpandStage` in package `io.github.joke.percolate.processor.stages.expand` that consumes a `MapperGraph` populated with `EdgeKind.SEED` edges. `ExpandStage` SHALL be `@Inject`-constructed via Lombok `@RequiredArgsConstructor(onConstructor_ = @Inject)` taking a `List<ExpansionPhase>` and any other dependencies as constructor arguments.

`ExpandStage.run(MapperContext)` SHALL iterate the injected phase list in declared order and invoke `phase.apply(graph)` on each. There is no convergence check, no fixed-point loop, and no round counter at the `ExpandStage` level — termination is guaranteed by each phase's own bounded behaviour (see `ExpandGroupsPhase` below). The shipped phase list contains a single phase, `ExpandGroupsPhase`; there is no `ResolveTargetChainsPhase` or any source/target pre-resolution phase.

Before invoking phases, `ExpandStage` SHALL set the `MapperContext.currentMethod` from the first SEED edge whose `from` node lives in a `MethodScope`. This ensures the context's currentMethod is available to downstream `ResolveCtx` consumers.

If the graph is `null` (no mapper-level graph was produced), `ExpandStage.run` SHALL return without invoking any phase.

#### Scenario: ExpandStage invokes the single ExpandGroupsPhase
- **WHEN** `ExpandStage.run(ctx)` is invoked with phase list `[ExpandGroupsPhase]`
- **THEN** `ExpandGroupsPhase.apply(graph)` runs
- **AND** no `ResolveTargetChainsPhase` (or any other pre-resolution phase) is invoked, because none exists in the pipeline

#### Scenario: ExpandStage skips when graph is absent
- **WHEN** `MapperContext.getGraph()` returns `null`
- **THEN** `ExpandStage.run(ctx)` returns without invoking any phase

#### Scenario: ExpandStage seeds currentMethod from MethodScope SEED edges
- **WHEN** the graph contains at least one SEED edge whose `from` node has `Scope` of type `MethodScope`
- **THEN** `ExpandStage.run(ctx)` calls `ctx.setCurrentMethod(...)` with the `MethodScope.getMethod()` value
- **AND** the call happens before any phase is invoked

### Requirement: ExpansionPhase contract

Every `ExpansionPhase` SHALL implement a single method `void apply(MapperGraph)` that augments the graph with new nodes, edges, and `ExpansionGroup`s. A phase SHALL NOT remove nodes or edges produced by earlier phases or by `SeedGraph`.

Phases SHALL be testable in isolation by populating a `MapperGraph` to the state expected at the phase's entry and invoking `apply` directly.

#### Scenario: Phase apply returns void
- **WHEN** any `ExpansionPhase.apply(graph)` is invoked
- **THEN** the method returns `void`
- **AND** no change-signal is exposed via the phase API

#### Scenario: Phase preserves existing seeds
- **WHEN** a phase runs on a graph containing `n` SEED edges
- **THEN** all `n` SEED edges remain in the graph after the phase completes

### Requirement: Slot Nodes are typed at producer commit

Target leaf and slot `Node`s — the umbrella assembly-group child leaves and the source/target chain nodes created untyped by `SeedGraph`, and the nodes emitted as `AddNode` deltas during expansion by `InputAllocator` (boundary slots and synthesized conversion inputs) — SHALL be created with both `type` and `nullability` empty. They SHALL be typed when their producer commits (a `TypeNode` delta applied by the `Applier`) — the same lifecycle that source path-segment-group roots follow (see `source-path-resolution`).

The `Applier`, on each `TypeNode` delta, SHALL invoke `slotNode.setTyping(producerType, producerNullability)` where:

- `producerType` is the actual type produced by the matched strategy (the resolved path-segment member type, the `ExpansionStep.getOutput()` of a `BOUNDARY` step, `MethodCandidate.getMethod().getReturnType()`, …).
- `producerNullability` is `NullabilityResolver.resolve(producerType, producerScope)`, with `producerScope` being the underlying `Element` of the match (the path-resolved member element, the producing method/constructor element, …).

The slot's consumer contract is NOT stored on the slot Node. It is derived on demand by code generation (see the `code-generation` capability).

The engine SHALL NOT use the prior `Node.setType(...)` API. All typing-commit sites SHALL use the paired `Node.setTyping(type, nullability)` (see the `graph-model` capability).

#### Scenario: Slot Node typed at producer commit via setTyping
- **WHEN** a slot Node is created untyped (a seed leaf, or an `InputAllocator`-allocated slot)
- **AND** `SourceDescentExpander` emits a `TypeNode` delta typing it to `String` from a resolved getter member element, and the `Applier` applies it
- **THEN** the slot Node's `setTyping(String, NullabilityResolver.resolve(String, getterElement))` is invoked exactly once
- **AND** after commit, `slot.getType()` is `Optional.of(String)`
- **AND** `slot.getNullability()` is `Optional.of(...)` matching the resolver's verdict

#### Scenario: No engine call site uses the legacy setType API
- **WHEN** the source of every class under `processor/src/main/java/io/github/joke/percolate/processor/stages/` is inspected
- **THEN** no source line invokes `Node.setType(...)` (the legacy single-field accessor)
- **AND** every typing-commit site invokes `Node.setTyping(type, nullability)`

#### Scenario: Cycle-rejected bundle leaves the slot Node untyped
- **WHEN** an expansion match would type a slot Node but its `DeltaBundle` is dropped whole by the `Applier`'s per-bundle cycle check
- **THEN** the `TypeNode` delta is never applied and the slot Node remains in its `Optional.empty()` state for both `type` and `nullability`
- **AND** a subsequent non-cyclic match types the slot successfully

### Requirement: All graph mutation flows through the Applier

`ExpandGroupsPhase` SHALL centralise all `MapperGraph` mutation in a single `Applier` collaborator. The `Applier` is the **only** call site that invokes `MapperGraph.addNode`, `MapperGraph.addEdge`, `MapperGraph.addEdgeIfAcyclic`, `MapperGraph.addGroup`, `MapperGraph.recordGroupOutcome`, `Node.setTyping`, `ExpansionGroup.addVertexToView`, or `ExpansionGroup.addEdgeToView`. No other class in package `io.github.joke.percolate.processor.stages.expand` SHALL call these mutators directly during `apply(graph)` execution.

The `Applier` SHALL implement `Delta.Visitor<Void>` and SHALL own the `producerScopes : IdentityHashMap<Node, Element>` map as private state. The map records, for each `Node` typed via `setTyping` during `apply(graph)`, the `Element` scope that drove the resolver call. Reads of the map are exposed only through `ExpansionSnapshot.producerScopeOf(node)`.

#### Scenario: Expanders never mutate the graph directly
- **WHEN** the source of `SourceDescentExpander`, `DirectiveBindingExpander`, `AssemblyExpander`, `BridgeExpander`, `FrontierMatcher`, and `InputAllocator` is inspected
- **THEN** no source line invokes `MapperGraph.addNode`, `MapperGraph.addEdge`, `MapperGraph.addEdgeIfAcyclic`, `MapperGraph.addGroup`, `MapperGraph.recordGroupOutcome`, `Node.setTyping`, `ExpansionGroup.addVertexToView`, or `ExpansionGroup.addEdgeToView`

#### Scenario: producerScopes is encapsulated in the Applier
- **WHEN** the source of `ExpandGroupsPhase`, the four `GroupExpander` impls, and helper classes (`FrontierMatcher`, `InputAllocator`) is inspected
- **THEN** none of them declare or expose an `IdentityHashMap<Node, Element>` field
- **AND** only the `Applier` declares the `producerScopes` map as a private field

### Requirement: GroupExpander interface contract

The phase SHALL define a `GroupExpander` interface with two methods:

- `boolean appliesTo(ExpansionGroup group)` — pure structural predicate; expanders SHALL recognise their group kind by inspecting the group's slots and locations (via `GroupShapes`).
- `GroupStepResult step(ExpansionGroup group, ExpansionSnapshot snapshot)` — pure function returning `(List<DeltaBundle> bundles, List<Node> pendingSlots)`. The implementation SHALL NOT mutate the graph, the group, the snapshot, or any other shared state. The `bundles` describe intended mutations; `pendingSlots` lists the group's slots that are not yet satisfied after applying the bundles (empty list ⇔ the expander considers the group SAT).

Expanders are supplied to the driver as an ordered `List<GroupExpander>` assembled by the `ExpandGroupsPhase.create` factory and passed via constructor injection. The driver SHALL dispatch each non-SAT group to the **first** expander whose `appliesTo` predicate returns true. The shipped expanders are `SourceDescentExpander`, `DirectiveBindingExpander`, `AssemblyExpander`, and `BridgeExpander`; their `appliesTo` predicates SHALL be mutually exclusive by construction (source-descent, directive-binding, and assembly shapes are distinct, and `BridgeExpander` is the fallback for any remaining non-seed sub-group).

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

### Requirement: ExpandGroupsPhase drives per-group expansion

`ExpandGroupsPhase` SHALL process registered `ExpansionGroup`s via a cross-group fixed-point loop (see the "Cross-group fixed-point loop" requirement): iterate every non-SAT group, dispatch each to its `GroupExpander`, collect emitted bundles, apply them, repeat until a full pass produces no changes. The initial group set is the groups present after `SeedGraph`: the source-side per-edge groups (path-segment and directive-binding) and the umbrella assembly groups (one per parent target node, slots = its child target leaves; see `seed-graph`). Groups registered during expansion (via `AddGroup` deltas) join the set and are processed in subsequent passes.

The phase SHALL enforce a single budget constant:

- `MAX_OUTER_PASSES = 32` — the maximum number of cross-group passes. Exceeding this records `unsatDidNotConverge` on every still-pending group.

No per-slot round budget exists. Each `GroupExpander.step` call describes the group's intended progress for one pass; further progress on a pending group happens in subsequent passes. The cross-group fixed-point loop subsumes what an in-pass round budget would protect against, and the cycle-rejection-in-applier semantics (see the "Bridge edge-emission rule" and "DeltaBundle atomicity" requirements) bound inverse-bridge dead-branch expansion at the structural level.

For each group visited in an outer pass, the phase SHALL invoke `GroupExpander.step(group, snapshot)` which produces `(bundles, pendingSlots)`. If `pendingSlots.isEmpty()` after the pass's applier run, the driver SHALL mark the group SAT on the state and SHALL invoke `recordGroupOutcome(GroupOutcome.sat(group))` on the underlying graph via the applier. Otherwise the group remains pending; outcome recording is deferred to outer-loop convergence (see "Cross-group fixed-point loop").

The legacy phase methods `fillGroup`, `resolveSlot`, `expandFrontier`, `tryBridges`, `tryBridgeOnCandidate`, `commitBridgeStep`, `tryGroupTargets`, `registerNestedGroupTarget`, `allocateInputNode`, `findCandidateByInputType`, `allocateFresh`, `importBoundaryNodes` SHALL NOT exist on `ExpandGroupsPhase`. Their semantic equivalents live in `GroupExpander` implementations, `FrontierMatcher`, and the `Applier`.

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

### Requirement: Candidate search scoped to current group's view

The driver SHALL source candidate input nodes from the current group's view (`snapshot.viewOf(group).vertexSet()`), excluding the frontier itself and excluding any node whose `Location` is a `TargetLocation`, and expose them to strategies only as the flat `frontier.candidates()` snapshot. No global scan over `snapshot.allNodes()` or any equivalent SHALL occur during candidate search, and no strategy SHALL receive the view or the graph itself.

The view-scoped search is the structural fix for sibling-leak cross-group cycles and multi-parameter directive ambiguity. Combined with `Node` instance-identity (see `graph-model`) and narrow boundary import (only `SourceLocation` nodes inherit from the parent group's view), it prevents *sibling-derived nodes* from being picked up as candidates. Cycle-closing matches (e.g. an inverse container step reusing the current group's root) can still be emitted; they are rejected at applier-time via the cycle check in "DeltaBundle atomicity".

#### Scenario: Candidates come only from the current group's view
- **WHEN** the driver builds the `Frontier` for a frontier node in `group`
- **THEN** `frontier.candidates()` contains only nodes from `snapshot.viewOf(group).vertexSet()`
- **AND** excludes the frontier
- **AND** excludes any node whose `Location` is a `TargetLocation`
- **AND** no candidate is sourced from `snapshot.allNodes()` or `MapperGraph.nodes()` directly

#### Scenario: Sibling sub-group nodes are invisible
- **WHEN** the underlying `MapperGraph` contains a `Node` `X` that is a member of some sibling sub-group `S` but not of `snapshot.viewOf(currentGroup).vertexSet()`
- **AND** the driver builds the `Frontier` for a node in `currentGroup`
- **THEN** `X` is not among `frontier.candidates()`

### Requirement: Single round-robin strategy invocation per pass

Each expansion pass SHALL try the one `List<ExpansionStrategy>` as a single round at every open frontier — there SHALL be no per-kind ordering (no "match strategies, then assembly strategies, then path resolvers"). The driver SHALL build a `Frontier` for the frontier node and invoke `expand(frontier, ctx)` on each strategy in list order (sorted by `priority()` then FQN), collecting the emitted `ExpansionStep`s.

The return-assembly (a multi-slot `BOUNDARY` step) SHALL be resolved by data dependency within the cross-group fixed-point loop, not by a dedicated earlier phase. Target→source direction bounds the search, dead-end sub-groups are pruned, and the node budget bounds transient growth, so convergence holds without an explicit assembly-first phase.

#### Scenario: strategies are tried as one unordered round
- **WHEN** the driver expands a frontier
- **THEN** every strategy in `List<ExpansionStrategy>` is offered the frontier in one round
- **AND** no strategy kind is gated to run before another

#### Scenario: assembly resolves by data dependency
- **WHEN** a return value requires a multi-slot assembly whose slots are produced in later passes
- **THEN** the assembly `BOUNDARY` step is committed and its slots resolve in subsequent passes via the fixed-point loop
- **AND** the result is identical regardless of which pass first emitted the assembly step

### Requirement: Driver deduplicates structurally-identical emitted steps

Within a single frontier's expansion round the driver SHALL collapse `ExpansionStep`s that would produce an identical graph result — same emitting strategy, `intent`, element-`scope`, output type, and ordered input slot types — to a single committed step. This is required because `CombinatorialMatch.expand` offers each in-view candidate to the author's `bridge(from, to, ctx)`, yet a container's target-driven branches (wrap, and unwrap-by-synthesis of the wrapper type) ignore the `from` type and so emit the same step once per candidate; without dedup each duplicate opens a structurally identical, equal-cost twin sub-group that only the plan oracle later prunes.

`from`-dependent steps (genuine conversions, whose input type derives from the candidate) differ by input type and SHALL NOT be collapsed. The emitting strategy's identity is part of the signature, so two distinct strategies that happen to emit a coincident shape SHALL both remain.

#### Scenario: a target-driven container step is emitted once despite multiple candidates
- **WHEN** a frontier with multiple in-view candidates is offered to a container strategy whose unwrap/wrap branch ignores the candidate `from` type
- **THEN** exactly one such `BOUNDARY` step is committed
- **AND** only one sub-group is opened for it

#### Scenario: distinct-input conversions are not collapsed
- **WHEN** a strategy emits two steps with different input slot types at one frontier
- **THEN** both steps are committed

### Requirement: Intent-driven fold versus subgroup at the single mutation site

The driver SHALL branch on `ExpansionStep.intent` alone to decide graph shape:

- For `intent == CONVERSION`: the driver SHALL obtain the input node by **same-type-within-view dedup** — if a node whose type is the same (`Types.isSameType`) as the step's single input type already exists in the **current** group's view (excluding the frontier itself and `TargetLocation` nodes), it SHALL reuse that node; otherwise it SHALL **synthesize** a fresh type-keyed node for the input type. It SHALL add the input node (when synthesized) and the realised input→frontier edge into the current group's view (the same `AsSubgraph` the frontier belongs to). It SHALL NOT open a new `ExpansionGroup`. A synthesized input node SHALL be an **expandable frontier** — offered to strategies via `matchAt` in later passes so its own producers are discovered — but SHALL NOT be added to the group's AND-required slot set (so an unreachable synthesized node is a retained dead end, not a blocker).
- For `intent == BOUNDARY`: the driver SHALL open a new `ExpansionGroup` rooted at the frontier whose `slots` are the step's `inputs` (`0..N`), with the realised slot→frontier edges as its initial edges. Container boundaries carry the step's `ElementScope` onto the realised edge.

All mutation SHALL continue to flow through the `Applier` as atomic `DeltaBundle`s.

#### Scenario: CONVERSION reuses an existing in-view node of the input type
- **WHEN** a strategy emits a `CONVERSION` step at frontier `F` in group `G` and a node of the step's input type already exists in `G`'s view
- **THEN** the realised input→`F` edge is folded from that existing node
- **AND** no new node is synthesized and no new `ExpansionGroup` is created

#### Scenario: CONVERSION synthesizes the input node when none of the type exists
- **WHEN** a strategy emits a `CONVERSION` step at frontier `F` in group `G` and no node of the step's input type exists in `G`'s view
- **THEN** a fresh type-keyed input node is synthesized and added to `G`'s view with the realised input→`F` edge
- **AND** the synthesized node is an expandable frontier offered to strategies in a later pass
- **AND** the synthesized node is NOT added to `G`'s AND-required slot set
- **AND** no new `ExpansionGroup` is created for the step

#### Scenario: BOUNDARY opens a subgroup with the step's slots
- **WHEN** a strategy emits an `ExpansionStep` with `intent == BOUNDARY` and `N` inputs at frontier `F`
- **THEN** a new `ExpansionGroup` rooted at `F` with those `N` slots is created
- **AND** its initial view contains `F`, the slot nodes, and the realised slot→`F` edges

### Requirement: Conversion folding makes round-trips structural cycles

Because consecutive `CONVERSION` steps share one group view, the input-node dedup is keyed on **type within the group's view** (not location): a conversion whose input type already has a node in the current view SHALL reuse that node rather than minting a fresh one. A no-progress round-trip (e.g. box∘unbox, which re-derives a type already present) therefore folds its edge onto the existing same-type node and closes a cycle in the REALISED projection, which SHALL be rejected by the existing acyclicity check in "DeltaBundle atomicity". No type-recurrence or no-progress guard SHALL exist; the cycle check is sufficient.

#### Scenario: box-then-unbox round-trip is rejected as a cycle
- **WHEN** a `CONVERSION` chain would re-derive a type already present in the group's view (a box∘unbox round-trip)
- **THEN** the second step reuses the existing same-type node
- **AND** the resulting realised edge closes a cycle that the applier's acyclicity check rejects
- **AND** no separate type-recurrence guard is consulted

### Requirement: Conversion-chain satisfaction is base-case reachability

A node satisfied via realised **conversion** edges SHALL satisfy iff at least one incoming conversion edge's **source node is itself satisfied** (a base case, a node with a SAT child sub-group, or another conversion node satisfied by this same rule — transitively to a base case). Mere presence of an incoming conversion edge SHALL NOT satisfy a node; the edge's source must be satisfied. A group SATs iff every one of its **fixed slots** is satisfied (by base case, SAT child sub-group, or conversion-chain reachability); synthesized conversion nodes that never become reachable are retained dead ends and SHALL NOT block group SAT.

This generalizes per-slot satisfaction from "has one incoming realised edge" to base-case reachability through realised conversion edges, so a chain `X→Y→Z` SATs only once a complete realised path from a base case exists.

#### Scenario: a conversion chain SATs only when complete
- **WHEN** a slot `Z` is fed by a synthesized conversion node `Y` which is fed by a base-case source `X` (`X→Y→Z`)
- **THEN** `Z` is satisfied only after `Y` is satisfied, which holds only because `X` is a base case
- **AND** before `Y` is reachable, `Z` is NOT satisfied despite having the incoming `Y→Z` edge

#### Scenario: an unreachable conversion node does not block group SAT
- **WHEN** a target-driven conversion synthesizes an alternative input node that never acquires a producing source (a dead end)
- **THEN** that node remains in the graph unsatisfied
- **AND** the group still SATs once its fixed slots are reachable via some other realised conversion path

### Requirement: Conversion expansion is type-keyed, bounded, and stops at SAT

`CONVERSION` input-node dedup SHALL key on type within the current group's view (at most one node per type per view); `BOUNDARY` slot nodes SHALL remain keyed on logical identity (slot role). Because the lossless primitive conversion lattice is finite, type-keyed synthesis SHALL produce a bounded per-group type-DAG. The existing stop-at-SAT behaviour SHALL be unchanged — once a group is SAT it is not expanded in later passes — which is sound for conversions because expansion is breadth-by-hops and conversion weights are uniform (`Weights.STEP`): the shortest (cheapest) realised path is already present when the group SATs, and deeper paths are strictly more expensive. Path selection among the retained alternatives SHALL remain the plan-view's cheapest-realised-path responsibility, not the expander's.

#### Scenario: distinct logical boundary slots of the same type are not merged
- **WHEN** a `BOUNDARY` step opens a subgroup with two slots whose declared types are equal but whose logical roles differ
- **THEN** two distinct slot nodes are created (logical-identity keying), not one

#### Scenario: a SAT conversion group is not expanded further
- **WHEN** a group reaches SAT via a complete realised conversion path in a pass
- **THEN** the group is excluded from subsequent passes
- **AND** the shorter/cheaper competing paths discovered up to that pass remain in the graph for the plan-view to select among

### Requirement: Directive propagation onto synthesized conversion nodes

The driver SHALL thread the in-effect `@Map` directive onto nodes it synthesizes as the input of a `CONVERSION` step, so a downstream strategy reading `frontier.directive()` sees the originating binding's configuration (source path/segment, patterns, default values). Nodes synthesized as the slots of a `BOUNDARY` step SHALL NOT inherit the parent's directive (a boundary crosses to a new value). Strategies SHALL NOT search for the directive; the driver supplies it via `Frontier`.

#### Scenario: a synthesized conversion node inherits the originating directive
- **WHEN** a frontier carrying directive `D` is resolved by a `CONVERSION` step that synthesizes input node `X`
- **THEN** the `Frontier` later built for `X` returns `D` from `directive()`

#### Scenario: boundary slots do not inherit the parent directive
- **WHEN** a frontier carrying directive `D` is resolved by a `BOUNDARY` step that synthesizes slot nodes
- **THEN** the `Frontier` built for a slot node does not return `D` from `directive()` solely by inheritance

### Requirement: Multi-fire per frontier; parallel sub-groups coexist

`FrontierMatcher` SHALL emit one `DeltaBundle` per matching `BOUNDARY` `ExpansionStep` at each frontier. Multiple strategies (or multiple steps from one strategy) that produce the frontier's type SHALL each yield their own bundle (a REALISED `AddEdge` plus an `AddGroup`). The siblings share the same root (the frontier) but have different slots and expand independently in subsequent passes.

A slot SATs iff at least one of its child sub-groups has outcome SAT. Dead branches (sub-groups that never reach SAT) remain in the graph as unsatisfied sub-groups; they do not contribute to the alive chain via outcome propagation.

Within one `ExpansionStrategy`, the strategy MAY return multiple `ExpansionStep`s describing alternative shapes; the driver deduplicates structurally-identical emitted steps within the round (see "Driver deduplicates structurally-identical emitted steps").

#### Scenario: Two matching strategies spawn two sibling sub-groups
- **WHEN** the frontier is a container target that two distinct container `ExpansionStrategy`s both produce (e.g. a collect-from-stream step and a wrap step)
- **THEN** two bundles are emitted, each with an `AddGroup` delta for a one-slot `ExpansionGroup` rooted at the frontier
- **AND** the two siblings have different slots and join the next pass's snapshot as DAG leaves

#### Scenario: Dead sibling sub-groups don't block the alive chain
- **WHEN** two sibling sub-groups exist at the same root and one's outcome is SAT, the other's is `unsatNoPlan`
- **THEN** the parent slot is SAT via the alive sibling
- **AND** the dead sibling remains in the graph with its outcome unchanged

### Requirement: GroupOutcome records per-group SAT/UNSAT verdicts

The graph SHALL retain a `GroupOutcome` for every registered `ExpansionGroup`, recorded via `MapperGraph.recordGroupOutcome(...)` by the `Applier` when the driver drains outcomes at fixed-point convergence. Outcomes are one of:

- `GroupOutcome.sat(group)` — every slot SAT'd via either base-case SAT or at least one SAT child sub-group.
- `GroupOutcome.unsatNoPlan(group, failingSlot)` — the group's `GroupExpander.step` returned `failingSlot` in `pendingSlots` with no bundle able to progress it, AND no existing child sub-group rooted at it is SAT.
- `GroupOutcome.unsatDidNotConverge(group, failingSlot)` — the cross-group fixed-point loop exceeded `MAX_OUTER_PASSES` without convergence.

Outcomes propagate up the dependency DAG: a parent group's slot is SAT iff at least one child sub-group rooted at that slot has outcome SAT. No global REALISED-traversal is performed at SAT time; the outcome of each group is the SAT input for its parent.

Outcomes are consumed by downstream validation phases (see `realisation-validation`) to surface closest-miss diagnostics.

#### Scenario: SAT outcome recorded when all slots SAT
- **WHEN** the cross-group fixed-point converges with `group` having every slot either base-case SAT or having at least one SAT child sub-group
- **THEN** `MapperGraph.recordGroupOutcome(GroupOutcome.sat(group))` is called

#### Scenario: UNSAT_NO_PLAN recorded with the failing slot
- **WHEN** the fixed-point converges with a group whose last-pass `pendingSlots` still contains `slot` and no SAT child sub-group is rooted at it
- **THEN** the driver records `GroupOutcome.unsatNoPlan(group, slot)` via the `Applier`

#### Scenario: Parent SAT requires at least one SAT child per slot
- **WHEN** a parent group has slot `S` with three child sub-groups rooted at `S`, all of whose outcomes are `unsatNoPlan`
- **THEN** the parent's slot `S` is NOT SAT
- **AND** the parent's outcome is `unsatNoPlan`

### Requirement: DirectiveBindingExpander root typing and direct-assign gating

`DirectiveBindingExpander` resolves a directive-binding group (root at a `TargetLocation`, single slot at a `SourceLocation`; see `seed-graph`). It SHALL NOT write the source slot's type onto the target root. The target root's type is supplied solely by the producer that consumes the root — the `ConstructorCall` (an `AssemblyStrategy`) or other assembly/bridge producer at the umbrella assembly group — via the existing "Slot Nodes are typed at producer commit" lifecycle. The expander SHALL NOT emit a `TypeNode` delta that types the root from the source slot.

While the root is untyped, the group SHALL remain pending across outer passes (it waits for the assembly producer to pin the declared type onto the root); it SHALL NOT be forced to SAT and SHALL NOT direct-assign.

Once both the slot and the root carry independently-resolved types, the expander SHALL gate on type identity:

- If `isSameType(slotType, rootType)`, the expander SHALL emit a single direct-assign REALISED edge (`slot → root`, `weight == Weights.NOOP`, `DirectAssignCodegen`) plus its `AddEdgeToView`, type the root with the target type at this producer commit when still untyped, and SAT the group.
- If the types differ, the expander SHALL NOT emit a direct-assign edge. It SHALL resolve the root as a frontier via `SlotResolver.resolve(root, ...)`, so container/element strategies (and recursive assembly/`MethodCallBridge` element mappings) convert the source type into the declared target type. The group SATs once a spawned child sub-group rooted at the root SATs.

The declared target type is read via `ExpansionSnapshot.effectiveTypeFor(root, group)`; it is pinned onto the directive-binding group (the node it shares as root with the umbrella assembly group's slot) by `Applier.pinExpectedTypesOnProducers` when the assembly producer's `AddGroup` is applied, so the read resolves from the group's own metadata without a cross-group scan. This preserves the container-conversion chain for directive-bound collection/`Optional` targets and prevents the source type from being stamped onto the target.

#### Scenario: Scalar same-type binding emits a direct-assign edge and SATs
- **WHEN** a directive-binding group has slot `src[person.lastName]:String` (typed) and root `tgt[lastName]` whose declared target type is `String`
- **THEN** `DirectiveBindingExpander.step` emits one bundle with a direct-assign REALISED edge `src[person.lastName] → tgt[lastName]` (`Weights.NOOP`, `DirectAssignCodegen`), a `TypeNode` typing the root `String`, and an `AddEdgeToView`
- **AND** the group is returned with `pendingSlots = []` (SAT)

#### Scenario: Source slot type is never stamped onto an untyped target root
- **WHEN** a directive-binding group has typed slot `src[person.addresses]:List<Optional<Person.Address>>` and root `tgt[addresses]` whose declared target type is not yet known to the group
- **THEN** `DirectiveBindingExpander.step` emits no `TypeNode` delta for the root
- **AND** the group is returned still pending (the root remains untyped after the applier run)

#### Scenario: Differing container types expand a conversion chain instead of direct-assign
- **WHEN** a directive-binding group has typed slot `src[person.addresses]:List<Optional<Person.Address>>` and the root `tgt[addresses]` has declared target type `Optional<Set<Human.Address>>`
- **THEN** `DirectiveBindingExpander.step` emits no direct-assign edge
- **AND** it resolves the root as a frontier, producing bundles whose container/element bridges convert `List<Optional<Person.Address>>` into `Optional<Set<Human.Address>>` (including a recursive `Person.Address → Human.Address` element mapping)
- **AND** the group SATs only once a spawned child sub-group rooted at `tgt[addresses]` SATs

#### Scenario: Collection-to-collection directive binding realises a conversion chain, not a typed-as-source direct assign
- **WHEN** the mapper declares `@Map(target = "addresses", source = "person.addresses")` with source `List<Optional<Person.Address>>` and target `Optional<Set<Human.Address>>`
- **THEN** the realised graph types `tgt[addresses]` with the declared target type `Optional<Set<Human.Address>>`, not the source type
- **AND** there is no single direct-assign REALISED edge from `src[person.addresses]` straight to `tgt[addresses]`
- **AND** a container-conversion chain of REALISED bridge edges connects `src[person.addresses]` to `tgt[addresses]`

### Requirement: Directive-binding declared target type pinned by the assembly producer

When an assembly producer at an umbrella assembly group binds a parameter to a directive-declared child (by name), it binds to a per-`(name, required-type)` typed leaf minted for **that** constructor (see "Type-divergent overloaded constructors coexist as per-type typed slots"). The producer's `AddGroup` delta SHALL carry that slot's declared parameter type in its `slotMetadata`. The `Applier`, on applying the `AddGroup` (`Applier.pinExpectedTypesOnProducers`), SHALL record that declared type onto the **directive-binding conversion feeding that constructor's typed leaf**, via `ExpansionGroup.recordExpectedType(node, slot)`, so the declared target type is readable through that conversion's own `expectedTypeFor`/`effectiveTypeFor` without any cross-group scan.

Because each constructor pins its own typed leaf, type-divergent overloaded constructors pin **distinct** types onto **distinct** leaves and never collide: the pin is no longer a last-writer-wins `slotMetadata.put` over a single shared leaf. A directive-binding conversion whose leaf is bound by no assembly producer slot SHALL NOT be pinned (and converges to `unsatNoPlan` if its leaf is never typed).

#### Scenario: Assembly-bound typed leaf pins its directive-binding conversion
- **WHEN** an umbrella assembly producer (e.g. `ConstructorCall`) binds its parameter slot to a typed leaf with declared type `T`, and the `Applier` applies the resulting `AddGroup`
- **THEN** `Applier.pinExpectedTypesOnProducers` calls `recordExpectedType(leaf, slot)` on the directive-binding conversion feeding that typed leaf
- **AND** that conversion's `expectedTypeFor(leaf)` returns `T`

#### Scenario: Type-divergent overloads pin distinct types without colliding
- **WHEN** one constructor binds a `number` leaf with declared type `int` and another binds a `number` leaf with declared type `long`, both for the same shared source value
- **THEN** each typed leaf's directive-binding conversion is pinned to its own declared type (`int` and `long` respectively)
- **AND** neither pin overwrites the other (no last-writer-wins over a single shared leaf)

#### Scenario: Unbound directive leaf stays unpinned
- **WHEN** a directive-binding conversion's leaf is bound by no assembly producer slot
- **THEN** `expectedTypeFor(leaf)` remains `null`
- **AND** the conversion converges to `unsatNoPlan` if its leaf is never typed by any producer

### Requirement: Assembly binds only directive-declared inputs

The assembly path (`FrontierMatcher` resolving an `AssemblyStrategy` step such as `ConstructorCall`) SHALL bind each constructor parameter **only** to a directive-declared child of the matching name, read from the umbrella assembly group's slots (`group.getSlots()` at the assembly root — the driver already holds them; no cross-group scan). It SHALL NOT fall through to `InputAllocator.allocate` to mint a fresh slot for an un-declared constructor parameter, and SHALL NOT auto-source such a parameter.

Because every constructor parameter is mandatory, this reduces to a **name-set equality** candidacy rule: a constructor is a candidate iff its parameter-name set equals the umbrella group's declared-child name set. The two halves of the equality are coverage (`⊇` — no declared child dropped) and no-silent-sourcing (`⊆` — no un-declared parameter invented). A constructor whose parameter-name set is not equal to the declared-child name set SHALL NOT open a sub-group at the assembly root; the rejection happens in the assembly path before sub-group creation, not via a later cost or SAT tweak.

This strictness is specific to `AssemblyStrategy` producers. Bridge producers (`MethodCallBridge` and friends) legitimately descend their own arguments — the author wrote that method — and SHALL retain their existing argument-sourcing behaviour; the strict-vs-descend split keys on `AssemblyStrategy`, a marker that already exists.

#### Scenario: Constructor whose parameter names equal the declared children is a candidate
- **WHEN** the umbrella declared children are `{number, street}` and the target declares `Address(int number, String street)`
- **THEN** the assembly path opens a sub-group rooted at `tgt[address]` whose slots are the `number` and `street` typed leaves
- **AND** no fresh slot is allocated for any un-declared parameter

#### Scenario: Constructor with an extra parameter is rejected without silent sourcing
- **WHEN** the umbrella declared children are `{street, zip}` and the target declares `Address(String street, String zip, String country)`
- **THEN** the assembly path does NOT open a sub-group for that constructor
- **AND** no fresh `country` slot is allocated and no source is auto-descended for it

#### Scenario: Constructor missing a declared parameter is rejected without dropping a field
- **WHEN** the umbrella declared children are `{street, zip}` and the target declares `Address(String street)`
- **THEN** the assembly path does NOT open a sub-group for that constructor
- **AND** the declared `zip` child is not dropped from any candidate

#### Scenario: No-arg constructor is not a candidate when children are declared
- **WHEN** the umbrella declared children are non-empty and the target declares a no-arg `Address()`
- **THEN** the assembly path does NOT open a zero-slot sub-group for `Address()`
- **AND** `new Address()` is never emitted while declared children remain unmapped

#### Scenario: Name-set equality applies identically inside element scopes
- **WHEN** an umbrella assembly group is constructed for a target inside a container element scope (`container-expansion`) with declared children `{number, street}`
- **THEN** the same name-set equality candidacy rule applies through the element seam without special-casing

### Requirement: Type-divergent overloaded constructors coexist as per-type typed slots

When two or more accessible constructors share the umbrella's declared-child name set but disagree on a child's parameter type, the assembly path SHALL let them coexist as competing OR-siblings rooted at the assembly output, rather than racing to type a single shared leaf. Each constructor's parameter SHALL become its **own** target leaf, keyed on `(name, required-type)` and pinned to that constructor's parameter type, fed from the **one shared source value** seeded for the directive by its **own** directive-binding conversion (reusing the `DirectiveBindingExpander` path: same-type ⇒ direct-assign; differing ⇒ a widen/box conversion chain).

The per-`(name, required-type)` typed leaves and their directive-binding conversions SHALL be minted during expansion by the assembly path (driver `AddNode`/`AddGroup` deltas), **reusing the one shared source node** rather than duplicating the source down to the parameter. The type check is then structural: a parameter leaf resolves iff a valid conversion from the shared source exists, so an incompatible overload is UNSAT and pruned — there is no separate type guard, and SAT comes to mean "compiles".

This respects the live architecture rules that conversions and candidate search exclude `TargetLocation` inputs, and that source→target-type conversion is owned by `DirectiveBindingExpander`: the adaptation is from the shared **source value** to each typed target leaf, never from one target leaf to another.

Each competing constructor's sub-group SHALL have slots disjoint from its OR-siblings': the first constructor to demand a declared child reuses the pre-seeded leaf, and every later sibling mints its own private leaf for **all** of its parameters, so no two co-rooted constructor groups share a slot node. (PlanView resolves the OR by dropping the losing group's slot→root edges; a shared leaf would yield a structurally-equal edge in both groups, and dropping the loser's copy would take the winner's with it.)

#### Scenario: int/long divergent overloads both materialise distinct typed leaves
- **WHEN** the declared children are `{number, street}` and the target declares both `Address(int number, String street)` and `Address(long number, String street)` and the shared source value `src[address.number]` is `int`
- **THEN** distinct typed leaves `tgt[number]:int` and `tgt[number]:long` are minted, each rooting its constructor's sub-group
- **AND** `tgt[number]:int` is fed by a direct-assign directive-binding edge from `src[address.number]`
- **AND** `tgt[number]:long` is fed by a widen directive-binding conversion from the same `src[address.number]` node (no duplicate source node)

#### Scenario: An incompatible overload is structurally UNSAT
- **WHEN** a constructor's parameter type admits no valid conversion from the shared source value
- **THEN** that constructor's typed leaf never resolves and its sub-group is UNSAT
- **AND** it is pruned by the plan without any separate type-guard check

### Requirement: Satisfiable assembly yields a planned return-root; unsatisfiable assembly is diagnosed

Whenever **at least one** accessible constructor is a name-set-equality candidate and is satisfiable (all its typed leaves resolve via valid conversions from the declared sources), `planView()` SHALL contain the assembly return-root node, and code generation SHALL emit a `new Target(...)` call for the selected constructor. `BuildMethodBodies.findReturnRoot` SHALL find the return-root in this case and SHALL NOT throw.

When an assembly output has directive-declared children but **no** name-matching, type-correct constructor, the engine SHALL record the umbrella assembly group's outcome as `unsatNoPlan` naming the unsatisfiable field(s), surfaced through the existing realisation-diagnostics path. It SHALL NOT silently drop the return-root and SHALL NOT fail the scope with an internal `IllegalStateException`; `BuildMethodBodies` SHALL degrade to a diagnostic that names the unresolved target rather than throwing. Only a genuinely unsatisfiable assembly fails the scope, and it fails with that diagnostic.

#### Scenario: One satisfiable overload guarantees a planned return-root
- **WHEN** a target declares type-divergent overloaded constructors and at least one is a satisfiable name-set-equality candidate
- **THEN** `planView()` contains the assembly return-root node
- **AND** codegen emits `new Target(...)` for the selected constructor
- **AND** `BuildMethodBodies.findReturnRoot` does not throw

#### Scenario: No name-matching constructor is diagnosed, not dropped
- **WHEN** an assembly output has declared children `{street, zip}` but every accessible constructor has a different parameter-name set
- **THEN** the umbrella assembly group's outcome is recorded `unsatNoPlan` naming the unsatisfiable field(s)
- **AND** codegen surfaces a diagnostic identifying the unresolved target instead of throwing an `IllegalStateException`
- **AND** no silent `new Target()` is emitted

### Requirement: Deterministic cost-driven constructor selection among satisfiable overloads

When more than one name-set-equality constructor is satisfiable, selection SHALL be the existing cheapest-co-rooted-group choice in `PlanView`, unchanged: a DirectAssign typed-leaf binding (cost `0`) beats a Widen binding (cost `1`), so the exact-type constructor is selected — the choice Java overload resolution makes. The selection SHALL be deterministic and stable across runs. No cost-oracle change is required: once per-type typed leaves make SAT mean "compiles" and name-set equality filters candidates, the cheapest-plan oracle already yields a single satisfiable constructor at the return-root.

#### Scenario: Exact-type overload wins over a widening overload
- **WHEN** both `Address(int number, …)` and `Address(long number, …)` are satisfiable and the shared source value is `int`
- **THEN** the `int` constructor is selected because its `number` leaf binds via DirectAssign (cost `0`) versus the `long` leaf's Widen (cost `1`)
- **AND** the selected constructor is stable across repeated runs
