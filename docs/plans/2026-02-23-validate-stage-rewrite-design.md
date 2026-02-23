# ValidateStage Rewrite with Lazy Graph — Design

## Problem

The current `ValidateStage` only checks whether constructor parameters have incoming `ConstructorParamEdge`s via simple set comparison. It does not:

- Verify reachability from method input parameters
- Check type compatibility along paths
- Verify converter method availability
- Detect cycles

This causes the processor to generate code that doesn't compile. Example: removing `mapVenue()` from `TicketMapper` produces no annotation processor error, but the generated `TicketMapperImpl` fails to compile because types don't match constructor arguments.

Additionally, type conversion logic lives ad-hoc in `CodeGenStage.applyConversion()` (List, Optional, Enum, primitive checks) instead of being represented in the graph.

## Solution

Lazy-expanding dependency graph + JGraphT algorithms for validation.

## Architecture

### LazyMappingGraph

A custom `Graph<GraphNode, GraphEdge>` implementation wrapping the base `DirectedWeightedMultigraph`. Lazily materializes conversion edges during traversal.

- `outgoingEdgesOf(node)` triggers `ConversionProvider` expansion for that node's type
- New TypeNodes and ConversionEdges are materialized, cached, and added to the base graph
- Depth limit (max 5 hops) prevents infinite expansion chains
- Used by both ValidateStage and CodeGenStage — single source of truth

Example traversal for `Optional<Integer> → long`:

```
outgoingEdgesOf(TypeNode<Optional<Integer>>)
  → OptionalProvider: Optional<Integer> --ConversionEdge--> TypeNode<Integer>

outgoingEdgesOf(TypeNode<Integer>)
  → PrimitiveWideningProvider: Integer --ConversionEdge--> TypeNode<int>

outgoingEdgesOf(TypeNode<int>)
  → PrimitiveWideningProvider: int --ConversionEdge--> TypeNode<long>
```

### ConversionProvider Interface

Replaces `GenericMappingStrategy` SPI. Each provider answers: "given a source type, what types can it convert to?"

```java
interface ConversionProvider {
    List<Conversion> possibleConversions(TypeMirror source, ProcessingEnvironment env);
}
```

Built-in providers:

| Provider | Conversion | Generated expression |
|---|---|---|
| MapperMethodProvider | Developer-defined mapper methods | `this.methodName(expr)` |
| OptionalProvider | `Optional<T>` ↔ `T` | `.orElse(null)` / `Optional.ofNullable(expr)` |
| ListProvider | `List<A>` → `List<B>` (if element conversion exists) | `expr.stream().map(this::convert).collect(toList())` |
| PrimitiveWideningProvider | Boxing/unboxing, widening (`int → long`) | Cast or identity |
| EnumProvider | `EnumA → EnumB` by name | `TargetEnum.valueOf(expr.name())` |
| SubtypeProvider | Subclass → superclass / interface upcasting | Identity (no code) |

### Rewritten ValidateStage

Two-phase validation on the `LazyMappingGraph`:

1. **Cycle detection** — `CycleDetector` on the lazy graph. Fail fast with cycle information.
2. **Connectivity check** — `ConnectivityInspector` verifies every ConstructorNode param is reachable from method parameter TypeNodes. Uses shortest-path algorithms (`DijkstraShortestPath` / `BFSShortestPath`) for nearest-source hints in error messages.

All errors collected before returning. Multiple errors reported per compilation.

### Enhanced GraphRenderer

- Full constructor node rendering (all params with ✓/✗ status)
- Source path display for successful mappings
- Nearest-source hints using shortest-path algorithms
- Actionable suggestions (concrete method signatures to add)

Example error output:

```
error: [TicketMapper] Cannot map parameter 'venue' of FlatTicket constructor

  mapPerson(Ticket ticket, Order order) → FlatTicket

  ConstructorNode(FlatTicket):
    ticketId     ← ticket.ticketId       ✓
    ticketNumber ← ticket.ticketNumber   ✓
    actors       ← ticket.actors         ✓  (via mapActor: Actor → TicketActor)
    orderId      ← order.orderId         ✓
    venue        ← ???                   ✗

  Required type: io.github.joke.FlatTicket.TicketVenue
  Nearest source: order.venue (type: io.github.joke.Venue)
  Missing conversion: Venue → TicketVenue

  Suggestion: Add method to TicketMapper:
    TicketVenue mapVenue(Venue venue);
```

### Simplified CodeGenStage

- Delete `applyConversion()` and all ad-hoc type conversion logic
- Pure graph traversal: walk edges from method param to constructor, emit code per edge type
- `ConversionEdge` → emit the expression from the `Conversion` model

## Changes Summary

| File | Change |
|---|---|
| `LazyMappingGraph` (new) | Custom Graph impl with lazy expansion |
| `ConversionProvider` (new) | SPI interface for type conversions |
| `Conversion` (new) | Model: source type, target type, expression template |
| `ConversionEdge` (new) | Edge type representing a type conversion |
| `MapperMethodProvider` (new) | Conversion via mapper methods |
| `OptionalProvider` (new) | Optional wrapping/unwrapping |
| `ListProvider` (new) | List element mapping |
| `PrimitiveWideningProvider` (new) | Boxing/unboxing/widening |
| `EnumProvider` (new) | Enum-by-name conversion |
| `SubtypeProvider` (new) | Subclass → superclass upcasting |
| `ValidateStage` | Rewrite: CycleDetector + ConnectivityInspector on lazy graph |
| `GraphRenderer` | Enhanced: full path display, nearest-source hints, suggestions |
| `CodeGenStage` | Simplified: delete applyConversion(), pure graph traversal |
| `GraphBuildStage` | Simplified: remove GenericPlaceholderEdge insertion |
| `GenericPlaceholderEdge` | Deleted |
| `GenericMappingStrategy` SPI | Replaced by ConversionProvider |
| `ValidateStageSpec` | Expanded with new test scenarios |

## Testing

**Unit tests (Spock):**
- `LazyMappingGraph`: lazy expansion, caching, depth limit
- Each `ConversionProvider` in isolation

**Integration tests (Google Compile Testing):**
- Happy path — all params reachable, no errors
- Missing property — no matching source → error with suggestion
- Missing converter — type mismatch, no mapper method → error naming missing method
- Conversion chains — `Optional<Integer>` → `long` via lazy expansion
- Cycle detection — circular mapper dependencies → error
- Removed mapper method (original bug) — remove `mapVenue()`, verify error with suggestion
- Ambiguous paths — two sources for same target → warning
- Subtype assignment — `Child → Parent` works without explicit converter
