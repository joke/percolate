## Why

The current mapping graph (`BuildGraphStage`) conflates property structure with type resolution — `SourcePropertyNode` carries a `ReadAccessor` with type information, and `BuildGraphStage` depends on full property discovery (types, accessors) just to build the graph. This tight coupling makes it impossible to support nested source chains like `person.address.street` without pushing accessor resolution into graph construction. Separating the graph into a symbolic layer (names and structure) and a resolution layer (types and accessors) enables nested chains, multi-parameter methods, and cleaner stage responsibilities.

## What Changes

- **BREAKING**: Replace the typed `PropertyNode`/`MappingEdge` graph in `BuildGraphStage` with a symbolic property graph that contains only property names and structural edges (access chains, mapping connections)
- **BREAKING**: Remove `DiscoverStage` — property name scanning moves into `BuildGraphStage` (lightweight, names-only) and full accessor discovery folds into `ResolveTransformsStage`
- **BREAKING**: `ResolveTransformsStage` takes the symbolic graph as input, resolves each edge (discovers accessors, determines types, bridges type gaps via strategies), and produces a fully resolved transform graph
- **BREAKING**: `ValidateStage` (structural property validation) merges into `ValidateTransformsStage`, which validates both property resolution failures and type transform failures
- Support nested source property chains (e.g., `@Map(source = "customer.address.street", target = "street")`) as first-class graph structure — chains are just sequences of access edges in the symbolic graph
- `BuildGraphStage` performs its own lightweight property name scanning for auto-mapping (names only, no types or accessors)
- `ResolveTransformsStage` operates purely on symbolic property names — it has no knowledge of `@Map` annotations

## Capabilities

### New Capabilities
- `symbolic-property-graph`: Defines the symbolic graph model (node types, edge types, construction rules) that replaces the current typed property graph
- `nested-source-chain`: Defines nested dot-separated source property chain parsing and representation as access edges in the symbolic graph

### Modified Capabilities
- `mapping-graph`: BuildGraphStage produces a symbolic graph instead of a typed property graph; property name scanning replaces full discovery dependency; ValidateStage merges out
- `property-discovery`: DiscoverStage is removed; full discovery folds into ResolveTransformsStage; name-only scanning extracted for BuildGraphStage
- `transform-resolution`: ResolveTransformsStage takes symbolic graph as input, resolves access edges (accessor discovery + type resolution) in addition to type transform edges
- `transform-validation`: ValidateTransformsStage absorbs structural validation (unmapped targets, unknown properties with "did you mean?" suggestions) alongside type gap validation
- `pipeline-stages`: Stage sequence changes — DiscoverStage and ValidateStage removed, remaining stages rewired
- `auto-mapping`: Auto-mapping uses lightweight name scanning instead of full property discovery

## Impact

- **Processor stages**: DiscoverStage removed, ValidateStage removed, BuildGraphStage rewritten, ResolveTransformsStage significantly expanded, ValidateTransformsStage expanded
- **Graph model**: Current `PropertyNode`/`MappingEdge`/`MappingGraph` classes replaced with new symbolic graph model
- **Accessor models**: `ReadAccessor`/`WriteAccessor` hierarchy unchanged but moved from graph nodes into resolution output
- **SPI interfaces**: `SourcePropertyDiscovery`/`TargetPropertyDiscovery` unchanged, consumed by ResolveTransformsStage instead of DiscoverStage
- **Tests**: All stage specs and tests need updating to reflect new graph model and stage responsibilities
