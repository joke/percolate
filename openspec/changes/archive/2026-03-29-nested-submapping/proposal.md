## Why

Percolate currently only supports flat property-to-property mappings. When a source property type differs from the target property type (e.g., `Address` → `AddressDTO`), there is no way to delegate to another mapping method on the same mapper. Developers must manually convert nested objects, defeating the purpose of the mapper. Submapping via sibling methods is a fundamental capability for any practical mapping framework.

## What Changes

- Introduce a **ResolveTransforms** stage that walks validated mapping edges, checks type assignability, and resolves non-assignable pairs to sibling mapping methods on the same mapper
- Introduce a **ValidateTransforms** stage that verifies all resolved transforms are fulfillable (i.e., a sibling method exists for every submapping edge), producing clear error diagnostics when not
- Introduce a **transform chain model** per mapping edge — for this change, chains are single-step (DIRECT or SUBMAP), but the structure supports future multi-step transforms (boxing, collections, optionals)
- Update **GenerateStage** to emit delegation calls (`this.mapAddress(source.getBillingAddress())`) for SUBMAP edges
- Restructure the mapping graph to be **per-method** rather than a single shared graph, so each method's property mappings are isolated and submapping references are explicit

## Capabilities

### New Capabilities
- `transform-resolution`: Resolving type-gap transforms between source and target properties, including sibling method lookup and transform chain construction
- `transform-validation`: Validating that all resolved transforms are fulfillable, with error diagnostics for unresolvable type mismatches

### Modified Capabilities
- `mapping-graph`: Per-method graph structure, SUBMAP edge type alongside DIRECT
- `pipeline-stages`: New ResolveTransforms and ValidateTransforms stages inserted between Validate and Generate
- `code-generation`: GenerateStage emits sibling method delegation for SUBMAP edges

## Impact

- **Processor pipeline**: Two new stages added. Pipeline order becomes: Analyze → Discover → BuildGraph → Validate → ResolveTransforms → ValidateTransforms → Generate
- **Graph model**: MappingEdge gains SUBMAP type with method reference. Graph becomes per-method
- **Code generation**: GenerateStage must handle SUBMAP edges by emitting `this.methodName(readExpr)` calls
- **Affected teams**: Processor maintainers
- **No breaking changes**: Existing flat mappers continue to work unchanged — all edges resolve as DIRECT
