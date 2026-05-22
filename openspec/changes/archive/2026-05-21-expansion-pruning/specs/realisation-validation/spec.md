## MODIFIED Requirements

### Requirement: ValidateRealisationStage

The processor SHALL define a stage `ValidateRealisationStage` in package `io.github.joke.percolate.processor.stages.validate` that consumes the post-expansion `MapperGraph` and emits diagnostics for unrealisable user directives. `ValidateRealisationStage` SHALL be `@Inject`-constructed via Lombok `@RequiredArgsConstructor(onConstructor_ = @Inject)`.

For each user `@Map` directive in the mapper, `ValidateRealisationStage` SHALL run a per-directive satisfiability search (the `satisfy()` algorithm — see the "Per-directive satisfy() search" requirement) starting at the directive's realised target node. If the search returns SAT, no diagnostic is emitted for that directive. If the search returns UNSAT, exactly one `Diagnostics.error` is emitted, anchored to the directive's `@Map` `AnnotationMirror`, carrying a closest-miss explanation (see the "Closest-miss diagnostic" requirement).

The stage SHALL NOT mutate the graph. It SHALL be possible to run the stage on a graph that has been read by other consumers (dumps, future codegen) without interference.

`ValidateRealisationStage` SHALL skip mappers that are scarred by an earlier validation stage (e.g., `ValidateNoDuplicateTargets`, `ValidateSourceParameters`).

#### Scenario: ValidateRealisationStage uses Lombok-generated injection
- **WHEN** the source of `ValidateRealisationStage` is inspected
- **THEN** the class carries `@RequiredArgsConstructor(onConstructor_ = @Inject)`

#### Scenario: Per-directive satisfy on a sound mapper emits no diagnostics
- **WHEN** `ValidateRealisationStage.run(ctx)` is invoked on a mapper whose every directive has a realised satisfying path in the post-expansion graph
- **THEN** no error or warning diagnostic is emitted

#### Scenario: Per-directive satisfy on an unsound mapper emits exactly one diagnostic per failing directive
- **WHEN** `ValidateRealisationStage.run(ctx)` is invoked on a mapper with two failing directives and one satisfiable directive
- **THEN** exactly two error diagnostics are emitted (one per failing directive)
- **AND** no diagnostic is emitted for the satisfiable directive

#### Scenario: ValidateRealisationStage does not mutate the graph
- **WHEN** `ValidateRealisationStage.run(ctx)` is invoked
- **THEN** the set of nodes and edges in the underlying `MapperGraph` before and after the call are equal (same instances, same membership)

#### Scenario: Scarred mappers are skipped
- **WHEN** a mapper has been scarred by an earlier validation stage
- **THEN** `ValidateRealisationStage` emits zero satisfy() diagnostics for that mapper

### Requirement: Diagnostic anchoring at @Map AnnotationMirror

Every satisfy() error emitted by `ValidateRealisationStage` SHALL be keyed to the originating `@Map` `AnnotationMirror` carried on the directive's SEED edge's `directive` field. Errors SHALL pass the `AnnotationMirror` to `Diagnostics.error(...)` so the IDE can underline the offending `@Map` annotation in the user's source.

When the failing directive's seed node has multiple incoming SEED edges (only possible if `SeedGraph` later emits convergent chains; not present in v1), the stage SHALL pick one deterministically by choosing the SEED edge whose `directive`'s source-position appears earliest in the source file.

#### Scenario: Diagnostic carries the AnnotationMirror
- **WHEN** `ValidateRealisationStage` emits an error for an unsatisfiable directive
- **THEN** the call to `Diagnostics.error(...)` includes the originating SEED edge's `directive` `AnnotationMirror` as the `Element` location argument
- **AND** the IDE that consumes the diagnostic underlines the corresponding `@Map` annotation in the source

## REMOVED Requirements

### Requirement: ValidateMarkersPhase walks every `?`-typed seed node

**Reason**: Subsumed by the per-directive `satisfy()` search in `ValidateRealisationStage`. A `?`-typed seed node with no outgoing MARKER edge has no typed realised counterpart, which means it cannot serve as a `satisfy()` base case nor reach one — the directive that references such a node is structurally unsatisfiable, which `satisfy()` reports as UNSAT and `ValidateRealisationStage` reports as a closest-miss error. The dedicated marker-presence check is no longer needed.

**Migration**: Tests that asserted Tier-2 marker errors SHALL be rewritten to assert the equivalent satisfy()-based error against the new `RealisationErrorMessagesSpec` (or its equivalent) using the message shape defined in the "Closest-miss diagnostic" requirement.

### Requirement: ValidatePathsPhase bidirectional gap walk

**Reason**: Replaced by the per-directive `satisfy()` search (see the "Per-directive satisfy() search" requirement). The bidirectional gap walk only inspected top-level SEED edges and never recursed into SUB_SEED or ELEMENT_SEED promises — the structural blind spot that allowed orphan element seeds to pass validation. The `satisfy()` algorithm closes that gap by following every promise transitively.

**Migration**: Tests that asserted Tier-3 gap diagnostics SHALL be rewritten to assert the equivalent satisfy()-based diagnostic. The message shape changes; see the "Closest-miss diagnostic" requirement.

## ADDED Requirements

### Requirement: Per-directive satisfy() search

`ValidateRealisationStage` SHALL implement a function `satisfy(Node target)` that returns either SAT (the target is producible) or UNSAT with a closest-miss explanation. The function SHALL be a recursive AND-OR search over the underlying `MapperGraph`:

- **Base case (SAT):** if `target` is a source-parameter node, return SAT.
- **Cycle case (UNSAT):** if `target` is already in the search's `visited` set on this branch, return UNSAT with a "cycle" closest-miss explanation.
- **Recursive case:** mark `target` as visited; for each incoming `REALISED` edge `E` into `target`, evaluate `satisfyEdge(E)`. If any call returns SAT, return SAT. Otherwise return UNSAT carrying the *deepest* closest-miss seen across the alternatives.

`satisfyEdge(Edge E)` SHALL return SAT iff:
1. `satisfy(E.source)` returns SAT (the upstream is producible), AND
2. For every promise `P` rooted in `E` — every `SUB_SEED` edge whose `from` equals `E.source`, and every `ELEMENT_SEED` edge whose source node's `parent` equals `E.source` (i.e., the inner element-seed of the container input) — `satisfy(P.target)` returns SAT.

When `satisfyEdge(E)` returns UNSAT, the explanation SHALL identify `E` as the candidate, name the strategy via `E.strategyClassFqn`, and identify which clause failed (the source, which promise, with the recursive miss attached).

"Deepest" miss means: the candidate closest to the leaves of the search tree (farthest from the originating directive). When multiple misses are at equal depth, ties SHALL be broken by lexical comparison of `strategyClassFqn` so the diagnostic is stable.

The search SHALL run on each user directive independently. The `visited` set SHALL be local to a single `satisfy()` invocation; it SHALL NOT be shared across directives.

#### Scenario: Source-parameter node is SAT (base case)
- **WHEN** `satisfy(N)` is invoked where `N` is a source-parameter node
- **THEN** the result is SAT

#### Scenario: Single REALISED hop from a parameter is SAT
- **WHEN** `satisfy(N)` is invoked where `N` has a single incoming `REALISED` edge whose source is a source-parameter node and which has no promises
- **THEN** the result is SAT

#### Scenario: Missing producer is UNSAT with no-incoming explanation
- **WHEN** `satisfy(N)` is invoked where `N` has zero incoming `REALISED` edges and `N` is not a source-parameter node
- **THEN** the result is UNSAT
- **AND** the closest-miss explanation states that `N` has no producer

#### Scenario: Unsatisfiable promise propagates UNSAT
- **WHEN** `satisfy(N)` is invoked where `N` has exactly one incoming `REALISED` edge `E`, `E.source` is a source-parameter node, and `E` has a `SUB_SEED` promise to a node `P` that itself has no incoming `REALISED` edges
- **THEN** the result is UNSAT
- **AND** the closest-miss explanation names `E.strategyClassFqn`, identifies the unsatisfied promise's type as `P.type`, and identifies the cause as "no producer for `P`"

#### Scenario: Unsatisfiable element-seed promise propagates UNSAT
- **WHEN** `satisfy(N)` is invoked where `N` has one incoming `REALISED` edge `E` whose source is satisfiable, but `E` has an `ELEMENT_SEED` promise (rooted in `E.source`) to a phantom node `eP` that has no incoming `REALISED` edges
- **THEN** the result is UNSAT
- **AND** the closest-miss explanation names `E.strategyClassFqn`, identifies the unsatisfied element-conversion promise (input type → output type), and identifies the cause as "no producer for `eP`"

#### Scenario: First SAT among parallel REALISED edges wins
- **WHEN** `satisfy(N)` is invoked where `N` has two incoming `REALISED` edges, one of which is satisfiable
- **THEN** the result is SAT (regardless of which edge is satisfiable; the function returns on first SAT)

#### Scenario: Deepest miss is reported when all alternatives are UNSAT
- **WHEN** `satisfy(N)` is invoked where `N` has two incoming `REALISED` edges `E1` and `E2`, both UNSAT, where `E1`'s miss is at depth 2 from `N` and `E2`'s miss is at depth 3 from `N`
- **THEN** the result is UNSAT
- **AND** the closest-miss explanation in the result is `E2`'s (the deeper one)

#### Scenario: Tie-broken by strategyClassFqn when depths are equal
- **WHEN** `satisfy(N)` is invoked where `N` has two incoming `REALISED` edges `E1` (strategy `com.b.B`) and `E2` (strategy `com.a.A`), both UNSAT, both with miss depth equal to 1
- **THEN** the result is UNSAT
- **AND** the closest-miss explanation in the result is `E2`'s (the strategy whose FQN sorts earlier)

#### Scenario: Cycle in REALISED returns UNSAT cycle explanation
- **WHEN** `satisfy(N)` is invoked where the only paths back from `N` to source parameters pass through `N` itself
- **THEN** the result is UNSAT
- **AND** the closest-miss explanation identifies the cause as a cycle in REALISED edges

#### Scenario: Visited set is per-invocation
- **WHEN** `satisfy(N1)` is invoked and returns; then `satisfy(N2)` is invoked
- **THEN** the second invocation's `visited` set does not retain entries from the first
- **AND** results for the same shared node are independently computed

### Requirement: Closest-miss diagnostic

When `ValidateRealisationStage` emits an error for an unsatisfiable directive, the diagnostic message SHALL include the closest-miss explanation returned by the `satisfy()` search. The format SHALL be a one-line headline followed by multi-line detail:

```
no plan for tgt[<path>] in method <method-name>
  considered <strategy-short-name>'s REALISED <input-type> → <output-type>,
  but its <promise-kind> <inner-from-type> → <inner-to-type> was not producible.
  Likely missing: <suggestion>
```

Where:
- `<path>` is the directive's target-location path.
- `<method-name>` is the simple name of the mapping method (no parameter list — already conveyed by the `AnnotationMirror` anchor).
- `<strategy-short-name>` is the simple class name (no package prefix) extracted from the closest-miss edge's `strategyClassFqn`.
- `<input-type>` / `<output-type>` are the endpoints of the closest-miss `REALISED` edge.
- `<promise-kind>` is either `element conversion` (for an unsatisfied `ELEMENT_SEED`) or `chain step` (for an unsatisfied `SUB_SEED`).
- `<inner-from-type>` / `<inner-to-type>` are the endpoints of the unsatisfied promise.
- `<suggestion>` is a heuristic hint, e.g. *"a `@Map`-annotated method producing `<inner-to-type>` from `<inner-from-type>`"*.

When the closest-miss explanation indicates "no producer at all" (the directive's realised target has zero incoming `REALISED` edges), the format SHALL collapse to:

```
no plan for tgt[<path>] in method <method-name>
  the target type <type> has no producer in the graph.
  Likely missing: a strategy that produces <type> from the available source parameters.
```

When the explanation indicates a cycle, the format SHALL identify the cycle's recurrent node.

Diagnostic message bodies SHALL be byte-stable for a given graph input — no timestamps, no object addresses, no nondeterministic ordering of alternatives (per the tie-break rule in "Per-directive satisfy() search").

#### Scenario: Element-conversion miss produces the canonical message
- **WHEN** `ValidateRealisationStage` emits an error for a directive `tgt[addresses]` in method `mapHuman`, where the closest miss is `SetMap`'s REALISED `List<Optional<Person.Address>>` → `Set<Human.Address>` whose element-conversion promise `Optional<Person.Address>` → `Human.Address` has no producer
- **THEN** the diagnostic message contains the literal text `no plan for tgt[addresses] in method mapHuman`
- **AND** the message contains the literal text `considered SetMap's REALISED List<Optional<Person.Address>> → Set<Human.Address>`
- **AND** the message contains the literal text `element conversion Optional<Person.Address> → Human.Address was not producible`
- **AND** the message contains a "Likely missing" suggestion that includes `Human.Address` and `Person.Address`

#### Scenario: No-producer-at-all message
- **WHEN** `ValidateRealisationStage` emits an error for a directive whose realised target has zero incoming REALISED edges
- **THEN** the diagnostic message contains a phrase identifying the target type
- **AND** the message contains the literal phrase `has no producer in the graph`
- **AND** the message contains a "Likely missing" suggestion

#### Scenario: Diagnostic message is byte-stable across runs
- **WHEN** the same `MapperGraph` produces a satisfy() failure twice (two separate per-mapper runs)
- **THEN** the two diagnostic message strings are byte-for-byte identical
