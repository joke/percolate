# DOT Export Debug Output — Design

**Date:** 2026-03-02

**Goal:** After `BindingStage` and after `WiringStage`, export each method's graph as a DOT file to `/tmp/` for debugging. One file per method per stage.

---

## Problem

There is currently no way to inspect the graphs produced by `BindingStage` and `WiringStage` at runtime. DOT output provides a quick, visualisable snapshot.

---

## Design decisions

### 1. Static helper in `Pipeline` — no new class, no Dagger changes

A `private static void exportDot(String mapperName, MethodRegistry registry, String phase)` method is added to `Pipeline`. `process()` calls it twice — once after `bindingStage.execute` and once after `wiringStage.execute`. This is the quickest change with the smallest blast radius.

### 2. One DOT file per method per stage

Each non-opaque registry entry (i.e. each abstract mapper method) produces its own file:

```
/tmp/{MapperSimpleName}-{methodName}-binding.dot
/tmp/{MapperSimpleName}-{methodName}-wiring.dot
```

Example for `TicketMapper`:
```
/tmp/TicketMapper-mapPerson-binding.dot
/tmp/TicketMapper-mapPerson-wiring.dot
/tmp/TicketMapper-mapVenue-binding.dot
/tmp/TicketMapper-mapVenue-wiring.dot
```

### 3. Labels via `toString()`

All `MappingNode` implementations already have meaningful `toString()` representations:

| Node type | Example label |
|---|---|
| `SourceNode` | `Source(ticket:io.github.joke.Ticket)` |
| `PropertyAccessNode` | `Property(ticketId:Ticket->long)` |
| `TargetSlotPlaceholder` | `Slot(ticketId on FlatTicket)` |
| `ConstructorAssignmentNode` | `Constructor(FlatTicket)` |

`FlowEdge.toString()` produces `type -> type` or `type -[slot]-> type`.

### 4. Vertex IDs via `System.identityHashCode`

DOT requires a unique ID per vertex. `System.identityHashCode(v)` is used — no risk of collision between different method graphs.

### 5. IOException silently swallowed

The export is a debug aid. If the file cannot be written the processor continues normally.

---

## Scope

| File | Change |
|---|---|
| `processor/src/main/java/io/github/joke/percolate/processor/Pipeline.java` | Add two `exportDot` calls in `process()`, add private static `exportDot` method |

No new files. No Dagger changes. `jgrapht-io` is already an `implementation` dependency.

---

## Key imports

- `org.jgrapht.nio.dot.DOTExporter`
- `org.jgrapht.nio.DefaultAttribute` (static import `createAttribute`)
- `java.io.FileWriter`, `java.io.IOException`, `java.io.Writer`
- `java.util.Collections` (static import `singletonMap`)
