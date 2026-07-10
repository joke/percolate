## 1. strategies-builtin: add wrap points

- [x] 1.1 `CollectionContainer.iterate()`: change `CodeBlock.of("$L.stream()", container)` to `CodeBlock.of("$L$Z.stream()", container)`
- [x] 1.2 `CollectionContainer.collect()`: change `CodeBlock.of("$L.collect($L)", stream, collector())` to `CodeBlock.of("$L$Z.collect($L)", stream, collector())`
- [x] 1.3 `OptionalContainer.iterate()`: change `CodeBlock.of("$L.stream()", container)` to `CodeBlock.of("$L$Z.stream()", container)`
- [x] 1.4 `OptionalContainer.mapPresence()`: change `CodeBlock.of("$L.map($N -> $L)", operand, var, body)` to `CodeBlock.of("$L$Z.map($N -> $L)", operand, var, body)`
- [x] 1.5 `StreamMap`'s `MAP` snippet: change `CodeBlock.of("$L.map($N -> $L)", operand, var, body)` to `CodeBlock.of("$L$Z.map($N -> $L)", operand, var, body)`
- [x] 1.6 `StreamMap`'s `FLAT_MAP` snippet: change `CodeBlock.of("$L.flatMap($N -> $L)", operand, var, body)` to `CodeBlock.of("$L$Z.flatMap($N -> $L)", operand, var, body)`
- [x] 1.7 `OptionalContainer.unwrap()`: change `"$L.orElse(null)"` / `"$L.orElseThrow()"` to `"$L$Z.orElse(null)"` / `"$L$Z.orElseThrow()"` (missed in original scoping — same requirement/file as 1.4)
- [x] 1.8 `ArrayContainer.collect()`: change `CodeBlock.of("$L.toArray()", stream)` to `CodeBlock.of("$L$Z.toArray()", stream)` (missed in original scoping — same requirement family as 1.1/1.2)
- [x] 1.9 `NullnessCrossing`'s `[coalesce]` `Optional.orElse(D)` form: change `"$L.orElse($L)"` to `"$L$Z.orElse($L)"` (different capability — `code-generation`'s nullness requirement — included per broadened scope decision)
- [x] 1.10 Accessor path resolvers: `GetterPathResolver` (`"$L.$N()"`), `MethodPathResolver` (`"$L.$N()"`), `FieldPathResolver` (`"$L.$N"`) — each gets `$Z` before the leading `.` (different capability — `source-path-resolution` — included per broadened scope decision)
- [x] 1.11 `MethodCallBridge`'s call rendering (`"$L.$N($L)"` → `"$L$Z.$N($L)"`) and `PrimitiveWrapperConversion`'s unbox accessor (`"$L.$N()"` → `"$L$Z.$N()"`) (different capabilities — `callable-method-discovery`/`expansion-strategy-spi`, `type-conversion` — included per broadened scope decision)

## 2. reactor: add wrap points

- [x] 2.1 `FluxMap`'s `MAP` snippet: change `CodeBlock.of("$L.map($N -> $L)", operand, var, body)` to `CodeBlock.of("$L$Z.map($N -> $L)", operand, var, body)`
- [x] 2.2 `FluxMap`'s `FLAT_MAP` snippet: change `CodeBlock.of("$L.flatMap($N -> $L)", operand, var, body)` to `CodeBlock.of("$L$Z.flatMap($N -> $L)", operand, var, body)`
- [x] 2.3 `MonoContainer.iterate()`: change `CodeBlock.of("$L.flux()", container)` to `CodeBlock.of("$L$Z.flux()", container)`
- [x] 2.4 `MonoContainer.mapPresence()`: change `CodeBlock.of("$L.map($N -> $L)", operand, var, body)` to `CodeBlock.of("$L$Z.map($N -> $L)", operand, var, body)`
- [x] 2.5 `CollectList` (`"$L.collectList()"`), `FluxSingle` (`"$L.single()"`), `SingleOptional` (`"$L.singleOptional()"`) — each gets `$Z` before the leading `.` (missed in original scoping — same module as 2.1-2.4)

## 3. reactor-blocking: add wrap points

- [x] 3.1 `FluxSingleBlock`: change `CodeBlock.of("$L.single().block()", inputs.single())` to `CodeBlock.of("$L$Z.single()$Z.block()", inputs.single())` (two wrap points — one before each chained call)
- [x] 3.2 `FluxCollectListBlock`: change `CodeBlock.of("$L.collectList().block()", inputs.single())` to `CodeBlock.of("$L$Z.collectList()$Z.block()", inputs.single())` (two wrap points)
- [x] 3.3 `FluxToStream`: change `CodeBlock.of("$L.toStream()", inputs.single())` to `CodeBlock.of("$L$Z.toStream()", inputs.single())`
- [x] 3.4 `MonoBlock`: change `CodeBlock.of("$L.block()", inputs.single())` to `CodeBlock.of("$L$Z.block()", inputs.single())`
- [x] 3.5 `MonoBlockOptional`: change `CodeBlock.of("$L.blockOptional()", inputs.single())` to `CodeBlock.of("$L$Z.blockOptional()", inputs.single())`

## 4. Document the convention on the SPI contract

- [x] 4.1 Add a Javadoc paragraph to `ScopeCodegen.weave` (spi module) explaining that a chain-continuation snippet should prefix its leading `.` with `$Z` so long pipelines wrap gracefully
- [x] 4.2 Add the same Javadoc convention note to `OperationCodegen.render`
- [x] 4.3 Add the same Javadoc convention note to `Container.UnarySnippet` and `Container.UnwrapSnippet`

## 5. Verify

- [x] 5.1 Rebuild `percolate-integration`'s `mappers` module and inspect the regenerated `PersonMapperImpl.java` — confirm the previously-190-character `addresses` line now wraps at call boundaries and reads clearly, with no stray whitespace introduced anywhere else in the file
- [x] 5.2 Run the `strategies-builtin`, `reactor`, `reactor-blocking`, `processor`, and `spi` test suites; update any unit spec or doc-e2e fixture whose expected generated-text assertion changed shape because a line now wraps. Found 9 failures, all in `strategies-builtin` (`ArrayContainerSpec`, `SetContainerSpec` x2, `ListContainerSpec` x2, `OptionalContainerSpec` x4, `StreamMapSpec` x2) — not from wrapping width, but from a JavaPoet `CodeBlock.toString()` quirk where trailing text after `$Z` is never flushed without a subsequent newline (see design.md risk). Fixed by wrapping each assertion's rendered `CodeBlock` in `CodeBlock.of("$L\n", ...)` before `.toString()`. `GetterPathResolverSpec`/`MethodPathResolverSpec`/`FieldPathResolverSpec`/`MethodCallBridgeSpec`/`PrimitiveWrapperConversionSpec`/`NullnessCrossingSpec`/`reactor-containers` fixtures don't assert rendered codegen text, so needed no changes. All suites green after the fix.
- [x] 5.3 Run `./gradlew check` across the whole build to verify everything (formatting, tests, static analysis) is green — NEVER continue if there are violations

## 6. Finalize

- [ ] 6.1 Commit the completed change with `/commit-commands:commit`
