# Mapping Graph Spec

## Purpose

Defines the JGraphT-based directed graph model for property mappings, including graph construction from discovered models, validation of mapping completeness, and DOT export for debugging.

## Requirements

### Requirement: JGraphT directed graph for property mappings
The mapping graph SHALL be a `DefaultDirectedGraph<PropertyNode, MappingEdge>` from JGraphT. Source properties SHALL be represented as `SourcePropertyNode` and target properties as `TargetPropertyNode`, both extending `PropertyNode`.

#### Scenario: Graph constructed from discovered model
- **WHEN** a discovered model has source properties `[firstName, lastName]` and target properties `[givenName, familyName]` with `@Map` directives connecting them
- **THEN** the graph SHALL contain 4 nodes and 2 directed edges from source to target nodes

### Requirement: PropertyNode carries accessor metadata
`SourcePropertyNode` SHALL carry the property name, type, and `ReadAccessor`. `TargetPropertyNode` SHALL carry the property name, type, and `WriteAccessor`. These accessors are used by the generate stage to emit correct access code. `PropertyNode` SHALL use Lombok `@Getter` and `@RequiredArgsConstructor(access = AccessLevel.PROTECTED)` to generate accessors and constructor. Leaf subclasses SHALL use Lombok `@Getter` for their own fields. Accessor methods SHALL follow JavaBean naming conventions (`getName()`, `getType()`, `getAccessor()`).

#### Scenario: Source node provides read accessor
- **WHEN** a `SourcePropertyNode` is created for property `firstName` with a `GetterAccessor`
- **THEN** `getAccessor()` SHALL return the `GetterAccessor` for code generation

### Requirement: MappingEdge represents a property mapping
`MappingEdge` SHALL represent a directed mapping from a source property to a target property. It SHALL carry the mapping type (e.g., `DIRECT` for explicit `@Map` directives).

#### Scenario: Direct mapping edge created from @Map directive
- **WHEN** `@Map(source = "firstName", target = "givenName")` is processed
- **THEN** a `MappingEdge` with type `DIRECT` SHALL connect `SourcePropertyNode("firstName")` to `TargetPropertyNode("givenName")`

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

### Requirement: MappingGraph uses per-method graph structure
`MappingGraph` SHALL store a `Map<DiscoveredMethod, DefaultDirectedGraph<PropertyNode, MappingEdge>>` keyed by method, replacing the single shared graph. It SHALL retain the `TypeElement` mapper type and `List<DiscoveredMethod>` methods.

#### Scenario: Accessing a method's graph
- **WHEN** `MappingGraph.getMethodGraphs().get(method)` is called for a discovered method
- **THEN** it SHALL return the directed graph containing only that method's property nodes and mapping edges

### Requirement: ValidateStage uses ConnectivityInspector for unmapped targets
The `ValidateStage` SHALL detect target properties with no incoming edges within each per-method graph. Unmapped target properties SHALL produce an error diagnostic whose message is constructed by `ErrorMessages`, including the mapper type, unmapped source properties (source nodes with out-degree 0), and a fuzzy-match mapping suggestion if applicable.

#### Scenario: All targets mapped
- **WHEN** every `TargetPropertyNode` has at least one incoming edge
- **THEN** the stage SHALL return success with the validated graph

#### Scenario: Unmapped target property
- **WHEN** a `TargetPropertyNode("middleName")` has no incoming edge and there are unmapped source properties `["secondName"]`
- **THEN** the stage SHALL return a failure with a diagnostic containing the unmapped target name, mapper type, unmapped source properties, and a "Did you mean to map 'secondName' -> 'middleName'?" suggestion

### Requirement: ValidateStage detects duplicate target mappings
The `ValidateStage` SHALL detect target properties with more than one incoming edge (in-degree > 1) within each per-method graph. Duplicate target mappings SHALL produce an error diagnostic whose message is constructed by `ErrorMessages`, listing the conflicting source property names.

#### Scenario: Two sources map to same target
- **WHEN** `@Map(source = "a", target = "x")` and `@Map(source = "b", target = "x")` both map to the same target
- **THEN** the stage SHALL return a failure with a diagnostic containing the target property name, mapper type, and the list of conflicting source names

### Requirement: DOTExporter for debug output
The mapping graph SHALL be exportable to DOT format via JGraphT's `DOTExporter` for debugging and visualization purposes.

#### Scenario: Graph exported to DOT
- **WHEN** a mapping graph is exported
- **THEN** the output SHALL be valid DOT syntax renderable by Graphviz
