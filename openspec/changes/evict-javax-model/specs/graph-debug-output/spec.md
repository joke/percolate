## MODIFIED Requirements

### Requirement: Bipartite DOT rendering

The DOT renderer SHALL draw the bipartite graph Petri-style: `Operation` vertices as boxes labelled
with the Operation's **typed production `label`** (the strategy-supplied description, e.g. `int→long`
or `new Address(int, String)`) and weight — never a codegen class name; `Value` vertices as ellipses
labelled with location plus a **readable type**: simple type names (no package qualifiers) with a
JSpecify nullness suffix `?`/`!` rendered **per level** from a `TypeRef` walk — the outer level's
nullness from the Value's own `nullness`, each nested type-argument's nullness from the model's
per-argument nullness data (e.g. `Optional<Set<Address?>>!`). Inline annotation FQNs SHALL NOT appear
in a value label.
`Dep` edges carry their port id (when feeding an Operation port). A scope-owning Operation's child
scope is written as a **separate per-scope file** (the renderer emits no clusters — grouping is the
one-file-per-scope split). Rendering remains deterministic (stable vertex and edge ordering).

#### Scenario: Operation label is the typed production
- **WHEN** an `int`-to-`long` widening Operation is rendered
- **THEN** its box label is `int→long (1)` (label and weight), with no `$$Lambda` or codegen class name

#### Scenario: Value type renders simple names with JSpecify nullness
- **WHEN** a Value of type `Optional<Set<Address>>` whose inner `Address` argument is nullable (non-null at the outer level) is rendered
- **THEN** its label's type segment is `Optional<Set<Address?>>!`, with no package qualifiers and no
  inline annotation FQN

#### Scenario: Vertex kinds are visually distinct
- **WHEN** a graph containing Values and Operations is rendered
- **THEN** Operations render as boxes and Values as ellipses, with port ids on port edges

#### Scenario: Child scope is a separate file, not a cluster
- **WHEN** a scope-owning container Operation owns a child scope
- **THEN** the child scope's vertices are written to their own per-scope `.dot` file, and no
  `subgraph cluster_` token appears in any rendered output
