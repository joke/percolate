# mapper-graph Specification

## Purpose
TBD - created by archiving change demand-driven-graph-expansion. Update Purpose after archive.
## Requirements
### Requirement: TargetRootNode is the fifth ValueNode subtype

The `ValueNode` hierarchy SHALL admit a fifth subtype: `TargetRootNode`. The class represents the constructed target value of a mapper method and SHALL carry:

- `type: TypeMirror` — the method's return type.
- `slots: List<TargetSlotNode>` — a mutable, ordered list of slot children, populated later by a `ROOT_CONSTRUCTION`-handling strategy via `setSlots(List<TargetSlotNode>)`.

`TargetRootNode.compose(Map<ValueEdge, CodeBlock> inputs, ComposeKind kind)` SHALL:

- Reject any `ComposeKind` other than `EXPRESSION` with an `IllegalStateException`.
- Return `new $T()` when `slots` is empty.
- Otherwise require `inputs.size() == slots.size()` and return `new $T($slot0, $slot1, ...)` by joining `inputs.values()` in iteration order. Callers SHALL insert inputs in declaration/slot order.

`TargetRootNode` equality SHALL be keyed on the stringified type only (`typeString`), so the same return type collapses to a single vertex across methods.

The class is a surface declaration in this slice: no stage instantiates it yet. Follow-up slices will produce one `TargetRootNode` per mapper method inside `BuildValueGraphStage`.

#### Scenario: ValueNode hierarchy admits five subtypes

- **WHEN** a developer enumerates `ValueNode` subclasses
- **THEN** exactly five SHALL be listed: `SourceParamNode`, `PropertyNode`, `TypedValueNode`, `TargetSlotNode`, `TargetRootNode`

#### Scenario: compose(EXPRESSION) emits constructor call when slots are set

- **WHEN** `TargetRootNode.compose(inputs, EXPRESSION)` is called on a node with slots `[name, age]` and `inputs.values()` yields `person.getName()` then `person.getAge()` in iteration order
- **THEN** the returned `CodeBlock` SHALL render as `new Human(person.getName(), person.getAge())`

#### Scenario: compose(EXPRESSION) emits no-arg constructor when slots are empty

- **WHEN** `TargetRootNode.compose(emptyMap, EXPRESSION)` is called on a node with no slots
- **THEN** the returned `CodeBlock` SHALL render as `new T()`

### Requirement: MapperGraph record holds mapper-level graph and per-method partitions

`MapperGraph` SHALL be a Lombok `@Value` class with fields:

```java
@Value
public class MapperGraph {
    Graph<ValueNode, ValueEdge> graph;
    Map<MethodMatching, VertexPartition> partitions;
}
```

The record is a surface declaration in this slice. The legacy per-method graph shape (`Map<MethodMatching, ValueGraph>`) remains the pipeline's in-flight representation; follow-up slices will switch `BuildValueGraphStage` to produce `MapperGraph` instead.

#### Scenario: MapperGraph is constructable with empty partitions

- **WHEN** a developer constructs `new MapperGraph(graph, Map.of())`
- **THEN** both `getGraph()` and `getPartitions()` SHALL return the supplied arguments

### Requirement: VertexPartition record identifies a method's slice of a MapperGraph

`VertexPartition` SHALL be a Lombok `@Value` class with fields:

```java
@Value
public class VertexPartition {
    SourceParamNode sourceParam;
    TargetRootNode targetRoot;
    Set<ValueNode> methodVertices;
}
```

Vertices MAY appear in multiple partitions. The record is a surface declaration; no stage populates a `VertexPartition` yet.

#### Scenario: VertexPartition fields are accessible

- **WHEN** a developer constructs `new VertexPartition(sourceParam, targetRoot, methodVertices)` and reads its fields
- **THEN** each getter SHALL return the value supplied at construction

### Requirement: TargetSlotNode exposes a mutable paramIndex

`TargetSlotNode` SHALL expose a mutable `int paramIndex` field with a Lombok `@Setter`, so that future `ROOT_CONSTRUCTION`-handling strategies can record the zero-based position of each slot in the target constructor's parameter list.

The existing `WriteAccessor writeAccessor` field SHALL be retained in this slice — removing it would break the existing `BuildValueGraphStage`, `GenerateStage`, and test specs. Its retirement is scheduled for the accessor-model retirement slice.

#### Scenario: paramIndex is settable and readable

- **WHEN** a developer creates a `TargetSlotNode("name", stringType, writeAccessor)` and calls `setParamIndex(2)`
- **THEN** `getParamIndex()` SHALL return `2`
