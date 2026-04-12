## 1. Annotation API — `using` Attribute

- [x] 1.1 Add `String using() default ""` element to `@Map` annotation

## 2. Processor Model — `using` Threading

- [x] 2.1 Add `String using` field to `MapDirective`
- [x] 2.2 Update `AnalyzeStage.parseDirective` to read `map.using()` into `MapDirective`
- [x] 2.3 Add `String using` field to `MappingEdge`
- [x] 2.4 Update `BuildGraphStage.processDirectives` to pass `using` from `MapDirective` to `MappingEdge`
- [x] 2.5 Update `BuildGraphStage.addSourceChain` to accept and propagate `using` to `MappingEdge`
- [x] 2.6 Update `BuildGraphStage.autoMap` to create `MappingEdge` with empty `using`
- [x] 2.7 Add `String using` field to `ResolutionContext`
- [x] 2.8 Update `ResolveTransformsStage.resolveMethod` to read `using` from `MappingEdge` and create per-mapping `ResolutionContext`

## 3. MethodCallStrategy — Name Filtering and Most-Specific-Match

- [x] 3.1 Add name filtering: when `ctx.getUsing()` is non-empty, filter candidates to methods matching that name
- [x] 3.2 Replace `.findFirst()` with most-specific-match: rank candidates by parameter type specificity (narrowest `isAssignable` wins)
- [x] 3.3 Add return type specificity as tiebreaker for equal param specificity
- [x] 3.4 Detect ambiguous candidates (incomparable param types) and return empty or attach diagnostic metadata

## 4. Validation

- [x] 4.1 Update error messages for unresolved mappings to include `using` method name when set
- [x] 4.2 Add compile error for ambiguous method specificity listing the conflicting candidates

## 5. Unit Tests — Model and Threading

- [x] 5.1 Write Spock tests for `MapDirective` with and without `using`
- [x] 5.2 Write Spock tests for `AnalyzeStage` parsing `using` from `@Map` annotation
- [x] 5.3 Write Spock tests for `BuildGraphStage` propagating `using` to `MappingEdge` (directive-driven and auto-mapped)
- [x] 5.4 Write Spock tests for `ResolveTransformsStage` creating per-mapping `ResolutionContext` with `using`

## 6. Unit Tests — MethodCallStrategy

- [x] 6.1 Write Spock tests for name filtering: `using` set filters to named method only
- [x] 6.2 Write Spock tests for name filtering: `using` not set considers all compatible methods
- [x] 6.3 Write Spock tests for most-specific-match: narrowest param type wins
- [x] 6.4 Write Spock tests for return type tiebreaker
- [x] 6.5 Write Spock tests for single candidate (no ranking needed)
- [x] 6.6 Write Spock tests for no candidates returning `Optional.empty()`
- [x] 6.7 Write Spock tests for ambiguous candidates (incomparable param types)

## 7. Integration Tests

- [x] 7.1 Write compile-testing integration test: `using` routes to specific method for same-type-pair mappings
- [x] 7.2 Write compile-testing integration test: `using` composes with `OptionalMapStrategy` (element-level routing)
- [x] 7.3 Write compile-testing integration test: `using` composes with `StreamMapStrategy` and collection strategies
- [x] 7.4 Write compile-testing integration test: `using` method not found produces compile error
- [x] 7.5 Write compile-testing integration test: `using` method wrong signature produces compile error
- [x] 7.6 Write compile-testing integration test: most-specific-match selects correct overload without `using`
- [x] 7.7 Write compile-testing integration test: ambiguous overloads produce compile error
