## 1. SPI Interface and Core Model

- [x] 1.1 Create `TypeTransformStrategy` interface with `canProduce(TypeMirror, TypeMirror, Context)` method
- [x] 1.2 Create `TransformProposal` value class (requiredInput, producedOutput, CodeTemplate, strategy reference)
- [x] 1.3 Create `CodeTemplate` functional interface (`CodeBlock apply(CodeBlock innerExpression)`)
- [x] 1.4 Create `TypeNode` value class (TypeMirror, label) for the type transformation graph
- [x] 1.5 Create `TransformEdge` value class (TypeTransformStrategy, CodeTemplate) for graph edges
- [x] 1.6 Write unit tests for model classes

## 2. Built-in Terminal Strategies

- [x] 2.1 Implement `DirectAssignableStrategy` using `Types.isAssignable()` with identity CodeTemplate
- [x] 2.2 Implement `MethodCallStrategy` that searches mapper methods (excluding current method) with method call CodeTemplate
- [x] 2.3 Register both strategies via `@AutoService(TypeTransformStrategy.class)`
- [x] 2.4 Write unit tests for `DirectAssignableStrategy`
- [x] 2.5 Write unit tests for `MethodCallStrategy`

## 3. Collection Strategies

- [x] 3.1 Implement `StreamFromCollectionStrategy` handling List/Set/Collection/Iterable sources with `.stream()` / `StreamSupport.stream()` CodeTemplate
- [x] 3.2 Implement `CollectToListStrategy` with `.collect(Collectors.toList())` CodeTemplate
- [x] 3.3 Implement `CollectToSetStrategy` with `.collect(Collectors.toSet())` CodeTemplate
- [x] 3.4 Implement `StreamMapStrategy` for `Stream<T>` → `Stream<U>` with `.map()` CodeTemplate and recursive sub-resolution
- [x] 3.5 Register all collection strategies via `@AutoService`
- [x] 3.6 Write unit tests for each collection strategy in isolation

## 4. Optional Strategies

- [x] 4.1 Implement `OptionalMapStrategy` for `Optional<T>` → `Optional<U>` with `.map()` CodeTemplate and recursive sub-resolution
- [x] 4.2 Implement `OptionalWrapStrategy` with `Optional.of()` CodeTemplate
- [x] 4.3 Implement `OptionalUnwrapStrategy` with `.get()` CodeTemplate
- [x] 4.4 Register all Optional strategies via `@AutoService`
- [x] 4.5 Write unit tests for each Optional strategy in isolation

## 5. Graph-based Resolver

- [x] 5.1 Rewrite `ResolveTransformsStage` to build a `DefaultDirectedGraph<TypeNode, TransformEdge>` per property edge
- [x] 5.2 Implement BFS expansion loop: ask all strategies, add edges, check `BFSShortestPath.findPathBetween()`, max 30 iterations
- [x] 5.3 Load strategies via `ServiceLoader` (consistent with existing SPI pattern)
- [x] 5.4 Update `ResolvedMapping` to carry `GraphPath<TypeNode, TransformEdge>` instead of `List<TransformNode>`
- [x] 5.5 Remove `TransformNode`, `TransformOperation`, `DirectOperation`, `SubMapOperation`, `UnresolvedOperation`
- [x] 5.6 Write unit tests for the resolver with direct, method call, and container scenarios

## 6. Update GenerateStage

- [x] 6.1 Replace `generateValueExpression` to walk `GraphPath.getEdgeList()` and compose `CodeTemplate`s left-to-right
- [x] 6.2 Remove direct handling of `SubMapOperation` and `DirectOperation` (now handled by templates)
- [x] 6.3 Write unit tests for generated code with container and Optional mappings

## 7. Update ValidateTransformsStage

- [x] 7.1 Update validation to check for unresolved mappings (no GraphPath) instead of checking for `UnresolvedOperation`
- [x] 7.2 Update error messages to be goal-directed ("provide a mapping method for T → U")
- [x] 7.3 Write unit tests for validation with resolved, unresolved, and partially-expanded scenarios

## 8. Integration Tests

- [x] 8.1 Integration test: `List<Person>` → `List<PersonDTO>` with sibling method
- [x] 8.2 Integration test: `List<Person>` → `Set<PersonDTO>` (container conversion) with sibling method
- [x] 8.3 Integration test: `Set<String>` → `List<String>` (container conversion, direct element)
- [x] 8.4 Integration test: `Optional<Person>` → `Optional<PersonDTO>` with sibling method
- [x] 8.5 Integration test: `T` → `Optional<T>` (wrap) and `Optional<T>` → `T` (unwrap)
- [x] 8.6 Integration test: mixed flat and container properties in same mapper
- [x] 8.7 Integration test: error case — `List<Person>` → `Set<PersonDTO>` without sibling method produces clear error
- [x] 8.8 Integration test: existing flat mapping behavior unchanged (regression)
