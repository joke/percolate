## MODIFIED Requirements

### Requirement: TypeTransformStrategy SPI interface
The processor SHALL provide a `TypeTransformStrategy` interface with a single method `canProduce(TypeMirror sourceType, TypeMirror targetType, ResolutionContext ctx)` that returns `Optional<TransformProposal>`. Implementations SHALL be discovered via `ServiceLoader` using `@AutoService`. Each strategy examines the source and target types and either proposes a transformation edge or returns empty. The `ResolutionContext` SHALL provide access to `Types`, `Elements`, the mapper's `TypeElement`, the current method's `ExecutableElement`, per-mapping options as `Map<MapOptKey, String>`, and a `String using` field for method name routing.

#### Scenario: Strategy matches and proposes a transformation
- **WHEN** a `TypeTransformStrategy` implementation recognizes the source-target type pair
- **THEN** it SHALL return a `TransformProposal` containing the required input type, produced output type, and a `CodeTemplate`

#### Scenario: Strategy does not match
- **WHEN** a `TypeTransformStrategy` implementation does not recognize the source-target type pair
- **THEN** it SHALL return `Optional.empty()`

#### Scenario: Strategy discovers callable methods via ResolutionContext
- **WHEN** a strategy needs to find a method that converts source type to target type
- **THEN** it SHALL use `ctx.getElements().getAllMembers(ctx.getMapperType())` to discover all methods including abstract, default, concrete, and inherited methods

#### Scenario: Strategy excludes current method from candidates
- **WHEN** a strategy searches for callable methods
- **THEN** it SHALL exclude `ctx.getCurrentMethod()` to prevent self-referential calls

#### Scenario: Strategy reads per-mapping options
- **WHEN** a strategy needs to check for a mapping option (e.g., `DATE_FORMAT`)
- **THEN** it SHALL read from `ctx.getOptions()` which returns `Map<MapOptKey, String>`

#### Scenario: Options are empty for auto-mapped properties
- **WHEN** a strategy is invoked for an auto-mapped property (no explicit `@Map` directive)
- **THEN** `ctx.getOptions()` SHALL return an empty map

#### Scenario: Strategy reads using for method name routing
- **WHEN** a strategy needs to check if a specific method name is requested for this mapping
- **THEN** it SHALL read from `ctx.getUsing()` which returns a `String` (empty string means not set)

#### Scenario: Using is empty for auto-mapped properties
- **WHEN** a strategy is invoked for an auto-mapped property (no explicit `@Map` directive)
- **THEN** `ctx.getUsing()` SHALL return an empty string
