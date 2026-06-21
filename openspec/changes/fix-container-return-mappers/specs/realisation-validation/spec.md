## MODIFIED Requirements

### Requirement: Diagnostics walk unsatisfied demands

`RealisationDiagnosticsStage` SHALL walk the unsatisfied demands after expansion: a method whose
**seeded return-root `Value`** (the graph-recorded return root per `graph-expansion`, **not** every
`Value` at the empty-path return location) is **unreachable** produces a diagnostic naming the
unresolved target. Over-emitted typed siblings that merely share the return location — e.g. a dead
`Set<E>`/`Optional<E>`/scalar `E` candidate minted while the real return is `List<E>` — SHALL NOT be
treated as return roots and SHALL NOT produce a diagnostic. "Unsatisfied" SHALL mean **infinite
extraction cost** — a `Value` with no finite-cost producer — queried through the extracted plan
(`reachable(value) == false`); there is no stored SAT predicate and no group outcome records.

#### Scenario: Unsatisfiable method is diagnosed
- **WHEN** expansion ends with a method's seeded return-root unreachable (infinite extraction cost)
- **THEN** one error diagnostic is emitted naming the unresolved return-root target (its location
  label) and the closest-miss demand, and code generation skips the mapper without throwing

#### Scenario: An earlier targeted diagnostic suppresses the generic message
- **WHEN** the mapper already has an error (e.g. a constant coercion failure or dead default)
- **THEN** `RealisationDiagnosticsStage` emits no "no plan" message (it returns early on
  `diagnostics.hasErrorsFor(mapperType)`)

#### Scenario: Dead typed siblings at the return location are not diagnosed
- **WHEN** a container-return method's seeded root `List<E>` is reachable but over-emission left
  unreachable typed siblings (`Set<E>`, `Optional<E>`, scalar `E`, the source-side element types, …)
  at the same return location
- **THEN** no `no plan for tgt[]` diagnostic is emitted for any of those siblings; only an unreachable
  *seeded* return root is ever diagnosed
