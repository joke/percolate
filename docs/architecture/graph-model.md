# Graph Model Reference

Each abstract mapper method is represented as a directed weighted multigraph
`Graph<MappingNode, FlowEdge>` (JGraphT `DirectedWeightedMultigraph`).  The graph is built in two
phases: **binding** (BindingStage) and **wiring** (WiringStage).

---

## Nodes

All node types implement `MappingNode` (marker interface).

### Binding-phase nodes

| Node | Constructor / fields | Purpose |
|------|---------------------|---------|
| `SourceNode(paramName, type)` | `paramName: String`, `type: TypeMirror` | One per method parameter — root of every property chain |
| `PropertyAccessNode(name, inType, outType, accessor)` | `propertyName: String`, `inType: TypeMirror`, `outType: TypeMirror` | Represents a single getter or field read along a source chain |
| `TargetSlotPlaceholder` | (no fields) | Marker for the method's output object; replaced during wiring |

### Wiring-phase additions

These nodes are inserted by `WiringStage` when substituting the placeholder or splicing
conversion fragments.

| Node | Purpose |
|------|---------|
| `ConstructorAssignmentNode(targetType, CreationDescriptor)` | Replaces `TargetSlotPlaceholder`; holds the resolved constructor and its parameter list |
| `MethodCallNode(method, inType, outType)` | Delegate call to another mapper method (inserted by `MapperMethodProvider`) |
| `BoxingNode(inType, outType)` | Primitive → wrapper conversion |
| `UnboxingNode(inType, outType)` | Wrapper → primitive conversion |
| `CollectionIterationNode(collectionType, elementType)` | Stream expansion — input is `Collection<A>`, output is element `A` (inserted by `ListProvider`) |
| `CollectionCollectNode(elementType, targetCollectionType)` | Collects individual elements back into a `List<B>` |
| `OptionalWrapNode(elementType, optionalType)` | Wraps a value into `Optional<T>` (inserted by `OptionalProvider`) |
| `OptionalUnwrapNode(optionalType, elementType)` | Unwraps `Optional<T>` to `T` |

---

## Edges

### `FlowEdge`

```
FlowEdge(sourceType: TypeMirror, targetType: TypeMirror, slotName: @Nullable String)
```

A typed data-flow edge.

- `sourceType` — the type of the value leaving the source node
- `targetType` — the type expected by the target node
- `slotName` — non-null only for edges whose target is a `ConstructorAssignmentNode`; identifies
  which constructor parameter this edge satisfies

Factory methods:
- `FlowEdge.of(sourceType, targetType)` — edge without a slot name
- `FlowEdge.forSlot(sourceType, targetType, slotName)` — edge for a named constructor slot

### Edge types by position

| Edge | sourceType | targetType | slotName |
|------|-----------|-----------|---------|
| `SourceNode → PropertyAccessNode` | `T` (the object type) | `T` (same) | null |
| `PropertyAccessNode → PropertyAccessNode` | `T` (intermediate object type) | `T` (same) | null |
| `PropertyAccessNode → TargetSlotPlaceholder` | `outType` of the property | target return type | non-null |
| Conversion edges (post-wiring) | output type of predecessor | input type of successor | null (or non-null at final slot edge) |

**Important:** the edge between a parent node and a `PropertyAccessNode` carries the
**object type** (`inType`) on both sides — it represents the object flowing *into* the accessor.
The type transformation (object → property value) is encoded in the `PropertyAccessNode` itself
via its `inType`/`outType` fields, not in the incoming edge.  `WiringStage` therefore sees a
compatible `FlowEdge(T, T)` and keeps the edge; it then uses `outTypeOf(PropertyAccessNode)` when
constructing the next edge in the chain.

---

## Graph Lifecycle

```
BindingStage:
  SourceNode(T) ──[T→T]──► PropertyAccessNode(inT=T, outT=U) ──[U→U -[slot]]──► TargetSlotPlaceholder

WiringStage:
  SourceNode(T) ──[T→T]──► PropertyAccessNode(inT=T, outT=U) ──[U→V -[slot]]──► ConstructorAssignmentNode
                                                                        │
                                                             ConversionNode(s) spliced when U≠V
```

After `WiringStage`, the graph is wrapped in `AsUnmodifiableGraph` before being stored back into
the `MethodRegistry`.
