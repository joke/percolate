## MODIFIED Requirements

### Requirement: EdgeCodegen marker interface
`EdgeCodegen` SHALL be a member of the `Codegen` handle family: it SHALL extend the `Codegen` marker interface defined by the container-codegen SPI. It represents a scalar closure attached to a `REALISED` edge that renders one expression at codegen time:

```java
interface EdgeCodegen extends Codegen {
    CodeBlock render(VarNames vars, IncomingValues inputs);
}
```

`Edge.codegen` references the `Codegen` family (an `EdgeCodegen` for a scalar edge, a container provider for a container edge), not `EdgeCodegen` exclusively.

#### Scenario: EdgeCodegen is a Codegen
- **WHEN** the `EdgeCodegen` interface is inspected
- **THEN** it extends `Codegen`

#### Scenario: Edge.codegen holds the Codegen family
- **WHEN** an `Edge` instance is inspected for the type of `codegen`
- **THEN** the field type is `Optional<Codegen>`

### Requirement: Edge value type
The processor SHALL define a Lombok `@Value` class `Edge` in `io.github.joke.percolate.processor.graph` with the following fields:
- `Node from`
- `Node to`
- `int weight` — uses the scale documented in `Weights`. `SEED` edges use `Weights.SENTINEL_UNREALISED`. `REALISED` edges use a value in `{0, 1, 2, 3}`. `MARKER` edges use `Weights.NOOP`.
- `EdgeKind kind` — categorises the edge for view filtering, DOT styling, and dispatch.
- `Optional<AnnotationMirror> directive` — present when the edge was seeded by a user `@Map` directive; empty for `REALISED` and `MARKER` edges.
- `Optional<Codegen> codegen` — present on `REALISED` edges; empty on `SEED` and `MARKER` edges. The value is a member of the `Codegen` family: an `EdgeCodegen` for a scalar edge, or a **container provider** (`ContainerCodegen`/`WrapperCodegen`) for a container edge. The composer reads it and, for a container provider, asks for the paradigm-appropriate snippet.
- `ScopeTransition scopeTransition` — `PRESERVING`/`ENTERING`/`EXITING`, persisted from the producing `BridgeStep`. For container edges the composer derives the container operation (iterate/collect/wrap/unwrap/map) from `(scopeTransition, isStream-of-child, handle-kind)`. Scalar edges are `PRESERVING`.
- `Optional<String> strategyClassFqn` — fully-qualified class name of the strategy that emitted this edge; empty for `SeedGraph` edges.

`Edge` SHALL be annotated `@Value @EqualsAndHashCode(exclude = {"codegen", "scopeTransition", "strategyClassFqn"})` so equality and hashing remain structural over `(from, to, weight, kind, directive)`. `codegen`, `scopeTransition`, and `strategyClassFqn` are emission metadata and SHALL NOT participate in equality.

`Edge` SHALL implement `Comparable<Edge>` ordered by `(from.id(), to.id(), weight, kind, presence-of-directive)`.

`Edge` SHALL provide static factory methods; the all-args constructor SHALL be package-private:

- `Edge.seed(Node from, Node to, Optional<AnnotationMirror> directive, Optional<String> strategyClassFqn)` — SEED edge, `scopeTransition = PRESERVING`, empty `codegen`.
- `Edge.realised(Node from, Node to, int weight, EdgeCodegen codegen, String strategyClassFqn)` — scalar REALISED edge, `scopeTransition = PRESERVING`.
- `Edge.realised(Node from, Node to, int weight, Codegen provider, ScopeTransition transition, String strategyClassFqn)` — container REALISED edge carrying the container provider and its transition.
- `Edge.marker(Node from, Node to, String strategyClassFqn)` — MARKER edge, `scopeTransition = PRESERVING`, empty `codegen`.

Group membership of a `REALISED` edge is determined by membership in an `ExpansionGroup.view().edgeSet()`; `Edge` does NOT carry a `groupId` field.

#### Scenario: Scalar realised edge factory populates codegen and PRESERVING transition
- **WHEN** `Edge.realised(from, to, Weights.STEP, <EdgeCodegen>, "com.example.SomeBridge")` is invoked
- **THEN** the edge has `kind == REALISED`, non-empty `codegen` holding the `EdgeCodegen`, `scopeTransition == PRESERVING`

#### Scenario: Container realised edge carries provider and transition
- **WHEN** `Edge.realised(from, to, Weights.CONTAINER, <SequenceContainer>, ScopeTransition.EXITING, "io.github.joke.percolate.spi.builtins.SetContainer")` is invoked
- **THEN** the edge has `kind == REALISED`, `codegen` holding the container provider, `scopeTransition == EXITING`

#### Scenario: Edge equality excludes codegen, scopeTransition, and strategyClassFqn
- **WHEN** two `Edge` instances are constructed with field-equal `(from, to, weight, kind, directive)` but different `codegen`, `scopeTransition`, or `strategyClassFqn`
- **THEN** they compare equal under `equals` and produce identical hash codes

#### Scenario: Edges have stable ordering
- **WHEN** two `Edge` values are compared
- **THEN** the comparison is determined by `from.id()`, then `to.id()`, then `weight`, then `kind`, then directive-presence
