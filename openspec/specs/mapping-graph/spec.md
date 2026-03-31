# Mapping Graph Spec

## Purpose

Defines the JGraphT-based symbolic directed graph model for property mappings, including graph construction from annotation-level directives using name-only nodes, and the per-method graph structure keyed by `MappingMethodModel`.

## Requirements

### Requirement: BuildGraphStage constructs graph from discovered model
`BuildGraphStage` SHALL construct a per-method symbolic property graph from `MappingMethodModel` directives and lightweight property name scanning. For each `@Map` directive, the stage SHALL split the `source` string on `"."` into chain segments, create `SourcePropertyNode` instances for each segment connected by `AccessEdge`s, and connect the final source node to a `TargetPropertyNode` via `MappingEdge`. The stage SHALL scan source and target types for property names (getters, fields) to support auto-mapping. The stage SHALL NOT perform full property discovery (no types, no accessors). If chain segments share a common prefix, the shared `SourcePropertyNode` instances SHALL be reused. The stage SHALL NOT validate that property names exist on their respective types — this is deferred to resolution and validation.

#### Scenario: Simple directive produces symbolic chain
- **WHEN** `@Map(source = "name", target = "name")` is processed
- **THEN** the symbolic graph SHALL contain `SourceRootNode → AccessEdge → SourcePropertyNode("name") → MappingEdge → TargetPropertyNode("name")`

#### Scenario: Nested source produces multi-segment chain
- **WHEN** `@Map(source = "customer.address", target = "addr")` is processed
- **THEN** the symbolic graph SHALL contain `SourceRootNode → AccessEdge → SourcePropertyNode("customer") → AccessEdge → SourcePropertyNode("address") → MappingEdge → TargetPropertyNode("addr")`

#### Scenario: Unknown property name does not produce error
- **WHEN** `@Map(source = "nonexistent", target = "name")` is processed
- **THEN** the stage SHALL create the symbolic nodes and edges without error — validation is deferred to `ValidateTransformsStage`

#### Scenario: Multi-method mapper has isolated graphs
- **WHEN** a mapper has methods `map(Order): OrderDTO` and `mapAddress(Address): AddressDTO`
- **THEN** each method's symbolic graph SHALL be independent

### Requirement: PropertyNode carries accessor metadata
`SourcePropertyNode` and `TargetPropertyNode` SHALL carry only the property name (string). They SHALL NOT carry `TypeMirror`, `ReadAccessor`, or `WriteAccessor`. Type and accessor information SHALL be resolved by `ResolveTransformsStage`.

#### Scenario: Source node has name only
- **WHEN** a `SourcePropertyNode` is created for property `"firstName"`
- **THEN** it SHALL have a `getName()` accessor returning `"firstName"` and no type or accessor fields

#### Scenario: Target node has name only
- **WHEN** a `TargetPropertyNode` is created for property `"givenName"`
- **THEN** it SHALL have a `getName()` accessor returning `"givenName"` and no type or accessor fields

### Requirement: MappingGraph uses per-method graph structure
`MappingGraph` SHALL store a `Map<MappingMethodModel, DefaultDirectedGraph>` keyed by method model, replacing the current `DiscoveredMethod` key. It SHALL carry the `TypeElement` mapper type and `List<MappingMethodModel>` methods.

#### Scenario: Accessing a method's symbolic graph
- **WHEN** `MappingGraph.getMethodGraphs().get(methodModel)` is called
- **THEN** it SHALL return the symbolic directed graph containing only that method's property nodes and edges
