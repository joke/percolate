## Why

The processor's expansion model has three near-parallel SPIs (`SourcePropertyDiscovery`, `TargetPropertyDiscovery`, `TypeTransformStrategy`) that ultimately answer the same question: "what can I reach from here?" Unifying them into a single `ValueExpansionStrategy` is a large, coordinated refactor that touches every stage, every strategy, and every golden fixture. That full rewrite cannot land in one commit without going red mid-way.

This change lands the **pre-wiring slice**: the new SPI surface, the compositional graph model types, the `@Routable` annotation, and a diagnostic helper. Nothing is wired into `BuildValueGraphStage` yet; the existing pipeline continues to work unchanged. Subsequent change proposals will port individual strategies, rewrite `BuildValueGraphStage` as demand-driven, and dissolve `ValidateResolutionStage`.

## What Changes

- **Introduce `ValueExpansionStrategy` SPI.** A new interface `ValueExpansionStrategy` with `priority()` and `expand(ExpansionDemand, ExpansionContext)` returning `Optional<Subgraph>`. Accompanied by:
  - `DemandKind` enum: `PROPERTY_READ`, `TYPE_TRANSFORM`, `TARGET_SLOT`, `ROOT_CONSTRUCTION`.
  - `ExpansionDemand(requester, demand, kind)` record.
  - `Subgraph(vertices, edges, entry, exit)` record.
  - `ExpansionContext` record carrying `Types`, `Elements`, mapper/method elements, mapping options, and a `RoutableIndex` (`Map<RoutableKey, ExecutableElement>`).
- **Introduce compositional graph model types.**
  - `TargetRootNode` — fifth `ValueNode` subtype, representing the constructed target value of a mapper method. Holds a mutable ordered `List<TargetSlotNode>` populated by the eventual `ConstructorCallStrategy`. `compose(EXPRESSION)` emits `new $T(...)` from its inputs.
  - `MapperGraph(Graph<ValueNode, ValueEdge>, Map<MethodMatching, VertexPartition>)` — mapper-level graph record for the eventual per-mapper graph shape.
  - `VertexPartition(sourceParam, targetRoot, methodVertices)` — per-method partition record.
  - `TargetSlotNode` gains a mutable `paramIndex: int` field (via `@Setter`) for the eventual `ConstructorCallStrategy` wiring. Its legacy `WriteAccessor` reference remains until the downstream slice that removes it.
- **Add `@Routable` annotation.** New public-API annotation on the `annotations` module: `@Retention(CLASS) @Target(METHOD) @interface Routable {}`. Not yet consulted by any processor stage.
- **Add `DiagnosticFormatter` helper.** Utility class with `noPathFound(...)` and `missingProperty(...)` producing `Diagnostic` messages. Injected via Dagger, ready for `BuildValueGraphStage` to use once demand-driven expansion lands.
- **No behavioural change.** `BuildValueGraphStage`, `ValidateResolutionStage`, strategy discovery, and generated output are unchanged. No existing tests are rehomed; no golden fixtures drift.

## Capabilities

### New Capabilities

- `value-expansion`: the `ValueExpansionStrategy` SPI types — interface, demand, subgraph, demand-kind enum, and context.
- `mapper-graph`: mapper-level graph model types — `MapperGraph`, `VertexPartition`, and the new `TargetRootNode` subtype.
- `routable-routing`: the `@Routable` annotation definition.

## Impact

- **Affected modules**: `annotations` (new `@Routable`), `processor` (new SPI, records, and helper classes). All additions; no existing code paths modified except `TargetSlotNode` gaining one mutable field.
- **Affected SPIs**: none of the existing SPIs (`SourcePropertyDiscovery`, `TargetPropertyDiscovery`, `TypeTransformStrategy`, `ConstructorDiscovery`, `MethodCallStrategy`) are retired yet; they continue to work as before.
- **Affected stages**: none — the new types are available as library surface but not yet consumed.
- **Affected tests**: none retired or rehomed. New specs MAY be added per-type in follow-up slices when wiring lands.
- **Affected dependencies**: unchanged (JGraphT 1.5.2, JavaPoet 0.12.0, Dagger 2.59.1, Lombok, JSpecify).
- **Risk**: low. Pure type/surface addition; no behavioural change to the existing pipeline.

## Follow-up Scope (Not in This Change)

The following slices will each be proposed separately once this foundation is in place:

1. **Strategy ports** — port `GetterDiscovery`, `FieldDiscovery.Source`, and each `TypeTransformStrategy` impl to `ValueExpansionStrategy`; introduce `ConstructorCallStrategy` handling `ROOT_CONSTRUCTION`.
2. **`@Routable` wiring** — `AnalyzeStage` builds a `RoutableIndex` per mapper; `RoutableMethodStrategy` handles `TYPE_TRANSFORM` via the index; `MethodCallStrategy` retired.
3. **Demand-driven `BuildValueGraphStage`** — replace the fan-out fixpoint with a target-seeded worklist; dissolve `ValidateResolutionStage`; surface diagnostics via `DiagnosticFormatter`; shift pipeline from eight stages to seven.
4. **Accessor model retirement** — drop `WriteAccessor` / `ReadAccessor` and their subclasses; switch `GenerateStage` to iterate per-method `VertexPartition`s within the mapper-level graph; rehome strategy specs; re-pin golden fixtures.
