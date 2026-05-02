## Why

The processor currently produces `MapperMappings` from `@Mapper` types but has no graph-shaped representation that the future expansion phase can operate on, and no way for a developer (or for tests) to *see* what the processor has built. To make any further progress toward MapStruct-like code generation we need (a) a per-mapper graph data structure that represents the user's declared mapping intent as nodes + weighted edges, and (b) a debugging surface (DOT dump, gated by a compiler option) that lets us — and downstream users — verify and inspect what was built. The DOT dump doubles as a stable test-verification surface for the graph.

## What Changes

- Introduce a new per-mapper, JGraphT-backed graph data structure (`MapperGraph`) backed by `DirectedMultigraph<Node, Edge>` and a small typed model around it (`Node`, `Edge`, `Location`, `AccessPath`, `TargetPath`, `Scope`).
- Introduce a new pipeline validator `ValidateSourceParameters` that checks every `@Map` directive's source first segment names a method parameter. Unknown source parameters produce compile errors pointing at the offending `source = "..."` literal.
- Introduce a new pipeline stage `SeedGraph` that consumes `MapperMappings` (the existing Tier-1 output) and produces a `MapperGraph`, capturing the user's declared mapping intent as low-weight directive-seeded edges (no expansion yet).
- Introduce a new pipeline stage `DumpGraph` that, when the compiler option `-Apercolate.debug.graphs=true` is set, writes one `<MapperFQN>.seed.dot` file per non-empty mapper into `StandardLocation.SOURCE_OUTPUT` (next to where generated mappers will eventually live). Empty mappers do not produce a file.
- Introduce a `ProcessorOptions` Dagger-provided carrier that parses processor options once per round; this change wires only `debugGraphs : boolean`.
- Declare the `percolate.debug.graphs` option from `PercolateProcessor.getSupportedOptions()`.
- Wire the two new stages into `Pipeline.process(TypeElement)` after `ValidateNoDuplicateTargets` and `ValidateSourceParameters`. `Pipeline.process` continues to return `null`.
- Establish DOT determinism: graph iteration is sorted by stable `Node.id()`; DOT export uses sorted vertex/edge iteration; per-method DOT clusters group nodes by method scope.
- Add a Spock Groovy extension module `MapperGraphExtensions` that decorates `MapperGraph` with a test DSL (`scope(...)`, `hasNode(...)`, etc.) so production classes carry no test-only methods.
- Test strategy: many small *single-aspect* Spock specs assert against the in-memory `MapperGraph` via the extension module; a small handful of *golden DOT files* under `src/test/resources/golden-graphs/` guard the rendering pipeline (escaping, label format, cluster boundaries, file location, option-off-no-file).

Out of scope for this change: any expansion of the seed graph; any strategy SPI / `Strategy` interface / `META-INF/services` loader; any Tier-2 (structural, e.g. "does target slot exist?") or Tier-3 (semantic) validation; any code generation; any `<MapperFQN>.expanded.dot` emission (the future expansion stage will introduce that). Setters / builders / constructor parameter discovery is explicitly *not* part of seeding — those will be discovered on demand by strategies during the future expansion phase.

## Capabilities

### New Capabilities
- `graph-model`: the typed graph model (`Node`, `Edge`, `Location` and its `SourceLocation` / `TargetLocation` cases, `AccessPath`, `TargetPath`, `Scope`, `MapperGraph`) with stable identity and deterministic iteration.
- `seed-graph`: the rules for turning a `MapperMappings` into a seeded `MapperGraph` (param roots, return-type roots, directive-seeded source/target chains, low-weight directive edges).
- `processor-options`: parsing of processor compiler options (`-Akey=value`) into a typed, Dagger-provided `ProcessorOptions` carrier.
- `graph-debug-output`: behaviour and file-emission contract for the DOT dump gated on `percolate.debug.graphs`.
- `mapping-validation`: the rules for validating `@Map` directives — source first segments must name method parameters (enforced by `ValidateSourceParameters`), and duplicate targets are rejected (enforced by `ValidateNoDuplicateTargets`).

### Modified Capabilities
- `processor`: `Pipeline` gains three new stage dependencies (`ValidateSourceParameters`, `SeedGraph`, `DumpGraph`) and invokes them after `ValidateNoDuplicateTargets`. `PercolateProcessor` declares `percolate.debug.graphs` via `getSupportedOptions()`. `ProcessorModule` provides `ProcessorOptions`.
- `unit-testing`: enumerates the new classes that require unit specs (`SeedGraph`, `DumpGraph`, `ProcessorOptions`, `MapperGraph`, the DOT renderer, `ValidateSourceParameters`, the `Node`/`Edge`/`Location` value types).

## Impact

- **Code**: `processor/src/main/java/io/github/joke/percolate/processor/`
  - Pipeline gains `ValidateSourceParameters`, `SeedGraph` and `DumpGraph` stages.
  - `Pipeline.process(...)` invokes the new stages after `ValidateNoDuplicateTargets`.
  - `PercolateProcessor.getSupportedOptions()` declares `percolate.debug.graphs`.
  - `ProcessorModule` provides `ProcessorOptions`.
  - New sub-package `io.github.joke.percolate.processor.graph` for `MapperGraph`, the DOT renderer, and the `Node` / `Edge` / `Location` / `AccessPath` / `TargetPath` / `Scope` value types.
- **Tests**: `processor/src/test/groovy/...`
  - One unit spec per new class (`SeedGraphSpec`, `DumpGraphSpec`, `ProcessorOptionsSpec`, `MapperGraphSpec`, `DotRendererSpec`, `ValidateSourceParametersSpec`, plus value-type specs where behaviour is non-trivial).
  - Single-aspect graph-shape specs (param-becomes-source-root, return-type-becomes-target-root, directive-seeds-chain, dotted-source-becomes-chain, two-methods-share-mapper, generic-substituted-method, etc.).
  - A handful of golden DOT specs under `src/test/resources/golden-graphs/` plus an integration spec under Google Compile Testing covering the option-on-emits-file and option-off-emits-no-file paths.
  - New Spock Groovy extension module `MapperGraphExtensions` registered via `META-INF/services/org.codehaus.groovy.runtime.ExtensionModule` in the `test` source set.
- **Build**: `processor/build.gradle` — JGraphT 1.5.2 already declared (currently unused); no new external dependencies.
- **Public API (annotations module)**: unchanged.
- **Generated output**: still none for compiled mappers. `<MapperFQN>.seed.dot` is emitted only when `-Apercolate.debug.graphs=true` is set.
- **Affected teams / areas**: processor module only; annotations module unaffected; downstream consumers unaffected unless they enable the new option.
