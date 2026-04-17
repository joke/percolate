## 1. Baseline and safety nets

- [x] 1.1 Verify the full Spock + Google Compile Testing suite is green on `main` before starting; record current number of passing tests as the baseline count
- [x] 1.2 Add a golden-output regression test harness that, for every existing processor fixture, captures the emitted `.java` file content and asserts byte-identical output across refactor commits (this is the regression contract from the proposal)
- [x] 1.3 Commit the harness on its own (conventional commit: `test(processor): pin golden output for existing mapper fixtures`) so refactor commits can be reverted without losing the safety net

## 2. Matching-layer types (`matching-model` capability)

- [x] 2.1 Create `processor/src/main/java/io/github/joke/percolate/processor/match/AssignmentOrigin.java` as an enum with exactly `EXPLICIT_MAP`, `AUTO_MAPPED`, `USING_ROUTED`
- [x] 2.2 Create `processor/src/main/java/io/github/joke/percolate/processor/match/MappingAssignment.java` as a Lombok `@Value` (or record) carrying `List<String> sourcePath`, `String targetName`, `Map<MapOptKey, String> options`, `@Nullable String using`, `AssignmentOrigin origin`
- [x] 2.3 Create `processor/src/main/java/io/github/joke/percolate/processor/match/MethodMatching.java` carrying `ExecutableElement method`, `MappingMethodModel model`, `List<MappingAssignment> assignments`
- [x] 2.4 Create `processor/src/main/java/io/github/joke/percolate/processor/match/MatchedModel.java` carrying `TypeElement mapperType`, `List<MethodMatching> methods`
- [x] 2.5 Write Spock unit tests for the records (value equality, null-safety of `using`, normalisation of empty `using` string to `null`)

## 3. `MatchMappingsStage` — extracts matching from `BuildGraphStage`

- [x] 3.1 Create `processor/src/main/java/io/github/joke/percolate/processor/stage/MatchMappingsStage.java` Dagger-injected with access to `Elements`/`Types`
- [x] 3.2 Move the `@Map` / `@MapList` parsing + auto-mapping decision logic from `BuildGraphStage` into `MatchMappingsStage`, emitting `MappingAssignment` records (ordering: explicit-then-auto, per spec)
- [x] 3.3 Ensure the stage does NOT call property discovery for types — name-level matching only
- [x] 3.4 Write Spock tests covering: explicit `@Map` → `EXPLICIT_MAP`, auto-mapping gap fill → `AUTO_MAPPED`, `@Map(using=...)` → `USING_ROUTED`, `@MapOpt` collection into options, empty `using` normalisation

## 4. `ValidateMatchingStage` — matching-layer validation

- [x] 4.1 Create `processor/src/main/java/io/github/joke/percolate/processor/stage/ValidateMatchingStage.java`
- [x] 4.2 Port duplicate-target detection from `ValidateTransformsStage`, rewriting against `MatchedModel`
- [x] 4.3 Implement unknown source-root parameter detection ("Source parameter 'X' not found on method ...")
- [x] 4.4 Implement unresolved `using=` method detection ("Helper method 'X' not found on mapper ...") including fuzzy-match suggestions
- [x] 4.5 Write Spock tests for each diagnostic, asserting message format and `Element` target

## 5. Graph primitives (`value-graph` capability) — sealed types only

- [x] 5.1 Create `processor/src/main/java/io/github/joke/percolate/processor/graph/ValueNode.java` as a sealed interface permitting `SourceParamNode`, `PropertyNode`, `TypedValueNode`, `TargetSlotNode`; require `TypeMirror type()`
- [x] 5.2 Create `SourceParamNode`, `PropertyNode`, `TypedValueNode`, `TargetSlotNode` — each Lombok `@Value` — with the fields specified in `value-graph/spec.md`
- [x] 5.3 Create `processor/src/main/java/io/github/joke/percolate/processor/graph/LiftKind.java` enum with exactly `NULL_CHECK`, `OPTIONAL`, `STREAM`, `COLLECTION`
- [x] 5.4 Create `processor/src/main/java/io/github/joke/percolate/processor/graph/ValueEdge.java` as a sealed interface permitting `PropertyReadEdge`, `TypeTransformEdge`, `NullWidenEdge`, `LiftEdge`
- [x] 5.5 Create `PropertyReadEdge` (no fields — derives from target `PropertyNode.getReadAccessor()`)
- [x] 5.6 Create `TypeTransformEdge` carrying `TypeTransformStrategy strategy`, `TypeMirror input`, `TypeMirror output`, `@Nullable CodeTemplate codeTemplate` (mutable single-assignment)
- [x] 5.7 Create `NullWidenEdge` (no-op edge type; nullness fields wait for `jspecify-nullability`)
- [x] 5.8 Create `LiftEdge` carrying `LiftKind kind`, `GraphPath<ValueNode, ValueEdge> innerPath`, `@Nullable CodeTemplate codeTemplate`
- [x] 5.9 Write Spock tests: sealed-subtype exhaustiveness at compile time (a rejected fifth-subtype fixture under `compile-testing`), node equality semantics (`TypedValueNode` dedup by type), graph invariants (DAG, target-slot leaves)

## 6. `BuildValueGraphStage` — the heart of the refactor

- [x] 6.1 Create `processor/src/main/java/io/github/joke/percolate/processor/stage/BuildValueGraphStage.java` Dagger-injected with `SourcePropertyDiscovery`, `TargetPropertyDiscovery`, all `TypeTransformStrategy`s
- [x] 6.2 Implement source-parameter node creation (one `SourceParamNode` per method parameter)
- [x] 6.3 Implement access-chain walking: for each `MappingAssignment.sourcePath`, reuse or create `PropertyNode`s via `SourcePropertyDiscovery`, connect via `PropertyReadEdge`s, mark missing segments as resolution-pending on the `MethodMatching` (do not abort)
- [x] 6.4 Implement target-slot creation via `TargetPropertyDiscovery`, adding one `TargetSlotNode` per `MappingAssignment`
- [x] 6.5 Implement the strategy edge-proposal fixpoint loop with the 30-iteration budget (moved from `ResolveTransformsStage`)
- [x] 6.6 Assert invariants listed in `value-graph/spec.md` (DAG, target-slot leaves, `LiftEdge.innerPath` edges ∈ parent graph) — fail with `IllegalStateException` on violation
- [x] 6.7 Write Spock tests covering: flat property, nested chain with shared `customer` node, multi-edge container path through `TypedValueNode`s, unresolvable segment handling

## 7. `TransformProposal` reshape + `TypeTransformStrategy` updates

- [x] 7.1 Remove `elementConstraint` field from `TransformProposal`; remove the constructor overload that accepts it
- [x] 7.2 Remove `templateComposer` field from `TransformProposal`; remove the constructor overload that accepts it
- [x] 7.3 Update `OptionalMapStrategy` to contribute a `LiftEdge(OPTIONAL, innerPath)` via `BuildValueGraphStage` instead of returning a `TransformProposal` with a `templateComposer`
- [x] 7.4 Update `OptionalWrapStrategy` to propose only `NON_NULL T → Optional<T>` via `Optional.of(...)`; the `NULLABLE T → Optional<T>` case is still emitted as `Optional.of(...)` in this refactor (identical to today) — the `ofNullable` fix lands in `jspecify-nullability` via `LiftEdge(NULL_CHECK)` fusion
- [x] 7.5 Update container strategies (`CollectToListStrategy`, `CollectToSetStrategy`, `StreamFromCollectionStrategy`, etc.) to contribute `LiftEdge(COLLECTION, ...)` / `LiftEdge(STREAM, ...)` where they previously used `templateComposer`
- [x] 7.6 Update all strategy tests to assert the new edge-contribution shape
- [x] 7.7 Grep for any remaining reference to `ElementConstraint` in the processor module; delete the `ElementConstraint` class once unreferenced

## 8. `ResolvePathStage` — shortest-path search only

- [x] 8.1 Create `processor/src/main/java/io/github/joke/percolate/processor/stage/ResolvePathStage.java`
- [x] 8.2 Implement the per-assignment `BFSShortestPath` loop over the per-method `ValueGraph` from `SourceParamNode` to `TargetSlotNode`
- [x] 8.3 Create `ResolvedAssignment` record carrying `MappingAssignment assignment`, `@Nullable GraphPath<ValueNode, ValueEdge> path`, `@Nullable ResolutionFailure failure` (migrated from `AccessResolutionFailure`) and helper `getReadChainEdges()`
- [x] 8.4 Move `ResolutionFailure` / `AccessResolutionFailure` types next to `ResolvedAssignment`; prune fields they no longer need
- [x] 8.5 Remove all template-materialisation calls from this stage — `codeTemplate` must still be `null` on exit for on-path edges
- [x] 8.6 Write Spock tests: flat assignment path shape, nested chain through shared `PropertyNode`, container multi-edge path, failed-resolution null path with failure context

## 9. `OptimizePathStage` — template materialisation

- [x] 9.1 Create `processor/src/main/java/io/github/joke/percolate/processor/stage/OptimizePathStage.java`
- [x] 9.2 Implement template materialisation: for every `TypeTransformEdge` on a resolved path, call the strategy's template-resolution function and set `codeTemplate` exactly once
- [x] 9.3 Implement `LiftEdge` template derivation: recursively materialise `innerPath` templates, then compose per `LiftKind` (`OPTIONAL` → `.map(x -> ...)`, `STREAM` → `.map(x -> ...)`, `COLLECTION` → loop)
- [x] 9.4 Ensure off-path edges never have their template materialised (Spock test: produce graph with N on-path + M off-path edges; assert M edges have `codeTemplate == null` post-optimize)
- [x] 9.5 Add defensive `IllegalStateException` on template-resolution failure (internal bug, not user diagnostic)
- [x] 9.6 Retire any `TransformEdge.resolveTemplate()` / lazy-mutation path from the codebase — grep confirms no callers

## 10. `ValidateResolutionStage` — resolution-layer validation

- [x] 10.1 Create `processor/src/main/java/io/github/joke/percolate/processor/stage/ValidateResolutionStage.java`
- [x] 10.2 Port unresolved-type-gap diagnostics from `ValidateTransformsStage`, rewriting against `ResolvedAssignment`
- [x] 10.3 Port unresolved-property-access diagnostics (reading from the resolution-pending markers on `MethodMatching`), preserving the "Did you mean:" fuzzy-match logic
- [x] 10.4 Port unmapped-target-property diagnostics
- [x] 10.5 Remove duplicate-target-mapping diagnostics from this stage (that detection moved to `ValidateMatchingStage`)
- [x] 10.6 Write Spock tests for each diagnostic path with exact message format assertions

## 11. Debug dump stages

- [x] 11.1 Rewrite `DumpPropertyGraphStage` as `DumpValueGraphStage` — dumps `Map<MethodMatching, ValueGraph>` to the debug DOT/JSON file per `ProcessorOptions`
- [x] 11.2 Rewrite `DumpTransformGraphStage` + `DumpResolvedOverlayStage` as a single `DumpResolvedPathsStage` — dumps the optimized `ResolvedAssignment`s with winning paths overlaid on the per-method `ValueGraph`
- [x] 11.3 Update `debug-graph-export` fixtures / golden files to reflect the new dump shapes (this is the one place golden-output is expected to change; document the diff in the PR)
- [x] 11.4 Ensure debug stages fail-soft (log via `Messager`, continue pipeline) per the pipeline-stages spec

## 12. `Pipeline` rewiring

- [x] 12.1 Update `Pipeline.java` to inject the 10 stages (8 real + 2 debug) in the order specified by `pipeline-stages/spec.md`
- [x] 12.2 Add early-exit-on-failure checks between each real stage; debug dumps fire unconditionally and fail-soft
- [x] 12.3 Update Dagger `ProcessorModule` bindings for the new stages (no-op: all stages use constructor injection; module only provides `Elements`/`Types`/`Messager`/`Filer`/`ProcessorOptions`)
- [x] 12.4 Delete `Pipeline` references to removed stages (`BuildGraphStage`, `ResolveTransformsStage`, `ValidateTransformsStage`) and their old dump-stage neighbours
- [x] 12.5 Write a Spock integration test that asserts the 10-stage order by spying on the `Pipeline` invocation sequence

## 13. `GenerateStage` switching

- [x] 13.1 Convert `GenerateStage`'s edge dispatch to an exhaustive switch on `ValueEdge` sealed subtypes — no `instanceof` ladder, no default branch (implemented via `ValueEdgeVisitor` because Java 11 `release` precludes sealed types + pattern switch; visitor enforces compile-time exhaustiveness)
- [x] 13.2 Ensure `GenerateStage` reads `edge.getCodeTemplate()` only — no template-resolution calls remain
- [x] 13.3 Implement `LiftEdge` emission for `OPTIONAL` / `STREAM` / `COLLECTION` from the already-materialised `codeTemplate`
- [x] 13.4 Panic (assertion / `IllegalStateException`) if a `NullWidenEdge` or `LiftEdge(NULL_CHECK)` is encountered — those are not constructed by this refactor
- [x] 13.5 Run the full Spock suite + golden-output harness from task 1.2; every existing fixture must produce byte-identical output (regressions resolved: DAG-cycle fixed via source→target vertex sorting + reverse-direction pending-lift dedup + post-fixpoint cycle guard in `BuildValueGraphStage`; diagnostic drift fixed by wiring `ambiguousMethodCandidates` and `dateFormatOnNonStringMapping` into `ValidateResolutionStage` and updating the `using=` diagnostic in `ValidateMatchingStage`)

## 14. Removal of the old types and stages

- [x] 14.1 Delete `BuildGraphStage`, `ResolveTransformsStage`, `ValidateTransformsStage` once all callers are retired
- [x] 14.2 Delete `MappingGraph` (`DefaultDirectedGraph<Object, Object>`), `SourceRootNode`, `SourcePropertyNode`, `TargetRootNode`, `TargetPropertyNode`, `AccessEdge`, `MappingEdge`
- [x] 14.3 Delete the pre-refactor per-mapping `TypeNode` / `TransformEdge` graph types (if any remain distinct from the new `ValueGraph` types)
- [x] 14.4 Delete `ResolvedModel` / `ResolvedMapping` once replaced by `Map<MethodMatching, List<ResolvedAssignment>>` / `ResolvedAssignment`
- [x] 14.5 Delete `TransformResolution` unless retained as thin debug-accessor record (design OQ2 decision)
- [x] 14.6 Grep confirms zero references to any deleted class in the processor module

## 15. Spec archival

- [ ] 15.1 Remove `openspec/specs/symbolic-property-graph/` entirely (captured by `matching-model` + `value-graph`)
- [ ] 15.2 Remove `openspec/specs/mapping-graph/` entirely
- [ ] 15.3 Apply the `pipeline-stages`, `transform-resolution`, `type-transform-strategy`, `transform-validation` delta specs onto the base specs (using `opsx:archive` flow) so `openspec/specs/` reflects post-refactor truth
- [ ] 15.4 Create the new spec folders `openspec/specs/matching-model/`, `openspec/specs/value-graph/`, `openspec/specs/path-optimization/` from the change's ADDED requirements

## 16. Verification and sign-off

- [x] 16.1 Run full Spock + Google Compile Testing suite; expect test count ≥ baseline from 1.1 (230 unit + 34 integration = 264 total, above the 215 baseline)
- [x] 16.2 Run the golden-output harness — every existing fixture must produce byte-identical output (debug-dump fixtures from 11.3 excepted)
- [x] 16.3 Verify `OptionalWrapStrategy` still emits `Optional.of(...)` for currently-tested inputs (the `Optional.ofNullable(...)` fix lands with `jspecify-nullability`, not here)
- [x] 16.4 `rtk ./gradlew :processor:check` passes with zero warnings escalated to errors (NullAway, ErrorProne)
- [x] 16.5 `rtk openspec validate value-graph-refactor` passes
- [ ] 16.6 PR description links back to this change, highlights the no-feature / refactor-only nature, and flags the debug-dump golden diff as the only intentional output change
