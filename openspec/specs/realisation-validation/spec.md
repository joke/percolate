# Realisation Validation Spec

## Purpose

This spec defines the post-expansion validation stage that emits diagnostics for demands that did not satisfy. `RealisationDiagnosticsStage` walks unsatisfied demands — a `Value` with no SAT producer, or an `Operation` naming its unsatisfied ports — and produces user-facing error messages with closest-miss information.

The validation model is **demand-driven, not topology-driven**: the engine has already decided, via Horn propagation, which Values and Operations are SAT during expansion. The validation stage's job is to translate the unsatisfied demands into actionable diagnostics — closest-miss is the deepest unsatisfied port chain — not to re-derive realisability from edge inspection.

## Requirements

### Requirement: Diagnostics anchor on the MapperContext.mapperType

`RealisationDiagnosticsStage` SHALL pass `ctx.getMapperType()` (the `TypeElement` of the mapper-under-compilation) as the `Element` location argument to `Diagnostics.error(...)`. The IDE that consumes the diagnostic underlines the mapper class name (not individual `@Map` annotations).

Anchoring at the mapper-type level is intentional: with the per-group greedy model, the failing slot's `@Map` directive is no longer the natural locus of the diagnostic — the failure is at the group/slot level, often crossing multiple directives.

#### Scenario: Diagnostic carries the mapper TypeElement
- **WHEN** an UNSAT outcome's diagnostic is emitted
- **THEN** the `Diagnostics.error(...)` call passes `ctx.getMapperType()` as its location argument

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
