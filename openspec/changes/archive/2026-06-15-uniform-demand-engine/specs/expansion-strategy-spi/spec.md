## MODIFIED Requirements

### Requirement: Demand decision context

Strategies SHALL receive a demand context exposing: the demanded Value's type and nullness; the
binding `Directive` in effect (carried by the work-list, see `graph-expansion`); the declared
bindings at the current target level (for assembly strategies); the **binding/slot name** the demand
serves (so a crossing strategy can name it, e.g. in a `requireNonNull` message); and a candidate
snapshot of the **in-scope source Values** — the current scope's parameter roots and the source
accessor Values already materialized in it, **not** a per-demand list hand-curated by the driver.
Directive selection of a specific source is carried by the demanded `SourceLocation`, not by
candidate filtering. The context exposes neither the graph nor any handle to traverse it.

#### Scenario: Assembly reads the goal spec from the context
- **WHEN** `ConstructorCall` matches a demand
- **THEN** it reads the declared-children name set from the demand context, not from a group

#### Scenario: Candidates are the in-scope source values
- **WHEN** a strategy inspects a demand's candidates
- **THEN** it sees the in-scope source Values of the current (method or child) scope, not a list the
  driver curated for that one demand

## ADDED Requirements

### Requirement: Nullness crossings and source accessors are strategies

Nullness crossings and source accessors SHALL be ordinary `ExpansionStrategy` implementations, not
engine-resident productions: the `NULLABLE → NON_NULL` crossings (`[requireNonNull]` and, with a
declared default, `[coalesce]`) and the per-segment source accessors (getter / method / field)
register through the existing `ServiceLoader`/`@AutoService` mechanism. A crossing strategy fires on a `(nullable candidate,
non-null demand)` pair, reading the binding/slot name and any `defaultValue` from the demand context;
an accessor strategy produces a source `Value` from its parent (a shallower `SourceLocation` demand).

#### Scenario: requireNonNull is a service-loadable strategy
- **WHEN** `ServiceLoader.load(ExpansionStrategy.class)` is enumerated
- **THEN** the nullness-crossing strategy is present, and it emits `[requireNonNull]` (or `[coalesce]`
  when the demand's directive declares a default) for a nullable-to-non-null pair

#### Scenario: An accessor strategy pulls its parent
- **WHEN** a `Value` at `SourceLocation("p.address.street")` is demanded
- **THEN** an accessor strategy emits the `getStreet()` Operation and demands `SourceLocation("p.address")`,
  which recurses to the parameter root — no eager whole-path materialization
