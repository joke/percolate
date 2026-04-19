## Why

The processor pipeline has accumulated structural asymmetries that make it harder to reason about, harder to extend, and harder to review. `OptimizePathStage` exists only to perform a two-phase code-template commit (`proposalTemplate` → `codeTemplate`) on BFS-winning edges. Two dump stages render the same graph at different observation points. `GenerateStage` reaches outside the graph (peeking at `PropertyNode.accessor`) to render getter-vs-field access, and assembles constructor calls via stage-local logic rather than graph traversal. These asymmetries predate planned work to unify the strategy SPI and flip the fixpoint to demand-driven expansion (slice 2 of this effort).

This slice removes the asymmetries as a pure refactor so the subsequent rewrite lands on a clean, self-describing graph model.

## What Changes

- **Collapse graph dumps.** Retire `DumpValueGraphStage` and `DumpResolvedPathsStage`. Introduce a single `DumpGraphStage` that runs after `ResolvePathStage` and renders each method's graph with winning-path edges bolded. On resolution failure the same dump shows zero bold edges, carrying the same signal the pre-resolve dump does today.
- **Retire `OptimizePathStage`.** Code templates are materialised at construction time for all edges except `LiftEdge`. `LiftEdge` becomes lazy: it captures `(innerInputNode, innerOutputNode, kind)` and computes its template at generation time via on-demand BFS over the parent graph.
- **Uniform node composition model.** Every `ValueNode` gains `compose(Map<ValueEdge, CodeBlock> inputs, ComposeKind kind) → CodeBlock`. 1-input nodes (`PropertyNode`, `TypedValueNode`, `TargetSlotNode`) forward trivially; `SourceParamNode` returns the parameter reference. Introduces `ComposeKind { EXPRESSION, STATEMENT_LIST }`; only `EXPRESSION` is used this slice, `STATEMENT_LIST` is scaffolding for slice 2.
- **Move accessor rendering onto `PropertyReadEdge`.** The edge carries its own code template. `GetterDiscovery` constructs it with `"$L.getFoo()"`, `FieldDiscovery.Source` with `"$L.foo"`. Removes the last asymmetry where `GenerateStage` has to peek at node state to render an edge.
- **`GenerateStage` becomes a pure recursive traversal.** No `instanceof` checks on node or edge types. For each `ResolvedAssignment`, chain `edge.apply(incomingExpression)` along the path; compose the method body via a positional-argument pass through the target constructor (still discovered via `ConstructorDiscovery` — that SPI dissolves in slice 2, not here).
- **No BREAKING changes** to public annotations (`@Mapper`, `@Map`), the SPI surface (`SourcePropertyDiscovery`, `TargetPropertyDiscovery`, `TypeTransformStrategy`, `ConstructorDiscovery`), or generated mapper output. This is strictly internal refactoring.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `pipeline-stages`: stage count drops from nine to seven; `OptimizePathStage` removed; two dump stages consolidated into one.
- `debug-graph-export`: single dump artefact per method after resolution, with winning-edge highlighting; pre-resolve dump no longer produced.
- `path-optimization`: capability retired; its remaining functional concern (lift-template composition) moves into `value-graph` as lazy edge behaviour.
- `value-graph`: `ValueNode` gains `compose(...)` contract; `PropertyReadEdge` gains a code template; `LiftEdge` becomes lazy (no pre-committed inner template).
- `code-generation`: `GenerateStage` becomes a pure recursive traversal with zero type-based branching on nodes or edges.

## Impact

- **Affected stages**: `DumpValueGraphStage` (removed), `DumpResolvedPathsStage` (removed, replaced by new `DumpGraphStage`), `OptimizePathStage` (removed), `BuildValueGraphStage` (constructs edges with templates directly), `GenerateStage` (rewritten as pure traversal).
- **Affected graph model**: `ValueNode` interface extended with `compose(...)`; `PropertyReadEdge` carries a `CodeTemplate`; `LiftEdge` no longer holds a pre-resolved `CodeTemplate`; new `ComposeKind` enum.
- **Affected SPIs**: none. `SourcePropertyDiscovery` / `TargetPropertyDiscovery` / `TypeTransformStrategy` / `ConstructorDiscovery` contracts unchanged.
- **Affected APIs**: none. `@Mapper` / `@Map` unchanged. Processor options (`debugGraphs`, `debugGraphsFormat`) unchanged.
- **Affected tests**: golden-output fixtures pinned in commit `4e0c891` must stay byte-identical. Spock tests covering `OptimizePathStage` get consolidated into tests for `BuildValueGraphStage` (edge template construction) and `LiftEdge` (lazy generation).
- **Dependencies**: unchanged (JGraphT 1.5.2, JavaPoet 0.12.0, Dagger 2.59.1, Lombok).
- **Risk**: low. All changes sit behind internal APIs. Behaviour-preserving by construction; the only observable effect is the dump-file consolidation, which is a debug artefact.
