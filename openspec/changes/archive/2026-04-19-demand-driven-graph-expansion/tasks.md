## 1. New SPI surface

- [x] 1.1 Add `ValueExpansionStrategy` interface with `priority()` and `expand(ExpansionDemand, ExpansionContext)`
- [x] 1.2 Add `DemandKind` enum (`PROPERTY_READ`, `TYPE_TRANSFORM`, `TARGET_SLOT`, `ROOT_CONSTRUCTION`)
- [x] 1.3 Add `ExpansionDemand` record (`requester`, `demand`, `kind`)
- [x] 1.4 Add `ExpansionContext` exposing `Types`, `Elements`, mapper/method elements, mapping options, and a `RoutableIndex`
- [x] 1.5 Add `Subgraph` record (`vertices`, `edges`, `entry`, `exit`)
- [x] 1.6 Add `@Routable` annotation (`@Retention(CLASS)`, `@Target(METHOD)`) to the `annotations` module
- [x] 1.7 Add `DiagnosticFormatter` helper class with `noPathFound(...)` and `missingProperty(...)` methods

## 2. Graph model types

- [x] 2.1 Add `TargetRootNode` as fifth permitted `ValueNode` subtype (fields: `type`, `slots: List<TargetSlotNode>`; `compose(EXPRESSION)` emits `new $T(...)`)
- [x] 2.2 Add mutable `paramIndex: int` field to `TargetSlotNode` (WriteAccessor removal deferred to the accessor-model retirement slice)
- [x] 2.3 Add `MapperGraph` record `(Graph<ValueNode, ValueEdge>, Map<MethodMatching, VertexPartition>)`
- [x] 2.4 Add `VertexPartition` record `(sourceParam, targetRoot, methodVertices)`

## 3. Verification

- [x] 3.1 `rtk ./gradlew :processor:compileJava` succeeds (all new types compile under NullAway + ErrorProne)
- [x] 3.2 `rtk ./gradlew :processor:test` succeeds (no existing tests regress; new types are surface-only with no wiring)
