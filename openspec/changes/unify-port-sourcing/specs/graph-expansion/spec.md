## MODIFIED Requirements

### Requirement: A port is a demand; matchmaking is not the driver's job

Binding an Operation's port SHALL be a dispatch on the port's **declared sourcing mode** (see the expansion-strategy-spi requirement "Port declares an explicit sourcing mode"), never a reconstruction of the port's intent from a name-match or a boolean:

- `SUBTARGET` — the driver mints a `FREE` demand for a child `Value` at the child location (the parent target path extended by the port name) and enqueues it.
- `REUSE` — the driver binds a matching in-scope source `Value`; with none, the Operation does not apply (the port is never minted).
- `REUSE_OR_MINT` — the driver binds a matching in-scope source `Value`, else mints and enqueues a fresh `FREE` intermediate of the port's type and nullness at the output location.

The driver SHALL NOT perform candidate-match-or-synthesize matchmaking, and SHALL NOT reconstruct a port's mode by matching the port name against the demand's declared-children set — that set gates assembly **in the demand** (see "Assembly is gated by the declared-bindings goal spec") and SHALL NOT participate in the engine's binding path. Selecting among matching in-scope sources SHALL preserve directive-preference (see "Forward target-bound descent walks a directive path"): a directive-pinned source is preferred over a same-typed sibling. A port that no in-scope source and no strategy can produce remains unreachable by exhaustion (its Operation unreachable) — there is no special "no candidate ⇒ only a zero-port producer" guard, because "unsatisfied = no producer" already yields it.

#### Scenario: Binding dispatches on the declared sourcing mode
- **WHEN** the driver binds a port carrying mode `SUBTARGET`, `REUSE`, or `REUSE_OR_MINT`
- **THEN** it respectively mints a child-target demand, binds-or-declines an in-scope source, or binds-an-in-scope-source-else-mints-an-intermediate — chosen by the declared mode, not by a name-match

#### Scenario: The declared-children set does not participate in binding
- **WHEN** the driver binds an Operation's ports
- **THEN** it consults each port's declared sourcing mode, not whether the port name is in the demand's declared-children set; that set is read only by assembly strategies gating in the demand

#### Scenario: A directive-pinned source is preferred over a same-typed sibling
- **WHEN** a port could bind either the leaf descended for its target's own directive source path or a same-typed sibling source
- **THEN** the driver binds the directive-pinned leaf

#### Scenario: An unsatisfiable port starves without a special guard
- **WHEN** a conversion Operation declares an input port whose type no in-scope source value or further strategy produces
- **THEN** the port Value acquires no producer and the conversion Operation is unreachable, with no driver-side guard consulted
