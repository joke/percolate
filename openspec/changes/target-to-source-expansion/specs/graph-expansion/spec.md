## MODIFIED Requirements

### Requirement: ExpandStage

The processor SHALL define a stage `ExpandStage` in `io.github.joke.percolate.processor.stages.expand` that runs after `SeedGraph`. `ExpandStage` SHALL be `@Inject`-constructed via Lombok `@RequiredArgsConstructor(onConstructor_ = @Inject)`.

`ExpandStage` SHALL invoke the registered `ExpansionPhase`s in order, exactly once each. The phase list is:

1. `ResolveTargetChainsPhase` — forward; settles target-side typed scaffolding and emits the initial `ExpansionGroup`(s) at the return root.
2. `ExpandGroupsPhase` — per-`ExpansionGroup`, target-driven, greedy; fills each group's slots and records per-group SAT/UNSAT outcomes for the diagnostic stage.

`ExpandStage` SHALL NOT run an outer fixpoint loop. The forward-expansion-era `MAX_EXPANSION_ROUNDS`, `MAX_EXPANSION_EDGES`, and `hasSeedSubSeedCycles()` checks are removed — termination, growth bounds, and cycle handling are per-slot concerns inside `ExpandGroupsPhase`.

`ExpandGroupsPhase` records each group's outcome on `MapperContext` (or an equivalent shared state) so `RealisationDiagnosticsStage` can read and report them.

#### Scenario: ExpandStage runs target-chain then group expansion in order

- **WHEN** `ExpandStage.run(ctx)` is invoked for a mapper
- **THEN** `ResolveTargetChainsPhase.apply(graph)` is invoked first
- **AND** `ExpandGroupsPhase.apply(graph, ctx)` is invoked second
- **AND** each phase is invoked exactly once
- **AND** no other expansion phases are invoked

#### Scenario: ExpandStage has no outer fixpoint loop

- **WHEN** the source of `ExpandStage` is inspected
- **THEN** no `while (round <= MAX_EXPANSION_ROUNDS)` loop is present
- **AND** no `MAX_EXPANSION_EDGES` check is present
- **AND** no `hasSeedSubSeedCycles()` invocation is present

### Requirement: ResolveTargetChainsPhase

`ResolveTargetChainsPhase` SHALL expand forward from each typed return-root node using registered `GroupTarget` strategies. For each typed root node in target space, the phase SHALL invoke every `GroupTarget` with `(returnType, targetTails, ctx)` and, for each non-empty `GroupBuild` returned, allocate typed slot nodes, emit a `REALISED` edge per slot connecting the slot to the root, emit `MARKER` edges linking each slot to the corresponding directive's untyped target counterpart, and construct an `ExpansionGroup` registering the root, the slot nodes, the slot-incoming REALISED edges, the codegen, and the strategy's class FQN. The phase SHALL call `MapperGraph.addGroup(group)` to register the group.

The phase SHALL allocate exactly one group per `(rootNode, strategy)` pair within a per-mapper run. The `ExpansionGroup` carries `strategyClassFqn` (the strategy's FQN) for deterministic identification; no separate `groupId` field is required on edges.

`ResolveTargetChainsPhase` is **forward-driven**. This direction is correct for constructor argument allocation; it is the only forward-driven phase in expansion after this change. Non-return slot resolution (multi-input bridges mid-expansion) is handled by `ExpandGroupsPhase` querying `GroupTarget`s during slot expansion.

#### Scenario: Constructor slot wiring emits REALISED per slot, MARKER per directive, and an ExpansionGroup

- **WHEN** the phase is invoked on a graph whose realised target root is `tgt[]:Human` and whose directives target slots `addresses`, `firstName`, `lastName`, with `ConstructorCall` registered as a `GroupTarget`
- **THEN** the phase emits three `REALISED` edges, one per slot, connecting `tgt[slotName]:slotType` to `tgt[]:Human`
- **AND** the phase emits three `MARKER` edges, one per directive, linking each `tgt[slotName]:?` (untyped seed counterpart) to the corresponding typed slot node
- **AND** the phase calls `MapperGraph.addGroup(...)` with an `ExpansionGroup` whose `root == tgt[]:Human`, whose `slots == [tgt[addresses]:Optional<Set<Address>>, tgt[firstName]:String, tgt[lastName]:String]`, whose `codegen` is `ConstructorCall`'s `GroupCodegen`, and whose `strategyClassFqn` equals `ConstructorCall.class.getName()`

#### Scenario: Group registration is idempotent within a phase run

- **WHEN** the phase is invoked twice on the same graph within a JVM run
- **THEN** the registered `ExpansionGroup` count after the second invocation equals the count after the first invocation (no duplicate groups)

## ADDED Requirements

### Requirement: ExpandGroupsPhase

`ExpandGroupsPhase` SHALL be a per-`ExpansionGroup`, target-driven, greedy expansion phase. It SHALL be `@Inject`-constructed via Lombok `@RequiredArgsConstructor(onConstructor_ = @Inject)` and depend on the list of registered `Bridge` strategies, the list of registered `GroupTarget` strategies, and the `ResolveCtx`.

The phase SHALL maintain a work list of `ExpansionGroup` objects, initially populated from `MapperGraph.groups()`. For each group `G` removed from the work list, the phase SHALL:

1. For each `slot` in `G.slots` (in registration order):
   a. If a path from any source-parameter-root (a node whose `loc` is a single-segment `SourceLocation` with a present `type`) to `slot` already exists via REALISED edges in the underlying graph, record `slot` SAT and continue to the next slot.
   b. Otherwise, initialise the per-slot frontier to `{slot}` and iterate rounds up to `MAX_SLOT_ROUNDS = 64`. Each round:
      - For each `F` in the frontier, for each registered `Bridge B` (deterministic order by class FQN), for each typed `candidateInput` in `MapperGraph.nodes()` whose `scope` matches `slot.scope` and whose `type` is present (deterministic order by `id()`), invoke `B.bridge(candidateInput.type, F.type, ctx)`. Commit the **first** non-empty `BridgeStep` whose `outputType` matches `F.type`. Subsequent matches for the same `F` are skipped (greedy).
      - Each commit emits one `REALISED` edge from `candidateInput` (or a freshly allocated input node if `step.inputType != candidateInput.type`) to `F`. For each `ElementSeed` on the step, allocate two element-location nodes (typed `inputType` and `outputType`) and register a nested `ExpansionGroup` rooted at the element-output node with one slot (the element-input node); append the new group to the work list.
      - If no `Bridge` step matches `F` after iterating every `(B, candidate)` combination, query each registered `GroupTarget` with `(F.type, [], ctx)`. If any returns a non-empty `GroupBuild`, allocate slot nodes per the build, emit slot-incoming REALISED edges, construct a nested `ExpansionGroup`, register it via `MapperGraph.addGroup(...)`, and append it to the work list. The slot defers until the nested group is processed (its REALISED edges become visible in the underlying graph).
      - After each round, re-check connectivity from any source-parameter-root to `slot`. If reachable, record `slot` SAT and exit the round loop.
      - If a round adds no new nodes and connectivity is still not achieved, record `slot` UNSAT(no-plan) and exit the round loop.
   c. If `slot` is UNSAT, record `G` UNSAT with the failing slot identified, and break out of the slot loop. Do not resolve subsequent slots of `G`.
2. If every slot of `G` is SAT, record `G` SAT.

The phase SHALL process groups independently. Recording UNSAT for one group SHALL NOT prevent expansion of subsequent groups in the work list. All groups are processed; all outcomes are recorded.

Nested `ExpansionGroup`s registered during slot resolution (from element seeds or mid-expansion `GroupTarget` matches) SHALL be appended to the work list and processed in the same per-mapper run.

#### Scenario: Bridges are queried with slot type as outputType, not inputType

- **WHEN** the phase reaches a slot or frontier node `F` and invokes a registered `Bridge B`
- **THEN** the invocation passes `F.type` as the `to` argument (the bridge's output side)
- **AND** the invocation passes a candidate input node's type as the `from` argument

#### Scenario: Slot terminates SAT on connectivity

- **WHEN** a round commits a `REALISED` edge sufficient to make `ConnectivityInspector` report a path from a source-parameter-root to the slot over the REALISED-only mask
- **THEN** the slot's outcome is recorded as SAT
- **AND** no further rounds are executed for that slot

#### Scenario: Slot terminates UNSAT on fixed point

- **WHEN** a round adds zero new nodes and connectivity to a source-parameter-root is still not achieved
- **THEN** the slot's outcome is recorded as `UNSAT(no-plan)`
- **AND** the containing group's outcome is recorded as UNSAT with that slot identified
- **AND** subsequent slots of the same group are not resolved
- **AND** the phase moves on to the next group in the work list

#### Scenario: Slot terminates UNSAT on round budget

- **WHEN** `MAX_SLOT_ROUNDS` rounds elapse without connectivity and the graph kept growing
- **THEN** the slot's outcome is recorded as `UNSAT(did-not-converge)`
- **AND** the containing group's outcome is recorded as UNSAT with that slot identified

#### Scenario: All groups are processed even when some fail

- **WHEN** the phase is invoked on a graph with three registered `ExpansionGroup`s, one of which will resolve UNSAT
- **THEN** all three groups are processed
- **AND** all three outcomes are recorded on `MapperContext`

#### Scenario: Greedy commit per slot

- **WHEN** a slot has two candidate Bridge×candidate combinations that would both emit valid REALISED edges
- **THEN** the phase commits exactly one REALISED edge (the lexically earlier `strategyClassFqn`, then earlier `candidate.id()`)
- **AND** the other matching combination is not committed

#### Scenario: Mid-expansion GroupTarget match registers a nested ExpansionGroup

- **WHEN** a slot's round iterates every `Bridge` × candidate combination without a match
- **AND** a registered `GroupTarget` returns a non-empty `GroupBuild` for the slot's type
- **THEN** the phase constructs an `ExpansionGroup` for the build and calls `MapperGraph.addGroup(...)`
- **AND** the new group is appended to the work list
- **AND** the slot defers until subsequent processing of the new group makes its source-side chains visible

#### Scenario: Nested element-scope groups are appended to the work list

- **WHEN** a committed `BridgeStep` has a non-empty `elementSeeds` list
- **THEN** one new `ExpansionGroup` per `ElementSeed` is registered with element-location nodes typed `inputType` and `outputType`
- **AND** each appended `ExpansionGroup` is processed as its own work item in the same phase invocation

### Requirement: Source-side reach via Bridge strategies

The engine SHALL NOT implement source-side traversal (e.g., getter chains from a source-parameter root) as engine-internal logic. Source-side reach SHALL be implemented as `Bridge` strategy implementations queried by `ExpandGroupsPhase` like any other `Bridge`.

Specifically: a `Bridge` implementing getter-style traversal (the rewritten `GetterRead`) is queried with `(parentType, fieldType, ctx)` and, if the `parentType` has a getter returning `fieldType`, emits a single-hop `BridgeStep` whose codegen produces `parent.getField()` at codegen time. Multi-hop access (`person.address.street`) emerges from the recursive structure of the slot expansion: when a `String` slot has no direct `Person` producer, the next round picks up an intermediate `Address` frontier node, the round after finds `Person → Address` via `GetterRead`, and connectivity closes.

#### Scenario: GetterRead is implemented as a Bridge, not as a SourceStep

- **WHEN** the source of the `GetterRead` built-in is inspected
- **THEN** the class implements `Bridge`
- **AND** the class does NOT implement `SourceStep` (the SPI is removed)

#### Scenario: Source-side reach uses ordinary Bridge queries

- **WHEN** `ExpandGroupsPhase` expands a slot `tgt[lastName]:String` and a source-parameter-root `src[person]:Person` is in scope
- **THEN** the phase invokes `Bridge.bridge(Person, String, ctx)` on registered bridges (including the `GetterRead`-as-`Bridge`)
- **AND** no engine-internal special-case source-reach logic is invoked

## REMOVED Requirements

### Requirement: ResolveSourceChainsPhase

**Reason:** Forward-driven source-chain expansion is incompatible with target-driven per-group expansion. Source-side typing is now produced on-demand by `Bridge` strategies during `ExpandGroupsPhase`.

**Migration:** Remove `ResolveSourceChainsPhase.java` and its `ProcessorModule` wiring. Re-register the `GetterRead` strategy as a `Bridge` implementation. No replacement phase is added.

### Requirement: BridgeSourceToTargetPhase

**Reason:** The forward-driven bridge phase is replaced by `ExpandGroupsPhase` (target-driven, per-group, greedy, connectivity-decided).

**Migration:** Delete `BridgeSourceToTargetPhase.java` and its `ProcessorModule` wiring. The `Bridge` SPI is unchanged.

### Requirement: ExpandSeedSubgraphPhase (per-SEED-edge driver)

**Reason:** The earlier iteration of this change defined a per-SEED-edge target-driven driver. That approach failed to express AND-join semantics (a constructor needs every slot reached simultaneously) and required workarounds for source-side untyped SEED endpoints. Replaced by `ExpandGroupsPhase`.

**Migration:** Delete `ExpandSeedSubgraphPhase.java` and its specs from the test tree. The frontier-walking machinery is retained inside `ExpandGroupsPhase` at slot granularity.

### Requirement: Bridge readiness predicate enforced by driver

**Reason:** Forward-direction "readiness" predicates are obsolete under per-group expansion, which initialises each slot's frontier from the slot itself (already typed by `ResolveTargetChainsPhase`).

**Migration:** Per-group expansion records UNSAT for slots that cannot be resolved; the diagnostic stage reports the framing gap. No readiness predicate survives.

### Requirement: Bridge edge-emission rule (unified)

**Reason:** The six-step forward emission rule is replaced by `ExpandGroupsPhase`'s `commit(...)` step: one `REALISED` edge per accepted `BridgeStep`, fresh input node if needed, element-scope nested `ExpansionGroup`s registered per `BridgeStep.elementSeeds`. No SUB_SEED emission; no output-intermediate SUB_SEED; no parent-field propagation.

**Migration:** The new `ExpandGroupsPhase` requirement above defines the commit step. Strategies' contracts (`Bridge` SPI signature, `BridgeStep` shape, `ElementSeed` shape, `GroupTarget` SPI signature, `GroupBuild` shape) are unchanged.

### Requirement: SUB_SEED edges drive outer-loop iteration

**Reason:** `SUB_SEED` edges are removed (collapsed into `SEED` and into nested `ExpansionGroup`s as appropriate). There is no outer-loop iteration on `SUB_SEED` because there is no outer fixpoint loop.

**Migration:** Element-scope and mid-expansion strategy-emitted groups join the per-group work list directly.

### Requirement: Container-Map outer REALISED edge represents an iteration

**Reason:** Behaviour preserved. Container-map strategies (e.g., `SetMap`) still emit a `REALISED` edge between typed container endpoints plus an `ElementSeed`. Under the new driver, the element seed becomes a nested `ExpansionGroup` rooted at the element-output node.

**Migration:** No spec migration needed; the strategy SPI is unchanged.

### Requirement: Same-side `?→?` driver normalisation

**Reason:** Same-side `?→?` SEED edges are framing only — they do not drive expansion under the new model. The new phase records UNSAT against any unresolved slot, not against SEED edges directly.

**Migration:** Tests asserting normalisation behaviour are revisited under the test audit.

### Requirement: ConstructorCall hint aggregation by driver

**Reason:** Subsumed by `ResolveTargetChainsPhase` (unchanged in behaviour) plus the `ExpansionGroup` registry. No standalone "hint aggregation" requirement.

**Migration:** No spec migration; behaviour preserved inside `ResolveTargetChainsPhase`.

### Requirement: Driver-emitted SUB_SEED edges (forward-compat)

**Reason:** Driver emits no `SUB_SEED` edges. `EdgeKind.SUB_SEED` is removed.

**Migration:** No replacement.

### Requirement: Cycle detection over SEED + SUB_SEED subgraph

**Reason:** No `SUB_SEED` edges, no outer fixpoint loop. Cycle handling within a slot's expansion is the per-slot round budget's responsibility; the `MAX_SLOT_ROUNDS` bound ensures termination.

**Migration:** Tests asserting `hasSeedSubSeedCycles()` behaviour are revisited under the test audit.

### Requirement: Mapper-level expansion round budget

**Reason:** The mapper-level budget is replaced by per-slot budgets (`MAX_SLOT_ROUNDS = 64` per slot). Total work is bounded by `(number of slots across all groups) × MAX_SLOT_ROUNDS`.

**Migration:** The new `ExpandGroupsPhase` requirement above defines per-slot budgets and termination conditions.

### Requirement: Driver-emitted MARKER edges

**Reason:** `MARKER` edge emission is preserved but happens inside `ResolveTargetChainsPhase` (linking untyped target-seed counterparts to typed slot nodes). No separate forward-driver MARKER pass.

**Migration:** Implementations may continue to emit `MARKER` edges from `ResolveTargetChainsPhase` and from `Bridge` strategies (via `Edge.marker(...)`). No spec wording change needed.

### Requirement: Self-call REALISED edges allowed

**Reason:** Self-call REALISED edges remain allowed; they're a `Bridge` strategy concern (`MethodCallBridge`-style implementations), not a driver requirement. Per-slot round budget catches infinite self-recursion.

**Migration:** No spec migration; behaviour preserved at the strategy level.

### Requirement: Multi-match parallel REALISED edges

**Reason:** Removed under greedy commit (D5). Each slot is filled by exactly one REALISED edge — the first matching `(Bridge, candidate)` combination in deterministic order. Multi-match commit was an exhaustive-multigraph artefact rejected in design.

**Migration:** Tests asserting parallel-REALISED emission are rewritten to assert single-edge commit with the deterministic tie-break. The multigraph-as-search-space property is intentionally dropped.
