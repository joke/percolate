# Wiring Validation Design

**Date:** 2026-03-03
**Status:** Approved

## Problem

When a developer maps a property that requires an element-level conversion (e.g. `List<Actor> → List<TicketActor>`) but does not supply a mapper method (`TicketActor mapActor(Actor actor)`), the wiring graph silently retains an incompatible edge. The incompatible edge goes undetected and would produce broken generated code.

Two related problems compound this:

1. **Provider ordering is undefined** — `ServiceLoader` provides no ordering guarantee. If `ListProvider` runs before `MapperMethodProvider`, a developer-supplied `List<TicketActor> myMap(List<Actor> actors)` direct method is shadowed by element-wise expansion.
2. **Unresolvable edges are re-inserted** — `expandEdge` puts an edge back when no fragment is found, leaving the wiring graph in a permanently inconsistent state.

## Design

### 1. Provider priority

Add a `default int priority()` method to the `ConversionProvider` interface (default = 100, lower = higher priority). `WiringStage` sorts providers by priority after loading via `ServiceLoader`.

Provider priorities:
- `MapperMethodProvider` → 10 (direct method wins; covers both element-level and collection-level methods)
- `ListProvider` → 50 (element-wise unwrapping, only tried if no direct method exists)
- All others → 100 (default)

### 2. Sever unresolvable edges in `expandEdge`

When `findFragment` returns empty for an incompatible edge, the edge is **removed and not re-added**. The `stabilizeGraph` loop already terminates correctly: once severed edges are gone, `findIncompatibleEdges` finds nothing and `expandIncompatibleEdges` returns `false`.

No severed-edge tracking is needed. The wired graph after stabilization is the source of truth — missing edges manifest as dead-end nodes.

### 3. Rewrite `ValidateStage` — dead-end detection

`ValidateStage` is rewritten from scratch. It receives `MethodRegistry` and `Messager`.

`execute(MethodRegistry registry)` iterates every non-opaque registry entry and runs dead-end detection on its wired graph:

```
sources           = all SourceNode vertices in the graph
sink              = the ConstructorAssignmentNode vertex
forwardReachable  = DFS from every source (following edge direction)
canReachSink      = DFS from sink on EdgeReversedGraph (backward reachability)
broken            = forwardReachable − canReachSink
```

For each broken `PropertyAccessNode` in `broken`, emit a compile error:

```
ERROR: Property 'actors' (List<Actor>) has no conversion path to the target constructor.
```

`ValidateStage` sets a `hasFatalErrors` flag. `Pipeline` can use this to skip code generation.

### 4. Wire `ValidateStage` into `Pipeline`

`Pipeline` injects `ValidateStage` and calls `validateStage.execute(registry)` immediately after `wiringStage.execute(registry)`.

## Scope

- No changes to `BindingStage`, `RegistrationStage`, `ParseMapperStage`.
- No changes to `RegistryEntry` structure.
- `ConversionProvider` interface gains one default method only.
- Existing `OptimizeStage` and `CodeGenStage` remain disconnected.

## Success Criteria

1. With `mapActor` commented out: processor emits a compile ERROR naming property `actors`.
2. With `mapActor` present: wiring graph contains `CollectionIterationNode`, `MethodCallNode(mapActor)`, `CollectionCollectNode`; no error emitted.
3. With a direct `List<TicketActor> myMap(List<Actor>)` method: `MapperMethodProvider` resolves it directly (no element-wise unwrapping); no error.
4. No regressions in existing `WiringStageSpec` tests.
