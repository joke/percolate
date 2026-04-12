# Using Method Routing Spec

## Purpose

TBD

## Requirements

### Requirement: @Map annotation accepts a using attribute
The `@Map` annotation SHALL have an additional element `String using() default ""`. When no `using` is specified, the default SHALL be an empty string, meaning no method name constraint.

#### Scenario: Map with no using
- **WHEN** a developer writes `@Map(source = "name", target = "name")`
- **THEN** `map.using()` SHALL return an empty string

#### Scenario: Map with using
- **WHEN** a developer writes `@Map(source = "date", target = "dateStr", using = "formatDate")`
- **THEN** `map.using()` SHALL return `"formatDate"`

### Requirement: MapDirective carries parsed using value
`MapDirective` SHALL carry a `String using` field in addition to `source`, `target`, and `options`. The `AnalyzeStage` SHALL parse `using` from each `@Map` annotation's `using()` element and populate the field. When no `using` is specified, the field SHALL be an empty string.

#### Scenario: Directive parsed from Map with using
- **WHEN** `@Map(source = "date", target = "dateStr", using = "formatDate")` is parsed
- **THEN** the resulting `MapDirective` SHALL have `using` equal to `"formatDate"`

#### Scenario: Directive parsed from Map without using
- **WHEN** `@Map(source = "name", target = "name")` is parsed
- **THEN** the resulting `MapDirective` SHALL have `using` equal to `""`

### Requirement: MappingEdge carries using from its directive
`MappingEdge` SHALL carry a `String using` field. The `BuildGraphStage` SHALL propagate `using` from the `MapDirective` to the `MappingEdge` when creating edges in `processDirectives`. Auto-mapped edges (created by `autoMap`) SHALL have empty `using`.

#### Scenario: Directive-driven edge carries using
- **WHEN** `BuildGraphStage` creates a `MappingEdge` from a `MapDirective` with `using = "formatDate"`
- **THEN** the `MappingEdge` SHALL carry `using` equal to `"formatDate"`

#### Scenario: Auto-mapped edge has empty using
- **WHEN** `BuildGraphStage` creates a `MappingEdge` via auto-mapping
- **THEN** the `MappingEdge` SHALL carry `using` equal to `""`

### Requirement: MethodCallStrategy filters by using when set
When `ResolutionContext` has a non-empty `using` value, `MethodCallStrategy` SHALL filter candidate methods to only those whose `getSimpleName()` matches the `using` value. When `using` is empty, all type-compatible methods SHALL be considered as candidates (current behavior).

#### Scenario: using filters to named method
- **WHEN** `using = "formatDate"` and the mapper has methods `formatDate(LocalDate) → String` and `convertDate(LocalDate) → String`
- **THEN** `MethodCallStrategy` SHALL only consider `formatDate` as a candidate

#### Scenario: using not set considers all methods
- **WHEN** `using` is empty and the mapper has methods `formatDate(LocalDate) → String` and `convertDate(LocalDate) → String`
- **THEN** `MethodCallStrategy` SHALL consider both methods as candidates

#### Scenario: using method not found returns empty
- **WHEN** `using = "formatDate"` and no method named `formatDate` exists on the mapper
- **THEN** `MethodCallStrategy` SHALL return `Optional.empty()`

#### Scenario: using method exists but types incompatible returns empty
- **WHEN** `using = "formatDate"` and `formatDate(Instant) → String` exists but source type is `LocalDate` (not assignable to `Instant`)
- **THEN** `MethodCallStrategy` SHALL return `Optional.empty()`

### Requirement: MethodCallStrategy selects most-specific-match
When multiple candidate methods match (after optional `using` name filtering), `MethodCallStrategy` SHALL select the most specific method rather than the first found. Specificity SHALL be determined by:

1. **Parameter type specificity** (primary): Among candidates where `isAssignable(sourceType, paramType)`, prefer the candidate whose `paramType` is the narrowest (most specific subtype). Candidate A is more specific than B if `isAssignable(A.paramType, B.paramType)`.
2. **Return type specificity** (tiebreaker): Among candidates with equal parameter specificity, prefer the candidate whose `returnType` is the narrowest. Candidate A is more specific than B if `isAssignable(A.returnType, B.returnType)`.

#### Scenario: Most specific parameter type wins
- **WHEN** candidates are `format(Object) → String`, `format(Temporal) → String`, and `format(LocalDate) → String`, and source type is `LocalDate`
- **THEN** `MethodCallStrategy` SHALL select `format(LocalDate)` as the most specific

#### Scenario: Return type breaks tie
- **WHEN** candidates are `convert(LocalDate) → CharSequence` and `convert(LocalDate) → String`, and source type is `LocalDate` and target type is `String`
- **THEN** `MethodCallStrategy` SHALL select `convert(LocalDate) → String` as it has the more specific return type

#### Scenario: Single candidate selected without ranking
- **WHEN** only one candidate method matches (after name and type filtering)
- **THEN** `MethodCallStrategy` SHALL select that method without specificity comparison

#### Scenario: No candidates after filtering
- **WHEN** no methods remain after name filtering and type compatibility checks
- **THEN** `MethodCallStrategy` SHALL return `Optional.empty()`

### Requirement: using composes with container strategies
The `using` value SHALL propagate through `ResolutionContext` into element-level sub-BFS resolution. Container strategies (`OptionalMapStrategy`, `StreamMapStrategy`, etc.) SHALL ignore the `using` field. When these strategies trigger element constraint resolution, the sub-BFS SHALL use the same `ResolutionContext` (including `using`), allowing `MethodCallStrategy` to apply the name filter at the element level.

#### Scenario: Optional composition with using
- **WHEN** source type is `Optional<LocalDate>`, target type is `Optional<String>`, and `using = "formatDate"` with method `String formatDate(LocalDate d)`
- **THEN** the BFS resolver SHALL find a path via `OptionalMapStrategy` composing with `MethodCallStrategy` using `formatDate` for the `LocalDate → String` element transform

#### Scenario: Stream composition with using
- **WHEN** source type is `Stream<LocalDate>`, target type is `Stream<String>`, and `using = "formatDate"` with method `String formatDate(LocalDate d)`
- **THEN** the BFS resolver SHALL find a path via `StreamMapStrategy` composing with `MethodCallStrategy` using `formatDate` for the `LocalDate → String` element transform

#### Scenario: List-to-List composition with using
- **WHEN** source type is `List<LocalDate>`, target type is `List<String>`, and `using = "formatDate"` with method `String formatDate(LocalDate d)`
- **THEN** the BFS resolver SHALL find a path via `StreamFromCollectionStrategy`, `StreamMapStrategy` composing with `MethodCallStrategy` using `formatDate`, and `CollectToListStrategy`

### Requirement: Compile error when using method is unresolvable
When `using` is set on a mapping but the overall transform resolution fails (no path found from source type to target type), the validation error message SHALL include the `using` method name to help the user diagnose the issue.

#### Scenario: using method does not exist
- **WHEN** `@Map(source = "date", target = "dateStr", using = "formatDate")` is specified but no method named `formatDate` exists on the mapper
- **THEN** the processor SHALL emit a compile error that mentions `using = "formatDate"`

#### Scenario: using method exists but wrong signature
- **WHEN** `@Map(source = "date", target = "dateStr", using = "formatDate")` is specified and `formatDate(Instant) → Integer` exists but source type is `LocalDate` and target type is `String`
- **THEN** the processor SHALL emit a compile error that mentions `using = "formatDate"` and the type mismatch

### Requirement: Ambiguous specificity produces compile error
When multiple candidate methods have incomparable parameter types (neither is assignable to the other) and no tiebreaker resolves the ambiguity, the processor SHALL emit a compile error listing the ambiguous candidates.

#### Scenario: Two candidates with incomparable param types
- **WHEN** candidates are `process(Serializable) → String` and `process(Comparable) → String`, source type implements both interfaces, and neither `Serializable` nor `Comparable` is assignable to the other
- **THEN** the processor SHALL emit a compile error indicating ambiguous method candidates
