## MODIFIED Requirements

### Requirement: BuildGraphStage constructs graph from discovered model
The `BuildGraphStage` SHALL create the JGraphT graph by adding source and target property nodes, then creating `MappingEdge` instances for each `@Map` directive. If a directive references a property name not found in the discovered properties, the stage SHALL return a failure with a diagnostic whose message is constructed by `ErrorMessages`, including the method context, type name, available properties, and fuzzy-match suggestions.

#### Scenario: Valid directives produce graph
- **WHEN** all `@Map` directives reference discovered source and target properties
- **THEN** the stage SHALL return a success with the constructed graph

#### Scenario: Directive references unknown source property
- **WHEN** `@Map(source = "nonexistent", target = "givenName")` but no source property `nonexistent` was discovered
- **THEN** the stage SHALL return a failure with a diagnostic pointing at the method element, with a message containing the unknown property name, method context, source type, available source properties, and a "Did you mean:" suggestion if applicable

#### Scenario: Directive references unknown target property
- **WHEN** `@Map(source = "firstName", target = "nonexistent")` but no target property `nonexistent` was discovered
- **THEN** the stage SHALL return a failure with a diagnostic pointing at the method element, with a message containing the unknown property name, method context, target type, available target properties, and a "Did you mean:" suggestion if applicable

### Requirement: ValidateStage uses ConnectivityInspector for unmapped targets
The `ValidateStage` SHALL detect target properties with no incoming edges. Unmapped target properties SHALL produce an error diagnostic whose message is constructed by `ErrorMessages`, including the mapper type, unmapped source properties (source nodes with out-degree 0), and a fuzzy-match mapping suggestion if applicable.

#### Scenario: All targets mapped
- **WHEN** every `TargetPropertyNode` has at least one incoming edge
- **THEN** the stage SHALL return success with the validated graph

#### Scenario: Unmapped target property
- **WHEN** a `TargetPropertyNode("middleName")` has no incoming edge and there are unmapped source properties `["secondName"]`
- **THEN** the stage SHALL return a failure with a diagnostic containing the unmapped target name, mapper type, unmapped source properties, and a "Did you mean to map 'secondName' -> 'middleName'?" suggestion

### Requirement: ValidateStage detects duplicate target mappings
The `ValidateStage` SHALL detect target properties with more than one incoming edge (in-degree > 1). Duplicate target mappings SHALL produce an error diagnostic whose message is constructed by `ErrorMessages`, listing the conflicting source property names.

#### Scenario: Two sources map to same target
- **WHEN** `@Map(source = "a", target = "x")` and `@Map(source = "b", target = "x")` both map to the same target
- **THEN** the stage SHALL return a failure with a diagnostic containing the target property name, mapper type, and the list of conflicting source names
