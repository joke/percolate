# Realisation Validation Spec

## Purpose

This spec defines the post-expansion validation stage that emits diagnostics for groups that did not satisfy. After `ExpandStage` records a `GroupOutcome` for every registered `ExpansionGroup`, `RealisationDiagnosticsStage` walks the unsatisfied outcomes and produces user-facing error messages with closest-miss information.

The validation model is **outcome-driven, not topology-driven**: the engine has already decided which groups SAT and which UNSAT during expansion. The validation stage's job is to translate UNSAT outcomes into actionable diagnostics, not to re-derive realisability from edge inspection.

## Requirements

### Requirement: RealisationDiagnosticsStage iterates UNSAT GroupOutcomes

The processor SHALL define a stage `RealisationDiagnosticsStage` in package `io.github.joke.percolate.processor.stages.validate` that consumes the post-expansion `MapperGraph` and emits one diagnostic per unsatisfied `GroupOutcome`. The stage SHALL be `@Inject`-constructed via Lombok `@RequiredArgsConstructor(onConstructor_ = @Inject)` taking a `Diagnostics` collaborator as constructor argument.

`RealisationDiagnosticsStage.run(MapperContext)` SHALL:

1. Return immediately if `ctx.getGraph()` is `null`.
2. Filter `graph.groupOutcomes()` to those whose `kind != GroupOutcome.Kind.SAT`.
3. For each UNSAT outcome, call `emitFor(graph, outcome, ctx)` which formats and emits a single error diagnostic via `Diagnostics.error(ctx.getMapperType(), message)`.

If an UNSAT outcome's `failingSlot` is empty (the engine couldn't even identify a failing slot — should not happen in practice), no diagnostic is emitted for that outcome.

#### Scenario: SAT groups emit no diagnostics
- **WHEN** the graph contains only `GroupOutcome.sat(...)` outcomes
- **THEN** `RealisationDiagnosticsStage.run(ctx)` emits zero diagnostics

#### Scenario: One UNSAT_NO_PLAN outcome emits one error
- **WHEN** the graph contains one `GroupOutcome.unsatNoPlan(group, failingSlot)`
- **THEN** one `Diagnostics.error(mapperType, message)` call is made
- **AND** the message mentions both the failing slot's rendered path and the closest-miss node's path

#### Scenario: Multiple UNSAT outcomes emit one error each
- **WHEN** the graph contains three UNSAT outcomes
- **THEN** three separate `Diagnostics.error` calls are made

#### Scenario: Stage skips when graph is absent
- **WHEN** `ctx.getGraph()` is `null`
- **THEN** the stage returns without emitting any diagnostic

### Requirement: UNSAT_NO_PLAN diagnostic includes closest-miss walk

For an outcome of kind `UNSAT_NO_PLAN`, the diagnostic message SHALL be of the form:

```
no plan for <slotPath>: <closestMissPath> has no producer in the graph. Likely missing: a @Map-annotated method whose source produces <closestMissType>
```

The closest-miss node SHALL be computed by `walkClosestMiss(graph, failingSlot)`: BFS the existing REALISED subgraph backwards from `failingSlot`, accumulating predecessors via REALISED edges, returning the deepest reached node. This is the node closest to source-side in the unsatisfied chain — the most informative point for the user to add a missing bridge.

The slot path SHALL be rendered by `renderSlotPath(node)`:
- For `TargetLocation` nodes: `tgt[<segments-joined-by-dot>]` or `tgt[]` if segments are empty.
- For other nodes: `Node.id()`.

The closest-miss type SHALL be the node's `TypeMirror.toString()`, or `?` if the type is empty.

#### Scenario: Closest-miss walk finds deepest predecessor
- **WHEN** an `UNSAT_NO_PLAN` outcome's failing slot has an existing REALISED chain `A → B → failingSlot` (B's incoming chain leads to A; A has no further predecessors)
- **THEN** `walkClosestMiss` returns `A`
- **AND** the message names `A`'s rendered path as the closest-miss point

#### Scenario: Closest-miss with no incoming REALISED edges falls back to slot itself
- **WHEN** the failing slot has no incoming REALISED edges
- **THEN** `walkClosestMiss` returns the failing slot
- **AND** the message names the slot's rendered path as the closest-miss point

#### Scenario: Diagnostic identifies the missing method's source type
- **WHEN** the closest-miss node carries type `Person.Address`
- **THEN** the message ends with `Likely missing: a @Map-annotated method whose source produces Person.Address`

### Requirement: UNSAT_DID_NOT_CONVERGE diagnostic

For an outcome of kind `UNSAT_DID_NOT_CONVERGE`, the diagnostic message SHALL be of the form:

```
no plan for <slotPath>: expansion did not converge within the per-slot round budget. Likely missing: a more direct conversion strategy
```

No closest-miss walk is performed for did-not-converge outcomes; the per-slot round-budget exhaustion implies the chain is unbounded (typically a self-multiplying bridge fire) and the closest-miss is not informative.

#### Scenario: DID_NOT_CONVERGE produces budget-exhaustion message
- **WHEN** an `UNSAT_DID_NOT_CONVERGE` outcome is emitted for a slot rendering as `tgt[addresses]`
- **THEN** the message reads `no plan for tgt[addresses]: expansion did not converge within the per-slot round budget. Likely missing: a more direct conversion strategy`

### Requirement: Diagnostics anchor on the MapperContext.mapperType

`RealisationDiagnosticsStage` SHALL pass `ctx.getMapperType()` (the `TypeElement` of the mapper-under-compilation) as the `Element` location argument to `Diagnostics.error(...)`. The IDE that consumes the diagnostic underlines the mapper class name (not individual `@Map` annotations).

Anchoring at the mapper-type level is intentional: with the per-group greedy model, the failing slot's `@Map` directive is no longer the natural locus of the diagnostic — the failure is at the group/slot level, often crossing multiple directives.

#### Scenario: Diagnostic carries the mapper TypeElement
- **WHEN** an UNSAT outcome's diagnostic is emitted
- **THEN** the `Diagnostics.error(...)` call passes `ctx.getMapperType()` as its location argument
