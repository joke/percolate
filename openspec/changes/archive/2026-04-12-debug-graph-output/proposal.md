# Debug Graph Output

## Problem

Percolate uses JGraphT directed graphs at multiple processing stages (property mapping graphs, type transform graphs), but these graphs are opaque during compilation. When a mapping doesn't resolve as expected, there's no way for a library user to inspect what the processor actually built.

## Proposal

Add opt-in graph debug output that dumps internal graphs to files alongside generated sources. Controlled via annotation processor options, disabled by default.

### Processor options

| Option | Default | Description |
|---|---|---|
| `percolate.debug.graphs` | `false` | Enable graph file output |
| `percolate.debug.graphs.format` | `dot` | Output format: `dot`, `graphml`, or `json` |

### What gets dumped

Three debug stages, injected via Dagger and called by `Pipeline` between the real stages:

| Debug stage | Runs after | Writes | Files per |
|---|---|---|---|
| `DumpPropertyGraphStage` | `BuildGraphStage` | Property mapping graph | One per mapping method |
| `DumpTransformGraphStage` | `ResolveTransformsStage` | Full transform exploration graph | One per mapping method |
| `DumpResolvedOverlayStage` | `ResolveTransformsStage` | Combined property + transform view | One per mapping method |

### Output location

Files written via `Filer.createResource(SOURCE_OUTPUT, ...)` in the same package as the generated `*Impl.java`. File extension matches the selected format (`.dot`, `.graphml`, `.json`).

### Naming convention

```
{MapperName}_{methodName}_property.{ext}
{MapperName}_{methodName}_transform.{ext}
{MapperName}_{methodName}_resolved.{ext}
```

## Key design decisions

### Debug stages as Dagger-injected pass-through classes

Rather than putting dump logic in Pipeline or the real stages, each dump is its own stage class. Pipeline calls them between real stages. Each checks `ProcessorOptions.isDebugGraphs()` and no-ops if disabled. This keeps the real stages pure and the debug logic testable.

### ProcessorOptions as a general config holder

Named `ProcessorOptions` (not `DebugConfig`) because other options may follow. Provided via `ProcessorModule` from `processingEnvironment.getOptions()`. `@Value` class.

### Full exploration graph for transforms

`ResolveTransformsStage.resolveTransformPath()` currently builds a `DefaultDirectedGraph<TypeNode, TransformEdge>` locally and discards it after finding the shortest path. Change: return both the full graph and the winning path via a new `TransformResolution` value type. `ResolvedMapping` gains a reference to `TransformResolution` instead of the bare `GraphPath`. This lets the dump stage show all candidate edges (roads not taken), which is the key debugging value.

### Overlay graph is one per method

The resolved overlay merges all mappings for a method into a single graph: property structure with resolved transform info annotated on each mapping edge. This gives the complete picture of "what did the processor decide to do for this method?"

## Scope

- `ProcessorOptions` — new value class, provided by `ProcessorModule`
- `TransformResolution` — new value class wrapping exploration graph + path
- `ResolvedMapping` — gains `TransformResolution` reference
- `ResolveTransformsStage.resolveTransformPath()` — returns `TransformResolution`
- `DumpPropertyGraphStage` — new stage
- `DumpTransformGraphStage` — new stage
- `DumpResolvedOverlayStage` — new stage
- `Pipeline` — calls debug stages between real stages
- `PercolateProcessor` — declares supported options via `@SupportedOptions`
- jgrapht-io exporters with custom label/attribute providers for readable output

## Out of scope

- Dumping after `AnalyzeStage` (no graph at that point)
- Dumping after `ValidateTransformsStage` (same model as resolve output, no new graph)
- Interactive or runtime graph visualization
