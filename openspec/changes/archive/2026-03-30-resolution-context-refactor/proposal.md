## Why

`ResolutionContext` currently exposes `List<DiscoveredMethod>` — a pipeline model — to SPI strategy implementations. This couples strategies to the processor's internal model and prevents them from seeing non-abstract methods (default methods, concrete methods on abstract classes, inherited methods). Custom mapper methods that users write by hand are invisible during type resolution, so they can't be used for nested conversions.

## What Changes

- **BREAKING**: `ResolutionContext` replaces `List<DiscoveredMethod> methods` and `DiscoveredMethod currentMethod` with `TypeElement mapperType` and `ExecutableElement currentMethod`
- `MethodCallStrategy` discovers callable methods via `Elements.getAllMembers(mapperType)` instead of iterating `DiscoveredMethod` list — this includes abstract, default, concrete, and inherited methods
- `ResolveTransformsStage` constructs `ResolutionContext` with `TypeElement` and `ExecutableElement` instead of pipeline models

## Capabilities

### New Capabilities

### Modified Capabilities

- `type-transform-strategy`: `ResolutionContext` fields change from pipeline models to `javax.lang.model` types
- `transform-resolution`: `ResolveTransformsStage` constructs `ResolutionContext` with `TypeElement` and `ExecutableElement`

## Impact

- **Code**: `ResolutionContext`, `MethodCallStrategy`, `ResolveTransformsStage`, and all existing `TypeTransformStrategy` implementations that use `ctx.getMethods()`
- **Tests**: `ResolveTransformsStageSpec`, `MethodCallStrategySpec`, and any test that constructs `ResolutionContext`
- **SPI**: Breaking change for any external `TypeTransformStrategy` implementations (none known outside the project)
- **Affected team**: Processor team
