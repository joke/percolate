## ADDED Requirements

### Requirement: Container strategies bind to ExpansionStrategy via ContainerMatch

Container strategies (the `Optional` / `List` / `Set` / `Array` iterate/collect/unwrap/wrap families) SHALL be `ExpansionStrategy` implementations, written via the `ContainerMatch` mixin from author-supplied `matches` / `element` snippets. Each iterate/collect/unwrap/wrap operation SHALL be emitted as an `ExpansionStep` with `intent == BOUNDARY` carrying the appropriate `ElementScope` in `scope`:

- An operation whose output lives at element scope (unwrap / iterate into elements) carries `ElementScope.ENTERING`.
- An operation whose input lives at element scope (collect elements into a container) carries `ElementScope.EXITING`.
- A single-element wrap (e.g. `List.of(x)`, `Optional.of(x)`) is a `BOUNDARY` step with no `scope`.

The generated code (the stream/loop snippets each container emits) is unchanged; only the SPI binding and the step representation change.

#### Scenario: a container iterate/collect carries ElementScope
- **WHEN** a container strategy emits its collect operation
- **THEN** the emitted `ExpansionStep` has `intent == BOUNDARY`
- **AND** `scope()` returns `Optional.of(ElementScope.EXITING)`

#### Scenario: a single-element wrap has no scope
- **WHEN** a container strategy emits a single-element wrap step
- **THEN** the emitted `ExpansionStep` has `intent == BOUNDARY`
- **AND** `scope()` returns `Optional.empty()`

#### Scenario: container strategies register under the unified service type
- **WHEN** the source of any container strategy is inspected
- **THEN** it carries `@AutoService(ExpansionStrategy.class)`
- **AND** it implements `ExpansionStrategy` through the `ContainerMatch` mixin

## REMOVED Requirements

### Requirement: Container strategies registered via AutoService
**Reason**: Container strategies register under the unified `@AutoService(ExpansionStrategy.class)` like every other strategy; the `Bridge`-specific registration no longer applies.
**Migration**: Annotate container strategies with `@AutoService(ExpansionStrategy.class)` and implement `ContainerMatch`.
