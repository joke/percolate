## MODIFIED Requirements

### Requirement: Diagnostics anchor on the MapperContext.mapperType

`RealisationDiagnosticsStage` SHALL **record** the realisation outcome into the per-mapper `MapperContext` rather than emit it in-stage. When that recorded outcome is later emitted by `MapperStep` (flushed at `processingOver` for a still-deferred mapper), the `Diagnostics.error(...)` call SHALL pass the mapper `TypeElement` (re-resolved by fully-qualified name) as the `Element` location argument. The IDE that consumes the diagnostic underlines the mapper class name (not individual `@Map` annotations).

Anchoring at the mapper-type level is intentional: with the demand work-list model, the failing demand is not tied to a single `@Map` directive — an unreachable target often crosses multiple directives and conversion hops, so the mapper class name is the stable locus. Moving emission to `MapperStep` does not change the anchor.

#### Scenario: Recorded diagnostic is anchored on the mapper TypeElement
- **WHEN** `MapperStep` emits a recorded realisation diagnostic for an unreachable demand
- **THEN** the `Diagnostics.error(...)` call passes the mapper's `TypeElement` (`ctx.getMapperType()`) as its location argument

### Requirement: Diagnostics walk unsatisfied demands

`RealisationDiagnosticsStage` SHALL walk the unsatisfied demands after expansion: a method whose
**seeded return-root `Value`** (the graph-recorded return root per `graph-expansion`, **not** every
`Value` at the empty-path return location) is **unreachable** contributes a closest-miss message
naming the unresolved target. Over-emitted typed siblings that merely share the return location —
e.g. a dead `Set<E>`/`Optional<E>`/scalar `E` candidate minted while the real return is `List<E>` —
SHALL NOT be treated as return roots and SHALL NOT contribute a message. "Unsatisfied" SHALL mean
**infinite extraction cost** — a `Value` with no finite-cost producer — queried through the
extracted plan (`reachable(value) == false`); there is no stored SAT predicate and no group outcome
records.

The stage SHALL **record** the collected messages onto `MapperContext` (an ordered list, empty when
the mapper is fully realised) and SHALL NOT call `Diagnostics.error(...)` itself; emission is owned
by `MapperStep` and flushed at `processingOver` for a still-deferred mapper. As today, when the mapper is already scarred
(`diagnostics.hasErrorsFor(mapperType)`) the stage SHALL record nothing (a targeted earlier
diagnostic already explains the failure).

#### Scenario: Unsatisfiable method is recorded, not emitted in-stage
- **WHEN** expansion ends with a method's seeded return-root unreachable (infinite extraction cost)
- **THEN** `RealisationDiagnosticsStage` records one closest-miss message naming the unresolved
  return-root target (its location label) and the deepest-miss demand onto `MapperContext`
- **AND** it does not call `Diagnostics.error(...)` directly
- **AND** code generation skips the mapper without throwing

#### Scenario: An earlier targeted diagnostic suppresses the recorded message
- **WHEN** the mapper already has an error (e.g. a constant coercion failure or dead default)
- **THEN** `RealisationDiagnosticsStage` records no "no plan" message (it returns early on
  `diagnostics.hasErrorsFor(mapperType)`)

#### Scenario: Dead typed siblings at the return location are not recorded
- **WHEN** a container-return method's seeded root `List<E>` is reachable but over-emission left
  unreachable typed siblings (`Set<E>`, `Optional<E>`, scalar `E`, the source-side element types, …)
  at the same return location
- **THEN** no `no plan for tgt[]` message is recorded for any of those siblings; only an unreachable
  *seeded* return root is ever recorded
