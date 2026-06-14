## MODIFIED Requirements

### Requirement: Diagnostics walk unsatisfied demands

`RealisationDiagnosticsStage` SHALL walk the unsatisfied demands after expansion: a method whose
return-root `Value` is **unreachable** produces a diagnostic naming the unresolved target. "Unsatisfied"
SHALL mean **infinite extraction cost** — a `Value` with no finite-cost producer — queried through the
extracted plan (`reachable(value) == false`); there is no stored SAT predicate and no group outcome
records.

#### Scenario: Unsatisfiable method is diagnosed
- **WHEN** expansion ends with a method's return-root unreachable (infinite extraction cost)
- **THEN** one error diagnostic is emitted naming the method and the unresolved target, and code
  generation skips the mapper without throwing
