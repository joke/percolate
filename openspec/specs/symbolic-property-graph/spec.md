# Symbolic Property Graph Spec

## Purpose

Defines the symbolic property graph model used by `BuildGraphStage` to represent property mappings as a directed graph with name-only nodes (no type or accessor information), using JGraphT with `SourceRootNode`, `SourcePropertyNode`, `TargetRootNode`, and `TargetPropertyNode` node types connected by `AccessEdge` and `MappingEdge` edge types.

## Requirements

### Requirement: Symbolic property graph uses name-only nodes
The symbolic property graph SHALL use JGraphT `DefaultDirectedGraph` with nodes that carry only property names (strings), not types or accessors. Node types SHALL be: `SourceRootNode` (carries parameter name), `SourcePropertyNode` (carries property name), `TargetRootNode`, and `TargetPropertyNode` (carries property name). All node classes SHALL use Lombok `@Getter` and appropriate constructor annotations.

#### Scenario: SourceRootNode carries parameter name
- **WHEN** a `SourceRootNode` is created for parameter `"order"`
- **THEN** `getName()` SHALL return `"order"` and the node SHALL carry no type or accessor information

#### Scenario: SourcePropertyNode carries property name only
- **WHEN** a `SourcePropertyNode` is created for property `"customer"`
- **THEN** `getName()` SHALL return `"customer"` and the node SHALL carry no `TypeMirror` or `ReadAccessor`

#### Scenario: TargetPropertyNode carries property name only
- **WHEN** a `TargetPropertyNode` is created for property `"address"`
- **THEN** `getName()` SHALL return `"address"` and the node SHALL carry no `TypeMirror` or `WriteAccessor`

### Requirement: Symbolic graph edge types
The symbolic graph SHALL have two edge types: `AccessEdge` and `MappingEdge`. `AccessEdge` SHALL represent property access (source root to property, or property to nested property). `MappingEdge` SHALL represent a mapping from a source property to a target property. Both edge types SHALL be distinguishable by type.

#### Scenario: AccessEdge connects source root to source property
- **WHEN** `@Map(source = "customer", target = "cust")` is processed
- **THEN** an `AccessEdge` SHALL connect `SourceRootNode("order")` to `SourcePropertyNode("customer")`

#### Scenario: MappingEdge connects source property to target property
- **WHEN** `@Map(source = "customer", target = "cust")` is processed
- **THEN** a `MappingEdge` SHALL connect `SourcePropertyNode("customer")` to `TargetPropertyNode("cust")`

### Requirement: Shared prefix nodes are deduplicated
When multiple source chains share a common prefix, the shared segments SHALL reuse the same `SourcePropertyNode` instances. A single `SourcePropertyNode` MAY have multiple outgoing `AccessEdge`s to different child properties.

#### Scenario: Two chains sharing a prefix
- **WHEN** `@Map(source = "customer.address", target = "addr")` and `@Map(source = "customer.name", target = "name")` are processed
- **THEN** the graph SHALL contain one `SourcePropertyNode("customer")` with two outgoing `AccessEdge`s to `SourcePropertyNode("address")` and `SourcePropertyNode("name")`

### Requirement: SymbolicGraph is a per-method structure
Each mapper method SHALL have its own symbolic property graph. The container model SHALL store a `Map<MappingMethodModel, DefaultDirectedGraph<Object, Object>>` (or appropriately typed) keyed by method. Nodes from one method SHALL NOT appear in another method's graph.

#### Scenario: Multi-method mapper has isolated symbolic graphs
- **WHEN** a mapper has methods `map(Order): OrderDTO` and `mapAddress(Address): AddressDTO`
- **THEN** each method's symbolic graph SHALL be independent

### Requirement: SymbolicGraph carries mapper type and method models
The symbolic graph container SHALL carry the `TypeElement` mapper type and the list of `MappingMethodModel` entries, preserving the annotation-level context for downstream stages.

#### Scenario: SymbolicGraph provides mapper type
- **WHEN** a `SymbolicGraph` is created for `OrderMapper`
- **THEN** `getMapperType()` SHALL return the `TypeElement` for `OrderMapper`
