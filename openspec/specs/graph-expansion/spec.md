# Graph Expansion Spec

## Purpose

This spec defines the expansion engine that turns a `MapperGraph` populated with `SEED` edges into a graph augmented with `REALISED` and `MARKER` edges, organised as a forest of `ExpansionGroup` subgraphs. Expansion proceeds target-to-source: target slots demand inputs, and the engine drives chains backwards through bridges and group targets until each slot is reachable from a source-parameter-root via REALISED edges.

The realisation engine is a per-group greedy work-list driver. There is no SUB_SEED edge kind, no global fixed-point loop, and no element-seed/diamond machinery. Element scope is declared per `BridgeStep` via `ScopeTransition`; the engine allocates element-scope nodes at the right `Location` and registers a one-slot nested `ExpansionGroup` for every scope-changing bridge match.

## Requirements

### Requirement: ExpandStage runs ExpansionPhases in declared order

The processor SHALL define a stage `ExpandStage` in package `io.github.joke.percolate.processor.stages.expand` that consumes a `MapperGraph` populated with `EdgeKind.SEED` edges. `ExpandStage` SHALL be `@Inject`-constructed via Lombok `@RequiredArgsConstructor(onConstructor_ = @Inject)` taking a `List<ExpansionPhase>` and any other dependencies as constructor arguments.

`ExpandStage.run(MapperContext)` SHALL iterate the injected phase list in declared order and invoke `phase.apply(graph)` on each. There is no convergence check, no fixed-point loop, and no round counter at the `ExpandStage` level — termination is guaranteed by each phase's own bounded behaviour (see `ExpandGroupsPhase` below).

Before invoking phases, `ExpandStage` SHALL set the `MapperContext.currentMethod` from the first SEED edge whose `from` node lives in a `MethodScope`. This ensures the context's currentMethod is available to downstream `ResolveCtx` consumers.

If the graph is `null` (no mapper-level graph was produced), `ExpandStage.run` SHALL return without invoking any phase.

#### Scenario: ExpandStage invokes phases in declared order
- **WHEN** `ExpandStage.run(ctx)` is invoked with phase list `[ResolveTargetChainsPhase, ExpandGroupsPhase]`
- **THEN** `ResolveTargetChainsPhase.apply(graph)` runs first
- **AND** `ExpandGroupsPhase.apply(graph)` runs second

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

### Requirement: ResolveTargetChainsPhase scaffolds target chains and ExpansionGroups

`ResolveTargetChainsPhase` SHALL iterate every `Node` in the graph whose `Location` is a `TargetLocation` with empty path segments and whose `type` is present — the return-root nodes (one per `@Map`-target return). For each return root, the phase SHALL:

1. Find leaf target nodes by BFS over incoming SEED edges within `TargetLocation` space, collecting nodes with no further incoming SEED edges and whose `type` is empty.
2. Extract `targetTails` — the deepest path segments of each leaf — to feed into `GroupTarget.buildFor(returnType, targetTails, ctx)`.
3. For every registered `GroupTarget` whose `buildFor` returns a non-empty `GroupBuild`, allocate one typed slot `Node` per `Slot`, emit a REALISED edge from each slot to the return root, optionally emit a MARKER edge from the corresponding seed node to the slot, and optionally emit a directive-binding REALISED edge if the seed's typed source side directly matches the slot's type (a same-type pass-through).
4. Register one parent `ExpansionGroup` per `GroupTarget` match, rooted at the return root, with the slots as members and the slot→root REALISED edges in `initialEdges`. The parent group joins the graph's group list.
5. For every directive-binding emitted in (3), register a one-slot nested `ExpansionGroup` whose root is the slot, slot is the typed source, and `initialEdges` is the directive-binding REALISED edge.

Phase mutation SHALL be committed to the graph via `MapperGraph.apply(GraphDelta)` for node/edge additions and `MapperGraph.addGroup(...)` for group registrations. Groups SHALL be registered AFTER all nodes/edges are added (groups require their root/slots/edges to already exist in the underlying graph).

#### Scenario: Return root with one constructor candidate registers parent group
- **WHEN** the graph contains return root `tgt[]:Human` and `ConstructorCall.buildFor(Human, [addresses, firstName, lastName], ctx)` emits a `GroupBuild` with three slots
- **THEN** three slot nodes are allocated and added (with `TargetLocation` paths matching the slot names)
- **AND** three REALISED edges are emitted from each slot to the return root
- **AND** one parent `ExpansionGroup` is registered with the return root as root, three slots, and three initial REALISED edges

#### Scenario: Directive-binding short-circuit emits a nested group
- **WHEN** a slot's type matches the typed source side of its corresponding seed edge
- **THEN** a REALISED edge `source → slot` is emitted with `strategyClassFqn = "io.github.joke.percolate.processor.stages.expand.DirectiveBinding"` and `weight = Weights.STEP`
- **AND** a one-slot nested `ExpansionGroup` is registered with the slot as root and the source as slot

### Requirement: ExpandGroupsPhase drives per-group greedy expansion

`ExpandGroupsPhase` SHALL drain a work-list of `ExpansionGroup`s, filling each group's slots by greedy bridge expansion. Initial work-list contents are the groups present after `ResolveTargetChainsPhase` (typically: one parent group per return root + nested directive-binding groups). As scope-changing bridge matches register additional nested groups (per the "scope-changing bridges register per-match nested groups" requirement below), they join the work-list and are drained in turn.

The phase SHALL enforce two budget constants:

- `MAX_WORK_LIST_ITERATIONS = 256` — the maximum number of groups drained per mapper. Exceeding this records `unsatDidNotConverge` on each remaining group without further processing.
- `MAX_SLOT_ROUNDS = 64` — the maximum number of expansion rounds per slot's `resolveSlot` call. Exceeding this records `unsatDidNotConverge` for that slot's group.

For each group drained from the work-list, the phase SHALL invoke `fillGroup(group, graph, workList)` which:

1. For each slot in `group.getSlots()`, call `resolveSlot(slot, graph, sourceRoots, workList)`. If any slot returns non-SAT, record the appropriate outcome on the group (`unsatNoPlan` or `unsatDidNotConverge`) and stop.
2. After all slots SAT, call `resolveSlot(group.getRoot(), graph, sourceRoots, workList)` to verify the group's root is also producible. This is trivially true for groups whose `initialEdges` carry slot→root REALISED edges (the standard case) — `resolveSlot` short-circuits via `slotReachable` immediately.
3. Record `GroupOutcome.sat(group)` on success.

`resolveSlot` SHALL implement bounded greedy expansion: starting with `frontier = [slot]`, iterate up to `MAX_SLOT_ROUNDS` rounds. Each round calls `expandFrontier(f, graph, workList, newNodes)` for every `f` in the current frontier, and replaces the frontier with the freshly allocated `newNodes`. After each round, check `slotReachable(slot, graph, sourceRoots)`; if true, return `SAT`. If `newNodes` is empty before the slot is reached, return `UNSAT_NO_PLAN`. If the round budget is exhausted, return `UNSAT_DID_NOT_CONVERGE`.

#### Scenario: Slot reaches source via realised chain ⇒ SAT
- **WHEN** a slot's `resolveSlot` is invoked and a REALISED chain from a source-parameter-root to the slot already exists
- **THEN** `slotReachable` returns true immediately
- **AND** `resolveSlot` returns `SAT` without entering the round loop

#### Scenario: Slot exhausts plan ⇒ UNSAT_NO_PLAN
- **WHEN** an expansion round produces no new nodes and the slot is still not reachable
- **THEN** `resolveSlot` returns `UNSAT_NO_PLAN`
- **AND** `fillGroup` records `GroupOutcome.unsatNoPlan(group, slot)` on the group

#### Scenario: Round budget exhaustion ⇒ UNSAT_DID_NOT_CONVERGE
- **WHEN** `MAX_SLOT_ROUNDS` rounds elapse without reaching source or running out of work
- **THEN** `resolveSlot` returns `UNSAT_DID_NOT_CONVERGE`

#### Scenario: Work-list budget exhaustion records remaining groups
- **WHEN** more than `MAX_WORK_LIST_ITERATIONS` groups are drained
- **THEN** every remaining group is recorded `unsatDidNotConverge` without `fillGroup` being called for it

### Requirement: Bridge edge-emission rule (unified)

For every `BridgeStep` returned by every `Bridge` query during frontier expansion, the phase SHALL apply the following rule:

Let `F` be the frontier node (the node demanding an input) and `C` be a candidate node (an existing graph node with a non-empty type and the same scope as `F`, excluding `F` itself and excluding `TargetLocation` nodes). Given a `BridgeStep(inputType, outputType, weight, codegen, scopeTransition, elementRole)`:

1. The phase SHALL verify `step.outputType` equals `F.type` (the bridge produces the frontier's type). If not, the step is skipped.

2. The phase SHALL verify `step.scopeTransition` is compatible with the frontier's location: `PRESERVING` always matches; `ENTERING` matches only when `F.loc instanceof ElementLocation`; `EXITING` matches only when `F.loc` is NOT an `ElementLocation`. Incompatible steps are skipped.

3. The phase SHALL determine `inputNode` per `step.scopeTransition`:

   - **PRESERVING**: if `C.type` equals `step.inputType`, use `C`. Otherwise, find or allocate a node with `(F.scope, F.loc, step.inputType)`.

   - **ENTERING**: try in order — (a) a same-element-scope candidate of the right type at `F.loc`; (b) `C` if `C.type` matches and `C.loc` is NOT an `ElementLocation`; (c) fresh allocation at `F.loc` if `step.inputType` equals `step.outputType` (the rare same-type ENTERING case) else at `F.loc` — both fall under "fresh at frontier's loc". The fresh allocation flag is set so the new node is added to `newNodes`.

   - **EXITING**: try (a) `C` if `C.type` matches and `C.loc.equals(new ElementLocation(step.elementRole))`; (b) any existing graph node at `(F.scope, ElementLocation(step.elementRole), step.inputType)`; (c) fresh allocation at `ElementLocation(step.elementRole)` in `F.scope`.

4. If `inputNode.equals(F)` (which can happen when a PRESERVING step matches with `inputType == F.type` — a no-op), the step is skipped.

5. The phase SHALL emit one `REALISED` edge from `inputNode` to `F`, carrying `step.weight`, `step.codegen`, and the strategy's class FQN in `Edge.strategyClassFqn`.

6. If `step.scopeTransition != PRESERVING`, the phase SHALL register a fresh one-slot nested `ExpansionGroup` with `inputNode` as the sole slot, `F` as the root, and the just-emitted REALISED edge in `initialEdges`. The new group joins the work-list. Each scope-changing bridge match is its own nested group — no fusion across matches.

7. If `inputNode` was freshly allocated, it is added to `newNodes` for the next expansion round.

The rule preserves the node-identity invariant: nodes are uniquely identified by `Node.equals` instance identity; same-shape allocation does not collapse nodes.

#### Scenario: PRESERVING direct match emits one REALISED edge with no allocation
- **WHEN** a `BridgeStep` with `scopeTransition == PRESERVING` is emitted, `inputType` matches a candidate's type, and `candidate.loc.equals(frontier.loc)`
- **THEN** the phase emits one REALISED edge `candidate → frontier`
- **AND** no new node is allocated
- **AND** no nested group is registered

#### Scenario: PRESERVING chain hop allocates intermediate at frontier loc
- **WHEN** a `BridgeStep` with `scopeTransition == PRESERVING` is emitted whose `inputType` matches no candidate
- **THEN** the phase allocates a fresh input node at the frontier's location
- **AND** emits one REALISED edge from the fresh node to the frontier
- **AND** no nested group is registered

#### Scenario: ENTERING bridge matches an existing same-element-scope candidate
- **WHEN** a `BridgeStep` with `scopeTransition == ENTERING` is emitted, frontier is at `ElementLocation("element")`, and a graph node at the same `ElementLocation("element")` carries the bridge's input type
- **THEN** `inputNode` is that existing node (no fresh allocation)
- **AND** the phase emits one REALISED edge from it to the frontier
- **AND** the phase registers a nested `ExpansionGroup`

#### Scenario: ENTERING bridge prefers regular-scope candidate when no same-scope match
- **WHEN** a `BridgeStep` with `scopeTransition == ENTERING` is emitted, no same-element-scope candidate exists, but a regular-scope (non-`ElementLocation`) candidate matches the bridge's input type
- **THEN** `inputNode` is that regular-scope candidate
- **AND** the phase emits one REALISED edge and registers a nested group

#### Scenario: ENTERING bridge fresh-allocates when neither candidate exists
- **WHEN** a `BridgeStep` with `scopeTransition == ENTERING` is emitted and neither same-element-scope nor regular-scope candidate matches
- **THEN** the phase fresh-allocates the input at the frontier's location (the flatMap fallback)
- **AND** the fresh node is added to `newNodes` and the next round expands it

#### Scenario: EXITING bridge allocates input at ElementLocation
- **WHEN** a `BridgeStep` with `scopeTransition == EXITING` is emitted, frontier is regular-scope, and no element-scope candidate of the input type exists
- **THEN** the phase fresh-allocates an input node at `ElementLocation(step.elementRole)` in the frontier's scope
- **AND** emits one REALISED edge from the fresh element-scope node to the frontier
- **AND** registers a nested `ExpansionGroup`

#### Scenario: EXITING bridge reuses an existing element-scope candidate
- **WHEN** a `BridgeStep` with `scopeTransition == EXITING` is emitted and an existing element-scope candidate at `ElementLocation(step.elementRole)` has the right input type
- **THEN** `inputNode` is that existing candidate
- **AND** the phase emits one REALISED edge to the frontier and registers a nested group

### Requirement: Multi-fire per frontier; parallel chains coexist

`ExpandGroupsPhase.tryBridges` SHALL commit every matching bridge at each frontier. Multiple bridges that produce the frontier's type SHALL each emit their own REALISED edge and, if scope-changing, their own nested group. Parallel chains coexist in the graph; dead branches (chains that never reach a source-parameter-root) remain in the graph as unresolved REALISED edges and unsatisfied nested groups. Slot reachability over REALISED edges picks the alive chain.

Within one `Bridge`, the first matching candidate wins — a single `Bridge` may return multiple `BridgeStep`s describing alternative shapes, and the engine commits the first that matches and stops within that bridge.

#### Scenario: Two matching bridges at the same frontier both commit
- **WHEN** the frontier is `tgt[addresses]:Optional<Set<HA>>` and both `OptionalCollect` (EXITING) and `OptionalWrap` (PRESERVING) match
- **THEN** the phase commits both REALISED edges
- **AND** `OptionalCollect` registers a nested group (scope-changing); `OptionalWrap` does not (PRESERVING)
- **AND** subsequent rounds expand both chains until one reaches a source-parameter-root

#### Scenario: Dead branches lie unresolved without blocking the alive chain
- **WHEN** two parallel chains exist after multi-fire and only one reaches a source-parameter-root via REALISED edges
- **THEN** `slotReachable` returns true for the slot via the alive chain
- **AND** the parent group SATs
- **AND** the dead chain's nested groups remain in the graph with `unsatNoPlan` outcomes; these do not contribute REALISED edges from a source root, so they are correctly excluded from the alive subgraph

### Requirement: Scope inheritance under target-to-source expansion

`ExpandGroupsPhase.allocateOrReuseInputNode` is the SOLE place in the engine where `BridgeStep.scopeTransition` and `BridgeStep.elementRole` are consulted. No other phase, no strategy, and no other call site SHALL inspect these fields to decide allocation behaviour. Strategies remain myopic: they declare their scope transition in the `BridgeStep` and let the driver materialise the consequences.

Every fresh node allocated by `allocateOrReuseInputNode` SHALL be born holding the REALISED edge to the frontier that demanded it. No node SHALL be created without an immediately-attached outgoing edge to an already-demanded node — the target-to-source invariant.

#### Scenario: Scope allocation is the only scope-aware engine surface
- **WHEN** the source of `processor/.../stages/expand/*.java` is inspected
- **THEN** `BridgeStep.getScopeTransition()` is invoked exactly once per `commitBridgeStep` call site, inside `allocateOrReuseInputNode` (or a helper it calls)
- **AND** no other engine class invokes `BridgeStep.getScopeTransition()` or `BridgeStep.getElementRole()`

#### Scenario: Every freshly allocated node is born with the outgoing edge
- **WHEN** `allocateOrReuseInputNode` returns a fresh node
- **THEN** the next instruction in `commitBridgeStep` emits a REALISED edge from that fresh node to the frontier
- **AND** the fresh node never exists in the graph without that outgoing edge

### Requirement: Scope-changing bridges register per-match nested groups

`ExpandGroupsPhase.commitBridgeStep` SHALL register a fresh nested `ExpansionGroup` for every `BridgeStep` whose `scopeTransition` is `ENTERING` or `EXITING`. Each scope-changing bridge match is its own nested group; no fusion across matches. `PRESERVING` matches do NOT register a nested group — they only emit a REALISED edge.

The nested group SHALL have:
- `root` = the current frontier `F`.
- `slots` = `[inputNode]` (a single-slot group).
- `initialEdges` = `{inputNode → F REALISED}` (the just-emitted edge).
- `codegen` = `step.getCodegen()::render` lifted to a `GroupCodegen` shape.
- `strategyClassFqn` = the originating bridge's class FQN.

The new group SHALL join the work-list and SHALL be drained by `fillGroup` like any other group.

`ExpandGroupsPhase.registerElementSeedGroup` and any "fused element-seed group" machinery SHALL NOT exist in the engine. The element-seed-group concept is retired alongside the `ElementSeed` SPI type.

#### Scenario: ENTERING bridge match registers a nested ExpansionGroup
- **WHEN** `commitBridgeStep` runs for a `BridgeStep` whose `scopeTransition == ENTERING`
- **THEN** the phase adds an `ExpansionGroup` rooted at the frontier with the just-allocated input node as its sole slot to the graph
- **AND** the new group joins the work list
- **AND** the group's `initialEdges` contains the just-emitted REALISED edge

#### Scenario: EXITING bridge match registers a nested ExpansionGroup
- **WHEN** `commitBridgeStep` runs for a `BridgeStep` whose `scopeTransition == EXITING`
- **THEN** the same is true as for ENTERING (a one-slot nested group joins the work list)

#### Scenario: PRESERVING bridge match does NOT register a nested ExpansionGroup
- **WHEN** `commitBridgeStep` runs for a `BridgeStep` whose `scopeTransition == PRESERVING`
- **THEN** the phase emits only the REALISED edge
- **AND** no new `ExpansionGroup` is added to the graph or work list

### Requirement: GroupTarget matches register nested groups via tryGroupTargets

`ExpandGroupsPhase.tryGroupTargets` SHALL run for every frontier that no bridge matched. For each registered `GroupTarget`, the phase SHALL call `groupTarget.buildFor(frontier.type, List.of(), ctx)`. The first non-empty `GroupBuild` SHALL:

1. Allocate one slot node per `Slot`, with `Location = ElementLocation(slot.name)` and `Scope = frontier.scope`.
2. Emit one REALISED edge per slot, from the slot to the frontier.
3. Register a nested `ExpansionGroup` with root = frontier, slots = the allocated nodes, initialEdges = the just-emitted REALISED edges.

#### Scenario: GroupTarget match at frontier with no bridge match
- **WHEN** the frontier is `elem:Human` and no `Bridge` matches but `ConstructorCall.buildFor(Human, [], ctx)` returns a non-empty `GroupBuild`
- **THEN** the phase allocates one slot node per `Slot` at `ElementLocation(slot.name)` in the frontier's scope
- **AND** emits one REALISED edge from each slot to the frontier
- **AND** registers a nested `ExpansionGroup` with the frontier as root and the slot nodes as slots

### Requirement: Source reachability gates slot SAT

`SourceReachability.slotReachable(slot, graph, sourceRoots)` SHALL return true iff there exists a directed path of REALISED edges from any node in `sourceRoots` to `slot` in the underlying graph. `sourceRoots` is the set of nodes whose `Location` is a single-segment `SourceLocation` and whose type is present.

`slotReachable` uses a JGraphT `MaskSubgraph` view that filters edges by `EdgeKind == REALISED` and traverses with a BFS.

#### Scenario: Slot is reachable when a REALISED chain exists from source
- **WHEN** a chain `src[person] → ... → slot` exists composed entirely of REALISED edges
- **THEN** `slotReachable(slot, graph, sourceRoots)` returns true

#### Scenario: Slot is not reachable via non-REALISED edges alone
- **WHEN** the only path from a source-parameter-root to a slot includes a MARKER or SEED edge
- **THEN** `slotReachable` returns false (only REALISED edges count for reachability)

### Requirement: Candidate inputs are scope-filtered, non-target, non-frontier

`SourceReachability.candidateInputs(scope, graph)` SHALL return the list of nodes in the graph satisfying ALL of:
- `node.getScope().equals(scope)` (same scope as the requesting frontier)
- `node.getType().isPresent()` (typed node)
- NOT `(node.getLoc() instanceof TargetLocation)` (target slots cannot be candidates — they are demand, not supply)

The returned list SHALL be sorted by `Node.id()` for determinism.

`expandFrontier` SHALL additionally filter out the frontier itself before passing candidates to `tryBridges`.

#### Scenario: Candidate list excludes target nodes
- **WHEN** `candidateInputs(scope, graph)` is invoked
- **THEN** no node in the result has `Location` of type `TargetLocation`

#### Scenario: Candidate list is deterministically ordered
- **WHEN** `candidateInputs(scope, graph)` is invoked twice on the same graph state
- **THEN** the two returned lists are equal element-wise (sorted by `Node.id()`)

### Requirement: GroupOutcome records per-group SAT/UNSAT verdicts

The graph SHALL retain a `GroupOutcome` for every registered `ExpansionGroup`, recorded via `MapperGraph.recordGroupOutcome(...)` during `fillGroup`. Outcomes are one of:

- `GroupOutcome.sat(group)` — every slot and the root SAT'd via REALISED reachability.
- `GroupOutcome.unsatNoPlan(group, failingSlot)` — `resolveSlot(failingSlot)` exhausted its expansion without producing new nodes.
- `GroupOutcome.unsatDidNotConverge(group, failingSlot)` — `resolveSlot(failingSlot)` exceeded `MAX_SLOT_ROUNDS` or the work-list budget was exhausted.

Outcomes are consumed by downstream validation phases (see `realisation-validation`) to surface closest-miss diagnostics.

#### Scenario: SAT outcome recorded when all slots and root SAT
- **WHEN** `fillGroup(group, graph, workList)` completes with all slots and the root reaching source
- **THEN** `MapperGraph.recordGroupOutcome(GroupOutcome.sat(group))` is called

#### Scenario: UNSAT_NO_PLAN recorded with the failing slot
- **WHEN** a slot's `resolveSlot` returns `UNSAT_NO_PLAN`
- **THEN** `fillGroup` records `GroupOutcome.unsatNoPlan(group, slot)` and returns early without processing remaining slots
