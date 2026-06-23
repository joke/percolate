## MODIFIED Requirements

### Requirement: Expansion self-seeds root demands from an empty graph

Expansion SHALL begin with an **empty** graph and seed itself: for each abstract mapper method it
SHALL enqueue exactly one demand for the method's return type (the return-root `Value`) and SHALL
**record that seeded `Value` as the method's return root** on the graph, so downstream stages identify
the return root by this recorded identity rather than by location alone. Over-emission later mints
conversion way-points at the **same** return location but with a different type — e.g. a `Stream<E>`
intermediate minted while producing a `List<E>` root — and those way-points are ordinary intermediates,
never return roots.

A scope's **output root is an eager demand; its input roots are lazy sources.** Every base-case input
`LEAF` `Value` — a method parameter *or* a container element root — SHALL be **declared** by its scope
(see "Scopes declare base-case inputs uniformly") and materialised lazily on first reference (when an
accessor chain bottoms out at it, or a port is bound to it), so an unreferenced input never enters the
graph. This SHALL hold uniformly for method scopes and child (element) scopes; the engine SHALL NOT
special-case a scope kind when materialising input roots. There SHALL be no separate seed stage.

#### Scenario: The graph starts empty and grows by demand

- **WHEN** expansion begins for a mapper
- **THEN** the graph contains no vertices until the return-root demand is enqueued and processed

#### Scenario: An unused parameter is never materialised

- **WHEN** a method declares a parameter no binding ever sources from
- **THEN** no `Value` for that parameter exists in the graph after expansion

#### Scenario: An unused element root is never materialised

- **WHEN** a container element transform produces a value that never sources from the element (e.g. it maps each element to a constant)
- **THEN** no element param-root `Value` exists in the graph after expansion, while the element lambda is still generated binding its parameter (see code-generation "Child scopes render as lambda bodies")

#### Scenario: A typed sibling at the return location is not a return root

- **WHEN** producing a container return root `List<E>` over-emits an intermediate `Stream<E>` (and other typed candidates) at the same empty-path return location
- **THEN** only the seeded `List<E>` Value is recorded as the method's return root; the same-location intermediates are not, despite sharing the location

## ADDED Requirements

### Requirement: Scopes declare base-case inputs uniformly

Each `Scope` SHALL declare its base-case inputs as a lazy sequence of input declarations, where an
input declaration is a scope-relative `(Location, type, nullness)` — an `AddValue` lacking only its
scope. A `MethodScope` SHALL declare one per method parameter (a single-segment `SourceLocation`); a
`ChildScope` SHALL declare its single element input (an `ElementLocation` with the element-in
type/nullness); the mapper-root scope SHALL declare none.

Source-binding and grounding-by-match SHALL consume this declaration **uniformly**, with no
`instanceof` test on the scope kind:

- the in-scope source **types** offered to grounding SHALL include the declared input types, available
  **without** materialising any `Value`;
- a port whose `(type, nullness)` matches a declaration SHALL be fed by materialising that declaration
  as a `LEAF` source `Value` on demand, idempotent through the `Value` dedup index.

The single intentional exception is the self-call rule, which is method-scope-only **by meaning** (a
named self-call) and remains so — see "A method never calls itself on its own whole parameter".

#### Scenario: Grounding sees a declared input type without materialising it

- **WHEN** grounding-by-match gathers the in-scope source types for a type-variable port in a scope
- **THEN** the scope's declared input types are included even when no corresponding `Value` has yet been materialised

#### Scenario: A matched input declaration is materialised on demand

- **WHEN** a port's `(type, nullness)` matches a scope input declaration and no in-scope source `Value` yet exists
- **THEN** the declaration is materialised as a `LEAF` source `Value` in that scope
- **AND** a second match for the same declaration returns the same `Value` (dedup-idempotent)

#### Scenario: Input sourcing does not branch on scope kind

- **WHEN** the driver sources a port's input in a method scope versus in a child (element) scope
- **THEN** the same declaration-driven path is taken for both, with no `instanceof MethodScope` branch in source-binding, grounding, or accessor-root typing
