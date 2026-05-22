# Graph Expansion Spec

## Purpose

This spec defines the expansion engine that turns SEED edges into REALISED, MARKER, and SUB_SEED edges through three sequential phases: source chain resolution, target chain resolution, and bridge resolution.

## Requirements

### Requirement: ExpandStage

The processor SHALL define a stage `ExpandStage` in package `io.github.joke.percolate.processor.stages.expand` that consumes a `MapperGraph` populated with `EdgeKind.SEED` edges and augments it with `EdgeKind.REALISED`, `EdgeKind.MARKER`, and `EdgeKind.SUB_SEED` edges. `ExpandStage` SHALL be `@Inject`-constructed via Lombok `@RequiredArgsConstructor(onConstructor_ = @Inject)`.

`ExpandStage` SHALL execute three sequential `ExpansionPhase`s in declared order: `ResolveSourceChainsPhase`, `ResolveTargetChainsPhase`, `BridgeSourceToTargetPhase`. The phase list SHALL run inside an outer fixed-point loop. Convergence SHALL be detected by comparing `MapperGraph.edgeCount()` immediately before and immediately after each pass over the phase list: if the count is unchanged, the loop terminates normally; otherwise the loop continues. The convergence check SHALL NOT depend on any signal returned by, or any field set by, `ExpansionPhase` implementations.

`ExpandStage` SHALL maintain a round counter incremented exactly once per iteration of the outer loop, independent of phase behaviour. If the counter exceeds the constant `MAX_EXPANSION_ROUNDS`, expansion terminates with the round-cap diagnostic (see "Mapper-level expansion round budget"). The counter is the sole non-cooperative termination guarantee for the loop.

After each phase invocation (i.e., after each individual phase, not only at the end of a full pass), `ExpandStage` SHALL run cycle detection over `EdgeKind.SEED + EdgeKind.SUB_SEED`. If the cycle guard fires, the mapper is scarred and the loop terminates immediately with the existing cycle-specific diagnostic.

#### Scenario: ExpandStage runs the three phases in declared order on each pass
- **WHEN** `ExpandStage.run(ctx)` is invoked
- **THEN** within each pass, `ResolveSourceChainsPhase.apply(graph)` runs first, then `ResolveTargetChainsPhase.apply(graph)`, then `BridgeSourceToTargetPhase.apply(graph)`

#### Scenario: ExpandStage terminates when edgeCount stops changing
- **WHEN** `ExpandStage.run(ctx)` is invoked on a graph that reaches a fixed point in one pass
- **THEN** `MapperGraph.edgeCount()` taken immediately before and immediately after the pass is identical
- **AND** the phase list runs exactly once
- **AND** the loop terminates without re-running

#### Scenario: ExpandStage re-runs the phase list when edgeCount grows
- **WHEN** `ExpandStage.run(ctx)` is invoked on a graph where the first pass emits a SUB_SEED that subsequent iterations resolve
- **THEN** `MapperGraph.edgeCount()` increases across the first pass
- **AND** the phase list runs at least twice
- **AND** the loop continues until a complete pass leaves `edgeCount()` unchanged

#### Scenario: Round counter ticks once per outer iteration regardless of phase behaviour
- **WHEN** any `ExpandStage.run(ctx)` invocation completes (normally or aborted)
- **THEN** the recorded round count equals the number of iterations of the outer loop entered
- **AND** the count is not influenced by the number of edges added per pass, nor by any value returned by phases

#### Scenario: ExpandStage uses Lombok-generated injection
- **WHEN** the source of `ExpandStage` is inspected
- **THEN** the class carries `@RequiredArgsConstructor(onConstructor_ = @Inject)`
- **AND** the generated constructor accepts the three phase classes (or their list) and dependencies for the guards

### Requirement: ExpansionPhase contract

Every `ExpansionPhase` SHALL implement a single method `void apply(MapperGraph)` that augments the graph with new nodes and/or edges. A phase SHALL NOT remove edges or nodes added by an earlier phase or by `SeedGraph`. A phase MAY be tested in isolation by constructing a `MapperGraph` populated to the state expected at that phase's entry.

A phase SHALL mutate the graph exclusively through `MapperGraph.apply(GraphDelta)`. A phase SHALL NOT call `MapperGraph.addNode` or `MapperGraph.addEdge` directly. Helpers internal to a phase that compute candidate nodes and edges SHALL return values (typically `Stream<GraphDelta>` or `GraphDelta`) and SHALL NOT mutate the graph; mutation SHALL be confined to the single `MapperGraph.apply(...)` site at the top of the phase.

Calling `apply` on a graph that is already at the phase's locally-stable state SHALL add zero new nodes and zero new edges. This is required for the outer fixed-point loop to converge.

#### Scenario: Phase apply returns no value
- **WHEN** any `ExpansionPhase.apply(graph)` is invoked
- **THEN** the method returns `void`
- **AND** there is no boolean or other change-signal exposed by the phase API

#### Scenario: Phase mutates the graph only via apply(GraphDelta)
- **WHEN** the source of any `ExpansionPhase` implementation is inspected
- **THEN** no helper method calls `MapperGraph.addNode` or `MapperGraph.addEdge` directly
- **AND** the only graph-mutating call is `MapperGraph.apply(GraphDelta)` (or a stream of deltas committed via `apply`)

#### Scenario: Phase preserves existing edges
- **WHEN** a phase runs on a graph containing `n` SEED edges
- **THEN** all `n` SEED edges remain in the graph after the phase completes

#### Scenario: Idempotence on a stable graph
- **WHEN** any `ExpansionPhase.apply(graph)` is invoked on a graph where the phase has nothing further to add
- **THEN** `MapperGraph.edgeCount()` is unchanged after the call
- **AND** `MapperGraph.nodeCount()` is unchanged after the call

### Requirement: ResolveSourceChainsPhase

`ResolveSourceChainsPhase` SHALL iterate every `EdgeKind.SEED` edge whose `to` node has `loc` of type `SourceLocation` (i.e., source-side seed edges including same-side `?→?` chain steps). For each such edge:

- The phase SHALL resolve the typed source of the FROM end. If `from.type` is non-empty, that type is used directly. Otherwise the phase SHALL pull the realised counterpart of `from` via outgoing `EdgeKind.MARKER` edges (driver normalisation of same-side `?→?`). If neither yields a typed source, the seed edge is skipped in the current invocation and revisited on the next outer-loop iteration after later realisations.
- The phase SHALL extract `pathTail` from `to.loc` (the deepest segment of the target's `SourceLocation`'s access path).
- The phase SHALL invoke every registered `SourceStep` with `(typedSourceType, pathTail, ResolveCtx)`. For each returned `Step`:
  - the phase SHALL construct a new typed `Node` whose id is derived from the step's description and the FROM node's id;
  - the phase SHALL emit one `EdgeKind.REALISED` edge from the typed source node to the new typed node, carrying `step.weight`, `step.codegen`, and the strategy's class FQN in `Edge.strategyClassFqn`;
  - the phase SHALL emit one `EdgeKind.MARKER` edge from the original `?`-typed seed `to` node to the new typed node.

Candidate nodes and edges SHALL be accumulated as `GraphDelta` values and committed to the graph via a single `MapperGraph.apply(...)` call at the end of `apply(MapperGraph)`. The phase SHALL perform a single pass per invocation; the outer `ExpandStage` round is the fixed-point. The phase SHALL NOT contain an internal `while`/`do-while` loop over its own outputs.

#### Scenario: Single-segment dotted source realises a typed node
- **WHEN** `ResolveSourceChainsPhase.apply(graph)` is invoked on a graph seeded for `@Map(target = "lastName", source = "person.lastName")` with `GetterRead` registered and `Person` declaring `String getLastName()`
- **THEN** the resulting graph contains a `REALISED` edge from `src[person]:Person` to a new node typed `String`, weight `Weights.STEP`, `strategyClassFqn` referencing `GetterRead`
- **AND** a `MARKER` edge from `src[person.lastName]:?` to the new typed node, weight `Weights.NOOP`

#### Scenario: Multi-segment dotted source resolves across outer rounds
- **WHEN** the phase runs once on a graph seeded for `@Map(source = "person.address.street", target = "x")` with `GetterRead` available, and the FROM end of the inner step is not yet realised at entry
- **THEN** in this single invocation only the chain prefix whose FROM end is already typed is realised
- **AND** the next outer-loop iteration, observing the new MARKER, realises the next prefix
- **AND** no internal fixed-point loop runs inside the phase

#### Scenario: Same-side ?→? edge waits for FROM realisation
- **WHEN** the phase iterates a seed edge `src[a.b]:? → src[a.b.c]:?` while no MARKER targets `src[a.b]:?`
- **THEN** the edge is skipped in this invocation
- **AND** the next outer-loop iteration revisits it after the FROM end is realised

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

`BridgeSourceToTargetPhase` SHALL iterate three classes of edge on every pass:

1. Every `EdgeKind.SEED` edge whose `from` node is in source space (location `SourceLocation`) and whose `to` node is in target space (location `TargetLocation`).
2. Every `EdgeKind.SUB_SEED` edge regardless of endpoint location.
3. Every `EdgeKind.SEED` edge whose `from` node and `to` node both have `Location` of type `ElementLocation` (element-scope SEEDs, emitted by container `Map` strategies per the element-seed emission rule).

For each such edge:

- The phase SHALL resolve the realised source-side counterpart of the FROM end (FROM itself if `from.type.isPresent()`, otherwise the typed node reachable via outgoing MARKER edges from FROM).
- The phase SHALL resolve the realised target-side counterpart of the TO end the same way.
- The phase SHALL skip the edge if either side has zero realised counterparts (the bridge is not ready and may resolve in a later iteration, or surface as a Tier-2 / Tier-3 error after the outer loop reaches its fixed point).
- For each `(typedFromNode, typedToNode)` pair, the phase SHALL invoke every registered `Bridge` with `(typedFromNode.type, typedToNode.type, ResolveCtx)`.
- For each `BridgeStep` in the returned stream, the phase SHALL apply the unified edge-emission rule (see "Bridge edge-emission rule").

If multiple `Bridge` strategies (or one strategy emitting multiple steps) match the same query, multiple parallel REALISED edges and possibly multiple parallel intermediate nodes SHALL be emitted, in accordance with the unified rule.

#### Scenario: DirectAssign realises an identity bridge
- **WHEN** `BridgeSourceToTargetPhase.apply(graph)` is invoked on a graph with a flavor ② seed whose endpoints both realise to typed `String` nodes, with `DirectAssign` registered
- **THEN** the resulting graph contains one new `REALISED` edge connecting the two typed `String` nodes, weight `Weights.NOOP`, `strategyClassFqn` referencing `DirectAssign`

#### Scenario: Bridge is skipped without a realised target
- **WHEN** the phase iterates a flavor ② seed whose source side has at least one MARKER but whose target side has zero MARKERs
- **THEN** no `Bridge` strategy is invoked for that seed
- **AND** no new `REALISED` edge is emitted for that seed in this iteration

#### Scenario: Multiple Bridge strategies emit parallel REALISED edges
- **WHEN** two `Bridge` strategies (or one strategy returning two steps) match the same `(typedFromNode, typedToNode)` pair with `inputType == sourceType` and `outputType == targetType`
- **THEN** the phase emits two parallel `REALISED` edges with each strategy's weight and codegen
- **AND** both edges share the same `from` and `to` nodes (multigraph)

#### Scenario: Bare-parameter source bridge
- **WHEN** the phase iterates a SEED edge `src[person]:Person → tgt[.x]:?` whose target side has been realised to a typed slot
- **THEN** the phase invokes registered `Bridge` strategies with `(Person, slotType, ResolveCtx)` and emits matching `REALISED` edges

#### Scenario: Phase processes SUB_SEED edges emitted by earlier iterations
- **WHEN** an earlier iteration emitted a SUB_SEED `src[g]:GR → src[g]:Dog` and the phase iterates again
- **THEN** the phase invokes registered `Bridge` strategies with `(GR, Dog, ResolveCtx)` for that SUB_SEED

#### Scenario: Phase processes element-scope SEED edges
- **WHEN** a prior iteration emitted a SEED `elem(parent=src[xs]:List<Optional<GR>>):Optional<GR> → elem(parent=src[xs]:List<Pet>):Pet` from an `OptionalMap`-style strategy
- **THEN** in the next pass, the phase iterates that SEED
- **AND** invokes registered `Bridge` strategies with `(Optional<GR>, Pet, ResolveCtx)` for it
- **AND** the resulting REALISED / SUB_SEED edges are emitted by the unified rule (with parent inheritance for any element-location allocations)

### Requirement: Bridge readiness predicate enforced by driver

The `BridgeSourceToTargetPhase` SHALL guarantee that every `Bridge.bridge(...)` invocation receives `TypeMirror` arguments sourced from typed nodes only. Strategies SHALL never observe a `?`-typed node through the SPI. The phase enforces this by deferring any seed bridge whose endpoints lack realised counterparts.

#### Scenario: Bridge sees only typed inputs
- **WHEN** any `Bridge.bridge(sourceType, targetType, ctx)` is invoked by the phase
- **THEN** both `TypeMirror` arguments came from a `Node` with `type.isPresent() == true`

### Requirement: Bridge edge-emission rule (unified)

The phase SHALL apply the following rule for every `BridgeStep` returned by every `Bridge` query, regardless of whether the step represents a direct match, a chain hop, a container wrap/unwrap, or a container map:

Let `F` be the seed's resolved typed source-side counterpart and `T` be the seed's resolved typed target-side counterpart. Given a `BridgeStep(inputType, outputType, weight, codegen, elementSeeds)`:

1. The phase SHALL determine `inputNode`:
   - If `step.inputType` equals `F.type`, then `inputNode = F`.
   - Otherwise, the phase SHALL find or allocate a `Node` with `scope = F.scope`, `loc = F.loc`, `type = step.inputType`, and `parent = F.parent` if `F.loc instanceof ElementLocation` else `parent = Optional.empty()`. If a node with this `(scope, loc, type, parent)` identity already exists in the graph, that node is reused; otherwise a new node is allocated and added to the graph.

2. The phase SHALL determine `outputNode`:
   - If `step.outputType` equals `T.type`, then `outputNode = T`.
   - Otherwise, the phase SHALL find or allocate a `Node` with `scope = F.scope`, `loc = F.loc`, `type = step.outputType`, and `parent = T.parent` if `F.loc instanceof ElementLocation` else `parent = Optional.empty()`. Find-or-allocate semantics as for `inputNode`.

3. The phase SHALL emit one `EdgeKind.REALISED` edge from `inputNode` to `outputNode`, carrying `step.weight`, `step.codegen`, and the strategy's class FQN in `Edge.strategyClassFqn`.

4. If `inputNode != F` (i.e. the input was allocated rather than identified with `F`), the phase SHALL emit one `EdgeKind.SUB_SEED` edge from `F` to `inputNode`, carrying weight `Weights.SENTINEL_UNREALISED` and the originating directive's `AnnotationMirror` (inherited from the seed that triggered the query). The SUB_SEED drives a subsequent outer-loop iteration to find a path from `F` to `inputNode`.

5. If `outputNode != T` (i.e. the output was allocated rather than identified with `T`), the phase SHALL emit one `EdgeKind.SUB_SEED` edge from `outputNode` to `T`, carrying weight `Weights.SENTINEL_UNREALISED` and the originating directive's `AnnotationMirror`. The SUB_SEED drives a subsequent outer-loop iteration to find a path from `outputNode` to `T`.

6. For each `ElementSeed(role, innerInputType, innerOutputType)` in `step.elementSeeds`, the phase SHALL:
   - Find or allocate an element `Node` `eFrom` with `scope = F.scope`, `loc = ElementLocation(role)`, `type = innerInputType`, `parent = Optional.of(inputNode)`.
   - Find or allocate an element `Node` `eTo` with `scope = F.scope`, `loc = ElementLocation(role)`, `type = innerOutputType`, `parent = Optional.of(outputNode)`.
   - Emit one `EdgeKind.SEED` edge from `eFrom` to `eTo`, carrying weight `Weights.SENTINEL_UNREALISED`, the emitting strategy's class FQN, and `Optional.empty()` for the directive.

The rule preserves the node-identity invariant: nodes are uniquely identified by `(scope, location, type, parent)`, and two emissions converging on the same identity reuse the same node, becoming parallel edges.

#### Scenario: Direct match emits one REALISED edge with no allocation and no SUB_SEED
- **WHEN** a `BridgeStep` is emitted whose `inputType == F.type`, `outputType == T.type`, and `elementSeeds` is empty
- **THEN** the phase emits one REALISED edge `F → T`
- **AND** no new node is allocated
- **AND** no SUB_SEED is emitted
- **AND** no element node or element SEED is emitted

#### Scenario: Chain hop allocates an input intermediate and emits SUB_SEED
- **WHEN** a `BridgeStep` is emitted whose `inputType` is not equal to `F.type`, whose `outputType` equals `T.type`, and `elementSeeds` is empty
- **THEN** the phase finds or allocates an intermediate node `I` with `(F.scope, F.loc, step.inputType, parent: as F)`
- **AND** the phase emits a REALISED edge `I → T`
- **AND** the phase emits a SUB_SEED edge `F → I`
- **AND** no element node or element SEED is emitted

#### Scenario: Two strategies emitting the same intermediate type collapse on identity
- **WHEN** two `Bridge` strategies each emit a `BridgeStep` with the same `inputType = X` (different from `F.type`) for the same seed
- **THEN** the phase allocates exactly one intermediate node with `(F.scope, F.loc, X)` (the second strategy reuses the first's node)
- **AND** the phase emits two parallel REALISED edges, both with target equal to the same shared intermediate (or its successor, depending on the steps' outputs)

#### Scenario: Output type differs from T.type allocates an output intermediate AND emits a SUB_SEED
- **WHEN** a `BridgeStep` is emitted whose `outputType` is not equal to `T.type`
- **THEN** the phase finds or allocates an intermediate node `O` with `(F.scope, F.loc, step.outputType)`
- **AND** the phase emits a REALISED edge from `inputNode` to `O`
- **AND** the phase emits a SUB_SEED edge from `O` to `T`

#### Scenario: Container-map step emits outer REALISED edge AND element-scope seed
- **WHEN** a `BridgeStep` is emitted whose `inputType == F.type`, `outputType == T.type`, and `elementSeeds = [ElementSeed("element", innerIn, innerOut)]`
- **THEN** the phase emits one REALISED edge `F → T`
- **AND** the phase allocates an element node `eFrom` with `(F.scope, ElementLocation("element"), innerIn, parent = F)`
- **AND** the phase allocates an element node `eTo` with `(F.scope, ElementLocation("element"), innerOut, parent = T)`
- **AND** the phase emits one SEED edge `eFrom → eTo`
- **AND** no SUB_SEED is emitted at the outer level (both input and output match F and T)

#### Scenario: Element-location intermediate inherits parent
- **WHEN** the phase processes an element-scope SEED `eFrom → eTo` (both with `loc instanceof ElementLocation`) and a strategy emits a step whose `outputType != eTo.type`
- **THEN** the phase allocates a new element node with `(eFrom.scope, eFrom.loc, step.outputType, parent = eTo.parent)`
- **AND** that new node's `id()` correctly resolves through `parent.id()`

### Requirement: SUB_SEED edges drive outer-loop iteration

`EdgeKind.SUB_SEED` edges emitted by `BridgeSourceToTargetPhase` per the unified edge-emission rule SHALL participate in subsequent outer-loop iterations the same way SEED edges do: `BridgeSourceToTargetPhase` (and any other phase that iterates seed-shaped edges) SHALL include SUB_SEED edges in its work-list on every pass.

A SUB_SEED edge is processed identically to a SEED edge during expansion: the phase resolves the FROM and TO endpoints' realised counterparts and queries each registered `Bridge` for the resulting type pair. Newly emitted edges from a SUB_SEED's processing again follow the unified edge-emission rule.

A SUB_SEED is "resolved" once a path of REALISED edges connects its FROM endpoint to its TO endpoint. The expansion driver does not explicitly track resolution; the outer fixed-point terminates when no phase has further work.

SUB_SEED endpoints MAY belong to different parent nodes (e.g., an element-scope SUB_SEED whose FROM endpoint is parented by the source-side container node and whose TO endpoint is parented by the target-side container node). The driver SHALL NOT reject such SUB_SEEDs; they represent legitimate cross-parent chain segments within a single per-element computation.

#### Scenario: SUB_SEED is iterated as a seed in subsequent passes
- **WHEN** an iteration emits a SUB_SEED `F → I` and the outer loop re-runs the phase list
- **THEN** in the next pass, `BridgeSourceToTargetPhase` queries every registered `Bridge` with `(F.type, I.type, ctx)`

#### Scenario: A chain closes when a SUB_SEED's input matches a parameter root
- **WHEN** the chain `F → I1 → I2 → … → In → T` is being expanded and the strategy emits a step whose input type equals `F.type` for some intermediate
- **THEN** the phase emits a REALISED edge from `F` to that intermediate without emitting a further SUB_SEED
- **AND** subsequent iterations report no changes; the outer loop terminates

#### Scenario: Cross-parent element-scope SUB_SEED is accepted
- **WHEN** the unified rule emits a SUB_SEED from an element node `eA` (parented by node `cA`) to an element node `eB` (parented by node `cB`) where `cA != cB`
- **THEN** the edge is added to the graph
- **AND** subsequent iterations process this SUB_SEED like any other

### Requirement: Container-Map outer REALISED edge represents an iteration

A REALISED edge whose emitting strategy is a container "map"-shaped strategy (e.g., `OptionalMap`, `ListMap`, `SetMap`) SHALL be:

- A single edge in the graph between the strategy's source-container node and target-container node, carrying the strategy's class FQN in `Edge.strategyClassFqn`.
- Paired with at least one element-scope SEED whose endpoints are parented by this edge's `from` and `to` nodes respectively. The element-scope SEEDs SHALL exist in the graph as separate edges and be expanded by subsequent outer-loop iterations into element-scope REALISED edges (and possibly further SUB_SEEDs).

The container-map edge's `codegen` field SHALL contain a lambda that, on `render(VarNames, IncomingValues)`, throws `UnsupportedOperationException` until the future codegen capability ships. The graph shape — the outer REALISED edge plus the parent-linked element-scope subgraph — is the complete container-expansion contract today.

#### Scenario: Outer ListMap edge co-exists with its element-scope SEED
- **WHEN** an outer REALISED edge `src[xs]:List<Dog> → tgt[]:List<Pet>` is emitted by `ListMap`
- **THEN** the graph also contains an element-scope SEED edge whose FROM is `elem(parent=src[xs]:List<Dog>):Dog` and TO is `elem(parent=tgt[]:List<Pet>):Pet`

#### Scenario: Outer container-map edge codegen throws
- **WHEN** the `EdgeCodegen.render(VarNames, IncomingValues)` of an outer container-map REALISED edge is invoked
- **THEN** an `UnsupportedOperationException` is thrown
- **AND** the message names the future codegen capability

### Requirement: Self-call REALISED edges allowed

When a `Bridge` strategy emits a step that, applied via the unified edge-emission rule, results in a REALISED edge whose codegen would invoke the currently-expanding method (`ctx.currentMethod()`), the phase SHALL emit the edge unconditionally. The phase does NOT detect or filter self-calls. The expansion's correctness is unaffected: at codegen time, the generated method body may contain a recursive call, which is well-formed Java and terminates if the user's directive structure recurses on a structurally smaller value.

#### Scenario: Self-call edge is emitted without filtering
- **WHEN** `MethodCallBridge` (or any other `Bridge`) emits a step whose codegen invokes `ctx.currentMethod()`, and the unified rule applies
- **THEN** the phase emits the REALISED edge for that step
- **AND** the phase does not emit any diagnostic

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

### Requirement: Mapper-level expansion round budget

The expansion driver SHALL track a per-mapper round counter incremented exactly once per iteration of the outer fixed-point loop in `ExpandStage`. The cap SHALL be a constant `MAX_EXPANSION_ROUNDS` defined in `ExpandStage` (initial value `64`). The counter ticks regardless of how many edges or nodes any phase adds in a given round, regardless of any value returned or stored by phases, and regardless of cycle-detection state.

If the round counter exceeds `MAX_EXPANSION_ROUNDS` before convergence, `ExpandStage` SHALL emit exactly one `Diagnostics.error` keyed to the mapper `TypeElement` itself (no directive `AnnotationMirror` argument) carrying a generic message stating that expansion did not converge within the configured number of rounds. The mapper SHALL be scarred and the loop SHALL terminate immediately. No per-directive blame heuristic SHALL be applied at this site; the directive-specific diagnostic surface is reserved for cycle detection.

#### Scenario: Round counter increments once per outer pass
- **WHEN** `ExpandStage.run(ctx)` runs `k` outer iterations before converging
- **THEN** the round counter at termination equals `k`

#### Scenario: V1 mappers do not exhaust the round budget
- **WHEN** `ExpandStage.run(ctx)` runs on any v1 demo mapper
- **THEN** the round counter at termination is below `MAX_EXPANSION_ROUNDS`
- **AND** no round-cap error diagnostic is emitted

#### Scenario: Round-cap fires at the mapper level with a generic message
- **WHEN** a synthetic test harness forces the outer loop past `MAX_EXPANSION_ROUNDS` (e.g., a strategy that emits new edges each round without converging)
- **THEN** exactly one error diagnostic is emitted against the mapper `TypeElement`
- **AND** the diagnostic does not reference any specific directive `AnnotationMirror`
- **AND** the mapper is scarred so subsequent stages skip it

#### Scenario: Round counter is independent of phase-reported signals
- **WHEN** a phase adds many edges in a single invocation but the harness inspects round count
- **THEN** the round counter increments by exactly one for that outer pass
- **AND** counts are not influenced by the size of any per-pass `GraphDelta`
