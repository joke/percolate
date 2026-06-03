## MODIFIED Requirements

### Requirement: ExpansionStep result type

The `percolate-spi` module SHALL define an immutable value type `io.github.joke.percolate.spi.ExpansionStep` carrying: an ordered `List<Slot> inputs` of length `0..N`; a `TypeMirror output`; a `Codegen codegen` that assembles the inputs into the output; an `Intent intent`; an `Optional<ElementScope> scope`; and an `int weight`.

A step with `intent == CONVERSION` SHALL have exactly one input and SHALL describe an in-place re-typing of the value at the frontier's position (same flow identity). The single input names a **type the driver reuses-or-synthesizes** in the current group's view: the input value need NOT already exist as an in-view candidate — when no node of the input type is present, the driver synthesizes one and resolves it as a frontier within the same view (see `graph-expansion`). A strategy author MAY therefore emit a `CONVERSION` step whose input names an intermediate type (e.g. a `long` between `int` and `Long`) that the engine will produce. A step with `intent == BOUNDARY` SHALL describe crossing into a new flow identity and its `inputs` SHALL be the slots of the subgroup it opens (`0` for a terminal producer, `1` for a getter or unary call, `N` for an assembly). `scope` SHALL be present only on container boundary steps.

#### Scenario: CONVERSION step has exactly one input
- **WHEN** an `ExpansionStep` is constructed with `intent == CONVERSION`
- **THEN** `inputs().size()` equals `1`

#### Scenario: CONVERSION input need not pre-exist in view
- **WHEN** a strategy emits a `CONVERSION` step whose single input type has no matching node in the frontier's group view
- **THEN** the step is still valid (the driver synthesizes the input node rather than discarding the step)

#### Scenario: scope is absent on non-container steps
- **WHEN** a non-container `ExpansionStep` is constructed
- **THEN** `scope()` returns `Optional.empty()`

#### Scenario: boundary step slot count is unconstrained
- **WHEN** an `ExpansionStep` is constructed with `intent == BOUNDARY` and `N` inputs
- **THEN** `inputs().size()` equals `N` for any `N >= 0`
