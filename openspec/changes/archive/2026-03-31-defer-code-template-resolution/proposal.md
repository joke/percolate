## Why

The `resolveTransformPath` BFS expansion loop calls `resolveCodeTemplate` for every edge added to the type graph, including edges that never appear on the final shortest path. Since `resolveCodeTemplate` recursively invokes `resolveTransformPath` for element constraints (e.g., container inner-type mapping), this produces wasted recursive resolution work for dead-end edges. Deferring code template resolution to only the edges on the final BFS path eliminates this waste and cleanly separates graph expansion (structural) from code template resolution (generative).

## What Changes

- Store raw `TransformProposal` on `TransformEdge` during BFS expansion instead of eagerly resolving `CodeTemplate`
- Move `resolveCodeTemplate` invocation to a post-BFS pass that only processes edges on the final `GraphPath`
- `TransformEdge` carries `TransformProposal` during expansion and resolved `CodeTemplate` after path selection

## Capabilities

### New Capabilities

_(none)_

### Modified Capabilities

- `transform-resolution`: The BFS expansion loop defers code template resolution to a post-path-selection pass instead of resolving eagerly per edge
- `type-transform-strategy`: `TransformEdge` construction changes to carry a `TransformProposal` initially, with `CodeTemplate` resolved lazily after path selection

## Impact

- `ResolveTransformsStage`: BFS loop and `resolveCodeTemplate` restructured
- `TransformEdge`: field changes from eager `CodeTemplate` to deferred resolution via `TransformProposal`
- `GenerateStage`: no changes expected (consumes `GraphPath<TypeNode, TransformEdge>` with `CodeTemplate` as before)
- No API or SPI changes to `TypeTransformStrategy` interface
