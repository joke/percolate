## ADDED Requirements

### Requirement: ValidateResolutionStage detects unresolvable type gaps

The `ValidateResolutionStage` (one of the two stages that replace `ValidateTransformsStage`) SHALL check that every `ResolvedAssignment` in every `MethodMatching` has a non-null `path`. If any `ResolvedAssignment` has `path == null`, the stage SHALL produce an error diagnostic whose message includes the source-path string (joined with `.`), the resolved source `TypeMirror`, the target slot name, the target `TypeMirror`, the method context, and the mapper type. The error message SHALL be goal-directed, indicating what the developer needs to provide (e.g., "provide a mapping method for Person → PersonDTO").

#### Scenario: All resolutions complete

- **WHEN** every `ResolvedAssignment` has `isResolved() == true`
- **THEN** `ValidateResolutionStage` SHALL return success with the input passed through

#### Scenario: Unresolved type gap produces error

- **WHEN** a `ResolvedAssignment` with `sourcePath = ["data"]`, target `"data"`, types `Foo → Bar`, in method `map` of `OrderMapper` has `path == null`
- **THEN** `ValidateResolutionStage` SHALL produce a diagnostic containing the source path, source type, target name, target type, method name, and mapper type

#### Scenario: Multiple unresolved assignments produce multiple errors

- **WHEN** two `ResolvedAssignment`s in the same method are unresolved
- **THEN** `ValidateResolutionStage` SHALL produce one diagnostic per unresolved assignment

#### Scenario: Partially expanded but unresolved container mapping

- **WHEN** source `List<Person>` maps to target `Set<PersonDTO>` but no `Person → PersonDTO` method exists — so the `ValueGraph` has `List<Person>` and `Set<PersonDTO>` nodes but no path between them
- **THEN** `ValidateResolutionStage` SHALL produce a diagnostic indicating a mapping method for `Person → PersonDTO` is needed

### Requirement: ValidateResolutionStage detects unresolved property access

`ValidateResolutionStage` SHALL check for source-path segments that could not be resolved during `BuildValueGraphStage` (property not found on the enclosing resolved type). For each unresolved segment, the stage SHALL produce an error diagnostic whose message includes the segment name, the segment index, the full source-path string, the type that was searched, available property names on that type, and a "Did you mean:" suggestion if a fuzzy match exists.

Unresolved-segment information SHALL be carried on the `MethodMatching` (or the containing `ValueGraph`) by `BuildValueGraphStage` — this validation only *reports* what the graph-build stage already recorded.

#### Scenario: Unknown property in chain with close match

- **WHEN** the source path `["customer", "adress", "street"]` has an unresolved segment `"adress"` at index 1 on type `Customer` which has properties `["address", "name", "email"]`
- **THEN** `ValidateResolutionStage` SHALL produce a diagnostic: `"Property 'adress' not found on type Customer (in source chain 'customer.adress.street', segment 2). Did you mean 'address'?"`

#### Scenario: Unknown property in chain with no close match

- **WHEN** source path `["customer", "zzz"]` has an unresolved segment `"zzz"` at index 1 on type `Customer` which has properties `["address", "name"]`
- **THEN** `ValidateResolutionStage` SHALL produce a diagnostic containing the unknown property, chain context, searched type, and available properties, without a "Did you mean:" suggestion

#### Scenario: First segment not found on source type

- **WHEN** source path `["custmer", "name"]` has an unresolved segment `"custmer"` at index 0 on type `Order` which has properties `["customer", "items"]`
- **THEN** `ValidateResolutionStage` SHALL produce a diagnostic with segment index 0, searched type `Order`, and suggestion `"customer"`

### Requirement: ValidateResolutionStage detects unmapped target properties

`ValidateResolutionStage` SHALL check that every target slot of the target type has a resolved `ResolvedAssignment`. Unmapped target slots SHALL produce an error diagnostic whose message includes the target slot name, mapper type, any unmapped source leaf properties (top-level source properties with no `MappingAssignment` consuming them), and a fuzzy-match suggestion if applicable.

#### Scenario: All targets mapped

- **WHEN** every target slot has a resolved `ResolvedAssignment`
- **THEN** `ValidateResolutionStage` SHALL NOT emit an unmapped-target diagnostic

#### Scenario: Unmapped target with matching unmapped source

- **WHEN** target slot `"middleName"` has no `ResolvedAssignment` and source has unmapped top-level properties `["secondName", "suffix"]`
- **THEN** `ValidateResolutionStage` SHALL produce a diagnostic containing the unmapped target name, mapper type, unmapped sources, and suggestion `"Did you mean to map 'secondName' → 'middleName'?"`

### Requirement: ValidateMatchingStage detects duplicate target assignments

`ValidateMatchingStage` SHALL detect when two or more `MappingAssignment`s in the same `MethodMatching` share the same `targetName`. Duplicate assignments SHALL produce an error diagnostic listing the conflicting source paths. The check happens on the `MatchedModel` without needing a `ValueGraph`, so the error surfaces before `BuildValueGraphStage` runs.

#### Scenario: Two explicit @Map directives collide

- **WHEN** a method has `@Map(source = "a", target = "x")` and `@Map(source = "b", target = "x")`
- **THEN** `ValidateMatchingStage` SHALL produce a diagnostic containing the target name `"x"`, mapper type, method name, and the conflicting source paths `["a", "b"]`

#### Scenario: Explicit @Map shadows auto-mapping without error

- **WHEN** a method has `@Map(source = "fullName", target = "name")` and source also has a top-level property `name`
- **THEN** `MatchMappingsStage` SHALL emit only the `EXPLICIT_MAP` assignment (auto-mapping is suppressed when the target is already covered); `ValidateMatchingStage` SHALL NOT diagnose this as a duplicate

### Requirement: ValidateMatchingStage detects unknown source-root parameters

`ValidateMatchingStage` SHALL validate that each `MappingAssignment.sourcePath[0]` — the source-root segment — matches a parameter name on the method. Unknown source roots SHALL produce an error diagnostic. This check is independent of type resolution and happens on the `MatchedModel`.

#### Scenario: Unknown parameter name in explicit @Map

- **WHEN** a method `OrderDTO map(Order order)` has `@Map(source = "ordre.name", target = "name")` (typo)
- **THEN** `ValidateMatchingStage` SHALL produce a diagnostic: `"Source parameter 'ordre' not found on method 'map'. Available parameters: [order]. Did you mean 'order'?"`

### Requirement: ValidateMatchingStage detects unresolved @Map(using=...) method names

`ValidateMatchingStage` SHALL validate that every `MappingAssignment` with `origin == USING_ROUTED` (i.e. non-null `using`) names a method that exists on the mapper. Unknown helper method names SHALL produce an error diagnostic listing available candidates.

#### Scenario: Unknown using method

- **WHEN** a method has `@Map(source = "raw", target = "normalised", using = "normallise")` (typo) and the mapper has methods `[normalise, map, mapAddress]`
- **THEN** `ValidateMatchingStage` SHALL produce a diagnostic: `"Helper method 'normallise' not found on mapper OrderMapper. Available methods: [normalise, map, mapAddress]. Did you mean 'normalise'?"`

## REMOVED Requirements

### Requirement: ValidateTransforms detects unresolvable type gaps

**Reason**: Renamed and re-homed. Type-gap detection is now owned by `ValidateResolutionStage` (one of the two stages that replace `ValidateTransformsStage`).

**Migration**: Behavior preserved under the ADDED requirement `ValidateResolutionStage detects unresolvable type gaps`; diagnostic message content remains goal-directed.

### Requirement: ValidateTransforms detects unresolved property access

**Reason**: Renamed and re-homed to `ValidateResolutionStage`. Unresolved source-path detection now reads the markers recorded by `BuildValueGraphStage` instead of walking chains itself.

**Migration**: Behavior preserved under the ADDED requirement `ValidateResolutionStage detects unresolved property access`; "Did you mean" fuzzy-match logic retained verbatim.

### Requirement: ValidateTransforms detects unmapped target properties

**Reason**: Renamed and re-homed to `ValidateResolutionStage`.

**Migration**: Behavior preserved under the ADDED requirement `ValidateResolutionStage detects unmapped target properties`; unmapped-source suggestion logic retained.

### Requirement: ValidateTransforms detects duplicate target mappings

**Reason**: Responsibility moved to `ValidateMatchingStage` — duplicate target detection is a matching-layer concern and now happens before `BuildValueGraphStage`, not after resolution.

**Migration**: The scenario about two sources mapping to the same target is preserved under `ValidateMatchingStage detects duplicate target assignments` in the ADDED Requirements above; error message content is unchanged.
