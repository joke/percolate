## Context

The full demand-driven graph expansion refactor spans roughly 70 coordinated tasks across five concerns: unifying three discovery SPIs, rewriting `BuildValueGraphStage` as target-seeded worklist, introducing a mapper-level graph with per-method partitions, adding `@Routable` routing, and retiring the `ReadAccessor` / `WriteAccessor` model. That rewrite cannot land atomically — the pipeline must stay green between commits.

This design documents the **pre-wiring slice** we deliberately carved out: the new type surface, with no behavioural changes. Every follow-up slice builds on these types.

## Scope Decision

**In scope (additive surface only):**

- `ValueExpansionStrategy` SPI interface + its value types (`ExpansionDemand`, `Subgraph`, `ExpansionContext`, `DemandKind`).
- `TargetRootNode` as the fifth `ValueNode` subtype.
- `MapperGraph` / `VertexPartition` records for the eventual mapper-level graph shape.
- `TargetSlotNode.paramIndex` mutable field (via `@Setter`).
- `@Routable` annotation on the `annotations` module.
- `DiagnosticFormatter` helper.

**Out of scope (moves to follow-up slices):**

- Any change to `BuildValueGraphStage`, `ValidateResolutionStage`, `GenerateStage`, or `DumpGraphStage`.
- Porting any existing strategy onto the new SPI.
- `RoutableIndex` construction, `RoutableMethodStrategy`, or retiring `MethodCallStrategy`.
- Retiring `ReadAccessor` / `WriteAccessor` classes.
- Re-pinning golden fixtures.

## Key Decisions

### TargetRootNode.compose orders inputs by iteration order, not by slot lookup

`TargetRootNode.compose(Map<ValueEdge, CodeBlock>, ComposeKind)` receives a map but does not have access to the graph and cannot look up "which edge came from which slot." The simplest contract is to assume the caller inserts inputs in slot-declaration order.

**Implication:** `GenerateStage`, when it eventually calls `compose` on a `TargetRootNode`, MUST iterate the incoming edges in the order prescribed by `targetRootNode.getSlots()` rather than in arbitrary JGraphT edge order. This is a constraint on the future `GenerateStage` rewrite, not on this slice. Callers that pass an empty input map get `new T()`.

### TargetSlotNode keeps its WriteAccessor reference for now

Task 2.2 in the original full-scope plan demanded dropping the `WriteAccessor` field. Doing that in this slice would break every call site — `BuildValueGraphStage`, `GenerateStage`, and roughly a dozen test specs all read `slot.getWriteAccessor()`. Those call sites disappear in the accessor-model retirement slice alongside a `GenerateStage` rewrite; removing the field here would force that slice to land simultaneously.

**Decision:** Retain the `WriteAccessor` field. Add `paramIndex` via `@Setter` as an additive change. Document the deferral in the slot's javadoc and in the mapper-graph delta spec.

### TargetRootNode equality keyed on type string, not mapper identity

`TargetRootNode` uses `@EqualsAndHashCode(onlyExplicitlyIncluded = true)` with `@EqualsAndHashCode.Include String typeString`. Two methods that return the same type will share one `TargetRootNode` vertex in a future `MapperGraph`, matching JGraphT's `.equals()`-based vertex identity.

**Rationale:** The mapper-graph spec already envisions shared `TypedValueNode`s across methods; applying the same policy to `TargetRootNode` keeps vertex identity consistent. Per-method distinctness is carried by `VertexPartition`, not by vertex identity.

### ExpansionContext carries a RoutableIndex up-front

Even though `@Routable` discovery is out of scope for this slice, `ExpansionContext` already carries a `routableIndex: Map<RoutableKey, ExecutableElement>` field. Deferring the field would force every call site in the follow-up `@Routable` wiring slice to be updated; exposing it now as an empty map keeps the record shape stable.

### Mutable fields on graph nodes

`TargetRootNode.slots` and `TargetSlotNode.paramIndex` are mutable. This is deliberate: `ConstructorCallStrategy` (in the follow-up slice) needs to register slots on a root it didn't create, and paramIndex is discovered during expansion rather than at slot construction. Making these fields `final` would force a constructor-time handshake that contradicts the demand-driven expansion flow.

The node types remain `final` classes, so reflective subclassing cannot bypass the mutation pattern. Spock tests asserting node identity will compare by `.equals()` on the explicitly-included fields, which excludes the mutable ones.

## Risks

- **Dead code warnings:** Some of the new types (`Subgraph`, `ExpansionContext`, `DiagnosticFormatter`) have zero call sites until the follow-up slice wires them in. NullAway + ErrorProne are currently happy; if any "unused" warnings arise on a later toolchain bump, they SHALL be suppressed at the type with a `// TODO: wired in <slice-name>` comment — or preferably the next slice lands fast enough to keep the window short.
- **Interface signature drift:** If the follow-up slices discover that `expand(ExpansionDemand, ExpansionContext)` is the wrong shape (e.g. needs a callback for cycle-guard queries), the interface will change in a breaking way. That is acceptable for an internal SPI; third-party strategy authors have been notified via the proposal that the SPI is stabilising.
