## Why

Phase 1 (`add-seed-graph-and-debug-dump`) shipped a graph schema sized for directive seeds only. Phase 2 (expansion) needs the same schema to carry realised edges, marker edges, sub-directives, group coordination, codegen closures, strategy provenance, and phantom container element nodes. Bolting these on incrementally inside Phase 2 would force schema-touching work in every expansion change. This alignment change ships the foundation in one step so Phase 2 work is purely additive: new stages, new edges, no schema churn.

## What Changes

**Edge schema additions** (`graph-model`):
- New `EdgeKind` enum with four values: `SEED`, `REALISED`, `MARKER`, `SUB_SEED`. Single `Edge` class carries `kind` as a field — no subclass hierarchy. Factory methods (`Edge.seed(...)`, `Edge.realised(...)`, `Edge.marker(...)`, `Edge.subSeed(...)`) enforce per-kind invariants at construction.
- New `groupId: Optional<String>` field on `Edge` for constructor / builder coordination.
- New `codegen: Optional<EdgeCodegen>` field on `Edge` carrying a closure that renders this edge's code fragment at codegen time. Excluded from `equals` / `hashCode` (structural equality only).
- New `strategyClassFqn: Optional<String>` field on `Edge` recording which strategy emitted it. Pure metadata; used for DOT labels, tiebreaking, diagnostics. Excluded from `equals` / `hashCode`.
- New `EdgeCodegen` interface: `CodeBlock render(VarNames vars, IncomingValues inputs)`.
- New `IncomingValues` interface with `single()`, `byGroupPosition(int)`, `byName(String)` accessors so constructor / builder edges can fetch their upstream inputs.
- New `VarNames` type holding the names of the local variables in the codegen scope.

**Node schema additions** (`graph-model`):
- New `parent: Optional<Node>` field on `Node`. Set only on phantom container element nodes; empty on every other node. Carries the live ref used by `Node.id()` derivation and DOT cluster grouping.
- `Location` interface gains a `segment()` method returning that location's per-segment contribution to id derivation. `Node.id()` becomes the sole owner of identity assembly.
- New `ElementLocation` `Location` subtype — a marker with no payload. Phantom container element nodes carry it; the parent ref lives on `Node.parent`, not in the location.

**MapperGraph additions** (`graph-model`):
- New `realisedSubgraph(): RealisedSubgraph` method returning a thin wrapper view filtered to non-`SEED`, non-`MARKER` edges. Same sorted-iteration contract as `MapperGraph` (`nodes()`, `edges()`, `nodesByScope(Scope)`).
- New group-closure storage `addGroupCodegen(groupId, GroupCodegen)` / `groupCodegen(groupId)` for constructor / builder coordination. `GroupCodegen` interface assembles a single expression from the group's incoming inputs.
- No removal API. New surface only.

**Weight scale** (`graph-model`):
- New `Weights` constants type:
  - `Weights.SENTINEL_UNREALISED = Integer.MAX_VALUE / 2` for `SEED` and `SUB_SEED` edges.
  - Realised-scale constants `Weights.NOOP = 0`, `Weights.STEP = 1`, `Weights.COPY = 2`, `Weights.EXPENSIVE = 3` documenting the agreed scale (notes §7).

**SeedGraph behaviour changes** (`seed-graph`):
- **BREAKING**: directive-seeded edges now use `weight = Weights.SENTINEL_UNREALISED` and `kind = EdgeKind.SEED` (previously `weight = 1`, no kind). The forest invariant continues to hold because no realised edges are added in this change.
- `SeedGraph` emits no `REALISED`, `MARKER`, or `SUB_SEED` edges in this change. Tests pin this — Phase 1 stays behaviour-stable.

**DOT renderer changes** (`graph-debug-output`):
- Edge style is now keyed off `EdgeKind` (colour / line-style table). `SEED` edges render as before; `REALISED`, `MARKER`, `SUB_SEED` get distinct visual styles for when they appear in Phase 2 — no edges of those kinds are emitted yet.
- Edge labels include `strategyClassFqn` when present (provenance metadata for diagnostics / debugging).
- Phantom container element nodes (when emitted in future phases) render inside their parent's cluster via `Node.parent`. No phantom nodes are emitted yet; the renderer is ready for them.
- Sentinel weight `∞` renders as the literal `∞` in edge labels.

**Strategy SPI groundwork** (no new capability — just type surface in `graph-model`):
- `EdgeCodegen`, `IncomingValues`, `VarNames`, `GroupCodegen` interfaces ship in this change. No `ExpansionStrategy` interface is shipped — that lands in Phase 2. The codegen interfaces ship now because `Edge.codegen` references `EdgeCodegen`.

**Out of scope (deferred to Phase 2):**
- The `ExpansionStrategy` SPI itself.
- Any expansion stage / driver / work queue.
- Phantom node *emission* (the schema supports them; no strategy creates them yet).
- Tier-2 walk via marker edges (the schema supports it; no checker runs yet).
- All concrete strategies (GetterRead, SetterWrite, ConstructorCall, BuilderCall, OptionalWrap, ContainerKind, MethodCall, etc.).
- The uniform `ServiceLoader` + AutoService strategy registration mechanism (decided, but lands with the SPI in Phase 2).

## Capabilities

### New Capabilities

None. This change is foundation-only — every addition extends an existing capability.

### Modified Capabilities

- `graph-model`: adds `EdgeKind`, `groupId`, `codegen`, `strategyClassFqn` to `Edge`; adds `parent` to `Node`; adds `Location.segment()` and `ElementLocation`; adds `Weights` constants; adds `realisedSubgraph()` view and group-closure storage on `MapperGraph`; adds `EdgeCodegen` / `IncomingValues` / `VarNames` / `GroupCodegen` SPI surface. Changes `Edge.equals` / `hashCode` to exclude `codegen` and `strategyClassFqn`.
- `seed-graph`: directive seed edges now carry `kind = SEED` and `weight = Weights.SENTINEL_UNREALISED`. Forest invariant preserved.
- `graph-debug-output`: DOT renderer gets kind-aware styling, strategy-FQN labels, sentinel-weight rendering, and parent-aware phantom cluster grouping (no phantoms emitted yet).

## Impact

**Affected code:**
- `io.github.joke.percolate.processor.graph` — `Node`, `Edge`, `Location`, `MapperGraph`, plus new types `EdgeKind`, `ElementLocation`, `Weights`, `EdgeCodegen`, `IncomingValues`, `VarNames`, `GroupCodegen`, `RealisedSubgraph`.
- `io.github.joke.percolate.processor.SeedGraph` — emit edges with `kind = SEED` and sentinel weight.
- `io.github.joke.percolate.processor.graph.DotRenderer` — kind-aware styling, strategy-FQN labels, sentinel-weight rendering, phantom-cluster grouping.
- Tests: `MapperGraph` / `Edge` / `Node` unit specs updated for the new fields and equality contract; `SeedGraph` specs updated for the new weight and kind; DOT golden specs updated for sentinel + kind styling.

**Affected APIs:** the `graph` package is internal to the processor; no external API change. `Edge` and `Node` are `@Value` types (immutable); existing call sites that rely on positional construction will need updates for the new fields. New fields default to `Optional.empty()` / `EdgeKind.SEED` via factory methods, keeping migrations contained.

**Dependencies:** no new external dependencies. Continues to use Lombok, JGraphT 1.5.2, JavaPoet 0.12.0, `javax.annotation.processing` types. AutoService is *not* added in this change — it lands with the SPI in Phase 2.

**Affected teams:** processor maintainers (the only consumers of the `graph` package today).

**Migration:** seed-graph's golden DOT outputs change (weight `1` → `∞`, kind label added). The Phase 1 `add-seed-graph-and-debug-dump` change is already archived; this delta updates the active spec under that capability.
