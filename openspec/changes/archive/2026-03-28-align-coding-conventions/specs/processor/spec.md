## MODIFIED Requirements

### Requirement: Data carrier classes use Java records

All data carrier classes in the processor module SHALL be Java records instead of Lombok `@Value` classes. Record accessors SHALL use the `x()` naming convention (not `getX()`).

#### Scenario: Diagnostic is a record
- **WHEN** `Diagnostic` is inspected
- **THEN** it SHALL be declared as `record Diagnostic(Element element, String message, Kind kind)`

#### Scenario: MapDirective is a record
- **WHEN** `MapDirective` is inspected
- **THEN** it SHALL be declared as `record MapDirective(String source, String target)`

#### Scenario: MapperModel is a record
- **WHEN** `MapperModel` is inspected
- **THEN** it SHALL be declared as `record MapperModel(TypeElement mapperType, List<MappingMethodModel> methods)`

#### Scenario: MappingMethodModel is a record
- **WHEN** `MappingMethodModel` is inspected
- **THEN** it SHALL be declared as `record MappingMethodModel(ExecutableElement method, TypeMirror sourceType, TypeMirror targetType, List<MapDirective> directives)`

#### Scenario: DiscoveredModel is a record
- **WHEN** `DiscoveredModel` is inspected
- **THEN** it SHALL be declared as `record DiscoveredModel(TypeElement mapperType, List<DiscoveredMethod> methods)`

#### Scenario: DiscoveredMethod is a record
- **WHEN** `DiscoveredMethod` is inspected
- **THEN** it SHALL be declared as `record DiscoveredMethod(MappingMethodModel original, Map<String, ReadAccessor> sourceProperties, Map<String, WriteAccessor> targetProperties)`

#### Scenario: MappingGraph is a record
- **WHEN** `MappingGraph` is inspected
- **THEN** it SHALL be declared as `record MappingGraph(TypeElement mapperType, List<DiscoveredMethod> methods, DefaultDirectedGraph<PropertyNode, MappingEdge> graph)`

#### Scenario: MappingEdge is a record
- **WHEN** `MappingEdge` is inspected
- **THEN** it SHALL be declared as `record MappingEdge(Type type)` with its `Type` enum nested inside

### Requirement: Abstract hierarchies are sealed

Abstract base classes with a fixed set of subclasses SHALL be declared as `sealed` with explicit `permits` clauses.

#### Scenario: ReadAccessor is sealed
- **WHEN** `ReadAccessor` is inspected
- **THEN** it SHALL be declared as `sealed` permitting `FieldReadAccessor` and `GetterAccessor`

#### Scenario: WriteAccessor is sealed
- **WHEN** `WriteAccessor` is inspected
- **THEN** it SHALL be declared as `sealed` permitting `FieldWriteAccessor` and `ConstructorParamAccessor`

#### Scenario: PropertyNode is sealed
- **WHEN** `PropertyNode` is inspected
- **THEN** it SHALL be declared as `sealed` permitting `SourcePropertyNode` and `TargetPropertyNode`

### Requirement: Pattern matching for instanceof checks

All `instanceof` checks followed by a cast SHALL use pattern matching syntax (`instanceof Type name`).

#### Scenario: SPI discovery classes use pattern matching
- **WHEN** `ConstructorDiscovery`, `FieldDiscovery`, or `GetterDiscovery` check `type instanceof DeclaredType`
- **THEN** they SHALL use `if (type instanceof DeclaredType declaredType)` pattern matching

#### Scenario: Stage classes use pattern matching
- **WHEN** `ValidateStage`, `GenerateStage`, or `DiscoverStage` use `instanceof` checks
- **THEN** they SHALL use pattern matching syntax to bind the variable

### Requirement: Collections are unmodifiable

Methods SHALL return unmodifiable collections. `List.of()` SHALL be used instead of `Collections.emptyList()`. Stream terminal operations SHALL use `.toList()` instead of `Collectors.toList()`.

#### Scenario: StageResult uses List.of for empty list
- **WHEN** `StageResult.success()` creates an empty error list
- **THEN** it SHALL use `List.of()` instead of `Collections.emptyList()`

#### Scenario: AnalyzeStage uses toList
- **WHEN** `AnalyzeStage.execute()` collects stream results
- **THEN** it SHALL use `.toList()` instead of `Collectors.toList()`

#### Scenario: SPI discovery methods return unmodifiable lists
- **WHEN** `ConstructorDiscovery`, `FieldDiscovery.Source`, `FieldDiscovery.Target`, or `GetterDiscovery` return discovered accessors
- **THEN** the returned lists SHALL be unmodifiable
