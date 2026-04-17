## Why

The current pipeline splits a single conceptual problem — "find a way to move a value from a source property into a target slot" — across four poorly-separated representations: a symbolic property graph (`MappingGraph` with `Object`-typed nodes), an accessor chain built imperatively in `ResolveTransformsStage#buildAccessorChain`, a per-mapping JGraphT type graph built only for transform BFS, and runtime mutation on `TransformEdge.codeTemplate` (deferred template resolution lives where execution happens, not where decisions are made). This split forces validation logic to be smeared across `AnalyzeStage`, `ResolveTransformsStage`, and `ValidateTransformsStage`, and it makes it structurally impossible to express "lift a transform under `null`/`Optional`/`Stream`/`Collection`" as a graph rewrite — which is the prerequisite for adding JSpecify nullability support without hardcoding null-handling into every `TypeTransformStrategy`.

This refactor is a no-feature, structure-only change that lands the seams those follow-on features need.

## What Changes

- **BREAKING** (internal SPI only): replace the untyped `MappingGraph` (`DefaultDirectedGraph<Object, Object>`) and the per-mapping ad-hoc transform graph with a single `ValueGraph` whose nodes and edges are sealed types.
- Introduce a sealed `ValueEdge` hierarchy: `PropertyReadEdge`, `TypeTransformEdge`, `NullWidenEdge`, and a `LiftEdge` carrying a `LiftKind` and an inner sub-path. Lifting is expressible as a graph rewrite, not strategy-internal logic.
- Introduce a sealed matching-layer model — `MappingAssignment(sourcePath, targetName, options, using, AssignmentOrigin)`, `MethodMatching`, `MatchedModel` — that replaces the current name-only symbolic graph used between annotation parsing and graph construction. Matching is now records-of-decisions, not edges-in-a-graph.
- Restructure the pipeline into eight stages with sharper responsibilities: `AnalyzeStage → MatchMappingsStage → ValidateMatchingStage → BuildValueGraphStage → ResolvePathStage → OptimizePathStage → ValidateResolutionStage → GenerateStage`. The current single `ValidateTransformsStage` splits along the matching/resolution boundary; the current single resolve stage splits into graph build, path search, and optimization.
- Add `OptimizePathStage` between resolve and generate. In this change it is a thin shell that owns code-template materialization, eliminating the mutation wart on `TransformEdge.codeTemplate` (templates are computed once per resolved path, not lazily during emit). The shell exists so subsequent changes can plug in peephole rewrites (null-lift fusion, etc.) without re-shaping the pipeline.
- Retire `ElementConstraint` and the `templateComposer: Function<CodeTemplate, CodeTemplate>` escape hatch on `TransformProposal`. Strategies propose plain edges; lifting and composition become graph operations owned by `BuildValueGraphStage` and `OptimizePathStage`.
- Remove the `mapping-graph` capability entirely. Its scenarios are absorbed into `value-graph` and `matching-model`.
- No change to public annotation surface (`@Mapper`, `@Map`, `@MapList`, `@MapOpt`), no change to generated mapper output for existing test fixtures, no change to `TypeTransformStrategy`/`SourcePropertyDiscovery`/`TargetPropertyDiscovery` ServiceLoader contracts other than the proposal-construction simplifications above.

## Capabilities

### New Capabilities

- `matching-model`: sealed records (`MappingAssignment`, `MethodMatching`, `MatchedModel`, `AssignmentOrigin`) that carry the output of `AnalyzeStage` + `MatchMappingsStage` — i.e. "for each method, here are the source-path → target-name decisions and where each one came from (explicit `@Map`, auto-mapping, `using=` routing)". Replaces the Object-typed symbolic graph as the matching-layer representation.
- `value-graph`: the unified per-method JGraphT graph with sealed `ValueNode`s and the sealed `ValueEdge` hierarchy described above. Defines node identity, edge typing, and the invariants that allow a single shortest-path search to materialize the value transformation for one mapping.
- `path-optimization`: defines `OptimizePathStage` and its contract — input is a list of resolved `GraphPath<ValueNode, ValueEdge>` per mapping, output is the same list with code templates materialized and (in future changes) peephole rewrites applied. In this change the only optimization is template materialization; the capability exists so the seam is specified.

### Modified Capabilities

- `pipeline-stages`: stage list, ordering, and Dagger wiring updated to the new eight-stage shape; `BuildGraphStage` → `BuildValueGraphStage`, `ResolveTransformsStage` → `ResolvePathStage`, `ValidateTransformsStage` splits into `ValidateMatchingStage` + `ValidateResolutionStage`, new `MatchMappingsStage` and `OptimizePathStage` slots in. Debug dump stages re-pointed at the new graph type.
- `transform-resolution`: scope narrows to "given a `ValueGraph` for one mapping, produce a `GraphPath<ValueNode, ValueEdge>` or a resolution failure". Accessor-chain construction moves out (now part of graph build); template materialization moves out (now part of optimization). The `TransformResolution` record reshapes accordingly.
- `type-transform-strategy`: `TransformProposal` loses `elementConstraint` and `templateComposer`; strategies return plain edges. Container/optional strategies that previously composed templates now contribute `LiftEdge`s the graph can wire up.
- `transform-validation`: split along the matching/resolution boundary. Matching-level validation (unknown source path, duplicate target, conflicting `@Map` directives) belongs to `ValidateMatchingStage`; resolution-level validation (no path, ambiguous candidates, type mismatch) belongs to `ValidateResolutionStage`.
- `symbolic-property-graph`: superseded — its scenarios about name-only nodes and shared-prefix deduplication move into `matching-model` (the matching layer no longer needs a graph) and `value-graph` (the resolution layer's nodes carry types, not just names). The capability folder is removed.
- `mapping-graph`: removed entirely — see above.

## Impact

- **Code:** every stage class under `processor/src/main/java/io/github/joke/percolate/processor/stage/` is touched; `processor/src/main/java/io/github/joke/percolate/processor/graph/` gains the sealed `ValueNode`/`ValueEdge` hierarchy and loses `TransformEdge.resolveTemplate()` mutation; `processor/src/main/java/io/github/joke/percolate/processor/transform/` records (`TransformProposal`, `TransformResolution`, `ResolvedMapping`) reshape; `processor/src/main/java/io/github/joke/percolate/processor/spi/` strategies (`OptionalMapStrategy`, `OptionalWrapStrategy`, container strategies) drop their composer/constraint usage in favor of `LiftEdge`s.
- **SPI:** `TypeTransformStrategy` implementations outside this repo would break if any exist — none are known. `ResolutionContext` is unchanged.
- **Public API:** none. Annotation surface, generated mapper output, and processor options are unchanged.
- **Tests:** Spock + Google Compile Testing suites for stages are restructured along the new stage boundaries; golden output of generated mappers for existing fixtures must remain byte-identical (this is the regression contract for the refactor).
- **Dependencies:** none added or removed. Continues to use JGraphT 1.5.2, JavaPoet 0.12.0, Dagger 2.59.1, Lombok.
- **Follow-on:** unblocks the planned `jspecify-nullability` change, which adds nullness-tagged `ValueNode`s, `NullWidenEdge` semantics, and `LiftEdge(NULL_CHECK)` wiring on top of the seams introduced here.
