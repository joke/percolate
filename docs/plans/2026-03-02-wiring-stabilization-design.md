# Wiring Stabilization Design

**Date:** 2026-03-02
**Status:** Approved

## Problem

Three bugs in `WiringStage` cause the wiring graph to miss conversion nodes:

1. **Erasure in `typesCompatible`** — `List<Actor>` and `List<TicketActor>` compare equal after erasure, so WiringStage never inserts collection-iteration nodes for them.
2. **`ListProvider` not SPI** — `ListProvider` uses constructor injection and is never loaded by `ServiceLoader`, so collection mappings are never expanded.
3. **`spliceFragment` uses wrong type for intermediate edges** — uses `outTypeOf(node)` for the edge label, so after inserting an `OptionalUnwrapNode(String)` the outgoing edge says `String → TargetType` instead of `Optional<String> → String`. The next pass cannot detect that the upstream edge still needs a mapper call for the element type.

These combine to silently produce incomplete wiring graphs (e.g. no `MethodCallNode(mapVenue)` even when a `mapVenue` method exists).

## Design

### Core mechanic: `inTypeOf` and stabilizing expansion

Add `inTypeOf(node)` alongside the existing `outTypeOf(node)`:

```
inTypeOf(SourceNode)           → node.getType()
inTypeOf(PropertyAccessNode)   → node.getInType()
inTypeOf(ConstructorAssignmentNode) → parameter type at that slot
inTypeOf(conversion nodes)     → the type the node consumes (e.g. Optional<T> for OptionalUnwrapNode)
```

Change `spliceFragment` to label each inserted edge with `inTypeOf(node)` as the target type (not `outTypeOf`). This ensures that after one expansion pass, residual incompatible edges are still detectable on the next pass.

Add `stabilizeGraph` — a loop that runs `expandIncompatibleEdges` repeatedly until no incompatible edges remain or 10 iterations are exhausted:

```
stabilizeGraph(graph):
    for i in 1..10:
        changed = expandIncompatibleEdges(graph)
        if !changed: break
```

The polling loop with a `boolean changed` flag is preferred over JGraphT `GraphListener` — listeners would require mutable listener state and registration/removal per pass with no behavioral or performance advantage for small annotation-processing graphs.

### SPI: all providers get `MethodRegistry` in `canHandle`

All `ConversionProvider` implementations become `@AutoService` with no-arg constructors. The `canHandle` and `provide` signatures gain a `MethodRegistry registry` parameter so providers can consult available mapper methods without constructor injection:

```java
boolean canHandle(TypeMirror source, TypeMirror target, MethodRegistry registry, ProcessingEnvironment env);
ConversionFragment provide(TypeMirror source, TypeMirror target, MethodRegistry registry, ProcessingEnvironment env);
```

Affected implementations: `SubtypeProvider`, `PrimitiveWideningProvider`, `EnumProvider`, `OptionalProvider` (add unused `registry`), `MapperMethodProvider` (use param instead of field), `ListProvider` (add `@AutoService`, use param instead of constructor arg).

`WiringStage.buildProviders` simplifies to a pure `ServiceLoader` list — no manual construction.

### Supporting node fix: `OptionalUnwrapNode.optionalType`

Add `optionalType` field to `OptionalUnwrapNode` so `inTypeOf(OptionalUnwrapNode)` can return the `Optional<T>` source type. `OptionalProvider` passes the Optional source type when constructing the node.

## Scope

- No changes to `BindingStage`, `ParseMapperStage`, `RegistrationStage`, or graph edge model.
- No changes to disconnected stages (`ValidateStage`, `OptimizeStage`, `CodeGenStage`).
- Existing pre-failing tests (7) are not affected.

## Success Criteria

1. Wiring DOT files show `MethodCallNode(mapVenue)` and `MethodCallNode(mapActor)` as intermediate nodes.
2. `WiringStageSpec` test for single constructor node continues to pass.
3. New `WiringStageSpec` tests verify correct node chains for nested-type and collection-type mappings.
4. No new test failures introduced.
