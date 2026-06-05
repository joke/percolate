## MODIFIED Requirements

### Requirement: EdgeKind enum

The processor SHALL define an enum `EdgeKind` in `io.github.joke.percolate.processor.graph` with exactly three values:

- `SEED` — directive-seeded framing edges produced by `SeedGraph` from user `@Map` directives. ∞-weight (`Weights.SENTINEL_UNREALISED`).
- `REALISED` — transformation edges produced by `ExpansionStrategy` strategies (bridge, assembly, conversion, and container) during expansion. These are the codegen substrate.
- `MARKER` — `realises` edges linking an untyped seed node to its typed counterpart. Weight `Weights.NOOP`.

`EdgeKind` SHALL be referenced by `Edge.kind` and SHALL participate in `Edge` equality, hash, and comparison.

The earlier `SUB_SEED` and `ELEMENT_SEED` values were removed when target-to-source per-group expansion replaced forward-driven `SUB_SEED` emission. Nested expansion work is expressed via `ExpansionGroup` registration, not via new SEED edge variants.

#### Scenario: EdgeKind has exactly three values
- **WHEN** `EdgeKind.values()` is invoked
- **THEN** the result contains exactly `SEED`, `REALISED`, `MARKER` in declaration order

#### Scenario: SUB_SEED and ELEMENT_SEED are not present
- **WHEN** the source of `EdgeKind` is inspected
- **THEN** no `SUB_SEED` or `ELEMENT_SEED` constant is declared

### Requirement: Edge value type
The processor SHALL define a Lombok `@Value` class `Edge` in `io.github.joke.percolate.processor.graph` with the following fields:
- `Node from`
- `Node to`
- `int weight` — uses the scale documented in `Weights`. `SEED` edges use `Weights.SENTINEL_UNREALISED`. `REALISED` edges use a value in `{0, 1, 2, 3}`. `MARKER` edges use `Weights.NOOP`.
- `EdgeKind kind` — categorises the edge for view filtering, DOT styling, and dispatch.
- `Optional<AnnotationMirror> directive` — present when the edge was seeded by a user `@Map` directive (i.e., `kind == SEED` and emitted from `SeedGraph`); empty for `REALISED` and `MARKER` edges.
- `Optional<Codegen> codegen` — present on `REALISED` edges; empty on `SEED` and `MARKER` edges. The value is a member of the `Codegen` family: an `EdgeCodegen` for a scalar edge, or a **container provider** (`ContainerCodegen`/`WrapperCodegen`) for a container edge. The composer reads it and, for a container provider, asks for the paradigm-appropriate snippet.
- `Optional<ElementScope> elementScope` — present (`ENTERING` / `EXITING`) on a container edge that crosses element scope, empty on a scalar (scope-preserving) edge. Persisted from the producing `ExpansionStep`'s `ElementScope`. For container edges the composer derives the container operation (iterate/collect/unwrap/map) from `(elementScope, isStream-of-child, handle-kind)`. Scalar edges (including the single-element `wrap`) carry empty `elementScope`.
- `Optional<String> strategyClassFqn` — fully-qualified class name of the strategy that emitted this edge; populated by strategies via `getClass().getName()` at edge construction; empty for edges emitted by `SeedGraph` (which is not a strategy).

`Edge` SHALL be annotated `@Value @EqualsAndHashCode(exclude = {"codegen", "elementScope", "strategyClassFqn"})` so that equality and hashing are structural over `(from, to, weight, kind, directive)`. The `codegen`, `elementScope`, and `strategyClassFqn` fields are emission metadata and SHALL NOT participate in equality.

`Edge` SHALL implement `Comparable<Edge>` ordered by `(from.id(), to.id(), weight, kind, presence-of-directive)` so that sorted iteration of edges is well-defined.

`Edge` SHALL provide static factory methods. The all-args constructor SHALL be package-private; consumers SHALL go through the factories:

- `Edge.seed(Node from, Node to, Optional<AnnotationMirror> directive, Optional<String> strategyClassFqn)` — produces a SEED edge with `kind = SEED`, `weight = Weights.SENTINEL_UNREALISED`, the supplied `directive` and `strategyClassFqn`, empty `codegen`, empty `elementScope`. User-directive seeds pass non-empty `directive` and empty `strategyClassFqn`.
- `Edge.realised(Node from, Node to, int weight, EdgeCodegen codegen, String strategyClassFqn)` — scalar REALISED edge with `kind = REALISED`, the supplied weight, `directive` empty, `codegen` populated, empty `elementScope`, `strategyClassFqn` populated.
- `Edge.realised(Node from, Node to, int weight, Codegen provider, ElementScope elementScope, String strategyClassFqn)` — container REALISED edge carrying the container provider and its element-scope crossing.
- `Edge.marker(Node from, Node to, String strategyClassFqn)` — produces a MARKER edge with `kind = MARKER`, `weight = Weights.NOOP`, `directive`, `codegen`, and `elementScope` empty, `strategyClassFqn` populated.

The forward-expansion factories `Edge.subSeed(...)` and `Edge.elementSeed(...)` are removed. Group membership of a `REALISED` edge is determined by membership in an `ExpansionGroup.view().edgeSet()`; `Edge` does NOT carry a `groupId` field.

#### Scenario: Directive-seeded edge carries the mirror, kind SEED, sentinel weight
- **WHEN** `Edge.seed(...)` is invoked with a `@Map(target = "lastName", source = "lastName")` directive's mirror
- **THEN** the resulting edge has `kind == EdgeKind.SEED`, `weight == Weights.SENTINEL_UNREALISED`, non-empty `directive` containing that `AnnotationMirror`, empty `codegen`

#### Scenario: Realised edge factory populates codegen and strategyClassFqn
- **WHEN** `Edge.realised(from, to, Weights.STEP, <closure>, "com.example.SomeBridge")` is invoked
- **THEN** the resulting edge has `kind == EdgeKind.REALISED`, `weight == 1`, empty `directive`, non-empty `codegen`, empty `elementScope`, `strategyClassFqn == "com.example.SomeBridge"`

#### Scenario: Container realised edge carries provider and element-scope crossing
- **WHEN** `Edge.realised(from, to, Weights.COPY, <SequenceContainer>, ElementScope.EXITING, "io.github.joke.percolate.spi.builtins.SetContainer")` is invoked
- **THEN** the edge has `kind == REALISED`, `codegen` holding the container provider, `elementScope == Optional.of(ElementScope.EXITING)`

#### Scenario: Marker edge has weight zero, no codegen
- **WHEN** `Edge.marker(seedNode, realisedNode, "com.example.SomeResolver")` is invoked
- **THEN** the resulting edge has `kind == EdgeKind.MARKER`, `weight == 0`, empty `directive`, empty `codegen`, non-empty `strategyClassFqn`

#### Scenario: Edge equality excludes codegen, elementScope, and strategyClassFqn
- **WHEN** two `Edge` instances are constructed with field-equal `(from, to, weight, kind, directive)` but different `codegen`, `elementScope`, or `strategyClassFqn`
- **THEN** they compare equal under `equals` and produce identical hash codes

#### Scenario: Edge equality includes kind
- **WHEN** two `Edge` instances are constructed with field-equal `(from, to, weight, directive)` but different `kind` values
- **THEN** they compare unequal under `equals`

#### Scenario: Edges have stable ordering
- **WHEN** two `Edge` values are compared
- **THEN** the comparison is determined entirely by `from.id()`, then `to.id()`, then `weight`, then `kind`, then directive-presence (no reliance on identity hashes)
