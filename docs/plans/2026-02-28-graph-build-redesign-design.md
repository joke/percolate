# Graph Build Redesign — Design

## Overview

The current `ResolveStage` and `GraphBuildStage` are replaced by two new stages — `BindingStage` and `WiringStage` — that share a single graph model and have non-overlapping SPI ownership. The graph is the single source of truth passed through the pipeline; no second graph is built downstream.

## Pipeline

```
Parse → Binding → Wiring → Validate → Optimize → CodeGen
```

| Stage | Replaces | Primary Responsibility |
|---|---|---|
| `BindingStage` | `ResolveStage` | Resolves source paths into typed property chains; expands wildcards; same-name matching |
| `WiringStage` | `GraphBuildStage` | Traverses topologically target→source; determines creation strategy; inserts conversion nodes |

`Validate`, `Optimize`, and `CodeGen` are unchanged for now.

---

## Graph Scope

One `MethodRegistry` per mapper interface, containing one graph per mapper method. Registries are never shared across mappers — no information leaks between mapper interfaces.

---

## Graph Model

### Node Hierarchy

`MappingNode` is the base type for all graph vertices:

```
MappingNode
├── SourceNode(paramName, TypeMirror)
│       Represents a method parameter — the origin of all data flow.
│
├── PropertyAccessNode(propertyName, inType, outType, Element accessor)
│       A getter call or field read. Accessor element set by BindingStage
│       using PropertyDiscoveryStrategy.
│
├── MethodCallNode(MethodRef, inType, outType)
│       Calls another mapper method. Opaque if user-provided (default method,
│       no subgraph); has a registry entry with a full graph if auto-generated.
│
├── CollectionIterationNode(elementType)
│       List<X> → X per element. Inline — does not register a helper method.
│
├── CollectionCollectNode(collectionType, elementType)
│       X → List<X> / Set<X> / Collection<X>. Inline.
│
├── OptionalWrapNode(elementType)
│       T → Optional<T>. Inline.
│
├── OptionalUnwrapNode(elementType)
│       Optional<T> → T. Inline.
│
├── BoxingNode(from, to)
│       int → Integer etc. Inline.
│
├── UnboxingNode(from, to)
│       Integer → int etc. Inline.
│
├── TargetSlotPlaceholder(targetType, slotName)
│       Created by BindingStage as the terminal node of each source chain.
│       Replaced by WiringStage with a concrete TargetAssignmentNode.
│
└── TargetAssignmentNode (interface)
    ├── ConstructorAssignmentNode(TypeElement, CreationDescriptor)
    ├── BuilderAssignmentNode(TypeElement, ...)       ← future
    └── SetterAssignmentNode(TypeElement, ...)        ← future
```

### FlowEdge

Edges represent value flow: "output of A becomes input of B".

```java
class FlowEdge {
    TypeMirror sourceType;
    TypeMirror targetType;
    @Nullable String slotName; // only on edges into TargetAssignmentNode
}
```

`slotName` identifies which constructor parameter / builder method / setter this value feeds into. All other edges carry `slotName = null`.

---

## Method Registry

The registry is the output of `BindingStage` and `WiringStage`, and the input to all downstream stages.

```java
class MethodRegistry {
    Map<TypePair, RegistryEntry> entries;
}

class TypePair {
    TypeMirror in;
    TypeMirror out;
    // equality via Types.isSameType(), not Object.equals()
}

class RegistryEntry {
    MethodDefinition signature;
    @Nullable Graph<MappingNode, FlowEdge> graph;
    // null  → opaque user-provided default method
    // non-null → abstract method or auto-generated helper
}
```

### Lifecycle

1. **`ParseStage`** — pre-populates the registry as part of `ParseResult`:
   - Abstract methods → `RegistryEntry(signature, null)` — graph built by Binding + Wiring
   - Default methods → `RegistryEntry(signature, null)` — stays null (user-implemented, opaque)

2. **`BindingStage`** — builds partial graphs for abstract methods and stores them in the registry entries.

3. **`WiringStage`** — completes graphs; auto-generated helper methods are registered dynamically as they are synthesized.

4. **Downstream stages** — iterate the registry; no further graph construction.

---

## BindingStage

**Input:** `ParseResult` (includes pre-populated `MethodRegistry`)
**Output:** `MethodRegistry` with partial graphs for abstract methods

**Owns:** `PropertyDiscoveryStrategy` SPI (`GetterPropertyStrategy`, `FieldPropertyStrategy`)

**Does NOT know about:** `ObjectCreationStrategy`, `ConversionProvider`

### For each abstract method

1. Creates a `SourceNode` per method parameter.

2. Resolves source paths from `@Map` directives via `PropertyDiscoveryStrategy`:
   - **Multi-param:** first path segment matches a parameter name (`"ticket.actors"` → start from `SourceNode("ticket")`, walk `actors`)
   - **Single-param:** first segment is a property on the sole parameter; the parameter name may be omitted

3. **Expands wildcards** — `source="order.*"` discovers all properties on `Order` via `PropertyDiscoveryStrategy`, generating one `PropertyAccessNode` chain per property with implicit same-name slot matching.

4. **Same-name matching** — for target slots not covered by any `@Map` directive, discovers source properties with matching names and generates implicit chains.

5. Creates a `TargetSlotPlaceholder(targetType, slotName)` at the terminal end of each chain.

6. Connects the chain with typed `FlowEdge`s:

```
SourceNode(ticket)
  └─[FlowEdge(Ticket, List<Actor>)]─► PropertyAccessNode(actors, Ticket, List<Actor>)
      └─[FlowEdge(List<Actor>, FlatTicket, slotName="actors")]─► TargetSlotPlaceholder(FlatTicket, "actors")
```

### Graph state after BindingStage

Source chains are complete and typed. Nothing is connected to a real target node yet.

```
SourceNode(ticket) → PropertyAccessNode(ticketId) → TargetSlotPlaceholder(FlatTicket, "ticketId")
SourceNode(ticket) → PropertyAccessNode(actors)   → TargetSlotPlaceholder(FlatTicket, "actors")
SourceNode(order)  → PropertyAccessNode(orderId)  → TargetSlotPlaceholder(FlatTicket, "orderId")
SourceNode(order)  → PropertyAccessNode(venue)    → TargetSlotPlaceholder(FlatTicket, "venue")
```

---

## WiringStage

**Input:** `MethodRegistry` with partial graphs
**Output:** `MethodRegistry` with complete graphs

**Owns:** `ObjectCreationStrategy` SPI, `ConversionProvider` SPI

**Does NOT know about:** `PropertyDiscoveryStrategy`

### Algorithm

For each abstract method graph in the registry:

1. **Resolve creation strategy** — `ObjectCreationStrategy` determines how the target type is constructed. Replace all `TargetSlotPlaceholder`s with a concrete `ConstructorAssignmentNode` (or future `BuilderAssignmentNode` / `SetterAssignmentNode`) and rewire incoming `FlowEdge`s.

2. **Traverse topologically target → source** — for each `FlowEdge`, check if `sourceType` is compatible with `targetType`.

3. **On type mismatch** — iterate `ConversionProvider` SPI to find a handler:
   - Provider found → obtain a `ConversionFragment` and splice it into the graph, replacing the original edge
   - Fragment contains a `MethodCallNode` → check registry for `(inType, outType)`:
     - Entry exists → reference it
     - No entry → synthesize a new graph recursively, register it, then reference it

### ConversionProvider SPI

```java
public interface ConversionProvider {
    boolean canHandle(TypeMirror source, TypeMirror target, ProcessingEnvironment env);
    ConversionFragment provide(TypeMirror source, TypeMirror target,
                               MethodRegistry registry, ProcessingEnvironment env);
}
```

`ConversionFragment` is an ordered list of `MappingNode`s to insert between the two mismatched endpoints. Inline nodes (`CollectionIterationNode`, `OptionalWrapNode`, `BoxingNode`, etc.) never register helper methods. Only `MethodCallNode` may trigger registry lookup or registration.

**Built-in providers:** `ListProvider`, `OptionalProvider`, `PrimitiveWideningProvider`, `SubtypeProvider`, `EnumProvider`

### Graph state after WiringStage

All placeholders replaced; conversion nodes inserted; types are compatible at every edge.

```
SourceNode(ticket) → PropertyAccessNode(ticketId)
    └─► ConstructorAssignmentNode(FlatTicket)   [slotName="ticketId"]

SourceNode(ticket) → PropertyAccessNode(actors)
    → CollectionIterationNode
    → MethodCallNode(mapActor, Actor, TicketActor)
    → CollectionCollectNode(List, TicketActor)
    └─► ConstructorAssignmentNode(FlatTicket)   [slotName="actors"]

SourceNode(order) → PropertyAccessNode(orderId)
    └─► ConstructorAssignmentNode(FlatTicket)   [slotName="orderId"]
```

---

## Design Decisions Log

| Decision | Choice | Rationale |
|---|---|---|
| Graph scope | One graph per mapper method, stored in a shared registry | Isolation; no cross-method graph edges |
| Registry sharing | One registry per mapper interface, never shared | Complete mapper isolation |
| Registry content | Signature + graph | CodeGen iterates registry directly; no second data structure |
| Registry pre-population | Part of ParseStage output | ParseStage already touches every method; no extra stage |
| Slot info location | In `FlowEdge.slotName` | Eliminates `ParameterBindingNode`; simpler graph |
| Binding nodes | Generic — no strategy-specific variants | Strategy decision lives only in `TargetAssignmentNode` |
| Default methods | Opaque `MethodCallNode` (no subgraph) | Annotation processors cannot read method bodies |
| Collection mapping | Explicit structural decomposition | Avoids combinatorial strategy explosion; element converter is always a first-class node |
| Inline nodes (Boxing, Optional, etc.) | Do not register helper methods | Pure expression-level transforms; no method boundary needed |
| SPI ownership split | `PropertyDiscoveryStrategy` → BindingStage; `ObjectCreationStrategy` + `ConversionProvider` → WiringStage | Non-overlapping ownership; each stage's SPI is independent |
| Stage naming | `BindingStage` + `WiringStage` | "Binding" = property name resolution; "Wiring" = type conversion connection |
