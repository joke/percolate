## ADDED Requirements

### Requirement: Harness results expose the bipartite surface

`ExpansionResult` SHALL expose the bipartite state for assertions: the Values and Operations per
scope, the SAT predicate, the unsatisfied demands, and the extracted plan view. Group- and
outcome-based accessors are removed. Invariant checks SHALL cover: every in-plan Value has exactly
one chosen producer, no `Dep` edge crosses a scope boundary, and every Operation's inbound edges
match its port signature exactly.

#### Scenario: Invariants run on every harness expansion
- **WHEN** a harness test expands a fixture graph
- **THEN** the result's invariant checks verify single-chosen-producer, scope-boundary, and
  port-signature completeness, failing the test with a rendered DOT excerpt on violation

## REMOVED Requirements

### Requirement: Invariant checks exposed on `ExpansionResult`
**Reason**: The checked invariants were group-shaped (group views, outcomes, slot typing).
**Migration**: See ADDED "Harness results expose the bipartite surface".
