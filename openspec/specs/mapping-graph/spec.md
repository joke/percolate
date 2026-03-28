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
The `BuildGraphStage` SHALL create the JGraphT graph by adding source and target property nodes, then creating `MappingEdge` instances for each `@Map` directive. If a directive references a property name not found in the discovered properties, the stage SHALL return a failure with a diagnostic.

#### Scenario: Valid directives produce graph
- **WHEN** all `@Map` directives reference discovered source and target properties
- **THEN** the stage SHALL return a success with the constructed graph

#### Scenario: Directive references unknown source property
- **WHEN** `@Map(source = "nonexistent", target = "givenName")` but no source property `nonexistent` was discovered
- **THEN** the stage SHALL return a failure with a diagnostic pointing at the method element

#### Scenario: Directive references unknown target property
- **WHEN** `@Map(source = "firstName", target = "nonexistent")` but no target property `nonexistent` was discovered
- **THEN** the stage SHALL return a failure with a diagnostic pointing at the method element

### Requirement: ValidateStage uses ConnectivityInspector for unmapped targets
The `ValidateStage` SHALL use JGraphT's `ConnectivityInspector` or equivalent graph analysis to detect target properties with no incoming edges. Unmapped target properties SHALL produce an error diagnostic.

#### Scenario: All targets mapped
- **WHEN** every `TargetPropertyNode` has at least one incoming edge
- **THEN** the stage SHALL return success with the validated graph

#### Scenario: Unmapped target property
- **WHEN** a `TargetPropertyNode("middleName")` has no incoming edge
- **THEN** the stage SHALL return a failure with a diagnostic: unmapped target property `middleName`

### Requirement: ValidateStage detects duplicate target mappings
The `ValidateStage` SHALL detect target properties with more than one incoming edge (in-degree > 1). Duplicate target mappings SHALL produce an error diagnostic.

#### Scenario: Two sources map to same target
- **WHEN** `@Map(source = "a", target = "x")` and `@Map(source = "b", target = "x")` both map to the same target
- **THEN** the stage SHALL return a failure with a diagnostic: conflicting mappings for target property `x`

### Requirement: DOTExporter for debug output
The mapping graph SHALL be exportable to DOT format via JGraphT's `DOTExporter` for debugging and visualization purposes.

#### Scenario: Graph exported to DOT
- **WHEN** a mapping graph is exported
- **THEN** the output SHALL be valid DOT syntax renderable by Graphviz
