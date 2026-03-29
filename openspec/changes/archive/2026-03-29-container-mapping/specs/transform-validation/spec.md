## MODIFIED Requirements

### Requirement: ValidateTransforms detects unresolvable type gaps
The `ValidateTransforms` stage SHALL check that every resolved mapping has a complete `GraphPath` from source type to target type. If any mapping is marked unresolved (no path found), the stage SHALL produce an error diagnostic whose message includes the source property name, source type, target property name, target type, method context, and mapper type. The error message SHALL be goal-directed, indicating what the developer needs to provide (e.g., "provide a mapping method for Person → PersonDTO").

#### Scenario: All transforms resolved
- **WHEN** every mapping in the `ResolvedModel` has a complete `GraphPath`
- **THEN** the stage SHALL return success with the `ResolvedModel` passed through

#### Scenario: Unresolved type gap produces error
- **WHEN** mapping `data` (type `Foo`) → `data` (type `Bar`) is marked unresolved in method `map` of `OrderMapper`
- **THEN** the stage SHALL return a failure with a diagnostic containing: source property name, source type, target property name, target type, method name, and mapper type

#### Scenario: Multiple unresolved mappings produce multiple errors
- **WHEN** two mappings in the same method are unresolved
- **THEN** the stage SHALL produce one error diagnostic per unresolved mapping

#### Scenario: Partially expanded but unresolved container mapping
- **WHEN** source `List<Person>` maps to target `Set<PersonDTO>` but no `Person → PersonDTO` method exists
- **THEN** the stage SHALL produce a diagnostic indicating a mapping method for `Person → PersonDTO` is needed
