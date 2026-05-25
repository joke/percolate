## MODIFIED Requirements

### Requirement: ResolveTargetChainsPhase scaffolds target chains and ExpansionGroups

`ResolveTargetChainsPhase` SHALL iterate every `Node` in the graph whose `Location` is a `TargetLocation` with empty path segments and whose `type` is present — the return-root nodes (one per `@Map`-target return). For each return root, the phase SHALL:

1. Find leaf target nodes by BFS over incoming SEED edges within `TargetLocation` space, collecting nodes with no further incoming SEED edges and whose `type` is empty.
2. Extract `targetTails` — the deepest path segments of each leaf — to feed into `GroupTarget.buildFor(returnType, targetTails, ctx)`.
3. For every registered `GroupTarget` whose `buildFor` returns a non-empty `GroupBuild`, allocate one **untyped** slot `Node` per `Slot` and emit a REALISED edge from each slot to the return root. Slot Nodes SHALL be created with both `type` and `nullability` empty; the slot is typed by its producer's match at a later expansion phase via `Node.setTyping(...)`. `Slot.type` (the consumer-side expected type) drives subsequent candidate search but is NOT written to `Node.type` at slot-creation time.
4. Register one parent `ExpansionGroup` per `GroupTarget` match, rooted at the return root, with the slots as members and the slot→root REALISED edges in `initialEdges`. The parent group joins the graph's group list.

`ResolveTargetChainsPhase` SHALL NOT emit MARKER edges from typed slots to the corresponding untyped seed leaves; `SeedGraph` is responsible for the directive-binding group that links the typed slot to the untyped source-leaf side (see `seed-graph`).

`ResolveTargetChainsPhase` SHALL NOT emit any directive-binding REALISED edge or register any directive-binding sub-group; both are now registered by `SeedGraph` as one group per SEED edge.

Phase mutation SHALL be committed via `MapperGraph.apply(GraphDelta)` for node/edge additions and `MapperGraph.addGroup(...)` for group registrations. Groups SHALL be registered AFTER all nodes/edges are added.

#### Scenario: Return root with one constructor candidate registers parent group only
- **WHEN** the graph contains return root `tgt[]:Human` and `ConstructorCall.buildFor(Human, [addresses, firstName, lastName], ctx)` emits a `GroupBuild` with three slots
- **THEN** three slot nodes are allocated and added (with `TargetLocation` paths matching the slot names)
- **AND** every slot node has `type` empty and `nullability` empty at the end of this phase
- **AND** three REALISED edges are emitted from each slot to the return root
- **AND** one parent `ExpansionGroup` is registered with the return root as root, three slots, and three initial REALISED edges
- **AND** no directive-binding REALISED edge is emitted by this phase
- **AND** no nested directive-binding `ExpansionGroup` is registered by this phase

#### Scenario: Slot.type is not propagated to Node.type at slot creation
- **WHEN** `ResolveTargetChainsPhase` constructs a slot Node for `Slot(name="firstName", type=String, weight=1, producedFrom=ctorParam)`
- **THEN** the slot Node's `getType()` returns `Optional.empty()`
- **AND** the slot Node's `getNullability()` returns `Optional.empty()`
- **AND** `Slot.type` remains reachable via the slot's `Slot` reference for candidate-search purposes

## ADDED Requirements

### Requirement: Slot Nodes are typed at producer commit

Slot `Node`s allocated by `ResolveTargetChainsPhase` (top-level) and `ExpandGroupsPhase.registerNestedGroupTarget` (nested sub-groups from `Bridge` matches) SHALL be created with both `type` and `nullability` empty. They SHALL be typed when their producer commits — exactly the same lifecycle that path-segment-group roots already follow (see "Path-segment-group resolution via PathSegmentResolver").

Each producer-commit site SHALL invoke `slotNode.setTyping(producerType, producerNullability)` where:

- `producerType` is the actual type produced by the matched strategy (`ResolvedSegment.getReturnType()`, `BridgeStep.getOutputType()`, `MethodCandidate.method.getReturnType()`, …).
- `producerNullability` is `NullabilityResolver.resolve(producerType, producerScope)`, with `producerScope` being the underlying `Element` of the match (`ResolvedSegment.getProducedFrom()`, `BridgeStep.getProducedFrom()`, `MethodCandidate.getMethod()`, …).

The slot's consumer contract is NOT stored on the slot Node. It is derived on demand by code generation by calling `NullabilityResolver.resolve(slot.getProducedFrom().getType-or-asType, slot.getProducedFrom())` (see the `code-generation` capability).

The engine SHALL NOT use the prior `Node.setType(...)` API. All typing-commit sites SHALL use the paired `Node.setTyping(type, nullability)` (see the `graph-model` capability).

#### Scenario: Slot Node typed at producer commit via setTyping
- **WHEN** a slot Node is created untyped by `ResolveTargetChainsPhase`
- **AND** a path-segment producer commits in `ExpandGroupsPhase` matching `ResolvedSegment(returnType=String, ..., producedFrom=getterMethod)`
- **THEN** the slot Node's `setTyping(String, NullabilityResolver.resolve(String, getterMethod))` is invoked exactly once
- **AND** after commit, `slot.getType()` is `Optional.of(String)`
- **AND** `slot.getNullability()` is `Optional.of(...)` matching the resolver's verdict

#### Scenario: No engine call site uses the legacy setType API
- **WHEN** the source of every class under `processor/src/main/java/io/github/joke/percolate/processor/stages/` is inspected
- **THEN** no source line invokes `Node.setType(...)` (the legacy single-field accessor)
- **AND** every typing-commit site invokes `Node.setTyping(type, nullability)`

#### Scenario: Cycle rollback leaves the slot Node untyped
- **WHEN** an expansion match attempts to type a slot Node but is rolled back by the CycleDetector
- **THEN** the slot Node remains in its `Optional.empty()` state for both `type` and `nullability`
- **AND** a subsequent non-cyclic match types the slot successfully
