## 1. ExpandedGraphView (the filter view)

- [x] 1.1 Add `ExpandedGraphView` final class in `processor/src/main/java/io/github/joke/percolate/processor/graph/`, modelled after `RealisedSubgraph`: package-constructible, holds a `MaskSubgraph<Node, Edge>` + delegate reference, exposes `Stream<Node> nodes()`, `Stream<Edge> edges()`, and `Stream<Node> nodesByScope(Scope)` with `Node::id` / natural `Edge` ordering
- [x] 1.2 Implement the edge mask predicate hiding `EdgeKind.SEED` and `EdgeKind.MARKER`; let `REALISED` and `SUB_SEED` pass through
- [x] 1.3 Implement the vertex mask predicate: build an index of `(scope, loc)` pairs that carry a concrete type, then hide a node iff it is the untyped placeholder AND its `(scope, loc)` appears in that index
- [x] 1.4 Add `public ExpandedGraphView expandedView()` factory method on `MapperGraph` that constructs the mask and wraps it
- [x] 1.5 Add `ExpandedGraphViewSpec` (Spock) covering: SEED filtered, MARKER filtered, REALISED retained, SUB_SEED retained, untyped placeholder hidden when typed counterpart exists at same `(scope, loc)`, untyped placeholder retained when no typed counterpart exists, underlying `MapperGraph` unchanged after view construction

## 2. Renderer abstraction over node/edge sources

- [x] 2.1 Introduce a package-private `GraphSource` interface in `processor/src/main/java/io/github/joke/percolate/processor/graph/` exposing `Stream<Node> nodes()` and `Stream<Edge> edges()`; have both `MapperGraph` and `ExpandedGraphView` implement it (additive — existing methods keep their signatures)
- [x] 2.2 Refactor `DotRenderer.render(...)` to accept a `GraphSource` in place of `MapperGraph` (the `TypeElement` parameter is unchanged)
- [x] 2.3 Update `DumpGraph` to call the new signature with the raw `MapperGraph` (unchanged behaviour for `seed.dot`)
- [x] 2.4 Verify the existing `DotRendererSpec` tests that construct a raw `MapperGraph` still compile against the new signature; no assertion changes expected at this step

## 3. Renderer styling and label rules

- [x] 3.1 Implement two-line node labels in `DotRenderer.nodeLabel(...)`: first line is the existing location segment; second line is the simplified type name
- [x] 3.2 Implement the type-name simplifier: strip the literal prefix `java.lang.` from class names, preserve all other packages verbatim, apply the rule recursively to generic type arguments, render the untyped placeholder as `?`
- [x] 3.3 Update `KIND_STYLE` in `DotRenderer`: REALISED → `solid` with elevated `penwidth` (heaviest visible stroke); SUB_SEED → `solid` with low `penwidth` and gray colour; SEED → unchanged; MARKER → removed entry (falls back to default style if a MARKER edge is ever passed to the renderer directly)
- [x] 3.4 Rewrite `DotRenderer.renderEdge(...)` label assembly per kind:
    - REALISED: label is `<simple strategy name> (<weight>)`, with `∞` for the sentinel weight; include `groupId` attribute when present
    - SUB_SEED: label is the literal string `SUB_SEED` only; no kind-token verbosity beyond that, no weight, no `directive` marker, no strategy attribution
    - SEED: keep the prior `SEED | <weight> | directive?` format
    - MARKER: keep the prior `MARKER | <weight>` format (used only when MARKER edges are rendered directly, outside the expanded view)
- [x] 3.5 Ensure `Node.id()` content (the quoted identifier preceding the attribute block) keeps fully qualified types — only the `label` attribute is simplified

## 4. Wire DumpExpandedGraph to the expanded view

- [x] 4.1 In `DumpExpandedGraph.apply(...)`, replace `dotRenderer.render(graph, mapperType)` with `dotRenderer.render(graph.expandedView(), mapperType)`
- [x] 4.2 Confirm the seed dump path (`DumpGraph`) still calls `dotRenderer.render(graph, mapperType)` against the raw `MapperGraph`
- [x] 4.3 Update `DumpExpandedGraphSpec` assertions to expect REALISED + SUB_SEED edges only; remove or invert assertions that expected SEED or MARKER edges in the expanded output

## 5. Test surface updates

- [x] 5.1 Update `DotRendererSpec` assertions:
    - flip "REALISED edge renders with dashed style" expectation to the new solid/heavy style
    - flip "SUB_SEED edge renders with bold style" expectation to the new solid/secondary style
    - delete "MARKER edge renders with dotted style" — no longer guaranteed
    - update the "renders all four edge kinds" test to assert correct labelling per the new per-kind format (REALISED has strategy + weight, SUB_SEED is `SUB_SEED` only, SEED unchanged, MARKER unchanged)
    - add assertions for the new two-line node label format and the `java.lang` stripping rule
- [x] 5.2 Update `DotRendererExpandedSpec` assertions for the new edge styles and label content; ensure no assertion expects MARKER or SEED in the expanded view
- [x] 5.3 Audit `MethodCallBridgeIntegrationSpec` for the single `.dot` / edge-kind reference flagged in the proposal; update if it asserts a now-stale string
- [x] 5.4 Regenerate the two affected golden files via `./gradlew :processor:updateGoldens` and review the diffs — confirm `expanded-trivial.dot` and `expanded-tier2-failing.dot` reflect the new label/style scheme and contain no SEED or MARKER edges
- [x] 5.5 Confirm the three seed-only goldens (`two-method-mapper.dot`, `per-method-clusters.dot`, `escaped-labels.dot`) regenerate only with two-line node labels (the new `\n?` second line) and no other changes

## 6. Verification

- [x] 6.1 Run `./gradlew :processor:test` and confirm all tests pass
- [x] 6.2 Run `openspec validate simplify-expanded-debug-graph --strict` and confirm clean
- [x] 6.3 Build the `percolate-integration` project against the modified processor; open `PersonMapper.expanded.dot` and confirm:
    - same-location-different-type nodes are now visually distinguishable
    - REALISED edges visually dominate
    - SEED, MARKER, and matched-pair `::?` nodes are absent
- [x] 6.4 Render `PersonMapper.expanded.dot` to SVG (`dot -Tsvg PersonMapper.expanded.dot > PersonMapper.expanded.svg`) and visually confirm the chain `List<Optional<Person.Address>> → Set<Human.Address> → Optional<Set<Human.Address>>` is readable on inspection
