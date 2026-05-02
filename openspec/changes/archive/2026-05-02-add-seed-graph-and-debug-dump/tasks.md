## 1. Graph value types

- [x] 1.1 Create new package `io.github.joke.percolate.processor.graph` with `package-info.java` (`@NullMarked`)
- [x] 1.2 Implement `AccessPath` Lombok `@Value` class wrapping `List<String>`, with `append(String)` factory and dot-joined `toString()`
- [x] 1.3 Implement `TargetPath` Lombok `@Value` class (parallel to `AccessPath`, distinct type)
- [x] 1.4 Implement `Location` interface with stable text-encoding contract
- [x] 1.5 Implement `SourceLocation` Lombok `@Value` (wraps `AccessPath`)
- [x] 1.6 Implement `TargetLocation` Lombok `@Value` (wraps `TargetPath`)
- [x] 1.7 Implement `Scope` interface with stable text-encoding contract
- [x] 1.8 Implement `MethodScope` Lombok `@Value` wrapping `ExecutableElement`; encoding includes method name + erased parameter types
- [x] 1.9 Implement `MapperScope` Lombok `@Value` (singleton or simple value); not used by seed stage but defined for forward-compat
- [x] 1.10 Implement `Node` Lombok `@Value` with `Optional<TypeMirror> type`, `Location loc`, `Scope scope`, deterministic `id()`, and `Comparable<Node>` natural order by `id()`
- [x] 1.11 Implement `Edge` Lombok `@Value` with `Node from`, `Node to`, `int weight`, `Optional<AnnotationMirror> directive`, and `Comparable<Edge>` ordered by `(from.id, to.id, weight, directive-presence)`

## 2. MapperGraph

- [x] 2.1 Implement `MapperGraph` wrapping `org.jgrapht.graph.DirectedMultigraph<Node, Edge>` with `addNode`, `addEdge`, sorted `nodes()`, sorted `edges()`, and `nodesByScope(Scope)`
- [x] 2.2 Make `addNode` idempotent on equal nodes; make `addEdge` reject structurally-equal duplicates
- [x] 2.3 Ensure no removal API is exposed
- [x] 2.4 Verify forest invariant via `org.jgrapht.GraphTests.isForest(...)` on an undirected view (used by tests; method to expose may be a package-private helper)

## 3. ProcessorOptions

- [x] 3.1 Implement `ProcessorOptions` Lombok `@Value` in `io.github.joke.percolate.processor` with `boolean debugGraphs`
- [x] 3.2 Add a static parser helper that takes `Map<String, String>` and returns a `ProcessorOptions`; case-insensitive `"true"` → `true`; anything else → `false`
- [x] 3.3 Add `@Provides ProcessorOptions provideProcessorOptions(ProcessingEnvironment)` to `ProcessorModule`
- [x] 3.4 Override `getSupportedOptions()` in `PercolateProcessor` to return `Set.of("percolate.debug.graphs")` (merged with any existing options if present)

## 4. ValidateSourceParameters

- [x] 4.1 Implement `ValidateSourceParameters` class in `io.github.joke.percolate.processor` with `@RequiredArgsConstructor(onConstructor_ = @Inject)` taking `Diagnostics`
- [x] 4.2 Implement `validate(MapperMappings)` that iterates each method and each directive, extracts the first segment of the source string, and checks it against the method's parameter names
- [x] 4.3 For each directive whose first source segment does not match any parameter, emit `Diagnostics.error(method, mirror, sourceValue, "unknown source parameter '<segment>' in @Map on <methodSig>\")`
- [x] 4.4 Ensure validation does not halt the pipeline (errors are emitted, processing continues)

## 5. SeedGraph stage

- [x] 5.1 Implement `SeedGraph` class in `io.github.joke.percolate.processor` with `@RequiredArgsConstructor(onConstructor_ = @Inject)`
- [x] 5.2 Implement `apply(MapperMappings) → MapperGraph` constructing a fresh `MapperGraph` per call
- [x] 5.3 For each `MethodMappings`, emit one parameter-root node per parameter (typed) and one return-type root node (typed)
- [x] 5.4 For each `MappingDirective`, split `source` on `.`, idempotently emit source nodes for each prefix, and emit edges from the parameter-root through the chain (each carrying the `@Map` mirror). The first segment MUST match a parameter name (validated by `ValidateSourceParameters` before this stage).
- [x] 5.5 For each `MappingDirective`, split `target` on `.`, idempotently emit target nodes for each prefix, and emit edges chaining inward-to-outward toward the return-type root (each carrying the `@Map` mirror)
- [x] 5.6 For each `MappingDirective`, emit one bridging edge from the deepest source node to the deepest target node (carrying the `@Map` mirror)
- [x] 5.7 Use weight `1` for every emitted edge

## 6. DOT renderer

- [x] 6.1 Implement `DotRenderer` in `io.github.joke.percolate.processor.graph` with `String render(MapperGraph, TypeElement)` API
- [x] 6.2 Render top-level digraph with a stable name derived from the `TypeElement` FQN
- [x] 6.3 Group nodes by `Scope` into `subgraph cluster_<encoding> { label="..."; ... }` blocks; sort clusters by scope encoding
- [x] 6.4 Render vertices in ascending `Node.id()` order
- [x] 6.5 Render edges in ascending natural `Edge` order
- [x] 6.6 Render each statement's attributes in ascending key order (TreeMap iteration)
- [x] 6.7 Apply distinct `shape` attributes for source-located vs target-located nodes; document the chosen shapes
- [x] 6.8 Apply a directive marker on directive-seeded edges (e.g., a `style` attribute and/or a `label` referencing the directive)
- [x] 6.9 Escape DOT-special characters (`"`, `\`, `\n`, `<`, `>`) in any string written into a quoted DOT context
- [x] 6.10 Emit single-`\n` line separators and a single trailing `\n`

## 7. DumpGraph stage

- [x] 7.1 Implement `DumpGraph` class in `io.github.joke.percolate.processor` with `@RequiredArgsConstructor(onConstructor_ = @Inject)` taking `Filer`, `Diagnostics`, `ProcessorOptions`, and `DotRenderer`
- [x] 7.2 Implement `apply(MapperGraph, TypeElement) → void` short-circuiting when `debugGraphs == false`
- [x] 7.3 Short-circuit when the `MapperGraph` has zero nodes and zero edges
- [x] 7.4 Compute file name as `<MapperFQN>.seed.dot`
- [x] 7.5 Create the resource via `Filer.createResource(StandardLocation.SOURCE_OUTPUT, "", fileName, originatingElement)` and write the DOT renderer's output
- [x] 7.6 On `IOException` (or any other write failure), emit a warning via `Diagnostics` referencing the `TypeElement` and return normally
- [x] 7.7 Add `Diagnostics.warning(String, Element)` (or equivalent) if not already present

## 8. Pipeline wiring

- [x] 8.1 Add `ValidateSourceParameters` to `Pipeline`'s constructor (Lombok `@RequiredArgsConstructor(onConstructor_ = @Inject)` will pick it up)
- [x] 8.2 In `Pipeline.process(TypeElement)`, after `ValidateNoDuplicateTargets`, call `ValidateSourceParameters.validate(...)` then `SeedGraph.apply(...)` then `DumpGraph.apply(graph, typeElement)`
- [x] 8.3 Ensure a fresh `MapperGraph` is constructed per call and not retained
- [x] 8.4 Confirm `Pipeline.process` continues to return `null`

## 9. Spock Groovy extension module

- [x] 9.1 Create `processor/src/test/groovy/io/github/joke/percolate/processor/graph/MapperGraphExtensions.groovy` with helper assertion methods (`scope(...)`, `hasNode(Map criteria)`, `hasEdge(Map criteria)`, `hasNoEdges()`, `nodesIn(Scope)`, etc.)
- [x] 9.2 Register the extension at `processor/src/test/resources/META-INF/services/org.codehaus.groovy.runtime.ExtensionModule`
- [x] 9.3 Verify production `MapperGraph` carries no test-only methods

## 10. Unit specs

- [x] 10.1 Add `SeedGraphSpec` covering empty mapper, parameter-root nodes, return-type root, source/target chains, dotted paths, shared-prefix idempotency, bridging edges, and forest invariant
- [x] 10.2 Add `DumpGraphSpec` covering option-off-no-write, option-on-writes-file, empty-graph-no-write, IOException-as-warning
- [x] 10.3 Add `ProcessorOptionsSpec` covering option-absent, option-true, option-TRUE, option-other-value
- [x] 10.4 Add `MapperGraphSpec` covering idempotency, duplicate-edge rejection, sorted iteration, scope filtering
- [x] 10.5 Add `DotRendererSpec` covering byte-stability, cluster emission, vertex order, edge order, attribute order, escape rules, shape distinction, directive marker
- [x] 10.6 Add value-type specs for `Node.id()`, `Edge` natural order, `AccessPath` / `TargetPath` immutability and `append(...)`, `Scope` encoding stability
- [x] 10.7 Add `ValidateSourceParametersSpec` covering matching source parameter (no error), single-segment mismatch (error), multi-segment mismatch (error), multiple directives (one error each), error points at source literal
- [x] 10.8 Update `PipelineSpec` to assert all six stages are invoked in order and the `MapperGraph` is threaded through

## 11. Single-aspect graph-shape specs

- [x] 11.1 Add `SeedParamBecomesSourceRootSpec`
- [x] 11.2 Add `SeedReturnTypeBecomesTargetRootSpec`
- [x] 11.3 Add `SeedDirectiveSeedsChainSpec`
- [x] 11.4 Add `SeedDottedSourceBecomesChainSpec`
- [x] 11.5 Add `SeedDottedTargetBecomesChainSpec`
- [x] 11.6 Add `SeedSharedPrefixIsDeduplicatedSpec`
- [x] 11.7 Add `SeedTwoMethodsOneMapperSpec`
- [x] 11.8 Add `SeedInheritedGenericMethodSpec`

## 12. Golden DOT specs

- [x] 12.1 Decide on golden re-generation workflow (`-PupdateGoldens=true` Gradle property or dedicated task) and document it in `processor/build.gradle` comments or a small README under `golden-graphs/`
- [x] 12.2 Add `processor/src/test/resources/golden-graphs/two-method-mapper.dot` and the corresponding spec (`OptionOnEmitsFileGoldenSpec`)
- [x] 12.3 Add `OptionOffEmitsNoFileGoldenSpec` (asserts no file is created when option is absent)
- [x] 12.4 Add `processor/src/test/resources/golden-graphs/escaped-labels.dot` and the corresponding spec (`DotEscapingGoldenSpec`)
- [x] 12.5 Add `processor/src/test/resources/golden-graphs/per-method-clusters.dot` and the corresponding spec (`PerMethodClustersGoldenSpec`)

## 13. Build and verification

- [x] 13.1 Run `./gradlew :processor:check` and verify all unit + integration specs pass
- [x] 13.2 Run `./gradlew :processor:build` to ensure errorprone (`-Werror`), NullAway, and palantir-java-format are clean
- [x] 13.3 Verify a manual smoke test by adding `-Apercolate.debug.graphs=true` to a sample compile and inspecting the produced `<MapperFQN>.seed.dot` file
