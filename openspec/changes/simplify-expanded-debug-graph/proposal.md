## Why

The `expanded.dot` debug file is unreadable in practice. Three problems compound:
the seed graph is duplicated verbatim inside the expanded file, so SEED edges dominate
the rendering at full visual weight while the actually-important REALISED edges are
rendered as faint dashes — the visual hierarchy is inverted relative to semantic
importance. Multiple nodes for the same slot (e.g. `src[person.addresses]` at four
different types) render with identical labels and cannot be told apart in the SVG.
MARKER edges and `::?` placeholder nodes add noise that carries no useful information
once expansion has finished.

Beyond the immediate readability issue, the same content that pollutes the debug
output also pollutes the input that the future codegen stage will consume — SEED
and SUB_SEED edges have weight `∞` and would confuse Dijkstra-based path selection,
and `::?` nodes have no type to generate code from. A filtered "operational view"
that both consumers can share is the structural fix.

## What Changes

- Add an **operational view** over `MapperGraph` — a pure-function transformation
  that drops SEED edges, MARKER edges, and `::?` placeholder nodes. The full
  graph is kept untouched in memory; the view is non-destructive.
- `DumpExpandedGraph` renders through the operational view instead of the raw graph.
- **BREAKING (debug-output only)**: `DotRenderer` is restyled and relabelled:
  - REALISED edges render **solid, bold** (currently dashed, thin).
  - SUB_SEED edges render **solid, faint gray** (currently bold).
  - SEED edges no longer appear in expanded output (still rendered in seed output).
  - MARKER edges are dropped entirely.
  - Node labels become **two-line**: location on top, simple type name on bottom
    (e.g. `src[address.street]` + `String`). Fully-qualified types remain in the
    node id for uniqueness.
  - Edge labels are simplified: REALISED shows `StrategyShortName (weight)`;
    SUB_SEED shows only `SUB_SEED` (strategy attribution and `directive` token
    dropped, recoverable via REALISED edges on the same node).
- Out of scope, deferred: a separate `expanded.full.dot` containing the
  unfiltered graph, compiler-diagnostic improvements, and the future codegen view
  (which will layer a tighter SUB_SEED filter on top of this one).

## Capabilities

### New Capabilities
<!-- none -->

### Modified Capabilities

- `graph-debug-output`: the expanded debug graph now renders a filtered operational
  view rather than the raw post-expansion graph; node-label and edge-style rules
  are revised to put REALISED on top of the visual hierarchy and to make
  same-location-different-type nodes distinguishable.

## Impact

- **Code**
  - `processor/src/main/java/io/github/joke/percolate/processor/graph/DotRenderer.java` — label format, edge styles, edge-label content, drop MARKER handling.
  - `processor/src/main/java/io/github/joke/percolate/processor/stages/dump/DumpExpandedGraph.java` — render through the operational view.
  - New: an operational-view component (final shape to be settled in `design.md`)
    that filters edges and nodes as a pure function of the full graph.
- **Tests**
  - `processor/src/test/resources/golden-graphs/expanded-trivial.dot` — regenerate.
  - `processor/src/test/resources/golden-graphs/expanded-tier2-failing.dot` — regenerate.
  - Seed-only goldens (`two-method-mapper.dot`, `per-method-clusters.dot`,
    `escaped-labels.dot`) unaffected — seed rendering does not change.
  - `DotRendererSpec.groovy` — flip style assertions (REALISED→solid, SUB_SEED→solid),
    remove MARKER-edge style test, update "all four kinds" test to three.
  - `DotRendererExpandedSpec.groovy` and `DumpExpandedGraphSpec.groovy` — adjust
    assertions that reference the dropped/restyled edge kinds.
  - `MethodCallBridgeIntegrationSpec.groovy` — single light-touch reference.
- **Dependencies / APIs** — none. No new libraries; no public API changes outside
  the debug-output capability.
- **Downstream / future** — the operational view is positioned to be the same
  input the future codegen stage consumes (with an added SUB_SEED filter on top),
  removing the need for codegen and debug-render to diverge on what counts as
  "expansion scaffolding".
- **Teams** — single-maintainer project; no cross-team coordination required.
  Power users who rely on expanded.dot for bug reports will see a different
  (simpler) file shape on next build.
