## MODIFIED Requirements

### Requirement: Three dumps over the bipartite graph

The three dump stages SHALL be retained with unchanged gating and file naming: the seed dump (roots
and goal-spec annotations), the full dump (every vertex and edge, **reachability-annotated** — i.e.
finite vs infinite extraction cost), and the transforms/plan dump (the extracted plan view only). The
full dump SHALL obtain reachability from the extracted plan's derived `reachable`/`cost` query, not
from a stored SAT predicate.

#### Scenario: Plan dump shows only chosen producers
- **WHEN** the plan dump is written for a method with competing constructors
- **THEN** only the chosen constructor Operation and its supply chains appear

#### Scenario: Full dump annotates reachability from cost
- **WHEN** the full dump is written
- **THEN** each vertex is annotated reachable/unreachable by finite vs infinite extraction cost, with
  no reference to a stored SAT bit
