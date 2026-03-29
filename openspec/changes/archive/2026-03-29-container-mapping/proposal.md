## Why

The annotation processor currently only supports direct property assignment and sibling method delegation for type conversion. When source and target properties use container types (e.g., `List<Person>` to `Set<PersonDTO>`), the processor marks them as unresolved. Developers must manually write conversion logic for any property wrapped in a collection or Optional, which defeats the purpose of automated mapping.

## What Changes

- **Replace the hardcoded transform resolution** (isAssignable / sibling method / unresolved if-else chain) with a graph-based resolution system using JGraphT's shortest-path algorithms
- **Introduce `TypeTransformStrategy` SPI** — a single uniform interface where every transformation (including direct assignability and method calls) is a pluggable strategy that contributes edges to a type transformation graph
- **Add built-in strategies** for collection streaming (`List`/`Set`/`Collection`/`Iterable` to `Stream`), collecting (`Stream` to `List`/`Set`), stream element mapping (`Stream<T>` to `Stream<U>`), and Optional operations (`Optional.map`, wrap, unwrap)
- **Each strategy carries its own code template**, so `GenerateStage` simply walks the resolved path and composes templates — no type-specific code generation logic
- **Update `GenerateStage`** to consume graph paths instead of single-step transform chains
- **Update `ValidateTransformsStage`** to validate the type transformation graph (unconnected gaps, cycles)

## Capabilities

### New Capabilities
- `type-transform-strategy`: SPI interface and graph-based BFS resolution algorithm for type transformation
- `container-transforms`: Built-in strategies for collection and Optional container mapping

### Modified Capabilities
- `transform-resolution`: Resolution moves from hardcoded if-else to strategy-driven graph expansion with BFS shortest path
- `code-generation`: `GenerateStage` walks resolved graph paths and composes strategy-provided code templates instead of handling each operation type directly
- `transform-validation`: `ValidateTransformsStage` validates the type transformation graph for unresolved gaps

## Impact

- **Processor stages**: `ResolveTransformsStage` is rewritten; `GenerateStage` and `ValidateTransformsStage` are modified
- **Transform model**: `TransformOperation` subclasses (`DirectOperation`, `SubMapOperation`, `UnresolvedOperation`) are replaced by strategy-contributed graph edges with code templates
- **SPI surface**: New `TypeTransformStrategy` interface added to the SPI package, registered via `ServiceLoader`
- **Dependencies**: No new dependencies — JGraphT is already used for property graphs
- **Affected team**: Processor maintainers; downstream users gain container mapping support without code changes
