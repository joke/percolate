## Why

The annotation processor framework is wired up but the `Pipeline` is a stub returning `null`. To fulfill the project's purpose — generating bean mapper implementations from `@Mapper` interfaces — the pipeline needs concrete processing stages that analyze annotated types, discover properties, build a mapping graph, validate correctness, and generate implementation source code.

## What Changes

- Implement a multi-stage processing pipeline: analyze → discover → build graph → validate → generate
- Introduce `StageResult` for stage-level success/failure with error accumulation per mapper
- Build a JGraphT-based directed graph model for property mappings
- Introduce SPI-based property discovery (`SourcePropertyDiscovery`, `TargetPropertyDiscovery`) with priority-based resolution
- Ship two built-in discovery strategies: `GetterDiscovery` (source) and `ConstructorDiscovery` (target)
- Generate constructor-based and field-based mapper implementations via JavaPoet
- Report all validation errors per mapper via `Messager` (no fail-fast across mappers)

## Capabilities

### New Capabilities
- `pipeline-stages`: Stage abstraction, `StageResult`, and pipeline orchestration chaining stages with per-mapper error isolation
- `property-discovery`: SPI interfaces for source/target property discovery with priority-based resolution and built-in getter/constructor strategies
- `mapping-graph`: JGraphT directed graph model representing property mappings, used for validation and code generation
- `code-generation`: JavaPoet-based source code generation producing mapper implementation classes

### Modified Capabilities
- `processor`: Pipeline gains constructor-injected stages and orchestrates the full analyze→generate flow instead of returning null

## Impact

- **processor module**: All new stages, models, SPI interfaces, and graph logic live here
- **annotations module**: No changes — existing `@Mapper`, `@Map`, `@MapList` annotations are sufficient
- **dependencies**: JGraphT and JavaPoet already present; no new dependencies needed
- **SPI**: New `META-INF/services` files for built-in property discovery strategies
- **Affected team**: processor maintainers
