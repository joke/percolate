## MODIFIED Requirements

### Requirement: Edge label includes EdgeKind marker

Each rendered edge's `kind` SHALL be identifiable from the DOT output without consulting the source graph data. The required identifier varies by kind:

- For `EdgeKind.SEED` edges, the edge `label` attribute SHALL include the literal token `SEED`.
- For `EdgeKind.REALISED` edges, the kind is identified by the combination of the edge's style attributes and the strategy short name in its label (see the "Node and edge visual distinction" requirement); the explicit token `REALISED` is not required in the label.

(`EdgeKind.MARKER` no longer exists; the renderer SHALL NOT special-case a MARKER token.)

#### Scenario: SEED edge label includes the SEED token
- **WHEN** the renderer writes an edge with `kind == EdgeKind.SEED`
- **THEN** the edge's `label` attribute contains the literal `SEED`

#### Scenario: REALISED edge kind is identified by style and strategy
- **WHEN** the renderer writes an edge with `kind == EdgeKind.REALISED` and `strategyClassFqn == Optional.of("io.github.joke.percolate.spi.builtins.ListContainer")`
- **THEN** the edge's `label` attribute contains the simple class name `ListContainer`
- **AND** the edge's style attributes match the documented REALISED styling (distinct from SEED)
- **AND** the edge's `label` attribute does NOT contain the literal token `REALISED`
