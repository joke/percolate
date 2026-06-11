## Why

Today every `@Map` target must trace back to a source parameter path — the mapper can only *move* values, never *supply* them. There is no way to set a fixed literal on a target, and no way to provide a fallback when a nullable or absent source yields nothing. Users are forced to hand-write these trivial mappings or post-process the generated output, which defeats the point of a declarative mapper.

## What Changes

- Add a `constant` member to `@Map`: a fixed literal value that produces a target with **no source** (e.g. `@Map(target = "status", constant = "ACTIVE")`).
- Add a `defaultValue` member to `@Map`: a fallback applied **only when the source is absent** — `null` for a nullable scalar, empty for `Optional<scalar>` (e.g. `@Map(target = "name", source = "in.name", defaultValue = "unknown")`).
- Make `source()` optional and introduce a collision-proof `UNSET` sentinel on `@Map`, so an unspecified member is distinguishable from a legitimate empty-string value.
- Validation: **constant XOR source** (exactly one must be present); `defaultValue` requires a `source` (illegal with `constant`); skip the "source's first segment names a parameter" check for constant directives; add a *post-nullability* check that flags a default that can never fire (source is `NON_NULL` or primitive → dead default).
- Constant becomes a **new kind of seed**: the seed plants an untyped constant-demand in the graph (no source chain, no bridge); a myopic `ConstantValue` provider strategy types the node from the demanded target type and emits the coerced literal.
- Default is **expansion-only**: a myopic, target-side coalescing strategy that reads the directive attribute and wraps the produced source value — no seed topology change.
- Add literal coercion shared by both features: turn the raw annotation string into a typed literal for the JDK defaults only — the 8 primitives, their wrappers, and `String` — with targeted coercion-failure diagnostics (e.g. *"cannot coerce 'abc' to int"*).

Out of scope for this change: defaults on collection-typed sources (deferred — closer to a null→empty normalization than a value default); enum / `BigDecimal` / `java.time` / array / collection constants; null-safe navigation through intermediate nulls on multi-segment paths (the default fires on the final resolved value only).

No breaking changes: the new `@Map` members are additive with sentinel defaults, so existing annotations compile and behave unchanged.

## Capabilities

### New Capabilities
- `constant-values`: declaring and producing a fixed literal target value with no source — annotation surface, the constant seed kind, the `ConstantValue` provider strategy, and literal coercion to the demanded target type.
- `default-values`: declaring and producing a fallback for an absent source — the target-side coalescing strategy and the per-source-kind "absent" trigger (nullable scalar → null; `Optional` → empty). Collection defaults are deferred.

### Modified Capabilities
- `mapping-discovery`: parse the new `constant` and `defaultValue` members (against the `UNSET` sentinel) and carry them — with their `AnnotationValue`s for diagnostic positioning — onto the directive.
- `mapping-validation`: enforce constant XOR source, require `defaultValue` to accompany a `source` (illegal with `constant`), and skip the source-parameter check for constants.
- `seed-graph`: add the constant seed kind (untyped constant-demand, no source chain or bridge).
- `expansion-strategy-spi`: surface the constant and default attributes to strategies via the directive, and host the `ConstantValue` and default-coalesce strategies.
- `nullability`: add the post-nullability legality check that a declared default can actually fire.
- `code-generation`: render coerced literals and the default coalesce snippet (`src != null ? src : D`, `opt.orElse(D)`).

## Impact

- **Annotations module** (`io.github.joke.percolate.Map`): new `constant` / `defaultValue` members and the `UNSET` sentinel; `source()` defaults to `UNSET`.
- **Discovery**: `DiscoverMappingsStage.toDirective` and the `MappingDirective` model read and carry the two new attributes plus their `AnnotationValue`s.
- **Validation**: `ValidateSourceParametersStage` (constant XOR source; skip param check for constants) and a new late validation stage for default legality, ordered after nullability inference.
- **Seed**: `SeedStage` gains the constant seed branch.
- **Strategies / SPI**: new `ConstantValue` provider strategy and a default-coalesce strategy; shared literal-coercion utility.
- **Code generation**: typed-literal rendering and the coalesce snippets per source kind.
- **Diagnostics**: targeted coercion-failure and dead-default messages with mirror/value positioning.
