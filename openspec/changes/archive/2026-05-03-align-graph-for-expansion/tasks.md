## 1. Foundation types (no dependencies)

- [x] 1.1 Create `EdgeKind` enum in `io.github.joke.percolate.processor.graph` with values `SEED`, `REALISED`, `MARKER`, `SUB_SEED` in declaration order
- [x] 1.2 Create `Weights` final non-instantiable class with `public static final int` constants `NOOP = 0`, `STEP = 1`, `COPY = 2`, `EXPENSIVE = 3`, `SENTINEL_UNREALISED = Integer.MAX_VALUE / 2`
- [x] 1.3 Create `VarNames` marker type (interface or empty type) in the `graph` package
- [x] 1.4 Create `IncomingValues` interface with `single()`, `byGroupPosition(int)`, `byName(String)` returning `CodeBlock` (JavaPoet)
- [x] 1.5 Create `EdgeCodegen` functional interface declaring `CodeBlock render(VarNames vars, IncomingValues inputs)`
- [x] 1.6 Create `GroupCodegen` functional interface declaring `CodeBlock render(VarNames vars, IncomingValues inputs)`
- [x] 1.7 Spock unit specs for `EdgeKind.values()` order and `Weights` constant values

## 2. Location refactor

- [x] 2.1 Add abstract `String segment()` method to the `Location` interface
- [x] 2.2 Implement `SourceLocation.segment()` returning the existing access-path encoding
- [x] 2.3 Implement `TargetLocation.segment()` returning the existing target-path encoding (empty path → `"[]"` or equivalent stable form)
- [x] 2.4 Create `ElementLocation` `@Value` class with no payload fields and `segment()` returning literal `"elem"`
- [x] 2.5 Spock unit specs: `ElementLocation.segment() == "elem"`, two `ElementLocation` instances are equal, `SourceLocation` and `TargetLocation` segments preserve segment order

## 3. Node refactor

- [x] 3.1 Add `Optional<Node> parent` field to `Node` (default Lombok `@Value` includes it in equals/hashCode)
- [x] 3.2 Move `id()` derivation into `Node` itself: phantom branch uses `parent.orElseThrow().id() + "::elem"`; non-phantom branch uses `scope.encode() + "::" + loc.segment() + "::" + typeEncode()`
- [x] 3.3 Throw an unchecked exception in `id()` when `loc instanceof ElementLocation && parent.isEmpty()`
- [x] 3.4 Verify `id()` output for non-phantom nodes is byte-identical to the Phase 1 implementation (existing seed-graph tests must pass without golden updates)
- [x] 3.5 Update `Node` constructor / builder call sites in `SeedGraph` and tests to pass `parent = Optional.empty()` for non-phantom nodes
- [x] 3.6 Spock unit specs for source/target id stability, phantom id derivation, phantom-without-parent throws, two phantoms with different parents have different ids

## 4. Edge refactor

- [x] 4.1 Add fields to `Edge`: `EdgeKind kind`, `Optional<String> groupId`, `Optional<EdgeCodegen> codegen`, `Optional<String> strategyClassFqn`
- [x] 4.2 Annotate `Edge` with `@Value @EqualsAndHashCode(exclude = {"codegen", "strategyClassFqn"})` so equality is structural over `(from, to, weight, kind, directive, groupId)`
- [x] 4.3 Make the all-args constructor package-private; require consumers to go through factories
- [x] 4.4 Implement `Edge.seed(Node from, Node to, AnnotationMirror directive)` factory enforcing `kind = SEED`, `weight = SENTINEL_UNREALISED`, only `directive` populated
- [x] 4.5 Implement `Edge.realised(Node from, Node to, int weight, Optional<String> groupId, EdgeCodegen codegen, String strategyClassFqn)` factory enforcing `kind = REALISED`, weight from `Weights` scale, `codegen` and `strategyClassFqn` populated
- [x] 4.6 Implement `Edge.marker(Node from, Node to, String strategyClassFqn)` factory enforcing `kind = MARKER`, `weight = NOOP`, only `strategyClassFqn` populated
- [x] 4.7 Implement `Edge.subSeed(Node from, Node to, String strategyClassFqn)` factory enforcing `kind = SUB_SEED`, `weight = SENTINEL_UNREALISED`, only `strategyClassFqn` populated
- [x] 4.8 Update `Edge` `Comparable` ordering to `(from.id(), to.id(), weight, kind, directive-presence, groupId)`
- [x] 4.9 Spock unit specs for each factory's per-kind invariants
- [x] 4.10 Spock unit specs for equality excluding `codegen` and `strategyClassFqn`
- [x] 4.11 Spock unit specs for kind participating in equality and ordering

## 5. MapperGraph refactor

- [x] 5.1 Add `Map<String, GroupCodegen> groupCodegens` storage to `MapperGraph` (mutable but only via the documented method)
- [x] 5.2 Implement `addGroupCodegen(String groupId, GroupCodegen codegen)` that throws an unchecked exception when invoked twice with the same `groupId`
- [x] 5.3 Implement `groupCodegen(String groupId)` returning `Optional<GroupCodegen>`
- [x] 5.4 Implement `RealisedSubgraph` class wrapping a JGraphT `MaskSubgraph` filtered to edges with `kind == REALISED` and nodes incident on at least one such edge
- [x] 5.5 Expose `RealisedSubgraph.nodes()`, `edges()`, `nodesByScope(Scope)` with the same sorted-iteration contract as `MapperGraph`; do NOT expose `addNode` / `addEdge`
- [x] 5.6 Add `MapperGraph.realisedSubgraph()` returning a fresh `RealisedSubgraph` view (no graph copy)
- [x] 5.7 Spock unit specs: empty `realisedSubgraph()` for seed-only graphs, filter excludes SEED/MARKER/SUB_SEED edges, only nodes incident on REALISED appear, `addGroupCodegen` rejects duplicates, `groupCodegen` returns the stored closure, returns empty for unknown ids

## 6. SeedGraph refactor

- [x] 6.1 Replace every direct `Edge(...)` constructor call in `SeedGraph` with the appropriate `Edge.seed(...)` factory invocation
- [x] 6.2 Verify (compile-time) the all-args `Edge` constructor is no longer reachable from outside the `graph` package
- [x] 6.3 Update existing seed-graph Spock specs to assert emitted edges have `kind == SEED` and `weight == Weights.SENTINEL_UNREALISED`
- [x] 6.4 Add Spock spec asserting `graph.edges().allMatch(e -> e.getKind() == EdgeKind.SEED)` for any non-empty mapper
- [x] 6.5 Add Spock spec asserting zero `REALISED`, `MARKER`, `SUB_SEED` edges and empty `codegen` / `groupId` / `strategyClassFqn` on every emitted edge
- [x] 6.6 Verify the forest-invariant test still passes (topology unchanged; only weight and kind changed)

## 7. DOT renderer refactor

- [x] 7.1 Add `EdgeKind` → DOT-style mapping (colour and/or line style) — implementation-defined but stable; document in `DotRenderer` source
- [x] 7.2 Render `∞` (U+221E) in edge labels when `weight == Weights.SENTINEL_UNREALISED`; otherwise render the numeric value
- [x] 7.3 Render `strategyClassFqn` in the edge label when present (full FQN or simple name; document the choice in `DotRenderer`)
- [x] 7.4 Verify the renderer never reads `Edge.codegen` (codegen closures are opaque metadata)
- [x] 7.5 Add `EdgeKind` marker to every edge label / attribute set so the kind is visible in DOT output
- [x] 7.6 Add phantom-node shape (third distinct shape, e.g., `diamond`) for nodes whose `loc` is `ElementLocation`
- [x] 7.7 Implement phantom cluster grouping: phantom nodes render inside `cluster_<encoding-of-parent-scope>`, looked up via `Node.parent`
- [x] 7.8 Throw an unchecked exception when the renderer encounters a phantom node with `parent = Optional.empty()` (schema invariant enforcement at render time)
- [x] 7.9 Update existing DOT golden test files to reflect the `∞` weight rendering and `kind = SEED` markers on edge labels
- [x] 7.10 Spock unit specs: sentinel renders as `∞`, phantom renders inside parent's cluster, codegen never appears in output, phantom-without-parent throws, kind-keyed edge styling per kind, strategyClassFqn appears when present

## 8. Verification

- [x] 8.1 Run the full processor unit test suite (`gradle :processor:test --tests "io.github.joke.percolate.processor.**"`) and confirm all green
- [x] 8.2 Run the full processor integration test suite (`gradle :processor:integrationTest`) and confirm all green
- [x] 8.3 Run `openspec validate align-graph-for-expansion --strict` and confirm clean
- [x] 8.4 Manually inspect a generated `.seed.dot` for a non-trivial fixture mapper; confirm `∞` weights, `SEED` kind labels, no codegen rendering
