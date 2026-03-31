## 1. ResolutionContext Refactor

- [x] 1.1 Update `ResolutionContext` fields: replace `List<DiscoveredMethod> methods` with `TypeElement mapperType`, replace `DiscoveredMethod currentMethod` with `ExecutableElement currentMethod`
- [x] 1.2 Update `MethodCallStrategy` to discover methods via `ctx.getElements().getAllMembers(ctx.getMapperType())`, filtering for single-param non-void methods and excluding `ctx.getCurrentMethod()`
- [x] 1.3 Update `ResolveTransformsStage` to construct `ResolutionContext` with `TypeElement` and `ExecutableElement` instead of pipeline models

## 2. Test Updates

- [x] 2.1 Update `MethodCallStrategySpec` to use `TypeElement` and `ExecutableElement` in test context setup, add scenarios for default and inherited methods
- [x] 2.2 Update `ResolveTransformsStageSpec` to construct `ResolutionContext` with `TypeElement` and `ExecutableElement`
- [x] 2.3 Update `PercolateProcessorSpec` integration tests if affected by context construction changes
