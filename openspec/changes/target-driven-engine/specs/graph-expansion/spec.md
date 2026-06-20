## ADDED Requirements

### Requirement: Type-variable ports are sourced by grounding-by-match

When an `OperationSpec` port carries a type variable, the driver SHALL source it by **matching** the port type against the in-scope source `Value`s, grounding the variable to each matching source's concrete type, substituting it across the Operation's output and child scope, and landing one concrete Operation per match through the `Applier`. The driver SHALL NOT enqueue an unbound type. This is the same port-sourcing step as for a concrete port, generalised from exact-type matching to unification; it remains strictly target→source (`never_forward` holds) and over-emit-and-prune.

#### Scenario: A type-variable port grounds and lands concretely
- **WHEN** the driver sources a `Set<A>` port with a `Set<Person>` source in scope
- **THEN** it grounds `A := Person`, substitutes into the Operation (output and child scope), and lands a concrete `Set<Person> → …` Operation via the Applier
- **AND** it never enqueues a Value typed `Set<A>`

#### Scenario: Grounding-by-match preserves target-driven order
- **WHEN** grounding-by-match sources a type-variable port
- **THEN** the produced concrete inputs are re-demanded target→source like any other port; no forward sweep from sources occurs

### Requirement: Grounding's match set is widened by SourceProjections

Before grounding a type-variable port, the driver SHALL widen its match set: in addition to the in-scope source `Value` types it SHALL include every registered `SourceProjection`'s one-step view of each in-scope source. Unification then proceeds **unchanged** against the widened set. This is what bootstraps a cross-kind pipeline — a `Stream<A>` port has no direct `Stream` source but grounds against the `Stream<X>` a `List<X>` source projects to — while keeping the engine type-agnostic (it calls `project` generically and names no kind) and preserving `never_forward` (a projection is a declarative one-step view of an in-scope source, not a forward sweep; the grounded concrete type enters the work-list as an ordinary target-driven demand).

#### Scenario: A cross-kind port grounds via a projection
- **WHEN** a `Stream<A>` port is sourced with only a `List<Optional<Paw>>` source in scope and a collection→stream `SourceProjection` registered
- **THEN** the projection contributes `Stream<Optional<Paw>>`, the port grounds `A := Optional<Paw>`, and a concrete `Stream<Optional<Paw>>` (produced by the list's `iterate`) enters the work-list

#### Scenario: With no projections, grounding uses only the raw sources
- **WHEN** no `SourceProjection` is registered
- **THEN** the match set is exactly the in-scope source types, and a cross-kind port with no direct source grounds nothing (additive: concrete-port sourcing is unchanged)

### Requirement: The work-list holds only concrete-typed Values

Every `Value` on the work-list SHALL have a concrete type. The engine SHALL NOT create or demand a `Value` whose type is an unbound type variable; type variables exist only inside `OperationSpec` ports and are grounded before any demand is enqueued.

#### Scenario: No Value is created for a type variable
- **WHEN** expansion runs to completion
- **THEN** no `Value` in the graph has a type variable as its type
