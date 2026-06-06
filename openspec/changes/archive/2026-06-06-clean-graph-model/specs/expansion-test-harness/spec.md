## MODIFIED Requirements

### Requirement: Invariant checks exposed on `ExpansionResult`

`ExpansionResult` SHALL expose four invariant-related checks that tests MAY call explicitly. The harness itself SHALL NOT assert these before returning — under explicit opt-in there is no auto-invariant to opt out of, so the previously deferred opt-out mechanism is closed as not needed.

- **Convergence flag** — `result.converged()` returns `false` when `ExpandStage` emitted an "Expansion did not converge after N rounds" diagnostic, otherwise `true`.
- **Idempotence stub** — `result.isIdempotent()` is reserved for a structural same-graph check; the current implementation returns `true` unconditionally. Wiring it to a real comparison is a future concern.
- **Identity collapse** — `result.hasIdentityCollisions()` SHALL return `true` iff two distinct `Node` instances in the result share the same `Node.id()` (which encodes `(scope, location, type)`).
- **Orphan REALISED nodes** — `result.hasOrphanRealisedNodes()` SHALL return `true` iff any `REALISED` edge endpoint is unreachable, via `REALISED`/`SEED` edges, from any `SEED`-edge endpoint. (The traversable lattice is `REALISED | SEED`; the former `MARKER` rung is removed with the `EdgeKind.MARKER` value.)

The `roundCount()` accessor SHALL be present but its value is currently a placeholder (`1`) until `ExpandStage` publishes rounds through `MapperContext`. Tests SHOULD NOT rely on the value today.

#### Scenario: Identity-collapse check is structural and total
- **WHEN** `ExpansionHarness.expand(seed, ...)` returns
- **THEN** `result.hasIdentityCollisions()` examines every node in the expanded graph
- **AND** returns `true` iff at least two distinct nodes share `Node.id()`

#### Scenario: Orphan-detection respects the traversable lattice
- **WHEN** a result graph contains a `REALISED` edge whose endpoints have no `REALISED`/`SEED` path to any `SEED`-edge endpoint
- **THEN** `result.hasOrphanRealisedNodes()` returns `true`
- **AND** a result graph whose `REALISED` edges are all anchored to `SEED` endpoints returns `false`
