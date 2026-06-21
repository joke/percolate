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

### Requirement: Closest-miss is the deepest unsatisfied port chain

The UNSAT diagnostic SHALL include a closest-miss walk over the bipartite graph: from the
unsatisfied return-root demand, follow an **unreachable** producer `Operation` (the first in
deterministic `Operation.id()` order) and descend its **first unreachable port** `Value` at each
step, down to the deepest demand that has no reachable producer at all. The emitted message SHALL
name the unresolved root target (its return-root location label), the deepest-miss demand (its
location label), and that demand's type — with a hint that a `@Map`-annotated method producing that
type is likely missing.

#### Scenario: Missing source names the starving demand
- **WHEN** `new Address(int number, String street)` is UNSAT because no binding feeds `street`
- **THEN** the diagnostic names the unresolved root target and the deepest-miss demand for `street`
  (its location label and `String` type), reporting it as having no producer in the graph
