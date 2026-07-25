## 1. Author-facing annotation

- [x] 1.1 Add repeatable `@MapEnum` (`String source`, `String target`; `@Target(METHOD)`, `CLASS` retention,
  `@Documented`) plus its container annotation in the `annotations` module
- [x] 1.2 Add a Google Compile Testing / Spock check that `@MapEnum` is repeatable and readable on a method

## 2. SPI surface (additive only)

- [x] 2.1 Add the `BodyCodegen` interface to `io.github.joke.percolate.spi` (renders a complete method body), a
  sibling of `OperationCodegen`; leave `OperationCodegen.render(IncomingValues)` and `ScopeCodegen` unchanged
- [x] 2.2 Add the source-aware render context: a superset of `IncomingValues` exposing, per port, the grounded
  concrete `TypeMirror`, a `ResolveCtx`, the effective `switch.style`, and the target `SourceVersion`
- [x] 2.3 Extend `Directive` with an ordered enum override table (source-name → target-name pairs), empty by default,
  inert for non-enum productions
- [x] 2.4 Keep the module-boundary/ArchUnit guards green (new SPI types live in `spi`); confirm no `OperationCodegen`
  render signature changed

## 3. Processor option

- [x] 3.1 Add `switchStyle` (enum `AUTO`/`CLASSIC`/`ARROW`, default `AUTO`, unrecognised → `AUTO`) to
  `ProcessorOptions`, parsed from `-Apercolate.switch.style` (case-insensitive)
- [x] 3.2 Declare `"percolate.switch.style"` in `PercolateProcessor.getSupportedOptions()`
- [x] 3.3 Spock specs: absent → `AUTO`, `classic` → `CLASSIC`, unrecognised → `AUTO`, and the option is declared

## 4. Engine rendering (codegen-agnostic)

- [x] 4.1 Implement the source-aware render-context runtime impl in the processor (supply grounded port types,
  `ResolveCtx`, `switchStyle`, and target `SourceVersion`); leave `IncomingValuesImpl` for `OperationCodegen` as-is
- [x] 4.2 In `BuildMethodBodies`/`Walk`, dispatch on codegen shape at the return-root: render a `BodyCodegen`
  verbatim as the whole body (no `return <expr>;` wrap); read no Java version and no processor option there
- [x] 4.3 Spock specs: a return-root `BodyCodegen` renders verbatim; the `OperationCodegen` `return <expr>;` path is
  unchanged; generator reads no version/option

## 5. @MapEnum discovery

- [x] 5.1 Read `@MapEnum` declarations on the conversion method and populate the `Directive` enum override table that
  travels with the return demand
- [x] 5.2 Report a compile error when a `@MapEnum` `source`/`target` names a constant the respective enum does not
  declare
- [x] 5.3 Spock specs for override-table population and the unknown-constant diagnostic (pure decision logic on plain
  data; javax tokens as opaque pass-through)

## 6. Enum conversion strategy (strategies-builtin)

- [x] 6.1 `EnumConversion` `ExpansionStrategy`: fire only when the demanded target is an enum; declare a type-variable
  input port; emit no production unless the grounded source is also an enum (`ctx.isEnum`)
- [x] 6.2 `BodyCodegen`: same-name-match every source constant, apply `@MapEnum` overrides with precedence over a
  coincidental same-name match
- [x] 6.3 Select the switch form from `switchStyle` + `SourceVersion` (`AUTO` → arrow for ≥ Java 14 else classic) and
  render: classic switch statement (Java 11) or modern arrow switch expression with **no** `default` (Java 14+)
- [x] 6.4 Register via `@AutoService(ExpansionStrategy.class)`; extend the built-in service-registration smoke check
- [x] 6.5 Spock unit specs over a mocked render context/`ResolveCtx`: name-match, overrides+precedence, each switch
  form, non-enum grounded source → empty

## 7. Coverage validation

- [x] 7.1 Classic tier: extend `realisation-validation` so an uncovered source constant fails the compile with a
  diagnostic naming that constant
- [x] 7.2 Arrow tier: confirm no percolate coverage check runs (defer to javac); the generated switch expression is
  `default`-free
- [x] 7.3 Compile-testing specs: uncovered constant fails on Java 17 (exhaustiveness) and on Java 11 (percolate
  diagnostic)

## 8. End-to-end fixtures

- [x] 8.1 e2e: mirrored enums map by name with no directive; fixture asserts runtime behaviour
- [x] 8.2 e2e: `OrderStatus toStatus(MyStatus)` with `@MapEnum(NEW→CREATED, COMPLETED→FULFILLED)`; assert mappings and
  that `ARCHIVED` is unreachable (no error)
- [x] 8.3 e2e: a bean member of the target enum bridges through the declared enum method via the existing bridge
- [x] 8.4 e2e compile-fail cases for an uncovered constant on both the arrow and classic tiers

## 9. Documentation

- [x] 9.1 Add the enum-mapping page under Conversions, co-located in the strategy's owning module, reached via the
  collector; single-source every input and generated-output snippet via `include::` from the 8.x fixtures + real
  generated output
- [x] 9.2 Document `-Apercolate.switch.style` in the compile-time-switches reference (classic vs `default`-free modern
  output)

## 10. Gate

- [x] 10.1 `./gradlew check --no-configuration-cache` green (architecture-tests, spotless, pitest, smoke)
- [x] 10.2 Update `percolate-smoke` only if the assembled consumer path is affected (not affected — no new
  modules or dependencies; `percolate-smoke` left unchanged)
