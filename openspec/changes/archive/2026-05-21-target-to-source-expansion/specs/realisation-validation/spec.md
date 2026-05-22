## MODIFIED Requirements

### Requirement: Diagnostic anchoring at @Map AnnotationMirror

Every realisation diagnostic emitted by `RealisationDiagnosticsStage` SHALL be keyed to the originating `@Map` `AnnotationMirror` carried on the directive's `SEED` edge's `directive` field. Errors SHALL pass the `AnnotationMirror` to `Diagnostics.error(...)` so the IDE can underline the offending `@Map` annotation in the user's source.

When the failing directive's seed node has multiple incoming `SEED` edges with a `directive` (a case `SeedGraph` does not produce today), the stage SHALL pick one deterministically by choosing the `SEED` edge whose `directive`'s source-position appears earliest in the source file.

#### Scenario: Diagnostic carries the AnnotationMirror

- **WHEN** `RealisationDiagnosticsStage` emits an error for an unsatisfiable directive subgraph
- **THEN** the call to `Diagnostics.error(...)` includes the originating `SEED` edge's `directive` `AnnotationMirror` as the `Element`-anchor argument
- **AND** the IDE that consumes the diagnostic underlines the corresponding `@Map` annotation in the source

## ADDED Requirements

### Requirement: RealisationDiagnosticsStage

The processor SHALL define a stage `RealisationDiagnosticsStage` in package `io.github.joke.percolate.processor.stages.validate` that consumes per-`ExpansionGroup` SAT/UNSAT outcomes recorded by `ExpandGroupsPhase` and emits a closest-miss diagnostic for each group whose outcome is UNSAT. The stage SHALL be `@Inject`-constructed via Lombok `@RequiredArgsConstructor(onConstructor_ = @Inject)`.

`RealisationDiagnosticsStage` SHALL NOT run its own satisfiability search. Its sole responsibility is to:

1. Read the per-group outcome records from `MapperContext` (or equivalent shared state) populated by `ExpandGroupsPhase`.
2. For each UNSAT outcome whose originating directive can be recovered (by walking the SEED-framing chain backward from the failing slot to a SEED edge carrying a `directive` `AnnotationMirror`), walk back from the failing slot through `REALISED` edges to find the closest-miss frontier (see the `Closest-miss diagnostic` requirement) and format the diagnostic message.
3. Emit `Diagnostics.error(mapperType, mirror, null, message)` per failing group, anchored at the `directive`'s `AnnotationMirror`.

Nested `ExpansionGroup`s (registered during slot resolution by mid-expansion `GroupTarget` matches or by element seeds) MAY also be UNSAT; their outcomes contribute to the closest-miss diagnostic of their containing user directive but DO NOT produce their own top-level error messages.

`RealisationDiagnosticsStage` SHALL skip mappers that are scarred by an earlier validation stage (`ValidateNoDuplicateTargets`, `ValidateSourceParameters`).

The stage SHALL NOT mutate the `MapperGraph`.

#### Scenario: Stage uses Lombok-generated injection

- **WHEN** the source of `RealisationDiagnosticsStage` is inspected
- **THEN** the class carries `@RequiredArgsConstructor(onConstructor_ = @Inject)`

#### Scenario: No satisfiability search inside the stage

- **WHEN** the source of `RealisationDiagnosticsStage` is inspected
- **THEN** the class does not invoke any per-node recursive satisfiability search
- **AND** it reads outcomes from `MapperContext` directly

#### Scenario: SAT groups emit no diagnostic

- **WHEN** the stage runs on a mapper whose every `ExpansionGroup` has outcome SAT
- **THEN** no error or warning diagnostic is emitted

#### Scenario: One diagnostic per failing group with a recoverable directive

- **WHEN** the stage runs on a mapper with two UNSAT groups whose failing slots can each be traced back to a user `@Map` directive, plus one SAT group
- **THEN** exactly two error diagnostics are emitted
- **AND** no diagnostic is emitted for the SAT group

#### Scenario: Stage does not mutate the graph

- **WHEN** `RealisationDiagnosticsStage.run(ctx)` is invoked
- **THEN** the set of nodes and edges in the underlying `MapperGraph` before and after the call are identical (same instances, same membership)

#### Scenario: Scarred mappers are skipped

- **WHEN** the mapper has been scarred by an earlier validation stage
- **THEN** `RealisationDiagnosticsStage` emits zero diagnostics for that mapper

### Requirement: Closest-miss diagnostic

When `RealisationDiagnosticsStage` formats an error for an UNSAT group, the diagnostic message SHALL include a closest-miss explanation computed by walking back from the failing slot through `REALISED` edges in the underlying graph. The walk identifies the deepest reachable frontier (the closest node to the source-side leaves that has at least one incoming `REALISED` edge), names the strategies that *did* offer producers reaching it, and identifies the gap beyond it.

The diagnostic format SHALL be a one-line headline followed by multi-line detail:

```
no plan for tgt[<path>] in method <method-name>
  considered <strategy-short-name>'s REALISED <input-type> → <output-type>,
  but its <promise-kind> <inner-from-type> → <inner-to-type> was not producible.
  Likely missing: <suggestion>
```

Where:
- `<path>` is the failing slot's target-location path (from the slot's `Location`).
- `<method-name>` is the simple name of the mapping method (from the slot's enclosing `MethodScope`).
- `<strategy-short-name>` is the simple class name (no package prefix) of the strategy that emitted the closest-miss `REALISED` edge.
- `<input-type>` / `<output-type>` are the endpoints of the closest-miss `REALISED` edge.
- `<promise-kind>` is `element conversion` (when the miss is in a nested element-scope subgraph) or `chain step` (otherwise).
- `<inner-from-type>` / `<inner-to-type>` are the endpoints of the unsatisfied sub-subgraph's SEED edge.
- `<suggestion>` is a heuristic hint, e.g. *"a `@Map`-annotated method producing `<inner-to-type>` from `<inner-from-type>`"*.

For the **no-producer-at-all** case — the failing slot has zero incoming `REALISED` edges in the underlying graph — the format SHALL collapse to:

```
no plan for tgt[<path>] in method <method-name>
  the target type <type> has no producer in the graph.
  Likely missing: a strategy that produces <type> from the available source parameters.
```

For the **did-not-converge** case — `UNSAT(did-not-converge)` from `MAX_SUBGRAPH_ROUNDS` exceeded — the format SHALL be:

```
no plan for tgt[<path>] in method <method-name>
  expansion did not converge after <MAX_SUBGRAPH_ROUNDS> rounds.
  This typically indicates an engine or strategy bug; please report it.
```

Diagnostic message bodies SHALL be byte-stable for a given input graph — no timestamps, no object addresses, no nondeterministic ordering. Tie-breaks among multiple candidates at the same depth use lexical comparison of `strategyClassFqn`.

#### Scenario: Element-conversion miss produces the canonical message

- **WHEN** `RealisationDiagnosticsStage` formats the diagnostic for `tgt[addresses]` in `mapHuman` where the closest miss is `SetMap`'s REALISED `List<Optional<Person.Address>> → Set<Human.Address>` whose nested element subgraph `Optional<Person.Address> → Human.Address` is UNSAT
- **THEN** the message contains the literal `no plan for tgt[addresses] in method mapHuman`
- **AND** the message contains the literal `considered SetMap's REALISED List<Optional<Person.Address>> → Set<Human.Address>`
- **AND** the message contains the literal `element conversion Optional<Person.Address> → Human.Address was not producible`
- **AND** the message contains a `Likely missing` suggestion mentioning `Human.Address` and `Person.Address`

#### Scenario: No-producer-at-all message

- **WHEN** the formatter encounters an UNSAT group whose failing slot has zero incoming `REALISED` edges in the underlying graph
- **THEN** the message contains the literal phrase `has no producer in the graph`
- **AND** the message contains a `Likely missing` suggestion

#### Scenario: Did-not-converge message

- **WHEN** the formatter encounters an `UNSAT(did-not-converge)` outcome
- **THEN** the message contains the literal phrase `expansion did not converge after`

#### Scenario: Diagnostic message is byte-stable across runs

- **WHEN** the same `MapperGraph` produces a UNSAT outcome twice (two separate per-mapper runs)
- **THEN** the two formatted diagnostic strings are byte-for-byte identical

## REMOVED Requirements

### Requirement: ValidateRealisationStage

**Reason:** Replaced by `RealisationDiagnosticsStage`. The new stage has a narrower responsibility (read outcome records, format diagnostics) and runs no satisfiability search of its own — that work is done inside `ExpandGroupsPhase` as part of expansion.

**Migration:** Replace `ValidateRealisationStage.java` with `RealisationDiagnosticsStage.java` per the new requirement. Tests that constructed `ValidateRealisationStage` directly (e.g., `RealisationErrorMessagesSpec`) are adapted to construct `RealisationDiagnosticsStage` with synthetic per-group outcome records on `MapperContext`. The closest-miss message format is preserved.

### Requirement: ValidateMarkersPhase walks every `?`-typed seed node

**Reason:** Subsumed by `ExpandGroupsPhase`'s slot-level UNSAT outcomes when no producer reaches a slot.

**Migration:** Tests previously asserting Tier-2 marker errors are revisited; they assert the equivalent slot-UNSAT outcome and the corresponding diagnostic message produced by `RealisationDiagnosticsStage`.

### Requirement: ValidatePathsPhase bidirectional gap walk

**Reason:** Replaced by per-group target-driven expansion + per-slot `ConnectivityInspector` SAT check. The bidirectional gap walk only inspected top-level SEED edges in the forward model; the new model recurses naturally through nested `ExpansionGroup`s.

**Migration:** Tests asserting Tier-3 gap diagnostics are rewritten to assert the closest-miss message produced by `RealisationDiagnosticsStage`.

### Requirement: ValidationPhase contract

**Reason:** The forward-era `ValidationPhase` interface bundled marker-check, paths-check, and realisation-check into a single contract. Under the new model `RealisationDiagnosticsStage` is a `Stage`, not a `ValidationPhase`; no two-tier inner contract is needed.

**Migration:** Delete `ValidationPhase.java` if it carries no remaining consumer. Tests are revisited.

### Requirement: Per-directive satisfy() search

**Reason:** This requirement was added by the in-flight `expansion-pruning` change. It is superseded by `ExpandGroupsPhase`'s connectivity-decided per-group algorithm — the satisfiability search is the expansion search.

**Migration:** Delete `SatisfySearch.java`, `SatisfyResult.java`, `SatisfyOutcome.java` from the processor implementation. `SatisfyAlgorithmSpec` is rewritten as integration coverage against `ExpandGroupsPhase`'s group-outcome semantics; the per-scenario assertions translate to assertions about group outcomes and the resulting diagnostic messages.
