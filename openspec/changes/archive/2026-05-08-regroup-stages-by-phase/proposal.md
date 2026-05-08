## Why

Seven of the nine pipeline stages live loose in the root `processor`
package while the two newest (`ExpandStage`, `ValidateRealisationStage`)
already sit in their own sub-packages. The asymmetry is mild today but
becomes friction as planned phases (codegen, container expansion,
sub-directive emitters) add more stages — every newcomer has to choose
between matching the older "loose" pattern or the newer "own package"
pattern. Cleaning this up before the next stage-adding change is
cheaper than cleaning it up alongside one.

## What Changes

- **NEW** package `io.github.joke.percolate.processor.stages` collecting
  every pipeline stage and its supporting phases under one umbrella,
  with one sub-package per pipeline phase.
- **NEW** sub-packages: `stages.discover` (holds `DiscoverAbstractMethods`,
  `DiscoverMappings`), `stages.seed` (holds `SeedGraph`), `stages.dump`
  (holds `DumpGraph`, `DumpExpandedGraph`).
- **MOVED** `Stage` interface from `processor` to `processor.stages`.
- **MOVED** `processor.expand` package → `processor.stages.expand`
  (`ExpandStage`, `ExpansionPhase`, `ResolveSourceChainsPhase`,
  `ResolveTargetChainsPhase`, `BridgeSourceToTargetPhase`).
- **MOVED** `processor.validate` package → `processor.stages.validate`
  (`ValidateRealisationStage`, `ValidateMarkersPhase`, `ValidatePathsPhase`).
- **MOVED** loose validators `ValidateNoDuplicateTargets` and
  `ValidateSourceParameters` into the consolidated
  `processor.stages.validate` package (alongside the existing
  realisation validators — one package, all validators).
- **BREAKING (internal)**: every moved stage class transitions from
  package-private to `public` so it remains reachable from
  `ProcessorModule`. Same precedent established by the existing `expand/`
  and `validate/` packages.
- **UNCHANGED**: stage ordering remains the explicit hand-rolled list in
  `ProcessorModule.stages(...)`; no new ordering machinery (no priority,
  no enum, no topological sort). The 9-arg `@Provides` method keeps the
  same shape, just with updated import paths.
- **UNCHANGED**: cross-cutting infrastructure stays at the root
  (`Pipeline`, `MapperContext`, `MapperStep`, `Diagnostics`,
  `ProcessorOptions`, `PercolateProcessor`, `ProcessorModule`,
  `ProcessorComponent`).
- **UNCHANGED**: infrastructure packages stay where they are
  (`processor.graph`, `processor.spi`, `processor.model`).
- **UNCHANGED**: behaviour. No interface-level change. No Dagger pattern
  change. Pipeline order is identical pre- and post-refactor.
- Spec text in five capabilities receives a delta updating the
  package references the spec pins (Stage's home package, the dump
  stages' home package, `ExpandStage`'s home package,
  `ValidateRealisationStage`'s home package, and the
  architectural-isolation test in the SPI spec that names
  `processor.expand` explicitly).

## Capabilities

### New Capabilities

None. This change is pure layout — no new behaviour, no new contracts.

### Modified Capabilities

- `processor`: the `Stage` interface moves from
  `io.github.joke.percolate.processor` to
  `io.github.joke.percolate.processor.stages`. Spec scenarios that pin
  the package update accordingly. The `ProcessorModule.stages(...)` and
  `Pipeline` requirements (which name stages by simple class name) are
  unchanged in substance; only their import-path implications shift.
- `graph-debug-output`: `DumpGraph` and `DumpExpandedGraph` move from
  `io.github.joke.percolate.processor` to
  `io.github.joke.percolate.processor.stages.dump`. Spec wording that
  pins the package updates accordingly. Behaviour is unchanged.
- `graph-expansion`: `ExpandStage` (and its phases) moves from
  `io.github.joke.percolate.processor.expand` to
  `io.github.joke.percolate.processor.stages.expand`. Spec wording that
  pins the package updates accordingly. Behaviour is unchanged.
- `realisation-validation`: `ValidateRealisationStage` (and its phases)
  moves from `io.github.joke.percolate.processor.validate` to
  `io.github.joke.percolate.processor.stages.validate`. Spec wording
  that pins the package updates accordingly. Behaviour is unchanged.
- `expansion-strategy-spi`: the architectural-isolation requirement
  that built-in strategies SHALL NOT import classes from
  `io.github.joke.percolate.processor.expand` updates the forbidden
  package to `io.github.joke.percolate.processor.stages.expand`. Intent
  and architectural test are unchanged; only the package name shifts.

## Impact

**Affected code** (processor module only): every stage class file moves
to its new package, with `package` declarations and visibility modifiers
updated. Every consumer of those classes — primarily `ProcessorModule`,
`Pipeline`, and a handful of test classes — gets updated import lines.
No method signatures, no interfaces, no Dagger graph topology change.
Estimated diff: file moves + ~20 lines of import / visibility churn in
the production sources, mirrored in the Spock test tree.

**Affected APIs**: the `processor.spi` package (the only externally
visible surface) is untouched. All moves are inside the processor
module's internal packages; downstream strategy authors are unaffected.

**Affected tests**: Spock specs under
`processor/src/test/groovy/.../expand/` and `.../validate/` move to
mirror the new main-source layout. New test directories appear for
`stages.discover`, `stages.seed`, `stages.dump`. Test bodies are
unchanged beyond their `package` declarations and imports. Golden
files under `src/test/resources/golden-graphs/` are unaffected.

**Affected build**: no Gradle change. `auto-service`, Dagger
multibindings, JGraphT — all wiring constant.

**Affected teams**: processor maintainers only. No downstream consumer
sees a difference; the change is invisible from the SPI surface.

**Migration plan**: single PR. The refactor is mechanical and the
existing test suite (full Spock + Compile Testing coverage) catches any
mis-routed import. Rolling back is `git revert`.
