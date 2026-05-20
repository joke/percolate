## Why

Source paths in `@Map` directives stay untyped after `SeedGraph` runs (`src[person.addresses] : ?`), so container bridges (`SetMap`, `OptionalMap`, `OptionalUnwrap`) never have an iterable/optional candidate to fire against. The integration mapper `PersonMapper.mapHuman` cannot bridge `tgt[addresses] : Optional<Set<Human.Address>>` to its source: at frontier `Human.Address` the chain dead-ends because the only typed candidates are `Person` parameter roots, and target-side `mapAddress(Person.Address)` never connects.

The same untyped-source weakness produces a quieter bug for scalar fields: when multiple parameters share a type (two `Person` parameters), `GetterRead`-as-`Bridge` picks the first type-matching getter on whichever `Person` candidate sorts lowest by id, ignoring `@Map(source = "person.lastName")` vs. `@Map(source = "person2.first")`. The generated code compiles but reads from the wrong source.

Both bugs share one root: the seed chain encodes user intent (which parameter, which path) but the expansion engine never consults it. Source paths must be resolved at seed time, with the specific source root baked into the resulting subgraph so type-only candidate selection cannot lose the directive's intent.

## What Changes

- Introduce a new `PathSegmentResolver` SPI in `spi/`: pure value-type SPI that, given a parent `TypeMirror` and a path segment, returns an optional `ResolvedSegment(returnType, codegen, weight)`. No engine internals leak into the SPI.
- Ship three `@AutoService(PathSegmentResolver.class)` built-ins in `strategies-builtin/`:
  - `GetterPathResolver`: matches `getX()` / `isX()` JavaBean accessors.
  - `RecordPathResolver`: matches canonical record component accessors `x()`.
  - `FieldPathResolver`: matches direct field reads on public/package-accessible fields.
- Extend `SeedGraph` to walk each directive's source path. For every segment beyond the parameter root, iterate registered `PathSegmentResolver`s, take the first match, and register a 1-slot `ExpansionGroup` (root = new typed source node, slot = parent source node, codegen = resolver-provided). Existing untyped seed-leaf source nodes remain with `MARKER` edges to the typed nodes for diagnostic origin tracking.
- **BREAKING (internal SPI only)**: `GetterRead`-as-`Bridge` becomes redundant for source-chain typing once `SeedGraph` pre-resolves paths. It stays in this change to avoid scope creep; a follow-up change removes it after integration is green.
- The `MethodCallBridge` filter relaxation that already landed in this branch (removed `isAssignable(sourceType, paramType)` gate) stays — it's orthogonal to source resolution and unblocks `mapAddress` from the target-direction side.

## Capabilities

### New Capabilities

- `source-path-resolution`: defines the `PathSegmentResolver` SPI, the three built-in resolvers, and the seed-time path-walking algorithm that pre-registers `GetterCall`-style `ExpansionGroup`s.

### Modified Capabilities

- `seed-graph`: directive seeding now walks source path segments using `PathSegmentResolver`s and registers per-segment `ExpansionGroup`s. The untyped seed-leaf nodes remain; the chain of typed source nodes is new.
- `expansion-strategy-spi`: SPI surface grows by one — `PathSegmentResolver` interface + `ResolvedSegment` value type. No changes to existing `Bridge` / `GroupTarget` / `ElementSeed` SPIs.
- `graph-model`: groups now include access-call subgraphs (1-slot groups produced by source-path resolution). No structural changes to `ExpansionGroup` itself.
- `builtin-strategy-unit-tests`: three new built-in specs (`GetterPathResolverSpec`, `RecordPathResolverSpec`, `FieldPathResolverSpec`).

## Impact

- **Affected code**: `spi/`, `strategies-builtin/`, `processor/stages/seed/SeedGraph.java`, processor module wiring (`ProcessorModule`), built-in registration via `ServiceLoader`.
- **Affected behaviour**: seed-graph dump (`*.seed.dot`) now shows typed source nodes; `*.full.dot` and `*.transforms.dot` show GetterCall-shaped clusters in source chains. Generated mapper code calls the correct getters per directive.
- **Integration verification**: `~/Projects/joke/percolate-integration/mappers` with both `mapHuman` and `mapAddress` compiles green; `tgt[addresses]` reaches `mapAddress` via `SetMap` → `ElementSeed` → nested element group; `firstName`/`lastName` call the receiver-specific getter named by the directive.
- **Out of scope** (separate future changes, but architecture preserved): automatic same-name mapping (would add a directive-synthesis pre-pass that calls the same resolvers); multi-segment target chains like `target="person.address.street"` (uses existing `GroupTarget` recursion plus marker-bookkeeping extension); removal of `GetterRead`-as-`Bridge`.
