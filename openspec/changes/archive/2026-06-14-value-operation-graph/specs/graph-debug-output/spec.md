## ADDED Requirements

### Requirement: Bipartite DOT rendering

The DOT renderer SHALL draw the bipartite graph Petri-style: `Operation` vertices as boxes labelled
with their codegen's simple class name (and weight), `Value` vertices as ellipses labelled with
location, simple type segment, and nullness, and `Dep` edges labelled with their port id (when
feeding an Operation port). Scope-owning Operations render their child scope as a DOT cluster.
Rendering remains deterministic (stable vertex and edge ordering).

#### Scenario: Vertex kinds are visually distinct
- **WHEN** a graph containing Values and Operations is rendered
- **THEN** Operations render as boxes and Values as ellipses, with port ids on port edges

#### Scenario: Child scope renders as a cluster
- **WHEN** a scope-owning container Operation is rendered
- **THEN** its child scope's vertices appear inside a DOT cluster attached to the Operation

### Requirement: Three dumps over the bipartite graph

The three dump stages SHALL be retained with unchanged gating and file naming: the seed dump (roots
and goal-spec annotations), the full dump (every vertex and edge, SAT-state annotated), and the
transforms/plan dump (the extracted plan view only).

#### Scenario: Plan dump shows only chosen producers
- **WHEN** the plan dump is written for a method with competing constructors
- **THEN** only the chosen constructor Operation and its supply chains appear

## REMOVED Requirements

### Requirement: Transforms view filter on MapperGraph
**Reason**: The structural REALISED-only filter has no equivalent; the plan dump renders the
extraction view.
**Migration**: See ADDED "Three dumps over the bipartite graph" and `plan-extraction`.

### Requirement: Plan view filter on MapperGraph
**Reason**: Plan selection moved to the `plan-extraction` capability; the dump consumes its view.
**Migration**: See `plan-extraction` ADDED "Extracted plan is a read-only single-producer view".

### Requirement: Edge label includes EdgeKind marker
**Reason**: No edge kinds exist; edges are labelled with port ids.
**Migration**: See ADDED "Bipartite DOT rendering".

### Requirement: Node and edge visual distinction
**Reason**: Restated for the two vertex kinds.
**Migration**: See ADDED "Bipartite DOT rendering".

### Requirement: DOT renderer renders all EdgeKind values
**Reason**: No edge kinds exist.
**Migration**: See ADDED "Bipartite DOT rendering".

### Requirement: Linear container chains render without diamond shortcuts
**Reason**: The no-diamond invariant is gone; container structure renders as clusters.
**Migration**: See ADDED "Bipartite DOT rendering" (child-scope clusters).

### Requirement: Node labels include the simple type segment
**Reason**: Restated for Value labels (type plus nullness).
**Migration**: See ADDED "Bipartite DOT rendering".

### Requirement: Container strategies render with their simple class name
**Reason**: Restated: every Operation (container or not) is labelled with its codegen's simple class
name.
**Migration**: See ADDED "Bipartite DOT rendering".
