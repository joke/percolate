## Why

The `MethodCallStrategy` currently selects the first type-compatible method it finds, with no way for the user to specify which method should handle a particular mapping. When a mapper has multiple methods with compatible signatures (e.g., several `String → String` transformations), the user cannot control which one is used for a given property. MapStruct solves this with `qualifiedByName`; percolate needs an equivalent that fits its BFS-based resolution model.

## What Changes

- Add `String using() default ""` attribute to `@Map` annotation, allowing users to specify a method name for a particular property mapping
- Thread the `using` value from `@Map` through `MapDirective` → `MappingEdge` → `ResolutionContext` so `MethodCallStrategy` can filter by method name
- Replace `MethodCallStrategy`'s current `.findFirst()` selection with most-specific-match resolution: rank candidates by parameter type specificity (narrowest assignable type wins), with return type specificity as tiebreaker
- When `using` is set, `MethodCallStrategy` filters candidates to only methods with the matching name; when not set, all compatible methods are considered (current behavior)
- When `using` is set but no method with that name matches after type filtering, emit a compile error — no silent fallback to other strategies
- The `using` constraint applies only to `MethodCallStrategy`; container strategies (Optional, Stream, Collection) compose freely around it, and the `using` value propagates into element-level sub-BFS via `ResolutionContext`

## Capabilities

### New Capabilities

- `using-method-routing`: The `using` attribute on `@Map`, threading through the model, `MethodCallStrategy` name filtering, compile error on unresolved `using`, and composition with container strategies

### Modified Capabilities

- `type-transform-strategy`: `ResolutionContext` gains a `using` field; `MethodCallStrategy` changes from first-match to most-specific-match resolution with optional name filtering

## Impact

- **Annotation API** (`annotations` module): `@Map` gains `String using() default ""` attribute
- **Processor model** (`processor` module): `MapDirective` gains `using` field, `MappingEdge` gains `using` field, `AnalyzeStage` parses `using` from annotation
- **Processor SPI** (`processor` module): `ResolutionContext` gains `using` field
- **Processor strategies** (`processor` module): `MethodCallStrategy` updated with name filtering and most-specific-match ranking
- **Processor validation** (`processor` module): `ValidateTransformsStage` or `AnalyzeStage` emits compile error when `using` method not found
