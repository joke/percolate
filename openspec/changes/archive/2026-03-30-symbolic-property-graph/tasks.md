## 1. Symbolic Graph Model

- [ ] 1.1 Create `SourceRootNode` class with `name` field (parameter name), Lombok annotations
- [ ] 1.2 Replace `SourcePropertyNode` — remove `TypeMirror` and `ReadAccessor` fields, keep only `name`
- [ ] 1.3 Create `TargetRootNode` class with Lombok annotations
- [ ] 1.4 Replace `TargetPropertyNode` — remove `TypeMirror` and `WriteAccessor` fields, keep only `name`
- [ ] 1.5 Create `AccessEdge` class for source chain traversal edges
- [ ] 1.6 Redefine `MappingEdge` — replace `Type.DIRECT` enum with simple source→target mapping edge
- [ ] 1.7 Update `MappingGraph` to use `MappingMethodModel` key and symbolic node/edge types

## 2. BuildGraphStage Rewrite

- [ ] 2.1 Implement lightweight property name scanner (`Set<String>` from getters/fields on a type)
- [ ] 2.2 Implement source chain parsing — split `@Map` source string on `"."`, create `SourcePropertyNode` chain with `AccessEdge`s, dedup shared prefixes
- [ ] 2.3 Rewrite `BuildGraphStage.execute()` to produce symbolic graph from `MapperModel` (no `DiscoveredModel` input)
- [ ] 2.4 Implement auto-mapping using name scanner — match top-level source names to target names
- [ ] 2.5 Write Spock tests for `BuildGraphStage`: simple mapping, nested chains, shared prefixes, auto-mapping, explicit priority over auto-map

## 3. ResolveTransformsStage Expansion

- [ ] 3.1 Add `SourcePropertyDiscovery` and `TargetPropertyDiscovery` SPI loading to `ResolveTransformsStage` (move from `DiscoverStage`)
- [ ] 3.2 Implement access edge resolution — discover accessor on parent's resolved type, record `ReadAccessor` and resolved type
- [ ] 3.3 Implement sequential chain resolution — walk `AccessEdge` chain from `SourceRootNode`, resolve each segment, build `List<ReadAccessor>` accessor chain
- [ ] 3.4 Implement target property resolution — discover `WriteAccessor` on target type for each `TargetPropertyNode`
- [ ] 3.5 Implement failure annotation for unresolved access edges — record segment name, index, chain, searched type, available properties
- [ ] 3.6 Update `ResolvedMapping` to carry `List<ReadAccessor>` source accessor chain instead of single `SourcePropertyNode`
- [ ] 3.7 Update `ResolvedModel` to use new `ResolvedMapping` structure
- [ ] 3.8 Write Spock tests for access edge resolution: getter, field, priority-based, chain, failure annotation

## 4. ValidateTransformsStage Expansion

- [ ] 4.1 Add unresolved access edge validation — produce diagnostics with chain context and "Did you mean?" suggestions
- [ ] 4.2 Add unmapped target property validation — detect targets with no incoming resolved mapping, suggest fuzzy matches from unmapped sources
- [ ] 4.3 Add duplicate target mapping validation — detect targets with multiple incoming mappings
- [ ] 4.4 Write Spock tests for new validation: unknown property in chain, unmapped targets, duplicate targets, fuzzy match suggestions

## 5. Pipeline and Stage Removal

- [ ] 5.1 Remove `DiscoverStage` class and its tests
- [ ] 5.2 Remove `ValidateStage` class and its tests
- [ ] 5.3 Update `Pipeline` to remove `DiscoverStage` and `ValidateStage`, wire: Analyze → BuildGraph → ResolveTransforms → ValidateTransforms → Generate
- [ ] 5.4 Update Dagger module/component to remove `DiscoverStage` and `ValidateStage` bindings
- [ ] 5.5 Update `Pipeline` tests for new 5-stage sequence

## 6. GenerateStage Update

- [ ] 6.1 Update `generateReadExpression` to walk `List<ReadAccessor>` chain — compose getter/field access expressions sequentially
- [ ] 6.2 Update `generateValueExpression` to use accessor chain + transform path
- [ ] 6.3 Write Spock tests for code generation with chained source accessors

## 7. Integration and Cleanup

- [ ] 7.1 Run full test suite, fix any remaining failures from model changes
- [ ] 7.2 Remove unused model classes: old `PropertyNode` base class (if no longer needed), `DiscoveredModel`, `DiscoveredMethod` (if replaced by `MappingMethodModel`)
- [ ] 7.3 Add integration test: nested source chain `@Map(source = "customer.address.street", target = "street")` compiles and generates correct code
- [ ] 7.4 Add integration test: nested chain through Optional intermediate type resolves via transform pipeline
