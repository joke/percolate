## Why

Users must explicitly declare every property mapping with `@Map(source, target)`, even when source and target properties share the same name. This creates unnecessary boilerplate for the most common case — mapping between types with overlapping property names. Auto-mapping by name match eliminates this verbosity while keeping explicit `@Map` for renames and overrides.

## What Changes

- `BuildGraphStage` adds implicit same-name edges for target properties that have no incoming edge after processing explicit `@Map` directives
- Explicit `@Map` directives take priority — auto-mapped edges are only added for unmapped targets
- Source properties left unmapped after auto-mapping are silently ignored (no error)
- No new annotations, flags, or opt-in mechanism — auto-mapping is on by default

## Capabilities

### New Capabilities

- `auto-mapping`: Defines the automatic same-name property matching behavior in `BuildGraphStage`, including priority rules for explicit vs implicit mappings

### Modified Capabilities

- `mapping-graph`: `BuildGraphStage` gains a new step after directive edge creation that adds implicit edges for same-name matches on unmapped targets

## Impact

- **Code**: `BuildGraphStage` in the processor module
- **Tests**: `BuildGraphStageSpec`, `ValidateStageSpec`, `PercolateProcessorSpec` — existing tests may need adjustment since previously unmapped same-name properties will now auto-map
- **Affected team**: Processor team
