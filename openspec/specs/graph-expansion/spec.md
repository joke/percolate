# Graph Expansion Spec

## Purpose

This spec defines the expansion engine that turns SEED edges into REALISED, MARKER, and SUB_SEED edges through three sequential phases: source chain resolution, target chain resolution, and bridge resolution.

## Requirements

### Requirement: ExpandStage

The processor SHALL define a stage `ExpandStage` in package `io.github.joke.percolate.processor.stages.expand` that consumes a `MapperGraph` populated with `EdgeKind.SEED` edges and augments it with `EdgeKind.REALISED`, `EdgeKind.MARKER`, and (forward-compat) `EdgeKind.SUB_SEED` edges. `ExpandStage` SHALL be `@Inject`-constructed via Lombok `@RequiredArgsConstructor(onConstructor_ = @Inject)`.

`ExpandStage` SHALL execute three sequential `ExpansionPhase`s in declared order: `ResolveSourceChainsPhase`, `ResolveTargetChainsPhase`, `BridgeSourceToTargetPhase`. Phases SHALL NOT be re-run; the pipeline is linear with no back-jumping.

#### Scenario: ExpandStage runs the three phases in declared order
- **WHEN** `ExpandStage.apply(graph)` is invoked
- **THEN** `ResolveSourceChainsPhase.apply(graph)` runs first
- **AND** `ResolveTargetChainsPhase.apply(graph)` runs next
- **AND** `BridgeSourceToTargetPhase.apply(graph)` runs last

#### Scenario: Each phase runs exactly once
- **WHEN** `ExpandStage.apply(graph)` completes for a given mapper
- **THEN** each phase has been invoked exactly once on that graph

#### Scenario: ExpandStage uses Lombok-generated injection
- **WHEN** the source of `ExpandStage` is inspected
- **THEN** the class carries `@RequiredArgsConstructor(onConstructor_ = @Inject)`
- **AND** the generated constructor accepts the three phase classes

### Requirement: ExpansionPhase contract

Every `ExpansionPhase` SHALL implement a single method `apply(MapperGraph)` that mutates the graph (adds new nodes / edges) and returns the same instance. A phase SHALL NOT remove edges or nodes added by an earlier phase or by `SeedGraph`. A phase MAY be tested in isolation by constructing a `MapperGraph` populated to the state expected at that phase's entry.

#### Scenario: Phase returns the same graph instance
- **WHEN** any `ExpansionPhase.apply(graph)` is invoked
- **THEN** the returned `MapperGraph` is the same instance as the input

#### Scenario: Phase preserves existing edges
- **WHEN** a phase runs on a graph containing `n` SEED edges
- **THEN** all `n` SEED edges remain in the graph after the phase completes

### Requirement: ResolveSourceChainsPhase

`ResolveSourceChainsPhase` SHALL iterate every `EdgeKind.SEED` edge whose `to` node has `loc` of type `SourceLocation` (i.e., source-side seed edges including same-side `?→?` chain steps). For each such edge:

- The phase SHALL resolve the typed source of the FROM end. If `from.type` is non-empty, that type is used directly. Otherwise the phase SHALL pull the realised counterpart of `from` via outgoing `EdgeKind.MARKER` edges (driver normalisation of same-side `?→?`). If neither yields a typed source, the seed edge is skipped in the current iteration and revisited after later realisations.
- The phase SHALL extract `pathTail` from `to.loc` (the deepest segment of the target's `SourceLocation`'s access path).
- The phase SHALL invoke every registered `SourceStep` with `(typedSourceType, pathTail, ResolveCtx)`. For each returned `Step`:
  - the phase SHALL construct a new typed `Node` whose id is derived from the step's description and the FROM node's id;
  - the phase SHALL create one `EdgeKind.REALISED` edge from the typed source node to the new typed node, carrying `step.weight`, `step.codegen`, and the strategy's class FQN in `Edge.strategyClassFqn`;
  - the phase SHALL create one `EdgeKind.MARKER` edge from the original `?`-typed seed `to` node to the new typed node.

The phase SHALL iterate until no new realisations are added (internal fixed point bounded by chain depth).

#### Scenario: Single-segment dotted source realises a typed node
- **WHEN** `ResolveSourceChainsPhase.apply(graph)` is invoked on a graph seeded for `@Map(target = "lastName", source = "person.lastName")` with `GetterRead` registered and `Person` declaring `String getLastName()`
- **THEN** the resulting graph contains a `REALISED` edge from `src[person]:Person` to a new node typed `String`, weight `Weights.STEP`, `strategyClassFqn` referencing `GetterRead`
- **AND** a `MARKER` edge from `src[person.lastName]:?` to the new typed node, weight `Weights.NOOP`

#### Scenario: Multi-segment dotted source realises iteratively
- **WHEN** the phase processes a graph seeded for `@Map(source = "person.address.street", target = "x")` and `GetterRead` finds the matching getters at each level
- **THEN** the typed node for `Person.Address` is realised before the typed node for `String` (the inner getter cannot fire until its FROM end has a marker)
- **AND** marker edges link each `?`-typed seed node to its realised counterpart

#### Scenario: Same-side ?→? edge waits for FROM realisation
- **WHEN** the phase iterates a seed edge `src[a.b]:? → src[a.b.c]:?` before any MARKER targets `src[a.b]:?`
- **THEN** the edge is skipped in that iteration
- **AND** the phase iterates again until the FROM end is realised

#### Scenario: No-match strategy emits no edges
- **WHEN** the only registered `SourceStep` returns an empty stream for a given seed edge
- **THEN** the phase emits no `REALISED` or `MARKER` edges for that seed
- **AND** the seed edge remains present (it will surface as a Tier-2 error later)

### Requirement: ResolveTargetChainsPhase

`ResolveTargetChainsPhase` SHALL iterate every typed return-type root node (every node with `type.isPresent()` and `loc = TargetLocation([])`). For each return root:

- The phase SHALL aggregate `targetTails`: the list of single-segment target tails from all SEED edges flowing into that root via `tgt[.X]:?` slot nodes. Order SHALL match the source-declaration order of the directives (deterministic).
- The phase SHALL invoke every registered `GroupTarget` with `(returnType, targetTails, ResolveCtx)`. For each returned `GroupBuild`:
  - the phase SHALL allocate a fresh `groupId`;
  - for each `Slot` in `build.slots`, the phase SHALL create one new typed `Node` (typed by `slot.type`, with id derived from group + slot name) and one `EdgeKind.REALISED` edge from that typed slot node to the return root, carrying `slot.weight`, an `EdgeCodegen` derived from the build's `GroupCodegen`, the shared `groupId`, and the strategy's class FQN;
  - the phase SHALL register `build.codegen` on the `MapperGraph` via `addGroupCodegen(groupId, build.codegen)`;
  - the phase SHALL create one `EdgeKind.MARKER` edge from each matching original `tgt[.X]:?` seed node to its corresponding new typed slot node.

#### Scenario: ConstructorCall realises an exact-match group
- **WHEN** `ResolveTargetChainsPhase.apply(graph)` is invoked on a graph with seeds `tgt[.firstName]:? → tgt[]:Human` and `tgt[.lastName]:? → tgt[]:Human`, with `ConstructorCall` registered and `Human(String firstName, String lastName)` declared
- **THEN** the resulting graph contains two `REALISED` edges sharing one `groupId`, from new typed slot nodes to `tgt[]:Human`, weight `Weights.STEP` each
- **AND** two `MARKER` edges from the seed slot nodes to the typed slot nodes
- **AND** the `MapperGraph` exposes a registered `GroupCodegen` for that `groupId`

#### Scenario: Multiple GroupTarget matches emit parallel groups
- **WHEN** two different `GroupTarget` strategies both return a `GroupBuild` for the same return root
- **THEN** the phase emits two distinct groups with different `groupId` values
- **AND** each group's `REALISED` edges and `GroupCodegen` are independently registered

#### Scenario: No matching GroupTarget emits no edges
- **WHEN** the phase runs against a return root for which no `GroupTarget` matches
- **THEN** no `REALISED` or `MARKER` edges are emitted for that root
- **AND** the seed edges remain present (they will surface as Tier-2 errors)

### Requirement: BridgeSourceToTargetPhase

`BridgeSourceToTargetPhase` SHALL iterate every `EdgeKind.SEED` edge whose `from` node is in source space and whose `to` node is in target space (flavor ② seeds and bare-parameter sources where the FROM is typed at seed time).

For each such bridge seed:
- The phase SHALL resolve the realised source-side counterparts of the FROM end (FROM itself if `from.type.isPresent()`, otherwise the set of typed nodes reachable via outgoing MARKER edges from FROM).
- The phase SHALL resolve the realised target-side counterparts of the TO end the same way.
- The phase SHALL skip the seed if either side has zero realised counterparts (the bridge is not ready and surfaces as a Tier-2 error later — *bridge readiness predicate*).
- For each `(typedFromNode, typedToNode)` pair, the phase SHALL invoke every registered `Bridge` with `(typedFromNode.type, typedToNode.type, ResolveCtx)`. For each returned `BridgeStep`, the phase SHALL create one `EdgeKind.REALISED` edge from `typedFromNode` to `typedToNode` carrying `step.weight`, `step.codegen`, and the strategy's class FQN. No new nodes are created. No MARKER edges are emitted by this phase.

If multiple `Bridge` strategies match the same `(typedFromNode, typedToNode)` pair, multiple parallel REALISED edges SHALL be emitted between the same two nodes.

#### Scenario: DirectAssign realises an identity bridge
- **WHEN** `BridgeSourceToTargetPhase.apply(graph)` is invoked on a graph with a flavor ② seed whose endpoints both realise to typed `String` nodes, with `DirectAssign` registered
- **THEN** the resulting graph contains one new `REALISED` edge connecting the two typed `String` nodes, weight `Weights.NOOP`, `strategyClassFqn` referencing `DirectAssign`

#### Scenario: Bridge is skipped without a realised target
- **WHEN** the phase iterates a flavor ② seed whose source side has at least one MARKER but whose target side has zero MARKERs
- **THEN** no `Bridge` strategy is invoked for that seed
- **AND** no new `REALISED` edge is emitted for that seed

#### Scenario: Multiple matches emit parallel REALISED edges
- **WHEN** two `Bridge` strategies match the same `(typedFromNode, typedToNode)` pair
- **THEN** the phase emits two parallel `REALISED` edges with each strategy's weight and codegen
- **AND** both edges share the same `from` and `to` nodes (multigraph)

#### Scenario: Bare-parameter source bridge
- **WHEN** the phase iterates a SEED edge `src[person]:Person → tgt[.x]:?` (typed → ? cross-side) whose target side has been realised to a typed slot
- **THEN** the phase invokes registered `Bridge` strategies with `(Person, slotType, ResolveCtx)` and emits matching `REALISED` edges

### Requirement: Bridge readiness predicate enforced by driver

The `BridgeSourceToTargetPhase` SHALL guarantee that every `Bridge.bridge(...)` invocation receives `TypeMirror` arguments sourced from typed nodes only. Strategies SHALL never observe a `?`-typed node through the SPI. The phase enforces this by deferring any seed bridge whose endpoints lack realised counterparts.

#### Scenario: Bridge sees only typed inputs
- **WHEN** any `Bridge.bridge(sourceType, targetType, ctx)` is invoked by the phase
- **THEN** both `TypeMirror` arguments came from a `Node` with `type.isPresent() == true`

### Requirement: Driver-emitted MARKER edges

The expansion phases SHALL emit `EdgeKind.MARKER` edges only from driver-side code. Strategies SHALL NOT construct MARKER edges. Every MARKER edge SHALL have:
- `weight = Weights.NOOP`,
- a `?`-typed seed node as `from`,
- a typed realised node as `to`,
- empty `directive`, empty `codegen`, empty `strategyClassFqn`, empty `groupId`.

`MapperGraph.realisedSubgraph()` SHALL exclude all MARKER edges.

#### Scenario: Marker edge weight is zero
- **WHEN** any phase emits a `MARKER` edge
- **THEN** the edge's `weight` equals `Weights.NOOP`

#### Scenario: Marker edges are excluded from realised subgraph
- **WHEN** `MapperGraph.realisedSubgraph()` is invoked on a graph containing both REALISED and MARKER edges
- **THEN** the returned view contains every REALISED edge and zero MARKER edges

### Requirement: Multi-match parallel REALISED edges

The phases SHALL emit one `REALISED` edge per matching result when a strategy invocation produces multiple matches (e.g., multiple `Step` results in one `SourceStep.stepsFrom(...)` call, or matching results from two distinct strategy instances). Parallel `REALISED` edges SHALL coexist between the same pair of nodes — the underlying `DirectedMultigraph` supports this, and the future codegen change will select among them via Dijkstra.

#### Scenario: Two SourceSteps return matches for one input
- **WHEN** a `SourceStep` invocation returns two `Step` results (e.g., `getX()` and the same path tail's lenient match)
- **THEN** the phase emits two distinct `REALISED` edges from the same source node, each terminating at its own new typed node
- **AND** each edge carries its respective `Step.weight` and `Step.codegen`

### Requirement: Same-side `?→?` driver normalisation

The driver SHALL normalise same-side `?→?` SEED edges (the inner segments of dotted source paths) by pulling the realised counterpart of the FROM end (via outgoing MARKER edges) and invoking `SourceStep.stepsFrom(realisedType, pathTail, ctx)` with that typed input. Strategies SHALL NEVER receive a `?`-typed input through the SPI.

#### Scenario: SourceStep sees only typed inputs
- **WHEN** any `SourceStep.stepsFrom(sourceType, pathTail, ctx)` is invoked by the phase
- **THEN** the `sourceType` argument came from a `Node` with `type.isPresent() == true` (either typed at seed time or via a MARKER realisation)

### Requirement: ConstructorCall hint aggregation by driver

The driver SHALL aggregate the set of single-segment target tails sharing a typed return root by walking incoming SEED edges to the root (via the `tgt[.X]:?` slot nodes) and collecting the deepest target segment of each. The aggregated `List<String>` SHALL be passed to `GroupTarget.buildFor(returnType, targetTails, ctx)`. Order SHALL be determined by the source position of each directive (stable across runs).

#### Scenario: Aggregation collects all directive targets for a return root
- **WHEN** a graph has two SEED edges flowing into `tgt[]:Human` via slot nodes `tgt[.firstName]:?` and `tgt[.lastName]:?`
- **THEN** the driver invokes `GroupTarget.buildFor(<Human>, ["firstName", "lastName"], ctx)` once
- **AND** the order matches the directive declaration order in the source

### Requirement: Driver-emitted SUB_SEED edges (forward-compat)

The expansion driver SHALL be capable of emitting `EdgeKind.SUB_SEED` edges to represent typed slots that have no source-side counterpart at the time of group emission. SUB_SEED edges SHALL carry weight `Weights.SENTINEL_UNREALISED` and re-enter the work queue for the relevant phase. In v1, no built-in strategy triggers sub-directive emission; the driver path SHALL exist for forward-compat with future strategies (e.g., `HybridConstructorSetter`, auto-recursion).

#### Scenario: No SUB_SEED edges in v1 demo
- **WHEN** `ExpandStage.apply(graph)` runs on the v1 demo mapper using only the v1 built-in strategies
- **THEN** the resulting graph contains zero `EdgeKind.SUB_SEED` edges

### Requirement: Cycle detection over SEED + SUB_SEED subgraph

After each `ExpansionPhase` completes, `ExpandStage` SHALL run JGraphT `CycleDetector` over the subgraph filtered to `EdgeKind.SEED` and `EdgeKind.SUB_SEED` edges only. Cycles in this subgraph indicate a sub-directive lineage looping back on itself (a malformed user strategy). On detection, `ExpandStage` SHALL emit one `Diagnostics.error` per cycle keyed to one of the cycle's seed edges' `directive` `AnnotationMirror`. The affected mapper SHALL be scarred so subsequent phases skip it.

#### Scenario: V1 demo has no cycles
- **WHEN** `ExpandStage.apply(graph)` completes on the v1 demo mapper
- **THEN** the cycle detector reports zero cycles
- **AND** no cycle-related error diagnostic is emitted

#### Scenario: A constructed cycle is detected and reported
- **WHEN** a synthetic test mapper produces a SUB_SEED lineage with a cycle
- **THEN** `ExpandStage` emits one error diagnostic referencing the cycling directive's `AnnotationMirror`
- **AND** the mapper is scarred

### Requirement: Per-seed expansion budget of 100

The expansion driver SHALL track, per original directive seed edge, the count of derived sub-directive expansions in its lineage. The budget SHALL be a hardcoded constant `100` in v1. If any seed's lineage produces more than 100 expansions, the driver SHALL emit a `Diagnostics.error` keyed to the originating directive's `AnnotationMirror` and abort further expansion of that lineage.

#### Scenario: V1 mappers do not exhaust the budget
- **WHEN** `ExpandStage.apply(graph)` runs on any v1 demo mapper
- **THEN** no seed lineage exceeds 100 sub-directive expansions
- **AND** no budget-exhausted error is emitted

#### Scenario: Budget is enforced per seed, not per mapper
- **WHEN** a synthetic test mapper has two seeds, one of which exceeds the budget
- **THEN** exactly one error is emitted (for the exceeding seed)
- **AND** the other seed's expansion remains unaffected
