## ADDED Requirements

### Requirement: Diagnostics walk unsatisfied demands

`RealisationDiagnosticsStage` SHALL walk the unsatisfied demands after expansion: a method whose
return-root `Value` is UNSAT produces a diagnostic naming the unresolved target. The walk uses the
vertex SAT predicate; no group outcome records exist.

#### Scenario: Unsatisfiable method is diagnosed
- **WHEN** expansion ends with a method's return-root UNSAT
- **THEN** one error diagnostic is emitted naming the method and the unresolved target, and code
  generation skips the mapper without throwing

### Requirement: Closest-miss is the deepest unsatisfied port chain

The UNSAT diagnostic SHALL include a closest-miss walk over the bipartite graph: from the
unsatisfied demand, follow the producer `Operation` that is closest to satisfaction (fewest
unsatisfied ports), naming each unsatisfied port (name, declared type, nullness) down to the
deepest Value that has no producer at all. The parameter-root base case is reported as the
satisfied anchor.

#### Scenario: Missing source names the starving port
- **WHEN** `new Address(int number, String street)` is UNSAT because no binding feeds `street`
- **THEN** the diagnostic names the constructor, the unsatisfied port `street : String`, and reports
  the port Value as having no producer

## REMOVED Requirements

### Requirement: RealisationDiagnosticsStage iterates UNSAT GroupOutcomes
**Reason**: No group outcomes; diagnostics read vertex SAT state.
**Migration**: See ADDED "Diagnostics walk unsatisfied demands".

### Requirement: UNSAT_NO_PLAN diagnostic includes closest-miss walk
**Reason**: Restated over port chains.
**Migration**: See ADDED "Closest-miss is the deepest unsatisfied port chain".

### Requirement: Base-case SAT clause for parameter-root slots
**Reason**: The base case is owned by `graph-expansion` Horn propagation; diagnostics only report
it.
**Migration**: See `graph-expansion` ADDED "Horn SAT propagation".

### Requirement: UNSAT_DID_NOT_CONVERGE diagnostic
**Reason**: Horn unit propagation is monotone over a finite vertex set; it always converges. The
failure mode no longer exists.
**Migration**: None — the diagnostic is unreachable and is deleted.
