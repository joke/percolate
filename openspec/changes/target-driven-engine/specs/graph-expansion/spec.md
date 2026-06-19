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

### Requirement: The work-list holds only concrete-typed Values

Every `Value` on the work-list SHALL have a concrete type. The engine SHALL NOT create or demand a `Value` whose type is an unbound type variable; type variables exist only inside `OperationSpec` ports and are grounded before any demand is enqueued.

#### Scenario: No Value is created for a type variable
- **WHEN** expansion runs to completion
- **THEN** no `Value` in the graph has a type variable as its type
