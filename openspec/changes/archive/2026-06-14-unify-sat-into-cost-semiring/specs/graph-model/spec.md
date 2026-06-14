## MODIFIED Requirements

### Requirement: MapperGraph wrapper over the bipartite graph

`MapperGraph` SHALL wrap the underlying `DirectedMultigraph<GraphVertex, Dep>`, exposing `valueFor`,
delta application (Applier-only), scope-tree access, and read-only views (`MaskSubgraph`-based). It
SHALL NOT store a satisfaction predicate (no `markSat`/`isSat`/`clearSat`): reachability is a derived
query over extraction cost (see `plan-extraction`). It remains append-only after construction:
vertices and edges are never removed; plan selection is a view, not a mutation.

#### Scenario: Append-only mutation surface
- **WHEN** the public surface of `MapperGraph` is inspected
- **THEN** no vertex- or edge-removal operation is exposed

#### Scenario: No stored satisfaction predicate
- **WHEN** the `MapperGraph` surface is inspected
- **THEN** no `markSat` / `isSat` / `clearSat` / SAT-set member is present; reachability is obtained
  from the extracted plan instead

## ADDED Requirements

### Requirement: Location carries a role

`Location` SHALL expose a `role()` returning one of `SUPPLY` (parameter / source-path values),
`DEMAND` (target values), `ELEMENT` (container child-scope element roots), or `CONSTANT`. The
supply-root, demand, element, and constant distinctions used by extraction and the driver SHALL be
made through `role()`, not through `instanceof` on the concrete `Location` implementations, so the
distinction lives in one place.

#### Scenario: Supply-root base case keys off role
- **WHEN** the cost fold determines whether a producerless `Value` is a base case
- **THEN** it consults `value.getLoc().role() == SUPPLY` (or `ELEMENT`), not an `instanceof` chain

#### Scenario: Each Location implementation reports its role
- **WHEN** `SourceLocation`, `TargetLocation`, `ElementLocation`, and `ConstantLocation` are inspected
- **THEN** they report `SUPPLY`, `DEMAND`, `ELEMENT`, and `CONSTANT` respectively
