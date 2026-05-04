## ADDED Requirements

### Requirement: ValidateRealisationStage

The processor SHALL define a stage `ValidateRealisationStage` in
package `io.github.joke.percolate.processor.validate` that consumes
the post-expansion `MapperGraph` and emits diagnostics for
unrealisable directives. `ValidateRealisationStage` SHALL be
`@Inject`-constructed via Lombok
`@RequiredArgsConstructor(onConstructor_ = @Inject)`.

`ValidateRealisationStage` SHALL execute two sequential
`ValidationPhase`s in declared order:
- `ValidateMarkersPhase` (Tier-2),
- `ValidatePathsPhase` (Tier-3).

Tier-2 SHALL run first and scar the mapper on failure. Tier-3 SHALL
skip scarred mappers.

#### Scenario: Stage runs Tier-2 then Tier-3
- **WHEN** `ValidateRealisationStage.apply(graph, typeElement)` is invoked
- **THEN** `ValidateMarkersPhase.apply(graph, typeElement)` runs first
- **AND** `ValidatePathsPhase.apply(graph, typeElement)` runs next

#### Scenario: ValidateRealisationStage uses Lombok-generated injection
- **WHEN** the source of `ValidateRealisationStage` is inspected
- **THEN** the class carries `@RequiredArgsConstructor(onConstructor_ = @Inject)`
- **AND** the generated constructor accepts the two phase classes

### Requirement: ValidateMarkersPhase walks every `?`-typed seed node

`ValidateMarkersPhase` SHALL iterate every `?`-typed seed node in the
post-expansion graph (every node with `type == Optional.empty()` and
`loc` of type `SourceLocation` or `TargetLocation`). For each such
node, the phase SHALL count outgoing `EdgeKind.MARKER` edges. If the
count is zero, the seed is *unrealised*.

For each unrealised seed node, the phase SHALL emit one
`Diagnostics.error` keyed to the `AnnotationMirror` of one of the
node's incoming `EdgeKind.SEED` edges' `directive`. The error message
SHALL identify:
- the directive (the `target` and `source` strings of the `@Map`),
- the unrealisable point in the chain (the `?`-typed node's `loc`).

The phase SHALL scar the affected mapper element via the existing
`Diagnostics` scarring mechanism so subsequent phases observe the
scar.

#### Scenario: Seed node with no markers emits Tier-2 error
- **WHEN** the post-expansion graph contains a `?`-typed seed node with zero outgoing MARKER edges and an incoming SEED edge whose `directive` carries an `@Map` mirror with `target = "address.street"` and `source = "x"`
- **THEN** `ValidateMarkersPhase` emits one error diagnostic
- **AND** the error references the `@Map` `AnnotationMirror` as its source location
- **AND** the message text identifies both the directive and the unrealisable node's `loc`

#### Scenario: Seed node with at least one marker passes
- **WHEN** the post-expansion graph contains a `?`-typed seed node with one or more outgoing MARKER edges
- **THEN** `ValidateMarkersPhase` emits no diagnostic for that node

#### Scenario: Tier-2 scars the mapper on failure
- **WHEN** `ValidateMarkersPhase` emits at least one error for a mapper
- **THEN** the mapper is scarred via the existing `Diagnostics` scarring mechanism

#### Scenario: Multiple unrealised seeds emit one error each
- **WHEN** the graph contains three unrealised `?`-typed seed nodes
- **THEN** the phase emits three separate error diagnostics
- **AND** each error is keyed to its own node's incoming SEED edge's `directive`

### Requirement: ValidatePathsPhase bidirectional gap walk

`ValidatePathsPhase` SHALL run only on un-scarred mappers. For each
`EdgeKind.SEED` edge bridging source-side to target-side (flavor ②
seeds — `from` in source space, `to` in target space), the phase
SHALL:

1. Resolve the realised source-side counterpart of the SEED's `from`
   end (FROM itself if `from.type.isPresent()`, otherwise the typed
   nodes reachable via outgoing `MARKER` edges from FROM).
2. Resolve the realised target-side counterpart of the SEED's `to`
   end the same way.
3. Walk `EdgeKind.REALISED` edges *forward* from each source-side
   counterpart, accumulating a chain of typed nodes, until no further
   outgoing REALISED edge exists. This is the **source shoulder**.
4. Walk `EdgeKind.REALISED` edges *backward* from each target-side
   counterpart, accumulating a chain of typed nodes in reverse, until
   no further incoming REALISED edge exists. This is the **target
   shoulder**.
5. If the source shoulder's terminal node and the target shoulder's
   initial node are connected by at least one REALISED edge (a
   bridge), the SEED is realised — no error is emitted.
6. Otherwise, the phase SHALL emit one `Diagnostics.error` keyed to
   the SEED's `directive` `AnnotationMirror`. The error message
   SHALL include:
   - a textual rendering of the source shoulder (each typed node's
     `loc` and `type`, with each producing edge's strategy FQN),
   - a textual rendering of the target shoulder (same format),
   - the missing type pair as `(<source-shoulder.terminalType>,
     <target-shoulder.initialType>)`.

The phase SHALL emit at most one error per SEED bridge edge.

#### Scenario: Tier-3 emits a bidirectional gap diagnostic
- **WHEN** the post-expansion graph contains a flavor ② SEED `src[person.address]:? → tgt[.address]:?` whose source side realises to `src[person→getAddress()]:Person.Address`, target side realises to `slot[address]:Human.Address`, and no `REALISED` bridge connects the two
- **THEN** `ValidatePathsPhase` emits one error diagnostic
- **AND** the error references the SEED edge's `@Map` `AnnotationMirror`
- **AND** the message includes a rendering of the source shoulder ending at type `Person.Address`
- **AND** the message includes a rendering of the target shoulder beginning at type `Human.Address`
- **AND** the message states the missing type pair as `(Person.Address, Human.Address)`

#### Scenario: Tier-3 passes when realised path exists
- **WHEN** the graph contains a flavor ② SEED whose source shoulder and target shoulder are connected by at least one REALISED edge (e.g., via DirectAssign on identical types)
- **THEN** `ValidatePathsPhase` emits no diagnostic for that SEED

#### Scenario: Tier-3 skips scarred mappers
- **WHEN** a mapper has been scarred by `ValidateMarkersPhase` (Tier-2)
- **THEN** `ValidatePathsPhase` emits zero diagnostics for any SEED in that mapper

#### Scenario: Tier-3 emits at most one error per SEED bridge
- **WHEN** a mapper contains a flavor ② SEED whose endpoints have multiple realisations on each side, none connected by a bridge
- **THEN** `ValidatePathsPhase` emits exactly one error for that SEED (not one per realisation pair)

### Requirement: Diagnostic anchoring at @Map AnnotationMirror

Both Tier-2 and Tier-3 errors SHALL be keyed to the originating
`@Map` `AnnotationMirror` carried on the SEED edge's `directive`
field. Errors SHALL pass the `AnnotationMirror` to
`Diagnostics.error(...)` so the IDE can underline the offending
`@Map` annotation in the user's source.

When the originating SEED node has multiple incoming SEED edges
(only possible if `SeedGraph` later emits convergent chains; not
present in v1), the phase SHALL pick one deterministically by
choosing the SEED edge whose `directive`'s source-position appears
earliest in the source file.

#### Scenario: Diagnostic carries the AnnotationMirror
- **WHEN** Tier-2 or Tier-3 emits an error for an unrealised seed
- **THEN** the call to `Diagnostics.error(...)` includes the originating SEED edge's `directive` `AnnotationMirror` as the `Element` location argument
- **AND** the IDE that consumes the diagnostic underlines the corresponding `@Map` annotation in the source

### Requirement: ValidationPhase contract

Every `ValidationPhase` SHALL implement a single method
`apply(MapperGraph, TypeElement)`. A phase SHALL emit diagnostics
via the injected `Diagnostics` collaborator. A phase SHALL NOT
mutate the graph (no nodes, no edges added or removed).

#### Scenario: Validation phase does not mutate the graph
- **WHEN** any `ValidationPhase.apply(graph, te)` is invoked
- **THEN** the graph's set of nodes and edges before and after the call are equal (same instances, same membership)
