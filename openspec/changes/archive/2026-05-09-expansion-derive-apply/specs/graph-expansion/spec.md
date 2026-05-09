## MODIFIED Requirements

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

## ADDED Requirements

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

## REMOVED Requirements

### Requirement: Per-seed expansion budget of 100

**Reason**: Replaced by the mapper-level round budget. The original budget counted graph additions reported via the `boolean` return of `ExpansionPhase.apply`; that signal is removed by this change, and an additions-counted budget is structurally unable to defend against the bug class where a phase mutates the graph but reports no change.

**Migration**: See the `Mapper-level expansion round budget` requirement above. The new budget is mapper-level rather than per-seed and counts outer-loop rounds rather than additions; the corresponding diagnostic is mapper-level and generic (cycle detection retains its directive-specific diagnostic for the diagnosable case).
