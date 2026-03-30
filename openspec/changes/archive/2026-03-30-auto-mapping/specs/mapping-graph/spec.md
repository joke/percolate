## MODIFIED Requirements

### Requirement: BuildGraphStage constructs graph from discovered model
The `BuildGraphStage` SHALL create a per-method JGraphT graph by adding source and target property nodes, then creating `MappingEdge` instances for each `@Map` directive. After processing all directives, it SHALL auto-map same-name properties: for each `TargetPropertyNode` with no incoming edge (`inDegreeOf == 0`), if a `SourcePropertyNode` with the same name exists, a `MappingEdge` with type `DIRECT` SHALL be added. Each `DiscoveredMethod` SHALL have its own `DefaultDirectedGraph<PropertyNode, MappingEdge>`. If a directive references a property name not found in the discovered properties, the stage SHALL return a failure with a diagnostic whose message is constructed by `ErrorMessages`, including the method context, type name, available properties, and fuzzy-match suggestions.

#### Scenario: Valid directives produce per-method graph
- **WHEN** all `@Map` directives reference discovered source and target properties
- **THEN** the stage SHALL return a success with a `MappingGraph` containing a separate graph per method

#### Scenario: Directive references unknown source property
- **WHEN** `@Map(source = "nonexistent", target = "givenName")` but no source property `nonexistent` was discovered
- **THEN** the stage SHALL return a failure with a diagnostic pointing at the method element, with a message containing the unknown property name, method context, source type, available source properties, and a "Did you mean:" suggestion if applicable

#### Scenario: Directive references unknown target property
- **WHEN** `@Map(source = "firstName", target = "nonexistent")` but no target property `nonexistent` was discovered
- **THEN** the stage SHALL return a failure with a diagnostic pointing at the method element, with a message containing the unknown property name, method context, target type, available target properties, and a "Did you mean:" suggestion if applicable

#### Scenario: Same-name properties auto-mapped after directives
- **WHEN** source has `[firstName, age]`, target has `[givenName, age]`, and directive is `@Map(source="firstName", target="givenName")`
- **THEN** the graph SHALL contain an explicit edge for `firstName→givenName` and an auto-mapped edge for `age→age`

#### Scenario: Multi-method mapper has isolated graphs
- **WHEN** a mapper has methods `map(Order): OrderDTO` and `mapAddress(Address): AddressDTO`
- **THEN** each method's graph SHALL be independent — nodes from one method SHALL NOT appear in another method's graph
