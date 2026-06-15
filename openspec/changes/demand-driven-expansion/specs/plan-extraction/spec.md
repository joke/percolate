## MODIFIED Requirements

### Requirement: Bottom-up cost extraction

The processor SHALL select the plan via a single bottom-up **minimum-cost hyperpath** fold over the
bipartite graph: the cost of a `Value` is the **minimum** `Cost` over its producer `Operation`s; the
cost of an `Operation` is its own `Cost` **combined with the sum** of the costs of all its port
`Value`s (plus the child return-root cost for a scope-owning Operation). The fold SHALL use no
shortest-path oracle: the cost through an n-ary `Operation` sums all ports, never minimises over one.
A `Value` with no producer is a base case (`Cost.ZERO`) **only when it is a `LEAF`** (a parameter
root or a container element root); **every other** producerless `Value` is `Cost.INFINITE` —
including a multi-segment `ACCESS` source demand whose accessor never matched. This one fold subsumes
satisfaction: there is **no separate SAT pass** (see ADDED "Reachability is derived from cost").

#### Scenario: Operation cost sums all ports

- **WHEN** an `Operation` with weight `w` has ports fed by Values costing `a` and `b`
- **THEN** the Operation's cost weight component is `w + a + b`

#### Scenario: OR resolution picks the cheapest producer

- **WHEN** a `Value` has two producers whose total costs differ
- **THEN** the extracted plan records the cheaper producer as the Value's chosen producer

#### Scenario: Unreachable producers never participate

- **WHEN** a `Value` has one reachable (finite-cost) and one unreachable (`Cost.INFINITE`) producer
- **THEN** the unreachable producer is never chosen, independent of its weight component

#### Scenario: A failed accessor chain is unreachable, not vacuously reachable

- **WHEN** a multi-segment `ACCESS` source demand acquires no accessor producer (no resolver matched)
- **THEN** its cost is `Cost.INFINITE` (it is not a base case, because it is not a `LEAF`), so any
  Operation depending on it is unreachable
