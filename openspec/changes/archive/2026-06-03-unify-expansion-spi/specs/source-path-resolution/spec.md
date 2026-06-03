## ADDED Requirements

### Requirement: Path resolution as a unified ExpansionStrategy

Source-path-segment resolution SHALL be performed by `ExpansionStrategy` implementations (the `Getter` / `Method` / `Field` path resolvers) rather than a dedicated `PathSegmentResolver` SPI. Each resolver SHALL read the segment to resolve from `frontier.directive()` (and the frontier's position within the directive's source path) instead of receiving a `segment` parameter, and SHALL emit a `BOUNDARY` `ExpansionStep` describing the typed access for that segment. The seed-time typing outcome (a typed source node per resolvable segment) SHALL be preserved.

Resolvers SHALL register via `@AutoService(ExpansionStrategy.class)` and SHALL be subject to the same single-list, `priority()`-then-FQN ordering as every other strategy.

#### Scenario: a path resolver reads its segment from the directive
- **WHEN** a path resolver's `expand(frontier, ctx)` is invoked for a frontier mid-way along a `@Map` source path
- **THEN** the resolver obtains the segment to resolve from `frontier.directive()`
- **AND** does not receive a `segment` method parameter

#### Scenario: a resolved segment is a boundary step
- **WHEN** a path resolver resolves a segment access
- **THEN** it emits an `ExpansionStep` with `intent == BOUNDARY`
- **AND** the step's slot describes the parent value the access reads from

### Requirement: Parameter-root base case resolves against the node's own method scope

A single-segment source node (a `src[param]` root) SHALL be recognised as satisfied-by-parameter using the method carried by **the node's own scope** (its `MethodScope`), never a process-global "current method". Because the driver expands every method's groups together in one cross-group fixed-point loop, a base case keyed on a single shared current method would satisfy only one method's parameter roots and strand the rest; resolving against the node's own scope keeps multi-method mappers correct regardless of expansion order. The same node-scope resolution SHALL be used wherever a parameter root must be recovered (producer-scope recovery and failing-slot diagnostics).

#### Scenario: a parameter root is satisfied in a multi-method mapper
- **WHEN** a mapper declares two methods and the driver expands their groups in one fixed-point loop
- **THEN** each method's single-segment source roots are satisfied against that method's own parameters
- **AND** the outcome does not depend on which method's seed edge the driver visited first

#### Scenario: a single-segment source naming no parameter of its own method is not a root
- **WHEN** a single-segment source node's segment matches no parameter of the method in its own scope
- **THEN** it is not treated as a parameter-root base case

## REMOVED Requirements

### Requirement: PathSegmentResolver SPI
**Reason**: Collapsed into the single `ExpansionStrategy` interface; the `segment` is read from `frontier.directive()` rather than passed in.
**Migration**: Implement `ExpansionStrategy` and emit a `BOUNDARY` step for the segment access (see "Path resolution as a unified ExpansionStrategy").

### Requirement: ResolvedSegment result type
**Reason**: Replaced by `ExpansionStep`; the consumer contract moves onto `Slot` metadata.
**Migration**: Emit a `BOUNDARY` `ExpansionStep`; carry the consumer contract on its `Slot`.

### Requirement: Resolver registration via ServiceLoader
**Reason**: Folded into the single `ExpansionStrategy` `ServiceLoader` registration.
**Migration**: Register via `@AutoService(ExpansionStrategy.class)`.

### Requirement: Resolver priority determinism
**Reason**: Ordering is now the uniform single-list `priority()`-then-FQN rule shared by all strategies.
**Migration**: Rely on the unified ordering defined in `expansion-strategy-spi`.

### Requirement: Expansion-time path-segment-group resolution
**Reason**: Path segments are resolved by an `ExpansionStrategy` emitting `BOUNDARY` steps in the single round-robin, not by a resolver-specific resolution path.
**Migration**: See "Path resolution as a unified ExpansionStrategy" and the unified emission rule in `graph-expansion`.
