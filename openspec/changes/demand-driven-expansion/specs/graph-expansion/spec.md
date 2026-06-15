## ADDED Requirements

### Requirement: Resolution mode dispatches the single work-list

The work-list SHALL dispatch each demanded `Value` on a **resolution mode** set when the demand is
created and derivable from its `Location` (kind + access-path length). There SHALL be exactly one
dispatch site and exactly one strategy-invocation site; there SHALL be no eager, forward, or
otherwise separate descent engine, and no second strategy dispatch.

- `FREE` (a `TargetLocation` demand or a conversion intermediate): the full strategy set is run; each
  emitted Operation's ports become new demands.
- `ACCESS` (a multi-segment `SourceLocation` demand): only accessor strategies may produce it; the
  resolved accessor Operation re-demands the **parent** path. Assembly strategies SHALL NOT fire on an
  `ACCESS` demand.
- `LEAF` (a single-segment `SourceLocation` parameter, or an element root): a base case — no
  expansion.
- `CONSTANT`: only a constant strategy may produce it.

#### Scenario: A multi-segment source demand expands via the work-list, not a descent engine

- **WHEN** a `Value` at `SourceLocation[p, address, street]` is demanded
- **THEN** an accessor strategy emits the `getStreet()` Operation and a demand for
  `SourceLocation[p, address]` is enqueued on the same work-list
- **AND** no eager whole-path materialisation, no `descend`/`resolveAccessor` component, and no
  separate `descended` memo participates

#### Scenario: Assembly does not fire on an ACCESS demand

- **WHEN** an `ACCESS` (multi-segment source) demand whose type has a public constructor is processed
- **THEN** `ConstructorCall` does not emit an Operation for it; only accessor strategies may

#### Scenario: A single-segment source demand is a leaf

- **WHEN** a `Value` at `SourceLocation[p]` (a parameter) is processed
- **THEN** it is treated as a base case and is not expanded

### Requirement: Expansion self-seeds root demands from an empty graph

Expansion SHALL begin with an **empty** graph and seed itself: for each abstract mapper method it
SHALL enqueue exactly one demand for the method's return type (the return-root `Value`). Parameter
`LEAF` Values SHALL be created lazily on first reference (when an accessor chain bottoms out at them
or a candidate is bound to one), so an unreferenced parameter never enters the graph. There SHALL be
no separate seed stage.

#### Scenario: The graph starts empty and grows by demand

- **WHEN** expansion begins for a mapper
- **THEN** the graph contains no vertices until the return-root demand is enqueued and processed

#### Scenario: An unused parameter is never materialised

- **WHEN** a method declares a parameter no binding ever sources from
- **THEN** no `Value` for that parameter exists in the graph after expansion

## MODIFIED Requirements

### Requirement: All graph mutation flows through the Applier

All graph mutation SHALL flow through the single `Applier`, which interprets `AddValue`/`AddOperation`
deltas. This includes the **initial root demands** (each enqueued return-root `Value` is created as a
bare `AddValue` through the `Applier`) and lazily-materialised parameter `LEAF`s. No stage SHALL
mutate the graph directly via `MapperGraph.valueFor(...)` or any other bypass; the previous seed-time
`valueFor` carve-out is removed.

#### Scenario: Expanders never mutate directly

- **WHEN** expansion sources are inspected
- **THEN** no expander or strategy invokes a graph mutation method; only the `Applier` does

#### Scenario: No stage bypasses the Applier to seed the graph

- **WHEN** the processor's stage sources are inspected
- **THEN** no stage calls `MapperGraph.valueFor(...)` (or another mutation) directly to pre-populate
  the graph; root demands are landed as `AddValue` deltas through the `Applier`
