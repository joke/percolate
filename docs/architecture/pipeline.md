# Pipeline Stage Reference

All stages are called in sequence by `Pipeline.process()`.  Each stage receives the shared
`MethodRegistry` (populated from `RegistrationStage` onwards) and may mutate the entries it
contains.

---

## ParseMapperStage

**Input:** `TypeElement` for the `@Mapper`-annotated interface
**Output:** `MapperDefinition`

Inspects the interface element and converts it into a `MapperDefinition`, capturing:

- the interface's qualified name, package, and simple name
- a list of `MethodDefinition` entries (abstract methods + default methods)
- each method's parameter list and return type

---

## RegistrationStage

**Input:** `MapperDefinition`
**Output:** populated `MethodRegistry`

Creates a `MethodRegistry` and inserts one `RegistryEntry` per method:

- **Abstract methods** get an entry with a non-null signature and a null graph (graph is filled in
  by `BindingStage`).
- **Default (opaque) methods** get an entry with a null graph and null signature — they are
  treated as black-box delegates available to `ConversionProvider`s.

---

## BindingStage

**Input:** `MethodRegistry`
**Output:** `Graph<MappingNode, FlowEdge>` stored per abstract entry

For each abstract method, builds a directed binding graph:

1. Creates a `SourceNode` for every method parameter.
2. Walks `@Mapping` directives: each directive chain produces a `PropertyAccessNode` sequence
   (one node per getter hop) rooted at the corresponding `SourceNode`.
3. Terminates each chain in a `TargetSlotPlaceholder` that represents the output object's
   constructor slot.
4. Connects nodes with `FlowEdge` edges:
   - **Object edges** (`SourceNode → PropertyAccessNode`, or between chained `PropertyAccessNode`s):
     `FlowEdge(T, T)` where `T` is the object type being accessed (`inType` of the downstream
     node).  Both ends carry the same type because the edge represents an object flowing into an
     accessor; the type transformation lives in the node, not the edge.
   - **Slot edges** (terminal node → `TargetSlotPlaceholder`): `FlowEdge(outType, returnType, slotName)`
     where `slotName` identifies the constructor parameter this value will fill.

---

## WiringStage

**Input:** `MethodRegistry` (graphs from `BindingStage`)
**Output:** updated `MethodRegistry` with fully-wired, stabilised graphs

Iterates the binding graph in topological order and builds a new wired graph:

1. **Node substitution** — `TargetSlotPlaceholder` is replaced by a `ConstructorAssignmentNode`
   carrying the `CreationDescriptor` (constructor + parameter list) from `ObjectCreationStrategy`.
2. **Edge adjustment** — slot edges are re-typed against the real constructor parameter type.
3. **Conversion splicing** — for edges whose source and target types are incompatible, the stage
   queries `ConversionProvider`s in priority order.  When a provider produces a non-empty
   `ConversionFragment`, its intermediate `MappingNode`s are spliced into the graph.
4. **Graph stabilisation** — repeated passes (up to 10) expand any remaining incompatible edges
   until none remain or no further progress is possible.  Severed edges become dead-ends detected
   by `ValidateStage`.

The original method entry is temporarily removed from the registry during wiring to prevent
`MapperMethodProvider` from self-referencing the method being wired.

---

## ValidateStage

**Input:** `MethodRegistry` (wired graphs)
**Output:** boolean (any errors found); side-effects via `Messager`

For each non-opaque entry with a complete graph:

1. Locates the `ConstructorAssignmentNode` (the sink).
2. **Dead-end check** — computes backward-reachable vertices from the sink via a reversed DFS.
   Any `PropertyAccessNode` not reachable is reported as an error: the property has no conversion
   path to the target type.
3. **Unmapped slot check** — inspects incoming `FlowEdge`s of the sink to collect mapped slot
   names; any constructor parameter without a corresponding slot edge is reported as an error.

---

## CodeGenStage

_To be implemented._

Will walk each wired graph and emit a concrete `*Impl` Java class via Palantir JavaPoet, with one
overriding method per abstract method in the mapper interface.
