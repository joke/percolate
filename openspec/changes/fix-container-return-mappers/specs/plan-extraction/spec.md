## ADDED Requirements

### Requirement: Extraction is rooted at the seeded return Values

Plan extraction SHALL root its per-method extraction at the **seeded return `Value`s** recorded during
expansion (`graph-expansion`), not at every `Value` whose location is the empty return path. A typed
conversion way-point minted at the return location (a `Stream<E>` minted while producing a `List<E>`
root) SHALL be treated as an ordinary intermediate — costed by the same minimum-cost fold and
selectable only as a port of some producer — and SHALL NOT seed an independent extraction root. This
changes neither the cost fold nor the cost-derived reachability rule; it fixes only **which** Values
are top-level roots.

#### Scenario: Only the seeded root seeds extraction
- **WHEN** a method returns `List<E>` and over-emission minted `Stream<E>` and `Set<E>` Values at the same return location
- **THEN** extraction is rooted only at the seeded `List<E>` Value; the `Stream<E>` participates solely as the `collect` producer's port, and an unreachable `Set<E>` is simply absent from the plan rather than an independent root

#### Scenario: Same-location intermediate is still costed normally
- **WHEN** the seeded `List<E>` root's chosen producer is `collect` whose port is a same-location `Stream<E>`
- **THEN** the `Stream<E>` Value's cost contributes to the root's cost through the ordinary `⊗` combination, exactly as any other port would
