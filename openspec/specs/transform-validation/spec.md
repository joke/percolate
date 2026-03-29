# Transform Validation Spec

## Purpose

Defines the ValidateTransforms stage that verifies all resolved transforms are fulfillable, producing error diagnostics for unresolvable type mismatches where no sibling mapping method exists.

## Requirements

### Requirement: ValidateTransforms detects unresolvable type gaps
The `ValidateTransforms` stage SHALL check that every resolved mapping has a fulfillable transform chain. If any mapping is marked `UNRESOLVED`, the stage SHALL produce an error diagnostic whose message includes the source property name, source type, target property name, target type, method context, and mapper type.

#### Scenario: All transforms resolved
- **WHEN** every mapping in the `ResolvedModel` has a DIRECT or SUBMAP transform
- **THEN** the stage SHALL return success with the `ResolvedModel` passed through

#### Scenario: Unresolved type gap produces error
- **WHEN** mapping `data` (type `Foo`) → `data` (type `Bar`) is marked UNRESOLVED in method `map` of `OrderMapper`
- **THEN** the stage SHALL return a failure with a diagnostic containing: `"Cannot map 'data' (Foo) → 'data' (Bar) in method 'map' of OrderMapper: no mapping method found for Foo → Bar"`

#### Scenario: Multiple unresolved mappings produce multiple errors
- **WHEN** two mappings in the same method are UNRESOLVED
- **THEN** the stage SHALL produce one error diagnostic per unresolved mapping
