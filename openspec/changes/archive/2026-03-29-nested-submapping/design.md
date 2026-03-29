## Context

Percolate's processor pipeline currently runs five stages: Analyze → Discover → BuildGraph → Validate → Generate. All mappings are flat (DIRECT): source property connects to target property by name, types are not checked. The mapping graph is a single `DefaultDirectedGraph<PropertyNode, MappingEdge>` containing nodes from all methods.

For nested submapping, we need to detect type mismatches between source and target properties, resolve them by finding sibling methods on the same mapper, and generate delegation calls. This requires both a structural change to the graph model and two new pipeline stages.

## Goals / Non-Goals

**Goals:**
- Support submapping via sibling methods defined on the same `@Mapper` interface
- Introduce a transform chain model that can be extended in future changes (boxing, collections, optionals)
- Provide clear error messages when a type mismatch has no resolvable sibling method
- Keep existing flat mapping behavior unchanged

**Non-Goals:**
- Type conversions (boxing/unboxing, widening/narrowing)
- Collection transforms (`List<A>` → `List<B>`)
- Optional wrapping/unwrapping
- External mapper references (only sibling methods on the same interface)
- Cycle detection between methods (deferred — true cycles are impossible with distinct type pairs)

## Decisions

### Decision: Per-method property graphs

**Choice:** Each `DiscoveredMethod` gets its own `DefaultDirectedGraph<PropertyNode, MappingEdge>` instead of one shared graph.

**Why:** The current shared graph doesn't enforce method boundaries — nodes from different methods coexist in the same graph. With submappings, edges need to reference specific sibling methods, and validation/generation operate per-method. Per-method graphs make ownership explicit.

**Alternative considered:** Keep shared graph, tag nodes with method ownership. Rejected because it adds complexity without benefit and makes per-method iteration awkward.

**Structure:**
```
MappingGraph {
    TypeElement mapperType
    List<DiscoveredMethod> methods
    Map<DiscoveredMethod, DefaultDirectedGraph<PropertyNode, MappingEdge>> methodGraphs
}
```

The existing `graph` field is replaced by `methodGraphs`.

### Decision: Transform chain as a linked list of TransformNode

**Choice:** Each mapping edge in the generation model carries a `List<TransformNode>`, where each node has an input type, output type, and operation. For this change, chains are always single-element (DIRECT or SUBMAP).

**Why:** A flat enum on MappingEdge would work today but would need restructuring when collection/optional transforms arrive. The chain model is trivially simple for single-step transforms while being ready for composition.

**Structure:**
```
TransformNode {
    TypeMirror inputType
    TypeMirror outputType
    TransformOperation operation  // DIRECT, SUBMAP
}

SubMapOperation extends TransformOperation {
    DiscoveredMethod targetMethod
}
```

**Alternative considered:** Just add a `SUBMAP` variant to `MappingEdge.Type` with a method reference. Simpler today but would require a redesign when chains arrive.

### Decision: Two new stages (ResolveTransforms, ValidateTransforms)

**Choice:** Insert `ResolveTransforms` and `ValidateTransforms` between Validate and Generate.

**Why:** Separating name/coverage validation (existing Validate) from type resolution keeps error messages layered — structural mistakes surface first, type issues second. Separating resolution from its validation follows the existing pattern (BuildGraph builds, Validate validates).

**Pipeline becomes:**
```
Analyze → Discover → BuildGraph → Validate → ResolveTransforms → ValidateTransforms → Generate
```

**Alternative considered:** Fold transform resolution into BuildGraphStage. Rejected because BuildGraphStage's responsibility is name resolution and graph construction, not type analysis.

### Decision: Type assignability check via javax.lang.model.util.Types

**Choice:** Use `Types.isAssignable(sourceType, targetType)` to determine if a mapping is DIRECT or needs a transform.

**Why:** This is the standard annotation processing API for type compatibility. It handles subtypes, erasure, and primitives correctly. Available via Dagger injection from `ProcessorModule`.

### Decision: Sibling method lookup by type signature

**Choice:** When types are not assignable, scan all `DiscoveredMethod` entries for one where `sourceType` is assignable from the edge's source type and `targetType` is assignable to the edge's target type.

**Why:** Exact type matching would be too strict (wouldn't work with subtypes). Assignability-based matching follows the same principle as the DIRECT check.

**Error when no match:** If no sibling method bridges the type gap, `ValidateTransforms` emits an error:
```
Cannot map 'billingAddress' (com.example.Address) → 'address' (com.example.AddressDTO)
in method 'map' of OrderMapper: no mapping method found for Address → AddressDTO
```

### Decision: Generation model is separate from validation graph

**Choice:** `ResolveTransforms` produces a new model (`ResolvedModel` or similar) distinct from `MappingGraph`. GenerateStage consumes this new model instead of `MappingGraph`.

**Why:** The validation graph represents structural correctness (names, coverage). The generation model represents executable transforms (typed operations). These are different concerns and will diverge further as transform types grow.

```
MappingGraph (validation)          ResolvedModel (generation)
├── per-method property graphs     ├── per-method resolved mappings
├── DIRECT edges only              ├── transform chains per edge
└── names + coverage               └── typed operations + method refs
```

## Risks / Trade-offs

**[Risk] Per-method graph restructuring breaks existing tests** → Existing BuildGraphStage and ValidateStage tests will need updates. Mitigated by keeping the per-method graph API similar (still JGraphT directed graphs, same node/edge types).

**[Risk] Transform chain is over-engineering for single-step transforms** → Accepted trade-off. The chain model adds minimal complexity for one-step chains (just a single-element list) while avoiding a redesign when multi-step transforms arrive.

**[Trade-off] Two new stages add pipeline length** → Each stage is small and focused. The pipeline is already sequential with early-exit on failure, so the additional stages have negligible performance impact during annotation processing.
