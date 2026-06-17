# Realisation Validation Spec

## Purpose

This spec defines the post-expansion validation stage that emits diagnostics for demands that did not satisfy. `RealisationDiagnosticsStage` walks unsatisfied demands — a `Value` with no reachable (finite-cost) producer, or an `Operation` naming its unsatisfied ports — and produces user-facing error messages with closest-miss information.

The validation model is **demand-driven, not topology-driven**: the engine derives, via the plan-extraction minimum-cost fold, which Values and Operations are reachable (finite cost). The validation stage's job is to translate the unsatisfied demands into actionable diagnostics — closest-miss is the deepest unsatisfied port chain — not to re-derive realisability from edge inspection.

## Requirements

### Requirement: Diagnostics anchor on the MapperContext.mapperType

`RealisationDiagnosticsStage` SHALL pass `ctx.getMapperType()` (the `TypeElement` of the mapper-under-compilation) as the `Element` location argument to `Diagnostics.error(...)`. The IDE that consumes the diagnostic underlines the mapper class name (not individual `@Map` annotations).

Anchoring at the mapper-type level is intentional: with the demand work-list model, the failing demand is not tied to a single `@Map` directive — an unreachable target often crosses multiple directives and conversion hops, so the mapper class name is the stable locus.

#### Scenario: Diagnostic carries the mapper TypeElement
- **WHEN** an unreachable demand's diagnostic is emitted
- **THEN** the `Diagnostics.error(...)` call passes `ctx.getMapperType()` as its location argument

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
