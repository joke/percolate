## Why

Generated mapper bodies render an entire fluent container/stream pipeline as one physical line — e.g. `Optional.ofNullable(person.getAddresses().stream().flatMap(optional -> optional.map(address -> this.mapAddress(address)).stream()).collect(Collectors.toSet()));` — because none of the codegen snippets that build these chains (`CollectionContainer`, `OptionalContainer`, `StreamMap`, and their `reactor`/`reactor-blocking` counterparts) emit a wrap point. The JavaPoet fork already in use (`com.palantir.javapoet`) has a column-aware line wrapper (100 cols) that only activates at an explicit `$Z`/`$W` marker in a `CodeBlock` format string, so this is a small, surgical gap rather than a missing capability. Readability of generated code matters directly now that the recently-shipped `features-as-documentation` change single-sources user-manual pages from real generated output — long unwrapped lines currently render poorly there too.

## What Changes

- Add a `$Z` (JavaPoet zero-width wrap point) immediately before the chained `.` in **every** first-party codegen snippet that appends a fluent call onto a rendered operand — not only the container/stream pipeline snippets from the reported example, but every `OperationCodegen`/`ScopeCodegen`/`Container` handle across `strategies-builtin`, `reactor`, and `reactor-blocking` shaped `CodeBlock.of("$L.method(...)", ...)` — so long chains wrap at call boundaries instead of overflowing one line. `$Z` (not `$W`) is required because the current style has no space before the dot (`x.stream()`); `$Z` renders nothing when the line fits and a newline+indent only when it doesn't.
- Concretely, across every first-party module:
  - `strategies-builtin`: `CollectionContainer.iterate()`/`collect()`, `OptionalContainer.iterate()`/`mapPresence()`/`unwrap()`, `ArrayContainer.collect()`, `StreamMap`'s `MAP`/`FLAT_MAP`, `NullnessCrossing`'s `[coalesce]` `Optional.orElse(D)` form, the accessor path resolvers (`GetterPathResolver`, `MethodPathResolver`, `FieldPathResolver`), and `MethodCallBridge`'s call rendering, and `PrimitiveWrapperConversion`'s unbox accessor.
  - `reactor`: `FluxMap`'s `MAP`/`FLAT_MAP`, `MonoContainer.iterate()`/`mapPresence()`, `CollectList`, `FluxSingle`, `SingleOptional`.
  - `reactor-blocking`: `FluxSingleBlock` (`"$L.single().block()"` — two wrap points), `FluxCollectListBlock` (`"$L.collectList().block()"` — two wrap points), `FluxToStream`, `MonoBlock`, `MonoBlockOptional`.
  - Snippets that **prepend** rather than chain (a cast `"($T) $L"`, a bare `"this"`, `wrap`'s `"$T.of($L)"`/`"$T.ofNullable($L)"`) have no leading `.` to mark and are unaffected.
- Document the convention on the SPI contract so third-party container/strategy authors follow it too: add a short Javadoc note to `ScopeCodegen.weave`, `OperationCodegen.render`, and `Container.UnarySnippet`/`UnwrapSnippet` explaining that a chain-continuation snippet should prefix its leading `.` with `$Z`.
- No change to `BuildMethodBodies`'s recursive assembly, the engine, or any graph/plan logic — nested `CodeBlock`s already stream through one shared `LineWrapper`, so per-snippet `$Z` markers compose automatically across the recursive chain with zero composer changes.
- Purely a formatting change: generated code's runtime behavior is byte-for-byte equivalent in semantics; only whitespace/line breaks in the emitted source differ (and only where a line already exceeds ~100 columns).

## Capabilities

### New Capabilities
<!-- None: no new capability, this refines how existing capabilities render their output. -->

### Modified Capabilities
- `code-generation`: generated fluent pipelines SHALL wrap at call boundaries when they would exceed JavaPoet's column limit, instead of rendering as one unbroken line. This is a rendering-only rule that applies uniformly to any first-party `OperationCodegen`/`ScopeCodegen` rendering, regardless of which capability's strategy produced it (container/stream pipelines, nullness crossings, accessor path resolution, method-call bridging, blocking bridges) — the requirement lives in `code-generation` because it governs how rendered text is composed, not the semantic decisions of those other capabilities.
- `container-codegen-spi`: the container/strategy codegen-handle contract SHALL document (via Javadoc) that chain-continuation snippets use `$Z` before the leading `.` so long pipelines wrap gracefully; this is a documentation/convention addition, not a change to the handle method signatures.

## Impact

- **`strategies-builtin`**: `CollectionContainer.java`, `OptionalContainer.java`, `ArrayContainer.java`, `StreamMap.java`, `NullnessCrossing.java`, `GetterPathResolver.java`, `MethodPathResolver.java`, `FieldPathResolver.java`, `MethodCallBridge.java`, `PrimitiveWrapperConversion.java` — snippet format-string edits only.
- **`reactor`**: `FluxMap.java`, `MonoContainer.java`, `CollectList.java`, `FluxSingle.java`, `SingleOptional.java` — snippet format-string edits only.
- **`reactor-blocking`**: `FluxSingleBlock.java`, `FluxCollectListBlock.java`, `FluxToStream.java`, `MonoBlock.java`, `MonoBlockOptional.java` — snippet format-string edits only.
- **`spi`**: `ScopeCodegen.java`, `OperationCodegen.java`, `Container.java` — Javadoc-only additions, no signature changes.
- **Docs**: single-sourced generated-output examples in the user manual (`docs/`, and module-owned doc pages per `features-as-documentation`) will pick up the new line-wrapped rendering automatically on next doc build — no manual doc edits needed, but existing captured snippets should be regenerated/reviewed for readability.
- **Tests**: existing unit specs asserting `CodeBlock`/generated-source text for short (sub-100-column) examples are unaffected, since `$Z` renders as nothing when a line doesn't need to wrap. Any spec/e2e fixture whose expected text happens to already exceed ~100 columns will need its expectation updated to the wrapped form. This now spans more test suites than the original container/stream-pipeline scope: `builtin-strategy-unit-tests`, `source-path-resolution`, `type-conversion`, `callable-method-discovery`/`expansion-strategy-spi`, and `reactor-containers` fixtures should all be checked, not only `container-codegen-spi`/`code-generation` ones.
- No new dependencies; no engine, graph, or plan changes; no build/Gradle changes.
