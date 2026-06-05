## MODIFIED Requirements

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

### Requirement: ExpandGroupsPhase drives per-group expansion

`ExpandGroupsPhase` SHALL process registered `ExpansionGroup`s via a cross-group fixed-point loop (see the "Cross-group fixed-point loop" requirement): iterate every non-SAT group, dispatch each to its `GroupExpander`, collect emitted bundles, apply them, repeat until a full pass produces no changes. The initial group set is the groups present after `SeedGraph`: the source-side per-edge groups (path-segment and directive-binding) and the umbrella assembly groups (one per parent target node, slots = its child target leaves; see `seed-graph`). Groups registered during expansion (via `AddGroup` deltas) join the set and are processed in subsequent passes.

The phase SHALL enforce a single budget constant:

- `MAX_OUTER_PASSES = 32` — the maximum number of cross-group passes. Exceeding this records `unsatDidNotConverge` on every still-pending group.

No per-slot round budget exists. Each `GroupExpander.step` call describes the group's intended progress for one pass; further progress on a pending group happens in subsequent passes.

For each group visited in an outer pass, the phase SHALL invoke `GroupExpander.step(group, snapshot)` which produces `(bundles, pendingSlots)`. If `pendingSlots.isEmpty()` after the pass's applier run, the driver SHALL mark the group SAT on the state and SHALL invoke `recordGroupOutcome(GroupOutcome.sat(group))` on the underlying graph via the applier. Otherwise the group remains pending; outcome recording is deferred to outer-loop convergence (see "Cross-group fixed-point loop").

The legacy phase methods `fillGroup`, `resolveSlot`, `expandFrontier`, `tryBridges`, `tryBridgeOnCandidate`, `commitBridgeStep`, `tryGroupTargets`, `registerNestedGroupTarget`, `allocateInputNode`, `findCandidateByInputType`, `allocateFresh`, `importBoundaryNodes` SHALL NOT exist on `ExpandGroupsPhase`. Their semantic equivalents live in `GroupExpander` implementations, `FrontierMatcher`, and the `Applier`.

#### Scenario: Slot is base-case SAT for parameter root
- **WHEN** a group's slot is `src[person]:Person` and `currentMethod` has parameter `person`
- **THEN** the expander's `step` for the group returns the slot in `pendingSlots = []` without emitting any bundle for that slot

#### Scenario: Slot SATs when any child sub-group SATs
- **WHEN** a slot's expansion has spawned three sibling sub-groups (multi-fire) and exactly one of them has outcome SAT in the snapshot
- **THEN** the expander's `step` for the parent group considers that slot satisfied
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
- **AND** it resolves the root as a frontier, producing bundles whose container/element strategies convert `List<Optional<Person.Address>>` into `Optional<Set<Human.Address>>` (including a recursive `Person.Address → Human.Address` element mapping)
- **AND** the group SATs only once a spawned child sub-group rooted at `tgt[addresses]` SATs

## REMOVED Requirements

### Requirement: Directive-binding declared target type pinned by ResolveTargetChainsPhase

**Reason**: `ResolveTargetChainsPhase` has been deleted from the code. The role it filled — pinning the declared target type onto the directive-binding group — is now performed during expansion by `Applier.pinExpectedTypesOnProducers` when the umbrella assembly producer's `AddGroup` delta is applied.

**Migration**: See the new "Directive-binding declared target type pinned by the assembly producer" requirement (below) for the shipped mechanism.

## ADDED Requirements

### Requirement: Directive-binding declared target type pinned by the assembly producer

When the assembly producer at an umbrella assembly group binds a slot to a pre-seeded target leaf (by name), the producer's `AddGroup` delta SHALL carry that slot's declared type in its `slotMetadata`. The `Applier`, on applying the `AddGroup` (`Applier.pinExpectedTypesOnProducers`), SHALL record that declared type onto the directive-binding group rooted at the same leaf node, via `ExpansionGroup.recordExpectedType(node, slot)`. This makes the declared target type readable through the directive-binding group's own `expectedTypeFor`/`effectiveTypeFor` without any cross-group scan. A directive-binding group whose root is bound by no assembly producer slot SHALL NOT be pinned (and converges to `unsatNoPlan` if its root is never typed).

#### Scenario: Assembly-bound target leaf pins the directive-binding group
- **WHEN** an umbrella assembly producer (e.g. `ConstructorCall`) binds its parameter slot to a target leaf with declared type `T`, and the `Applier` applies the resulting `AddGroup`
- **THEN** `Applier.pinExpectedTypesOnProducers` calls `recordExpectedType(leaf, slot)` on the directive-binding group rooted at that leaf
- **AND** that group's `expectedTypeFor(leaf)` returns `T`

#### Scenario: Unbound directive root stays unpinned
- **WHEN** a directive-binding group's root leaf is bound by no assembly producer slot
- **THEN** `expectedTypeFor(root)` remains `null`
- **AND** the group converges to `unsatNoPlan` if its root is never typed by any producer
