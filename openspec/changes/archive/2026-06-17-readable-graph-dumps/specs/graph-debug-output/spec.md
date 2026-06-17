## MODIFIED Requirements

### Requirement: Bipartite DOT rendering

The DOT renderer SHALL draw the bipartite graph Petri-style: `Operation` vertices as boxes labelled
with the Operation's **typed production `label`** (the strategy-supplied description, e.g. `int→long`
or `new Address(int, String)`) and weight — never a codegen class name; `Value` vertices as ellipses
labelled with location plus a **readable type**: simple type names (no package qualifiers) with a
JSpecify nullness suffix `?`/`!` rendered **per level** from a `TypeMirror` walk — the outer level's
nullness from the Value's own `nullness`, each nested type-argument's nullness from its annotation
mirrors (e.g. `Optional<Set<Address?>>!`). Inline annotation FQNs SHALL NOT appear in a value label.
`Dep` edges carry their port id (when feeding an Operation port). Scope-owning Operations render their
child scope as a DOT cluster.

#### Scenario: Operation label is the typed production
- **WHEN** an `int`-to-`long` widening Operation is rendered
- **THEN** its box label is `int→long (1)` (label and weight), with no `$$Lambda` or codegen class name

#### Scenario: Value type renders simple names with JSpecify nullness
- **WHEN** a Value of type `Optional<Set<@Nullable Address>>` (non-null at the outer level) is rendered
- **THEN** its label's type segment is `Optional<Set<Address?>>!`, with no package qualifiers and no
  inline annotation FQN

#### Scenario: Vertex kinds are visually distinct
- **WHEN** a graph containing Values and Operations is rendered
- **THEN** Operations render as boxes and Values as ellipses, with port ids on port edges

#### Scenario: Child scope renders as a cluster
- **WHEN** a scope-owning container Operation is rendered
- **THEN** its child scope's vertices appear inside a DOT cluster attached to the Operation

### Requirement: Three dumps over the bipartite graph

The three dump stages SHALL be retained with unchanged gating and file naming, all running **after**
expansion: the full dump (every vertex and edge, **reachability-annotated**), the transforms dump, and
the plan dump (the extracted plan view only). There is no seed dump (no separate seed stage exists).
The full dump SHALL obtain reachability from the extracted plan's derived `reachable`/`cost` query
(not from a stored SAT predicate) and SHALL render **all** vertices, visually **dimming** the
unreachable ones (e.g. grey fill / dashed outline) rather than omitting them — so the pruned
over-emission is distinguishable from the surviving plan at a glance.

#### Scenario: Plan dump shows only chosen producers
- **WHEN** the plan dump is written for a method with competing constructors
- **THEN** only the chosen constructor Operation and its supply chains appear

#### Scenario: Full dump dims unreachable vertices
- **WHEN** the full dump is written for a target with one surviving producer and several pruned
  over-emitted candidates
- **THEN** every vertex is present, the reachable ones rendered normally and the unreachable ones
  dimmed (grey/dashed) by finite vs infinite extraction cost, with no reference to a stored SAT bit
