## ADDED Requirements

### Requirement: The driver is a pure work-list; the engine builds no Operations

The expansion driver SHALL be a single uniform work-list: a demanded `Value` is turned into one
strategy query round (`run(all strategies, demand)`), each accepted match lands atomically as an
`AddOperation`, and each landed Operation's ports are enqueued as new demands. The driver SHALL NOT
contain a per-supply-mode branch (no `assembly` / `bridge` split) and SHALL NOT hand-build any
`Operation` itself — every plan Operation, **including nullness crossings and source accessors**,
originates from an `ExpansionStrategy` match. Misfires are prevented structurally by emission-time
gating (assembly strategies on the declared-bindings goal spec; conversions on a candidate type
match), not by a routing branch in the driver.

#### Scenario: One uniform query round per demand
- **WHEN** any target `Value` is demanded
- **THEN** the driver runs the full strategy set against the demand once and enqueues every emitted
  Operation's ports as demands, with no assembly-versus-bridge branch selecting the strategy set

#### Scenario: The driver constructs no Operation directly
- **WHEN** the expansion driver source is inspected
- **THEN** it builds no `Operation`/codegen by hand (no driver-resident `requireNonNull`/`coalesce`
  emission, no eager source-descent component); every landed Operation came from a strategy

### Requirement: A port is a demand; matchmaking is not the driver's job

Binding an Operation's port SHALL be expressed as enqueuing a demand for a `Value` of the port's
type and nullness at the appropriate location; the driver SHALL NOT perform candidate-match-or-
synthesize matchmaking. A port that no strategy can produce remains unreachable by exhaustion (its
Operation unreachable) — there is no special "no candidate ⇒ only a zero-port producer" guard,
because "unsatisfied = no producer" already yields it.

#### Scenario: An unsatisfiable port starves without a special guard
- **WHEN** a conversion Operation declares an input port whose type no in-scope source value or
  further strategy produces
- **THEN** the port Value acquires no producer and the conversion Operation is unreachable, with no
  driver-side guard consulted
