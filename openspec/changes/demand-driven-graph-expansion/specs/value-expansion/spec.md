## ADDED Requirements

### Requirement: ValueExpansionStrategy SPI interface is defined

The processor SHALL expose a `ValueExpansionStrategy` interface on the `io.github.joke.percolate.processor.spi` package:

```java
public interface ValueExpansionStrategy {
    int priority();
    Optional<Subgraph> expand(ExpansionDemand demand, ExpansionContext ctx);
}
```

The interface is a **surface declaration only** in this slice — no stage consults it yet. It exists so that follow-up slices can port the existing `SourcePropertyDiscovery`, `TargetPropertyDiscovery`, and `TypeTransformStrategy` implementations onto it and wire `BuildValueGraphStage` to dispatch via it. A strategy returning `Optional.empty()` declines the demand; returning `Optional.of(subgraph)` offers a contribution.

Built-in priority constants SHALL be documented in the package javadoc as anchors for downstream strategy authors.

#### Scenario: Interface is present on the classpath

- **WHEN** a developer imports `io.github.joke.percolate.processor.spi.ValueExpansionStrategy`
- **THEN** the import SHALL resolve to the defined interface with `priority()` and `expand(...)` methods

#### Scenario: Surface-only — not consulted by any stage

- **WHEN** the processor runs in a consumer project against this slice alone
- **THEN** no `ValueExpansionStrategy` implementation SHALL be consulted; the legacy discovery SPIs continue to drive expansion

### Requirement: ExpansionDemand carries requester, demand, and kind

`ExpansionDemand` SHALL be a Lombok `@Value` class with fields:

- `requester: ValueNode` — the node that needs an incoming edge (target side of the demand).
- `demand: ValueNode` — the node or type representation the strategy is asked to produce.
- `kind: DemandKind` — one of `PROPERTY_READ`, `TYPE_TRANSFORM`, `TARGET_SLOT`, `ROOT_CONSTRUCTION`.

#### Scenario: DemandKind enumerates four values

- **WHEN** `DemandKind.values()` is read
- **THEN** it SHALL return exactly `[PROPERTY_READ, TYPE_TRANSFORM, TARGET_SLOT, ROOT_CONSTRUCTION]`

### Requirement: Subgraph carries vertices, edges, and wiring nodes

`Subgraph` SHALL be a Lombok `@Value` class with fields:

- `vertices: Set<ValueNode>` — the nodes contributed by this subgraph (MAY be empty if all referenced nodes already exist).
- `edges: Set<ValueEdge>` — the edges contributed.
- `entry: ValueNode` — the node at which external edges feed in.
- `exit: ValueNode` — the node from which external edges consume.

The record is a surface declaration; merge semantics are defined by the follow-up slice that rewrites `BuildValueGraphStage`.

#### Scenario: Subgraph fields are accessible

- **WHEN** a developer constructs a `Subgraph(vertices, edges, entry, exit)` and reads its fields
- **THEN** each getter SHALL return the value supplied at construction

### Requirement: ExpansionContext exposes analysis services and routable lookup

`ExpansionContext` SHALL be a Lombok `@Value` class carrying:

- `types: Types` and `elements: Elements` — the processing-environment services.
- `mapperType: TypeElement` — the current mapper interface.
- `currentMethod: ExecutableElement` — the abstract method whose expansion is in progress.
- `options: Map<MapOptKey, String>` — per-mapping options already parsed by the matching stage.
- `routableIndex: Map<ExpansionContext.RoutableKey, ExecutableElement>` — lookup of `@Routable` default methods keyed on `(inputType, outputType)`.
- `using: @Nullable String` — the `@Map(using = "...")` method name if present, otherwise null.

`RoutableKey` SHALL be a nested `@Value` class with fields `inputType: TypeMirror` and `outputType: TypeMirror`.

#### Scenario: Context exposes a RoutableKey nested class

- **WHEN** a developer references `ExpansionContext.RoutableKey`
- **THEN** the nested class SHALL be present with `inputType` and `outputType` fields
