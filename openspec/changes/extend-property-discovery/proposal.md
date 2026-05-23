## Why

Property discovery for source paths is incomplete and silently mis-ordered. `RecordPathResolver` is gated to `RECORD`-kind types only, so fluent-style classes (`String address() { … }`) miss the method-call path. All three built-in resolvers (`GetterPathResolver`, `FieldPathResolver`, `RecordPathResolver`) share the same weight `Weights.STEP`, so when more than one matches the same segment the engine falls back to alphabetical class-name sort to break ties — which today picks `Field > Getter > Record`, the wrong precedence (a public field should not silently beat an explicit `getX()`).

A separate cleanup: the jqwik property tests under `processor/.../stages/expand/properties/` did not catch any of the graph-expansion bugs from recent changes. They run against fake bridges over a tiny `TypeUniverse`, so they assert structural invariants of the harness, not behaviour of the algorithm. They are dead weight.

## What Changes

- **Generalise method-style access**. Rename `RecordPathResolver` → `MethodPathResolver` and drop the `RECORD`-kind gate (`RecordPathResolver.java:33`). Records keep working because their canonical accessors already fit the `no-arg method whose simple name equals segment` predicate.
- **Weight-based precedence**. `PathSegmentGroupResolver.resolveFor` SHALL collect every resolver's match and return the one with the lowest `ResolvedSegment.weight`, instead of first-match-wins via `ProcessorModule`'s alphabetical class-name sort.
- **New weight constants** in `Weights`: `STEP_GETTER < STEP_METHOD < STEP_FIELD`. Each built-in resolver uses its distinct constant so precedence is encoded in the resolver, not the orchestrator.
- **Shared `Members` utility** (package-private, in `strategies-builtin/spi/builtins/`). Extracts the duplicated DECLARED-check + `asElement` + `TypeElement` cast + `getAllMembers` scaffolding from all three resolvers. Composition, not inheritance.
- **BREAKING**: `RecordPathResolver` is removed (renamed). Any external code or `@AutoService` registration referencing the old class name will fail. No known external consumers.
- **Remove jqwik property tests**. Delete `processor/src/test/groovy/.../stages/expand/properties/` (seven `*Spec.groovy` files + `ExpansionPropertyBase.groovy` + the `fakes/` directory). Drop the jqwik dependency from `processor/build.gradle.kts`. Shrink `ExpansionHarness.expand` signature if any params exist only for property tests.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `source-path-resolution`: precedence rule for multi-resolver matches changes from class-name sort to lowest-weight wins; resolver weights become distinct constants; `RecordPathResolver` renamed to `MethodPathResolver` and applies to all `DECLARED` types, not just records.
- `builtin-strategy-unit-tests`: required spec list updates `RecordPathResolverSpec` → `MethodPathResolverSpec`.
- `expansion-test-harness`: property-test sections (`engine-algebra property tests`, `Fake strategies as the engine-algebra input alphabet`) are removed; `ExpansionHarness.expand` signature simplification, if applicable.

## Impact

- **Code**: `strategies-builtin/src/main/java/io/github/joke/percolate/spi/builtins/` (rename + new utility + weight tweaks); `processor/src/main/java/io/github/joke/percolate/processor/stages/expand/PathSegmentGroupResolver.java` (selection algorithm); `spi/src/main/java/io/github/joke/percolate/spi/Weights.java` (new constants).
- **Tests**: rename `RecordPathResolverSpec` → `MethodPathResolverSpec` and broaden its fixture set to include a non-record fluent class; delete property-test tree; co-located `MembersSpec` if the util warrants it.
- **Build**: drop `net.jqwik:jqwik` dependency from `processor/build.gradle.kts`.
- **APIs**: `Weights` gains new public constants; `RecordPathResolver` class name disappears (BREAKING for anyone wiring it by FQN, but the SPI surface via `@AutoService(PathSegmentResolver.class)` continues to work transparently).
- **Teams**: processor maintainers only.
