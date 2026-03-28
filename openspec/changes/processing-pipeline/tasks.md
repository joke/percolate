## 1. Pipeline Infrastructure

- [x] 1.1 Create `Diagnostic` class with `Element`, `String message`, and `Diagnostic.Kind`
- [x] 1.2 Create `StageResult<T>` class with `success(T)` / `failure(List<Diagnostic>)` factory methods, `isSuccess()`, `value()`, `errors()`
- [x] 1.3 Unit test `StageResult` and `Diagnostic`

## 2. Models

- [x] 2.1 Create `MapDirective` (source, target strings)
- [x] 2.2 Create `MappingMethodModel` (ExecutableElement method, TypeMirror sourceType, TypeMirror targetType, List<MapDirective> directives)
- [x] 2.3 Create `MapperModel` (TypeElement mapperType, List<MappingMethodModel> methods)
- [x] 2.4 Create `ReadAccessor` abstract type with `GetterAccessor` and `FieldReadAccessor`
- [x] 2.5 Create `WriteAccessor` abstract type with `ConstructorParamAccessor` and `FieldWriteAccessor`
- [x] 2.6 Create `DiscoveredMethod` (MappingMethodModel original, Map<String, ReadAccessor> sourceProperties, Map<String, WriteAccessor> targetProperties)
- [x] 2.7 Create `DiscoveredModel` (TypeElement mapperType, List<DiscoveredMethod> methods)

## 3. Graph Model

- [x] 3.1 Create `PropertyNode` with `SourcePropertyNode` and `TargetPropertyNode` subtypes
- [x] 3.2 Create `MappingEdge` with mapping type enum (DIRECT)
- [x] 3.3 Unit test graph construction with JGraphT `DefaultDirectedGraph`

## 4. AnalyzeStage

- [x] 4.1 Implement `AnalyzeStage` — extract abstract methods, parse `@Map`/`@MapList` annotations, resolve source/target types
- [x] 4.2 Add validation: method must have exactly one parameter and non-void return type
- [x] 4.3 Unit test with mocked `Elements` and `TypeElement`
- [x] 4.4 Integration test with compile-testing framework

## 5. Property Discovery SPI

- [x] 5.1 Create `SourcePropertyDiscovery` SPI interface
- [x] 5.2 Create `TargetPropertyDiscovery` SPI interface
- [x] 5.3 Implement `GetterDiscovery` (get/is prefix, priority 100)
- [x] 5.4 Implement `ConstructorDiscovery` (constructor params, priority 100)
- [x] 5.5 Implement `FieldDiscovery` for source and target (public fields, priority 50)
- [x] 5.6 Register built-in strategies in `META-INF/services`
- [x] 5.7 Unit test each discovery strategy

## 6. DiscoverStage

- [x] 6.1 Implement `DiscoverStage` — load SPIs via ServiceLoader, run strategies, merge by priority
- [x] 6.2 Unit test priority-based merging
- [x] 6.3 Integration test with compile-testing framework

## 7. BuildGraphStage

- [x] 7.1 Implement `BuildGraphStage` — create JGraphT graph from discovered model, add nodes and edges for each directive
- [x] 7.2 Return failure diagnostic for directives referencing unknown source or target properties
- [x] 7.3 Unit test graph construction and error cases

## 8. ValidateStage

- [x] 8.1 Implement `ValidateStage` — use `ConnectivityInspector` to detect unmapped target properties (error)
- [x] 8.2 Detect duplicate target mappings via in-degree check
- [x] 8.3 Add `DOTExporter` support for debug output
- [x] 8.4 Unit test validation scenarios

## 9. GenerateStage

- [x] 9.1 Implement `GenerateStage` — walk graph, emit `JavaFile` via JavaPoet
- [x] 9.2 Support constructor-based target construction (order args by parameter index)
- [x] 9.3 Support field-based target construction (no-arg constructor + field assignment)
- [x] 9.4 Support getter-based and field-based source access
- [x] 9.5 Write generated `JavaFile` to `Filer`
- [x] 9.6 Unit test generated code structure

## 10. Pipeline Wiring

- [x] 10.1 Update `Pipeline` to inject all five stages and chain with `StageResult` short-circuiting
- [x] 10.2 Report diagnostics to `Messager` on failure
- [x] 10.3 Update `ProcessorComponent` if needed for new Dagger bindings
- [x] 10.4 Unit test pipeline orchestration with mocked stages

## 11. End-to-End Integration Tests

- [x] 11.1 Integration test: `@Mapper` interface with `@Map` directives generates correct constructor-based implementation
- [x] 11.2 Integration test: `@Mapper` interface with field-based target generates correct implementation
- [x] 11.3 Integration test: invalid `@Map` directive produces compiler error pointing at correct element
- [x] 11.4 Integration test: unmapped target property produces compiler error
- [x] 11.5 Integration test: multiple mappers with one failing — other still generates
