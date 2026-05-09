## Why

The processor currently realises directives via getter chains, constructor
calls, and direct assignment, but it has no mechanism to use one mapping
method as a building block of another. A user authoring a `@Mapper` with
`HumanAddress mapAddress(Address)` cannot have their `Human map(Person)`
method automatically delegate `Person.address → HumanAddress` through
`mapAddress` — the strategy machinery cannot see sibling methods, and
cannot chain conversions through intermediate types.

This change adds method-call bridges and the underlying graph-iteration
infrastructure they need. The infrastructure is the load-bearing piece:
the iterative target-driven expansion, intermediate-node materialisation,
and outer fixed-point loop introduced here are the same primitives that
future container, conversion, and cross-mapper changes will reuse. By
shipping them now in service of a focused, user-visible feature, the
foundation is validated end-to-end before larger trajectory work begins.

## What Changes

- **BREAKING** `Bridge.bridge()` now returns `Stream<BridgeStep>` rather
  than `Optional<BridgeStep>`. A single `Bridge` query may produce
  multiple parallel candidates (e.g. one per overload, one per chain hop).
- **BREAKING** `BridgeStep` gains `inputType` and `outputType` fields. The
  driver uses these to materialise intermediate nodes when a bridge
  consumes or produces a type that is not the seed's endpoint type.
- **BREAKING** `ResolveCtx` is widened with three accessors: `mapperType()`,
  `currentMethod()`, and `callableMethods()`. Strategies that need the
  enclosing `@Mapper` interface or the discovery-built method index may
  reach them without a richer context type.
- New SPI types in `processor.spi`: `CallableMethods` (the index queried
  by strategies), `MethodCandidate` (a method paired with its receiver),
  `Receiver` (abstraction over `this` vs. future field-receiver),
  `ThisReceiver` (the only v1 implementation).
- New `Weights.METHOD` constant for the method-call weight band.
- New discovery stage `DiscoverCallableMethods` walks the current
  `@Mapper` interface's full linearisation, filters Object-inherited
  members and multi-parameter methods, and produces a `CallableMethods`
  index keyed by output type.
- New built-in strategy `MethodCallBridge` in `processor.spi.builtins`
  emits one `BridgeStep` per matching method, weighted by JLS-style
  parameter / return specificity distance.
- `ExpandStage` gains an outer fixed-point loop that re-runs its phase
  list until no phase reports a change. Existing cycle detection on
  `SEED + SUB_SEED` and the per-seed expansion budget guard termination.
- `BridgeSourceToTargetPhase` materialises intermediate nodes at
  `(scope, F.loc, step.outputType)` whenever the emitted step's
  `inputType` does not equal the seed's `from`-side type, and emits a
  `SUB_SEED` to drive the next iteration.

Out of scope (named so the boundary is explicit): multi-parameter method
calls, cross-mapper composition (`@Mapper(uses = …)`), static and
generic methods, container strategies, codegen, and chain-aware Tier-3
diagnostic enrichment.

## Capabilities

### New Capabilities

- `callable-method-discovery`: walks a `@Mapper` interface's
  linearisation, filters method candidates eligible as conversion
  bridges, and exposes them through a `producing(TypeMirror)` index.

### Modified Capabilities

- `expansion-strategy-spi`: `Bridge.bridge()` returns `Stream<BridgeStep>`;
  `BridgeStep` carries `inputType` and `outputType`; `ResolveCtx` exposes
  `mapperType`, `currentMethod`, and `callableMethods`; `Weights.METHOD`
  joins the existing weight constants; `MethodCallBridge` ships as a
  built-in strategy.
- `graph-expansion`: `ExpandStage` runs its phase list under an outer
  fixed-point loop; `BridgeSourceToTargetPhase` materialises intermediate
  nodes and emits `SUB_SEED` edges when an emitted step's input type
  does not match the seed's from-node type.

## Impact

- Processor module: new packages / files in `processor.spi`,
  `processor.spi.builtins`, `processor.stages.discover`,
  `processor.stages.expand`. Existing `Bridge` implementor `DirectAssign`
  is updated for the new SPI signature.
- Annotation processing pipeline: one additional discovery stage runs per
  mapper round; expansion gains an outer loop whose worst-case bound is
  governed by the existing 100-per-seed budget.
- No changes to `@Map`, `@Mapper`, or any other public annotation surface.
- No build / dependency changes. Google `auto-service` is already on the
  classpath from the prior expansion change.
- Affected stakeholders: processor maintainers (this change), future
  authors of container / conversion / cross-mapper strategies (whose
  infrastructure prerequisites are unblocked here).
