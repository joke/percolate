## MODIFIED Requirements

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

## ADDED Requirements

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
