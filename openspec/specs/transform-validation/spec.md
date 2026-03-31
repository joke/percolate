# Transform Validation Spec

## Purpose

Defines the ValidateTransforms stage that verifies all resolved transforms have complete GraphPath chains from source to target type, detects unresolved property access in source chains, unmapped target properties, and duplicate target mappings, producing error diagnostics with goal-directed messages.

## Requirements

### Requirement: ValidateTransforms detects unresolvable type gaps
The `ValidateTransforms` stage SHALL check that every resolved mapping has a complete `GraphPath` from source type to target type. If any mapping is marked unresolved (no path found), the stage SHALL produce an error diagnostic whose message includes the source property name (or full chain), source type, target property name, target type, method context, and mapper type. The error message SHALL be goal-directed, indicating what the developer needs to provide (e.g., "provide a mapping method for Person -> PersonDTO").

#### Scenario: All transforms resolved
- **WHEN** every mapping in the `ResolvedModel` has a complete `GraphPath`
- **THEN** the stage SHALL return success with the `ResolvedModel` passed through

#### Scenario: Unresolved type gap produces error
- **WHEN** mapping `data` (type `Foo`) -> `data` (type `Bar`) is marked unresolved in method `map` of `OrderMapper`
- **THEN** the stage SHALL return a failure with a diagnostic containing: source property name, source type, target property name, target type, method name, and mapper type

#### Scenario: Multiple unresolved mappings produce multiple errors
- **WHEN** two mappings in the same method are unresolved
- **THEN** the stage SHALL produce one error diagnostic per unresolved mapping

#### Scenario: Partially expanded but unresolved container mapping
- **WHEN** source `List<Person>` maps to target `Set<PersonDTO>` but no `Person -> PersonDTO` method exists
- **THEN** the stage SHALL produce a diagnostic indicating a mapping method for `Person -> PersonDTO` is needed

### Requirement: ValidateTransforms detects unresolved property access
The `ValidateTransforms` stage SHALL check for access edges that could not be resolved (property not found on the resolved type). For each unresolved access edge, the stage SHALL produce an error diagnostic whose message includes the segment name, the segment index, the full source chain string, the type that was searched, available property names on that type, and a "Did you mean:" suggestion if a fuzzy match exists.

#### Scenario: Unknown property in chain with close match
- **WHEN** chain `"customer.adress.street"` has an unresolved segment `"adress"` at index 1 on type `Customer` which has properties `["address", "name", "email"]`
- **THEN** the stage SHALL produce a diagnostic: `"Property 'adress' not found on type Customer (in source chain 'customer.adress.street', segment 2). Did you mean 'address'?"`

#### Scenario: Unknown property in chain with no close match
- **WHEN** chain `"customer.zzz"` has an unresolved segment `"zzz"` at index 1 on type `Customer` which has properties `["address", "name"]`
- **THEN** the stage SHALL produce a diagnostic containing the unknown property, chain context, searched type, and available properties, but no "Did you mean:" suggestion

#### Scenario: First segment not found on source type
- **WHEN** chain `"custmer.name"` has an unresolved segment `"custmer"` at index 0 on type `Order` which has properties `["customer", "items"]`
- **THEN** the stage SHALL produce a diagnostic with segment index 0, searched type `Order`, and suggestion `"customer"`

### Requirement: ValidateTransforms detects unmapped target properties
The `ValidateTransforms` stage SHALL check that every target property has an incoming mapping from a resolved source. Unmapped target properties SHALL produce an error diagnostic whose message includes the target property name, mapper type, any unmapped source leaf properties (source chain endpoints with no outgoing mapping edge), and a fuzzy-match suggestion if applicable.

#### Scenario: All targets mapped
- **WHEN** every target property has a resolved source mapping
- **THEN** the stage SHALL return success

#### Scenario: Unmapped target with matching unmapped source
- **WHEN** target property `"middleName"` has no source mapping and there are unmapped source leaves `["secondName", "suffix"]`
- **THEN** the stage SHALL produce a diagnostic containing the unmapped target name, mapper type, unmapped sources, and suggestion `"Did you mean to map 'secondName' -> 'middleName'?"`

### Requirement: ValidateTransforms detects duplicate target mappings
The `ValidateTransforms` stage SHALL detect target properties with more than one incoming mapping. Duplicate target mappings SHALL produce an error diagnostic listing the conflicting source property names (or chains).

#### Scenario: Two sources map to same target
- **WHEN** `@Map(source = "a", target = "x")` and `@Map(source = "b", target = "x")` both map to the same target
- **THEN** the stage SHALL produce a diagnostic containing the target property name, mapper type, and conflicting source names
