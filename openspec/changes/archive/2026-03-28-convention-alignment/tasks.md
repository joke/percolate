## 1. Lombok: Abstract Base Classes

- [x] 1.1 Add `@Getter` and `@RequiredArgsConstructor(access = AccessLevel.PROTECTED)` to `ReadAccessor`, remove manual constructor and accessor methods
- [x] 1.2 Add `@Getter` and `@RequiredArgsConstructor(access = AccessLevel.PROTECTED)` to `WriteAccessor`, remove manual constructor and accessor methods
- [x] 1.3 Add `@Getter` and `@RequiredArgsConstructor(access = AccessLevel.PROTECTED)` to `PropertyNode`, remove manual constructor and accessor methods (keep `toString()`)

## 2. Lombok: Leaf Subclasses

- [x] 2.1 Add `@Getter` to `GetterAccessor`, remove manual `method()` accessor
- [x] 2.2 Add `@Getter` to `FieldReadAccessor`, remove manual `field()` accessor
- [x] 2.3 Add `@Getter` to `FieldWriteAccessor`, remove manual `field()` accessor
- [x] 2.4 Add `@Getter` to `ConstructorParamAccessor`, remove manual `constructor()` and `paramIndex()` accessors
- [x] 2.5 Add `@Getter` to `SourcePropertyNode`, remove manual `accessor()` accessor
- [x] 2.6 Add `@Getter` to `TargetPropertyNode`, remove manual `accessor()` accessor

## 3. Update Call Sites (fluent to JavaBean)

- [x] 3.1 Update `DiscoverStage` — `accessor.name()` to `accessor.getName()`, `accessor.type()` to `accessor.getType()`
- [x] 3.2 Update `BuildGraphStage` — `accessor.name()`/`.type()` to `.getName()`/`.getType()`, `node.name()` to `node.getName()`
- [x] 3.3 Update `ValidateStage` — `node.name()` to `node.getName()`
- [x] 3.4 Update `GenerateStage` — `.name()`, `.type()`, `.accessor()`, `.method()`, `.paramIndex()` to JavaBean equivalents
- [x] 3.5 Update `PropertyNode.toString()` — `name` field access to `getName()`
- [x] 3.6 Update Spock test specs — change any `.name()`, `.accessor()` etc. to `.getName()`, `.getAccessor()` or Groovy property access

## 4. Static Imports

- [x] 4.1 Replace qualified `ElementKind.*`, `Modifier.*` with static imports in `FieldDiscovery`, `GetterDiscovery`, `ConstructorDiscovery`
- [x] 4.2 Replace qualified `ElementKind.*`, `Modifier.*`, `TypeKind.*`, `Kind.*` with static imports in `AnalyzeStage`
- [x] 4.3 Replace qualified `Kind.*` with static imports in `BuildGraphStage` and `ValidateStage`
- [x] 4.4 Replace qualified `Modifier.*`, `Kind.*`, `Comparator.comparingInt` with static imports in `GenerateStage`
- [x] 4.5 Replace qualified `Comparator.comparingInt` with static imports in `DiscoverStage` and `ConstructorDiscovery`

## 5. Streams and Immutability

- [x] 5.1 Convert `DiscoverStage.execute` loop to stream producing an unmodifiable list of `DiscoveredMethod`
- [x] 5.2 Convert `DiscoverStage.loadAndSort` to stream-based loading with immutable result
- [x] 5.3 Convert simple collection-to-node loops in `BuildGraphStage` to streams where no error accumulation is involved
- [x] 5.4 Convert `FieldDiscovery` element filtering loops to streams producing unmodifiable lists
- [x] 5.5 Convert `GetterDiscovery` element filtering loop to stream producing unmodifiable list
- [x] 5.6 Convert `ConstructorDiscovery` parameter loop to stream where feasible
- [x] 5.7 Convert `Pipeline` error reporting loop to `.forEach()`
- [x] 5.8 Convert simple loops in `GenerateStage`, `ValidateStage`, and `AnalyzeStage` to streams where they produce simple collections

## 6. Verify

- [x] 6.1 Compile all sources and fix any issues
- [x] 6.2 Run all tests and fix any failures
