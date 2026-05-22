## ADDED Requirements

### Requirement: New container bridges render with their simple class name

The deterministic DOT renderer SHALL render REALISED edges emitted by the new container built-ins (`IterableUnwrap`, `OptionalCollect`, `SetCollect`, `ListCollect`, `ArrayCollect`) with `label` attributes containing the bridge's simple class name and weight, formatted by the same rule that applies to every REALISED edge (per the existing `Node and edge visual distinction` requirement).

No REALISED edge in any rendered DOT file SHALL carry a `strategyClassFqn` ending in `.SetMap`, `.ListMap`, or `.OptionalMap` — those classes are deleted by `split-container-bridges`. Any DOT file produced by the processor for a mapper compiled against the post-change classpath SHALL be free of those tokens in every edge label.

#### Scenario: IterableUnwrap REALISED edge label contains its simple name and weight
- **WHEN** the renderer writes a REALISED edge with `strategyClassFqn == Optional.of("io.github.joke.percolate.spi.builtins.IterableUnwrap")` and `weight == Weights.CONTAINER`
- **THEN** the edge's `label` attribute contains both the literal `IterableUnwrap` and the literal value of `Weights.CONTAINER` (rendered as the configured integer, e.g. `2`)
- **AND** the `label` does NOT contain the package prefix `io.github.joke.percolate.spi.builtins`

#### Scenario: SetCollect / ListCollect / ArrayCollect / OptionalCollect REALISED edge labels contain their simple names
- **WHEN** the renderer writes a REALISED edge whose `strategyClassFqn` resolves to one of `SetCollect`, `ListCollect`, `ArrayCollect`, or `OptionalCollect` under `io.github.joke.percolate.spi.builtins`
- **THEN** the edge's `label` attribute contains the simple class name verbatim
- **AND** the `label` does NOT contain the package prefix

#### Scenario: No DOT file contains the deleted container-map bridge names
- **WHEN** any `<MapperFQN>.seed.dot`, `<MapperFQN>.full.dot`, or `<MapperFQN>.transforms.dot` is produced for a mapper compiled with the post-change `strategies-builtin` module
- **THEN** no edge `label` attribute and no edge attribute string contains the literal token `SetMap`, `ListMap`, or `OptionalMap`

### Requirement: Linear container chains render without diamond shortcuts

The DOT renderer's output for any container-bearing chain (a chain involving an `*Unwrap` and a matching `*Collect`) SHALL be a linear sequence of REALISED edges from the regular-scope source candidate through `ElementLocation` nodes back to the regular-scope target candidate. No additional "outer" REALISED edge SHALL connect the source container directly to the target container in parallel with the chain.

This requirement formalises, at the rendering level, the structural invariant established by `[[split-container-bridges/specs/graph-expansion]]`: chains are linear by construction; the renderer simply renders what the engine produces.

#### Scenario: Integration mapper addresses chain renders linearly in transforms.dot
- **WHEN** the integration mapper at `~/Projects/joke/percolate-integration/mappers` is rebuilt with `ProcessorOptions.debugGraphs == true` and the produced `PersonMapper.transforms.dot` is inspected
- **THEN** for the subgraph rooted at `tgt[addresses]:Optional<Set<Human.Address>>`, the REALISED edges trace exactly one linear path back to `src[person]:Person`, passing through `elem(element):Optional<Person.Address>`, `elem(element):Person.Address`, `elem(element):Human.Address`, and `src[person.addresses]:Set<Human.Address>` (the precise node identifiers may vary with renderer naming rules, but no diamond shape SHALL appear)
- **AND** no `elem(element)` node in the rendered subgraph has zero outgoing REALISED edges except where it represents the source-parameter-root boundary
- **AND** no parallel REALISED edge connects `src[person.addresses]:List<Optional<Person.Address>>` directly to `src[person.addresses]:Set<Human.Address>` (the old diamond's outer edge)

#### Scenario: No outer container-map shortcut edges in full.dot either
- **WHEN** `PersonMapper.full.dot` is inspected for the same mapper
- **THEN** for every pair of container-typed nodes joined by the chain pattern (Unwrap → … → Collect), the only REALISED edges between them traverse `ElementLocation` nodes
- **AND** no REALISED edge connects two regular-scope container-typed nodes directly with a `*Map`-style strategy label
