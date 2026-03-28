## MODIFIED Requirements

### Requirement: PropertyNode carries accessor metadata
`SourcePropertyNode` SHALL carry the property name, type, and `ReadAccessor`. `TargetPropertyNode` SHALL carry the property name, type, and `WriteAccessor`. These accessors are used by the generate stage to emit correct access code. `PropertyNode` SHALL use Lombok `@Getter` and `@RequiredArgsConstructor(access = AccessLevel.PROTECTED)` to generate accessors and constructor. Leaf subclasses SHALL use Lombok `@Getter` for their own fields. Accessor methods SHALL follow JavaBean naming conventions (`getName()`, `getType()`, `getAccessor()`).

#### Scenario: Source node provides read accessor
- **WHEN** a `SourcePropertyNode` is created for property `firstName` with a `GetterAccessor`
- **THEN** `getAccessor()` SHALL return the `GetterAccessor` for code generation
