## MODIFIED Requirements

### Requirement: Bottom-up cost extraction

The processor SHALL select the plan via a single bottom-up **minimum-cost hyperpath** fold over the
bipartite graph: the cost of a `Value` is the **minimum** `Cost` over its producer `Operation`s; the
cost of an `Operation` is its own `Cost` **combined with the sum** of the costs of all its port
`Value`s (plus the child return-root cost for a scope-owning Operation). The fold SHALL use no
shortest-path oracle: the cost through an n-ary `Operation` sums all ports, never minimises over one.
A `Value` with no producer is the base case — `Cost.ZERO` when it is a supply root, `Cost.INFINITE`
otherwise. This one fold subsumes satisfaction: there is **no separate SAT pass** (see ADDED
"Reachability is derived from cost").

#### Scenario: Operation cost sums all ports
- **WHEN** an `Operation` with weight `w` has ports fed by Values costing `a` and `b`
- **THEN** the Operation's cost weight component is `w + a + b`

#### Scenario: OR resolution picks the cheapest producer
- **WHEN** a `Value` has two producers whose total costs differ
- **THEN** the extracted plan records the cheaper producer as the Value's chosen producer

#### Scenario: Unreachable producers never participate
- **WHEN** a `Value` has one reachable (finite-cost) and one unreachable (`Cost.INFINITE`) producer
- **THEN** the unreachable producer is never chosen, independent of its weight component

### Requirement: Total producers dominate partial producers

Totality SHALL be the **most significant component** of the `Cost` vector: a producer whose subtree
contains fewer **partial** Operations (a production that may throw on a structurally-valid input —
`unwrap`/`orElseThrow`, `[requireNonNull]`, counted transitively through ports and child plans) is
strictly preferred, **independent of weight**, because the single `min`-over-`Cost` comparison orders
the totality component ahead of the weight component. A partial producer is selected only when every
alternative carries at least as much partiality; the weight component and the deterministic tie-break
decide only among producers of equal totality. There is no separate dominance pass — it is one
comparison of one vector.

#### Scenario: Drop-empties beats throwing unwrap
- **WHEN** a stream element `Stream<Optional<A>> → Stream<B>` has both a total `flatMap` (drop empties)
  producer and a partial `map`+`orElseThrow` producer
- **THEN** extraction selects the `flatMap` producer regardless of which has the lower weight component

#### Scenario: Partial chosen only as the sole producer
- **WHEN** a non-null target field is fed from a nullable source with no declared default
- **THEN** the partial `[requireNonNull]` producer is selected because the Value has no total producer

#### Scenario: Default coalesce dominates requireNonNull
- **WHEN** a crossing has both a total `[coalesce]` (declared default) and a partial `[requireNonNull]`
  producer
- **THEN** extraction selects `[coalesce]` by the totality component, not by weight

## ADDED Requirements

### Requirement: Cost is a lexicographically-ordered vector

Selection SHALL be expressed over a single comparable `Cost` value — a lexicographically-ordered
vector whose components are, in order: **totality** (partial-operation count, most significant),
**weight** (the summed operation weights), and a **deterministic tie-break** derived from vertex
identity. `Cost.INFINITE` SHALL denote an unreachable vertex and `Cost.ZERO` a base case. Combination
is the semiring shape: `⊕` (at a `Value`) is `min` over producers; `⊗` (at an `Operation`) combines
componentwise (counts and weights add). A **new selection preference SHALL be introduced as an
additional `Cost` component** at its chosen significance, with no change to the fold algorithm or to
any strategy.

#### Scenario: Component order is totality, then weight, then tie-break
- **WHEN** two producers differ in totality
- **THEN** the lower-partiality producer wins regardless of weight
- **WHEN** two producers have equal totality but different weight
- **THEN** the lower-weight producer wins, and equal weight falls to the deterministic tie-break

#### Scenario: A new preference is a new component, not a new pass
- **WHEN** a new selection preference (e.g. prefer non-deprecated producers) is added as a `Cost`
  component
- **THEN** the minimum-cost fold consumes it through the same `⊕`/`⊗` with no algorithm change

### Requirement: Reachability is derived from cost

There SHALL be no stored satisfaction predicate on the graph; a vertex is reachable **iff** its
extraction `Cost` is finite (`< Cost.INFINITE`). `ExtractedPlan` SHALL expose `reachable(Value)` (and
the underlying `cost(Value)`), computed by the same memoized fold (with the existing cycle guard, so a
zero-weight cycle yields `Cost.INFINITE` and is never chosen). Downstream consumers (realisation
diagnostics, the full debug dump) SHALL query this rather than a SAT bit.

#### Scenario: Reachable iff finite cost
- **WHEN** a `Value`'s only producers form a zero-weight cycle with no acyclic derivation from a
  supply root
- **THEN** `reachable` for that Value is false because its cost is `Cost.INFINITE`

#### Scenario: No stored SAT predicate
- **WHEN** the graph and plan surfaces are inspected
- **THEN** reachability is available only as a derived query over extraction cost, with no
  `markSat`/`isSat` predicate stored on the graph
