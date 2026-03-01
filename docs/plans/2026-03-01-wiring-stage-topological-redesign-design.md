# WiringStage Topological Redesign — Design

**Date:** 2026-03-01

**Goal:** Replace WiringStage's in-place graph mutation with a clean read-then-build pattern: traverse the immutable binding graph using `TopologicalOrderIterator`, produce a new wired graph, and seal both graphs as unmodifiable after their respective stages.

---

## Problem with the current design

- `WiringStage` mutates the binding graph created by `BindingStage` in place — replacing `TargetSlotPlaceholder` vertices and splicing conversion nodes between edges.
- Mutation is done via manual `vertexSet()` and `edgeSet()` snapshots, not JGraphT traversal algorithms.
- The binding graph has no immutability guarantee — any stage can modify it.
- The code is hard to follow: `resolveCreationStrategy`, `replacePlaceholders`, `rewireIncomingEdges`, `insertConversions`, and `spliceFragment` all mutate the same graph in interlocked ways.

---

## Design decisions

### 1. Binding graph is sealed after `BindingStage`

`BindingStage` wraps its completed `DirectedWeightedMultigraph` in `Graphs.unmodifiableGraph()` before storing it in `RegistryEntry`. Any attempt to mutate it will throw at runtime.

### 2. `WiringStage` reads via `TopologicalOrderIterator`, builds a new graph

`WiringStage` creates a fresh `DirectedWeightedMultigraph` (the wired graph) and populates it by traversing the binding graph in topological order. It never touches the binding graph's structure.

### 3. Node substitution and edge splicing happen during traversal

For each node visited in topological order:
- `SourceNode` / `PropertyAccessNode` → copied as-is into the wired graph
- `TargetSlotPlaceholder` → resolved to a `ConstructorAssignmentNode` via creation strategies; the substitution is recorded in a `Map<MappingNode, MappingNode>`

After placing each node, its incoming binding-graph edges are processed (predecessors are guaranteed to be in the map — topological order ensures this):
- Types compatible → direct `FlowEdge` added to wired graph
- Types incompatible → conversion fragment spliced between source and target

### 4. Wired graph is sealed after `WiringStage`

`WiringStage` wraps its completed wired graph in `Graphs.unmodifiableGraph()` before storing it — replacing the registry entry: `registry.register(method, new RegistryEntry(signature, wiredGraph))`.

### 5. `RegistryEntry` is unchanged

The `graph` field stays `@Nullable Graph<MappingNode, FlowEdge>`. After `BindingStage` it holds the immutable binding graph. After `WiringStage` it holds the immutable wired graph. No second field, no rename.

---

## Data flow

```
BindingStage:
  buildMethodGraph → DirectedWeightedMultigraph (mutable)
  → Graphs.unmodifiableGraph(graph) → RegistryEntry(signature, bindingGraph)

WiringStage:
  for each non-opaque entry:
    wiredGraph = new DirectedWeightedMultigraph()
    nodeMap    = new LinkedHashMap<MappingNode, MappingNode>()

    TopologicalOrderIterator(bindingGraph):
      SourceNode / PropertyAccessNode  → copy to wiredGraph, record in nodeMap
      TargetSlotPlaceholder            → resolve → ConstructorAssignmentNode, record in nodeMap

      for each incoming binding edge (source already in nodeMap):
        wiredSource = nodeMap(bindingSource)
        wiredTarget = nodeMap(bindingTarget)
        if types compatible  → add FlowEdge(wiredSource, wiredTarget)
        if types incompatible → splice conversion fragment

    → Graphs.unmodifiableGraph(wiredGraph)
    → registry.register(method, new RegistryEntry(signature, wiredGraph))
```

---

## What is removed

- `resolveCreationStrategy` — replaced by node substitution during topological traversal
- `replacePlaceholders` — same
- `rewireIncomingEdges` — same
- `insertConversions` — replaced by edge processing during traversal
- `spliceFragment` — kept (used for inserting conversion nodes), signature unchanged

## What is kept

- `buildProviders` — same
- `findFragment` — same
- `outTypeOf` — same
- `findCreationDescriptor` — same
- `asTypeElement` — same

---

## Scoping

| Class | Change |
|---|---|
| `BindingStage` | Wrap graph in `Graphs.unmodifiableGraph()` before storing |
| `WiringStage` | Full rewrite of `execute` using `TopologicalOrderIterator` |
| `RegistryEntry` | No structural change |
