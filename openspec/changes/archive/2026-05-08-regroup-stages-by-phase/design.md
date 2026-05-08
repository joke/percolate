## Context

The processor module pipeline today has nine stages threaded through
`Pipeline` in a hand-rolled order published by
`ProcessorModule.stages(...)`. Seven stages live at the root package
(`io.github.joke.percolate.processor`), the two newest occupants —
`ExpandStage` (and three phases) and `ValidateRealisationStage` (and
two phases) — sit in their own sub-packages. The asymmetry is mild
today but worsens as the planned phases (codegen, container expansion,
sub-directive emitters) add stages.

The most recent change's design doc flagged the question explicitly:

> **Stage-list ordering: explicit list or `@Order(N)` annotation?**
> Explicit `List<Stage>` injection (one Dagger module declaring the
> order) is more visible than scattered `@Order` annotations. Lean:
> explicit list.

That lean stands. This change does not introduce ordering machinery;
it is a pure layout refactor.

**Stakeholders**: processor maintainers (the only consumers of the
internal packages). Downstream strategy authors (consumers of
`processor.spi`) are unaffected — `processor.spi` does not move.

```
   Before                                After
   ──────                                ─────
   processor/                            processor/
   ├── Stage.java          (root)        ├── (root: cross-cutting only)
   ├── DiscoverAbstractMethods           │   Pipeline, MapperContext,
   ├── DiscoverMappings                  │   MapperStep, Diagnostics,
   ├── ValidateNoDuplicate…              │   ProcessorOptions,
   ├── ValidateSourceParameters          │   PercolateProcessor,
   ├── SeedGraph                         │   ProcessorModule,
   ├── DumpGraph                         │   ProcessorComponent
   ├── DumpExpandedGraph                 ├── stages/
   ├── Pipeline.java       (root)        │   ├── Stage.java
   ├── MapperContext.java                │   ├── discover/
   ├── …                                 │   │   ├── DiscoverAbstractMethods
   ├── expand/                           │   │   └── DiscoverMappings
   │   ├── ExpandStage                   │   ├── validate/
   │   ├── ExpansionPhase                │   │   ├── ValidateNoDuplicate…
   │   ├── ResolveSourceChainsPhase      │   │   ├── ValidateSourceParameters
   │   ├── ResolveTargetChainsPhase      │   │   ├── ValidateRealisationStage
   │   └── BridgeSourceToTargetPhase     │   │   ├── ValidateMarkersPhase
   ├── validate/                         │   │   └── ValidatePathsPhase
   │   ├── ValidateRealisationStage      │   ├── seed/
   │   ├── ValidateMarkersPhase          │   │   └── SeedGraph
   │   └── ValidatePathsPhase            │   ├── expand/
   ├── graph/                            │   │   ├── ExpandStage
   ├── spi/                              │   │   ├── ExpansionPhase
   └── model/                            │   │   ├── ResolveSourceChainsPhase
                                         │   │   ├── ResolveTargetChainsPhase
                                         │   │   └── BridgeSourceToTargetPhase
                                         │   └── dump/
                                         │       ├── DumpGraph
                                         │       └── DumpExpandedGraph
                                         ├── graph/    (unchanged)
                                         ├── spi/      (unchanged)
                                         └── model/    (unchanged)
```

## Goals / Non-Goals

**Goals:**

- Every pipeline stage and its supporting phases live under one
  `stages/` umbrella, sub-packaged by phase.
- A new contributor adding a stage has exactly one place to look ("how
  do existing phases lay out their files?") and one consistent answer.
- The `Stage` interface lives next to its implementations, mirroring
  the JDK convention of grouping interfaces with their canonical
  implementations.
- The five spec capabilities that pin processor sub-package paths
  receive deltas updating those paths.

**Non-Goals:**

- Introducing any stage-ordering machinery. No `priority()`, no
  `@Order` annotation, no enum-driven phase, no topological partial
  order. The hand-rolled `ProcessorModule.stages(...)` list stays.
- Changing the `Stage` interface surface, the `MapperContext` surface,
  or the Dagger graph topology.
- Generalising `DumpGraph` and `DumpExpandedGraph` into a single
  parameterised stage (the previous design doc explicitly leaned
  "refactor lazily"; that lean stands).
- Touching infrastructure packages (`graph`, `spi`, `model`).
- Touching the public SPI surface or its `META-INF/services` registration.
- Changing tests other than to follow the package moves.

## Decisions

### D1. Layout: nested under `stages/`, not flat at processor root

The flat alternative would add `processor.discover`, `processor.seed`,
`processor.dump` as siblings to the existing `processor.expand` and
`processor.validate` (no rename, fewer file moves). Adopted instead is
the nested form: a single `processor.stages` umbrella with every phase
package beneath it.

**Rationale.** The flat form preserves the existing inconsistency in a
different shape — one is left guessing whether `processor.graph` is a
phase or infrastructure. The nested form gives a single visual
discriminator: anything under `stages/` is a pipeline phase; anything
not under `stages/` is infrastructure or cross-cutting glue. The
discriminator scales: when a future change adds phases (codegen,
container expansion, conversions), they land under `stages/` and the
"is this a phase?" question is answered by the package path alone.

**Cost.** Two existing packages move (`processor.expand` →
`processor.stages.expand`, `processor.validate` →
`processor.stages.validate`). The diff is mechanical and the test
suite catches any mis-routed import.

### D2. `Stage` interface lives in `processor.stages`, not at the root

`Stage.java` could remain at the root next to `Pipeline.java` (its
consumer) or move into `processor.stages` next to its implementations.
Adopted: move into `processor.stages`.

**Rationale.** `Stage` is the contract every implementation in
`stages/*` satisfies. Co-location with implementations matches the JDK
convention (`java.util.Collection` lives with `ArrayList`, not in a
separate "interfaces" package). `Pipeline` continues to import the
interface from one well-defined location; the import-direction story
("orchestrator depends on contract; contract lives with
implementations") is clearer.

**Alternative considered.** Keep `Stage.java` at the root. The case
for: `Pipeline` is the sole consumer, and consumer-co-location has its
own logic. The case against: every stage implementation imports
`Stage`, and the importer set is much larger than the consumer set.
Co-locating with the larger group wins.

### D3. Validators consolidate into one `stages.validate` package

`ValidateNoDuplicateTargets` and `ValidateSourceParameters` (today
loose at the root) move into the same package as
`ValidateRealisationStage` and its phases. The alternative would split
input validation (pre-seed) from output validation (post-expand) into
two packages.

**Rationale.** Java packages organise by topic, not by pipeline
position. All five files are validators; their shared concern is
"flagging diagnostic conditions through `Diagnostics`," which the
`mapping-validation` capability spec already enforces uniformly across
"any class under `processor` named with the `Validate*` prefix." The
hand-rolled stage list in `ProcessorModule` continues to encode
pipeline position; the package layout encodes topic. Five files in one
package is not crowded.

**Alternative considered.** `stages.precheck` for input validators,
`stages.validate` for output validators. The case against: it
introduces a vocabulary distinction ("precheck" vs. "validate") that
nothing else in the codebase carries; the five files don't
collaborate within a package boundary anyway.

### D4. Stage classes become `public`, mirroring the existing precedent

Stages currently in the root package rely on package-private visibility
to be reachable from `ProcessorModule`. Once they move to a sub-package,
that reachability is broken; either `ProcessorModule` moves with them
(undesirable — it would split off from the cross-cutting glue at the
root) or the stages become `public`.

**Decision.** Promote stages to `public`. Same precedent already
established by `ExpandStage`, `ValidateRealisationStage`, and their
phases — those classes live in sub-packages today and are `public`.
This change extends the same pattern uniformly to the seven stages
that have so far escaped it.

**Cost.** The change-control argument against `public` types is they
become candidates for external consumption. In this codebase, the
processor module is internal-only; the only externally visible surface
is `processor.spi`. Stages becoming `public` does not enlarge the
external API.

### D5. No ordering machinery

Several alternatives were canvassed in conversation: integer priority
on `Stage` with Dagger multibindings; a `StagePhase` enum with
ordinal-based ordering; topological `@Before`/`@After` annotations.
All rejected for this change.

**Rationale.** The pipeline is genuinely linear and will stay so for
the foreseeable phases (the design doc of the most recent change
sketches Phases 3–10, all linear). With ten stages and a stable
ceiling, the explicit hand-rolled list in `ProcessorModule.stages(...)`
remains readable in one place and immune to drift problems
(priority-number gaps, equal-priority undefined order, partial-order
conflict diagnosis). Adding ordering machinery would solve a problem
the codebase does not have.

**Future re-litigation.** If pipeline branching ever genuinely
appears (e.g., codegen short-circuit on validation failure), revisit.
The current change does not foreclose any future ordering scheme — it
just declines to introduce one preemptively.

### D6. `MapperContext`, `Pipeline`, `Diagnostics`, `MapperStep`,
### `ProcessorModule`, `ProcessorComponent`, `PercolateProcessor`,
### `ProcessorOptions` stay at the root

These are cross-cutting: no one of them belongs to a pipeline phase.
Demoting any of them under `stages/` would force every consumer to
import across the umbrella for unrelated concerns. Demoting some but
not others would re-introduce the asymmetry this change is correcting.
Decision: leave the root package as the home for cross-cutting glue
plus the Dagger graph definitions; everything else moves under
`stages/`.

### D7. Spec deltas update package pins on five capabilities

The five capabilities whose specs reference processor sub-package paths
explicitly:

| Capability | What pins package |
|---|---|
| `processor` | `Stage` interface declared in `processor` |
| `graph-debug-output` | `DumpGraph`, `DumpExpandedGraph` in `processor` |
| `graph-expansion` | `ExpandStage` in `processor.expand` |
| `realisation-validation` | `ValidateRealisationStage` in `processor.validate` |
| `expansion-strategy-spi` | architectural test forbids `processor.expand` imports |

Each receives a delta updating its package pins. Behavioural
requirements are unchanged in every case; only the package path
shifts.

The `mapping-validation` capability also references the processor
package, but the wording is "any class **under**
`io.github.joke.percolate.processor` named with the `Validate*`
prefix" — "under" already encompasses sub-packages. No delta needed.

## Risks / Trade-offs

- **Risk:** A stale import in a non-obvious place (a Spock fixture, a
  golden file, a build-time utility) survives the move and only
  surfaces in CI.
  **Mitigation:** the move is mechanical and IDE-driven; the full
  Spock + Compile Testing suite must pass before merge. The
  architectural test in `expansion-strategy-spi` already enforces a
  package-isolation invariant that catches stray cross-package
  imports.

- **Risk:** A user-facing tool (script, code search) anchored on the
  old package paths breaks silently.
  **Mitigation:** none of the moved packages are public API. The
  `processor.spi` package — the only externally visible surface — is
  unchanged. No script in this repo references the moved internal
  paths.

- **Trade-off:** stage classes become `public`, formally enlarging
  what could be referenced from outside the module.
  **Mitigation:** the processor module is internal-only and not
  published as a separate artifact. The visibility increase is a
  documentation concern, not an exposure concern. The same trade-off
  was already accepted for the existing `expand/` and `validate/`
  classes.

- **Trade-off:** `ProcessorModule.stages(...)` keeps its 9-arg shape
  with longer import lines instead of trending toward a Dagger
  multibinding. The trade-off is paid here in service of D5.

- **Risk:** the `validate/` consolidation merges two cohorts
  (input-validation and realisation-validation phases) that have
  different lifetimes in the pipeline.
  **Mitigation:** the merge is by topic, not by lifetime; the existing
  `mapping-validation` spec already addresses both cohorts uniformly
  via the "under processor named `Validate*`" pattern.

## Migration Plan

Single PR, mechanical refactor, no behavioural change.

1. Create the new package directories under
   `processor/src/main/java/io/github/joke/percolate/processor/stages/`.
2. Move each file via IDE rename-package (production source), updating
   the `package` declaration and adjusting visibility to `public`.
3. Move the corresponding test file (`processor/src/test/groovy/...`)
   to the mirroring path.
4. Update import lines in `ProcessorModule`, `Pipeline`, and any
   consumer/test that references the moved classes.
5. Run the full Spock + Compile Testing suite. Goldens under
   `src/test/resources/golden-graphs/` are unaffected and should
   re-pass byte-for-byte.
6. Apply the spec deltas (`processor`, `graph-debug-output`,
   `graph-expansion`, `realisation-validation`,
   `expansion-strategy-spi`) updating package pins.
7. Verify via `openspec verify` (or equivalent).

**Rollback strategy:** `git revert`. The change is one commit's worth
of mechanical movement; reverting is safe.

## Open Questions

None. The conversation that led to this change canvassed the
alternatives (flat vs. nested, ordering machinery, validator
consolidation) and reached lean decisions on each.
