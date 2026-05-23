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
3. Apply the "alive sibling" filter: a sub-group outcome is suppressed from diagnostic emission iff there exists a sibling sub-group (same root) whose outcome is SAT. This prevents multi-fire dead branches from drowning the user in noise — the alive chain SATs the parent slot, so dead siblings are expected and should not surface as user-facing errors.
4. For each surviving UNSAT outcome, call `emitFor(graph, outcome, ctx)` which formats and emits a single error diagnostic via `Diagnostics.error(ctx.getMapperType(), message)`.

If an UNSAT outcome's `failingSlot` is empty, no diagnostic is emitted for that outcome.

#### Scenario: SAT groups emit no diagnostics
- **WHEN** the graph contains only `GroupOutcome.sat(...)` outcomes
- **THEN** `RealisationDiagnosticsStage.run(ctx)` emits zero diagnostics

#### Scenario: One UNSAT_NO_PLAN outcome emits one error
- **WHEN** the graph contains one `GroupOutcome.unsatNoPlan(group, failingSlot)` and no SAT sibling exists at the same root
- **THEN** one `Diagnostics.error(mapperType, message)` call is made
- **AND** the message mentions both the failing slot's rendered path and the closest-miss node's path

#### Scenario: Dead sibling sub-groups do not emit diagnostics when an alive sibling exists
- **WHEN** two sibling sub-groups share the same root; one's outcome is SAT and the other's is `unsatNoPlan`
- **THEN** the `unsatNoPlan` sibling emits no diagnostic
- **AND** zero error diagnostics total are emitted for the alive chain

#### Scenario: Multiple UNSAT outcomes with no SAT siblings emit one error each
- **WHEN** the graph contains three UNSAT outcomes, none of which has a SAT sibling
- **THEN** three separate `Diagnostics.error` calls are made

#### Scenario: Stage skips when graph is absent
- **WHEN** `ctx.getGraph()` is `null`
- **THEN** the stage returns without emitting any diagnostic

### Requirement: UNSAT_NO_PLAN diagnostic includes closest-miss walk

For an outcome of kind `UNSAT_NO_PLAN`, the diagnostic message SHALL be of the form:

```
no plan for <slotPath>: <closestMissPath> has no producer in the graph. Likely missing: a @Map-annotated method whose source produces <closestMissType>
```

The closest-miss node SHALL be computed by `walkClosestMiss(graph, failingSlot)`: BFS the existing REALISED subgraph backwards from `failingSlot`, accumulating predecessors via REALISED edges, returning the deepest reached node. This is the node closest to source-side in the unsatisfied chain.

The closest-miss walk uses the **global REALISED edges** as its traversal (not just the failing group's view) so that the message points the user to the most informative chain endpoint regardless of which sub-group the missing producer lives in.

The slot path SHALL be rendered by `renderSlotPath(node)`:
- For `TargetLocation` nodes: `tgt[<segments-joined-by-dot>]` or `tgt[]` if segments are empty.
- For `SourceLocation` nodes: `src[<segments-joined-by-dot>]`.
- For other nodes: `Node.id()`.

The closest-miss type SHALL be the node's `TypeMirror.toString()`, or `?` if the type is empty.

#### Scenario: Closest-miss walk finds deepest predecessor
- **WHEN** an `UNSAT_NO_PLAN` outcome's failing slot has an existing REALISED chain `A → B → failingSlot`
- **THEN** `walkClosestMiss` returns `A`
- **AND** the message names `A`'s rendered path as the closest-miss point

#### Scenario: Closest-miss with no incoming REALISED edges falls back to slot itself
- **WHEN** the failing slot has no incoming REALISED edges
- **THEN** `walkClosestMiss` returns the failing slot
- **AND** the message names the slot's rendered path as the closest-miss point

#### Scenario: Diagnostic identifies the missing method's source type
- **WHEN** the closest-miss node carries type `Person.Address`
- **THEN** the message ends with `Likely missing: a @Map-annotated method whose source produces Person.Address`

#### Scenario: Closest-miss for an untyped slot reports `?`
- **WHEN** the failing slot's `type.isEmpty()` (a path-segment group whose resolver did not match)
- **THEN** the message's closest-miss type renders as `?`
- **AND** the message reads `... Likely missing: a @Map-annotated method whose source produces ?` (suggesting the user inspect the path-segment group failure)

### Requirement: Base-case SAT clause for parameter-root slots

`RealisationDiagnosticsStage` SHALL recognise that a slot is structurally satisfied (base-case SAT, no child sub-group required) iff `slot.loc` is a single-segment `SourceLocation` matching one of `currentMethod`'s declared parameter names. The validation stage SHALL NOT emit diagnostics for slots that are base-case SAT — those slots have no producer in the graph by design (their value is the method-parameter binding), and treating them as UNSAT would emit spurious diagnostics.

This clause mirrors the engine's SAT rule (see `graph-expansion` capability — "Base-case SAT for parameter-root slots"). The validation stage SHALL consult `ctx.getCurrentMethod().getParameters()` to determine parameter names; no global `sourceParameterRoots` set is referenced.

#### Scenario: Parameter-root slot emits no diagnostic
- **WHEN** an `ExpansionGroup` has slot `src[person]:Person` and `currentMethod.getParameters()` contains a parameter named `person`
- **THEN** no diagnostic is emitted for this slot
- **AND** the group's outcome is treated as SAT for diagnostic purposes regardless of whether a child sub-group exists at the slot

#### Scenario: Multi-segment slot is not base-case SAT and may emit a diagnostic
- **WHEN** an `ExpansionGroup` has slot `src[person.weirdSegment]:?` and the corresponding path-segment group is `unsatNoPlan`
- **THEN** the diagnostic for the path-segment group's failure is emitted normally (subject to the alive-sibling filter)

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
