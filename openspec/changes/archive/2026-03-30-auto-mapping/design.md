## Context

Currently, `BuildGraphStage` creates property mapping edges only from explicit `@Map` directives. Every property must be manually wired, even when source and target share the same name. The graph infrastructure (JGraphT, `PropertyNode`, `MappingEdge`) already supports all the primitives needed — we just need to add implicit edge creation after explicit edges are placed.

## Goals / Non-Goals

**Goals:**
- Auto-map same-name properties without requiring `@Map` annotations
- Explicit `@Map` directives take priority over auto-mapped edges
- Seamless integration into existing pipeline — no new stages or model types

**Non-Goals:**
- Opt-in/opt-out mechanism (auto-mapping is always on)
- Fuzzy or type-based matching (only exact name match)
- Ignoring/excluding specific properties from auto-mapping

## Decisions

### Decision: Add implicit edges in BuildGraphStage after explicit edges

After processing all `@Map` directives, iterate target nodes. For each target with `inDegreeOf(targetNode) == 0`, check if a source node with the same name exists. If so, add a `MappingEdge`.

**Why**: This is the simplest insertion point. `BuildGraphStage` already has both `sourceNodes` and `targetNodes` maps keyed by name, making the lookup trivial. No new stages, no new model types, no changes to downstream stages.

**Alternative considered**: A separate `AutoMapStage` between `BuildGraph` and `Validate`. Rejected because it would add pipeline complexity for what is essentially 5 lines of logic, and it would require exposing the graph's internal node maps to a second stage.

### Decision: No distinction between implicit and explicit edges

Auto-mapped edges use the same `MappingEdge` type as directive-created edges. Downstream stages (`ValidateStage`, `ResolveTransformsStage`, `GenerateStage`) see no difference.

**Why**: The user explicitly requested this. An edge is an edge — how it was created is irrelevant to validation, resolution, and code generation. This keeps the model simple and avoids conditional logic throughout the pipeline.

### Decision: Use inDegree check for priority

Explicit `@Map` directives are processed first. Auto-mapping only fills in targets with no incoming edges (`inDegreeOf == 0`). This gives explicit mappings natural priority without any special-casing.

**Why**: If a user writes `@Map(source = "fullName", target = "name")`, the target `name` already has an incoming edge, so auto-mapping skips it. The source property `name` (if it exists) simply has no outgoing edge, which is not an error.

## Risks / Trade-offs

- **[Behavior change for existing mappers]** → Mappers that previously failed validation (unmapped same-name targets) will now silently succeed. This is the desired behavior but could surprise users who relied on the error as a safety net. Mitigation: this is opt-out by design; a future `@IgnoreMapping` annotation could provide fine-grained control if needed.
- **[Test adjustments]** → Existing tests that assert on unmapped target errors for same-name properties will need updating. Mitigation: straightforward test changes, not architectural risk.
