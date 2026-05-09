## MODIFIED Requirements

### Requirement: ExpandStage

The processor SHALL define a stage `ExpandStage` in package `io.github.joke.percolate.processor.stages.expand` that consumes a `MapperGraph` populated with `EdgeKind.SEED` edges and augments it with `EdgeKind.REALISED`, `EdgeKind.MARKER`, and `EdgeKind.SUB_SEED` edges. `ExpandStage` SHALL be `@Inject`-constructed via Lombok `@RequiredArgsConstructor(onConstructor_ = @Inject)`.

`ExpandStage` SHALL execute three sequential `ExpansionPhase`s in declared order: `ResolveSourceChainsPhase`, `ResolveTargetChainsPhase`, `BridgeSourceToTargetPhase`. The phase list SHALL run inside an outer fixed-point loop: after each pass over the phase list, `ExpandStage` SHALL re-run the entire list if any phase reported that it changed the graph during the pass. The loop SHALL terminate when a complete pass over the phase list reports no changes.

After each phase invocation (i.e., after each individual phase, not only at the end of a full pass), `ExpandStage` SHALL run cycle detection over `EdgeKind.SEED + EdgeKind.SUB_SEED` and the per-seed expansion-budget guard. If either guard fires, the mapper is scarred and the loop terminates immediately.

#### Scenario: ExpandStage runs the three phases in declared order on each pass
- **WHEN** `ExpandStage.run(ctx)` is invoked
- **THEN** within each pass, `ResolveSourceChainsPhase.apply(graph)` runs first, then `ResolveTargetChainsPhase.apply(graph)`, then `BridgeSourceToTargetPhase.apply(graph)`

#### Scenario: ExpandStage stops after the first pass that changes nothing
- **WHEN** `ExpandStage.run(ctx)` is invoked on a graph that reaches a fixed point in one pass
- **THEN** the phase list runs exactly once
- **AND** the loop terminates without re-running

#### Scenario: ExpandStage re-runs the phase list when a phase changes the graph
- **WHEN** `ExpandStage.run(ctx)` is invoked on a graph where the first pass emits a SUB_SEED that subsequent iterations resolve
- **THEN** the phase list runs at least twice
- **AND** the loop continues until a complete pass reports no changes

#### Scenario: ExpandStage uses Lombok-generated injection
- **WHEN** the source of `ExpandStage` is inspected
- **THEN** the class carries `@RequiredArgsConstructor(onConstructor_ = @Inject)`
- **AND** the generated constructor accepts the three phase classes (or their list) and dependencies for the guards

### Requirement: ExpansionPhase contract

Every `ExpansionPhase` SHALL implement a single method `apply(MapperGraph)` that mutates the graph (adds new nodes / edges) and returns a `boolean` indicating whether it modified the graph during this invocation. A phase SHALL return `true` if it added at least one node or edge, and `false` otherwise. A phase SHALL NOT remove edges or nodes added by an earlier phase or by `SeedGraph`. A phase MAY be tested in isolation by constructing a `MapperGraph` populated to the state expected at that phase's entry.

`apply` is idempotent in the following sense: invoking a phase a second time on a graph it has already brought to its locally-stable state SHALL return `false` and add no edges or nodes. This is required for the outer fixed-point loop to terminate correctly.

#### Scenario: Phase returns true when it adds an edge
- **WHEN** any `ExpansionPhase.apply(graph)` is invoked and the phase adds at least one new node or edge
- **THEN** the method returns `true`

#### Scenario: Phase returns false on a stable graph
- **WHEN** any `ExpansionPhase.apply(graph)` is invoked on a graph where the phase has nothing further to add
- **THEN** the method returns `false`
- **AND** the graph is unchanged

#### Scenario: Phase preserves existing edges
- **WHEN** a phase runs on a graph containing `n` SEED edges
- **THEN** all `n` SEED edges remain in the graph after the phase completes

### Requirement: BridgeSourceToTargetPhase

`BridgeSourceToTargetPhase` SHALL iterate every `EdgeKind.SEED` edge and every `EdgeKind.SUB_SEED` edge whose `from` node is in source space and whose `to` node is in target space (or, for SUB_SEED, whose `to` is any typed intermediate node previously allocated by this phase).

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

## ADDED Requirements

### Requirement: Bridge edge-emission rule (unified)

The phase SHALL apply the following rule for every `BridgeStep` returned by every `Bridge` query, regardless of whether the step represents a direct match or a chain hop:

Let `F` be the seed's resolved typed source-side counterpart and `T` be the seed's resolved typed target-side counterpart. Given a `BridgeStep(inputType, outputType, weight, codegen)`:

1. The phase SHALL determine `inputNode`:
   - If `step.inputType` equals `F.type`, then `inputNode = F`.
   - Otherwise, the phase SHALL find or allocate a `Node` with `scope = F.scope`, `loc = F.loc`, and `type = step.inputType`. If a node with this `(scope, loc, type)` identity already exists in the graph, that node is reused; otherwise a new node is allocated and added to the graph.

2. The phase SHALL determine `outputNode`:
   - If `step.outputType` equals `T.type`, then `outputNode = T`.
   - Otherwise, the phase SHALL find or allocate a `Node` with `scope = F.scope`, `loc = F.loc`, and `type = step.outputType`, with the same find-or-allocate semantics as for `inputNode`.

3. The phase SHALL emit one `EdgeKind.REALISED` edge from `inputNode` to `outputNode`, carrying `step.weight`, `step.codegen`, and the strategy's class FQN in `Edge.strategyClassFqn`.

4. If `inputNode != F` (i.e. the input was allocated rather than identified with `F`), the phase SHALL emit one `EdgeKind.SUB_SEED` edge from `F` to `inputNode`, carrying weight `Weights.SENTINEL_UNREALISED` and the originating directive's `AnnotationMirror` (inherited from the seed that triggered the query). The SUB_SEED drives a subsequent outer-loop iteration to find a path from `F` to `inputNode`.

The rule preserves the node-identity invariant: nodes are uniquely identified by `(scope, location, type)`, and two emissions converging on the same identity reuse the same node, becoming parallel edges.

#### Scenario: Direct match emits one REALISED edge with no allocation and no SUB_SEED
- **WHEN** a `BridgeStep` is emitted whose `inputType == F.type` and `outputType == T.type`
- **THEN** the phase emits one REALISED edge `F → T`
- **AND** no new node is allocated
- **AND** no SUB_SEED is emitted

#### Scenario: Chain hop allocates an input intermediate and emits SUB_SEED
- **WHEN** a `BridgeStep` is emitted whose `inputType` is not equal to `F.type` and whose `outputType` equals `T.type`
- **THEN** the phase finds or allocates an intermediate node `I` with `(F.scope, F.loc, step.inputType)`
- **AND** the phase emits a REALISED edge `I → T`
- **AND** the phase emits a SUB_SEED edge `F → I`

#### Scenario: Two strategies emitting the same intermediate type collapse on identity
- **WHEN** two `Bridge` strategies each emit a `BridgeStep` with the same `inputType = X` (different from `F.type`) for the same seed
- **THEN** the phase allocates exactly one intermediate node with `(F.scope, F.loc, X)` (the second strategy reuses the first's node)
- **AND** the phase emits two parallel REALISED edges, both with target equal to the same shared intermediate (or its successor, depending on the steps' outputs)

#### Scenario: Output type differs from T.type allocates an output intermediate
- **WHEN** a `BridgeStep` is emitted whose `outputType` is not equal to `T.type`
- **THEN** the phase finds or allocates an intermediate node `O` with `(F.scope, F.loc, step.outputType)`
- **AND** the phase emits a REALISED edge from `inputNode` to `O`
- **AND** subsequent iterations may emit further bridges from `O` toward `T`

### Requirement: SUB_SEED edges drive outer-loop iteration

`EdgeKind.SUB_SEED` edges emitted by `BridgeSourceToTargetPhase` per the unified edge-emission rule SHALL participate in subsequent outer-loop iterations the same way SEED edges do: `BridgeSourceToTargetPhase` (and any other phase that iterates seed-shaped edges) SHALL include SUB_SEED edges in its work-list on every pass.

A SUB_SEED edge is processed identically to a SEED edge during expansion: the phase resolves the FROM and TO endpoints' realised counterparts and queries each registered `Bridge` for the resulting type pair. Newly emitted edges from a SUB_SEED's processing again follow the unified edge-emission rule.

A SUB_SEED is "resolved" once a path of REALISED edges connects its FROM endpoint to its TO endpoint. The expansion driver does not explicitly track resolution; the outer fixed-point terminates when no phase has further work.

#### Scenario: SUB_SEED is iterated as a seed in subsequent passes
- **WHEN** an iteration emits a SUB_SEED `F → I` and the outer loop re-runs the phase list
- **THEN** in the next pass, `BridgeSourceToTargetPhase` queries every registered `Bridge` with `(F.type, I.type, ctx)`

#### Scenario: A chain closes when a SUB_SEED's input matches a parameter root
- **WHEN** the chain `F → I1 → I2 → … → In → T` is being expanded and the strategy emits a step whose input type equals `F.type` for some intermediate
- **THEN** the phase emits a REALISED edge from `F` to that intermediate without emitting a further SUB_SEED
- **AND** subsequent iterations report no changes; the outer loop terminates

### Requirement: Self-call REALISED edges allowed

When a `Bridge` strategy emits a step that, applied via the unified edge-emission rule, results in a REALISED edge whose codegen would invoke the currently-expanding method (`ctx.currentMethod()`), the phase SHALL emit the edge unconditionally. The phase does NOT detect or filter self-calls. The expansion's correctness is unaffected: at codegen time, the generated method body may contain a recursive call, which is well-formed Java and terminates if the user's directive structure recurses on a structurally smaller value.

#### Scenario: Self-call edge is emitted without filtering
- **WHEN** `MethodCallBridge` (or any other `Bridge`) emits a step whose codegen invokes `ctx.currentMethod()`, and the unified rule applies
- **THEN** the phase emits the REALISED edge for that step
- **AND** the phase does not emit any diagnostic
