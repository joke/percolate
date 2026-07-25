## Why

percolate cannot map one `enum` type to another today: a `StatusDto` source flowing to a differently-typed `Status`
target is an unsatisfiable demand, forcing the user to hand-write a conversion method. The obvious hand-written
workaround — `Status.valueOf(src.name())` — compiles but throws `IllegalArgumentException` at **runtime** the moment
the two enums drift (a constant added or renamed on one side). Enum-to-enum mapping is table-stakes for a bean mapper,
and percolate's whole value proposition is compile-time safety, so it should map enums with the drift caught at
**compile time**, not at runtime.

## What Changes

- Users declare an abstract enum conversion method on their `@Mapper` (e.g. `Status toStatus(StatusDto s)`) and
  percolate generates its body. A bean mapper that maps such a member bridges through it automatically via the
  existing callable-method discovery — no engine change to the bridge.
- Constants that share a name map automatically; the user writes nothing for the common mirrored-enum case.
- A new repeatable `@MapEnum(source = "…", target = "…")` annotation declares per-constant overrides where the two
  enums use different names (e.g. `NEW → CREATED`). A target enum is free to carry constants nothing maps to.
- **Compile-time coverage safety.** A source constant with no name-match and no `@MapEnum` override fails the build.
  On Java 14+ this is delivered by generating a modern switch **expression with no `default`**, so javac's own
  exhaustiveness check rejects a forgotten constant; on the Java 11 baseline (where javac does not check statement
  switches) percolate validates coverage itself and reports the uncovered constant.
- Generated switch form is selected by a new `switch.style` processor option: `AUTO` (default — classic switch
  statement for Java 11 targets, modern arrow switch expression for Java 14+), `CLASSIC`, or `ARROW`. **The strategy
  reads the option and target `SourceVersion` and owns the entire codegen decision; the engine chooses nothing.**
- The enum conversion is one ordinary `ExpansionStrategy` produced through graph expansion — no dedicated processor
  stage. The concrete source enum reaches the strategy via type-variable-port grounding (the mechanism `TemporalFormat`
  already relies on), surfaced to its codegen.

## Capabilities

### New Capabilities
- `enum-conversion`: enum-to-enum mapping via a user-declared conversion method — automatic same-name matching,
  `@MapEnum` per-constant overrides, `@MapEnum`/method discovery, compile-time coverage safety (javac exhaustiveness
  on Java 14+, percolate coverage validation on Java 11), and the strategy-owned classic/arrow switch codegen.

### Modified Capabilities
- `expansion-strategy-spi`: a conversion strategy's codegen gains access to the **grounded** concrete source type (and
  the `ResolveCtx`), so a strategy whose emitted code depends on the source's shape (here: the source enum's constants)
  can read it. Today codegen receives only incoming expressions.
- `code-generation`: a strategy may emit a **complete-body (statement) codegen shape** that the engine renders
  verbatim as a method body, in addition to the current single-expression form the engine wraps as `return <expr>;`.
  The engine dispatches on the shape the strategy declares and makes no code-generation choice of its own.
- `processor-options`: a new `switch.style` option (`AUTO` / `CLASSIC` / `ARROW`) on the existing `ProcessorOptions`
  rail, with `AUTO` resolved against the target `SourceVersion`, exposed to strategies.
- `user-manual`: a new enum-mapping page under the Conversions section, single-sourced from a compiled, behaviourally
  asserted end-to-end fixture (per the docs-drive-tests architecture).

## Impact

- **New public API:** `@MapEnum` (repeatable) in the `annotations` module — the only new author-facing surface.
- **SPI additions (strategy-author facing):** grounded-source-type access in the codegen contract; a complete-body
  codegen shape. Additive — existing strategies and the boxing/widening/temporal conversions are unaffected.
- **Code:** new enum-conversion `ExpansionStrategy` (built-in strategies); codegen-contract + method-body rendering
  additions (`spi`, `processor`); `switch.style` in `ProcessorOptions`; new annotation (`annotations`); doc page +
  e2e fixture (`docs` / test-foundation).
- **No new runtime dependencies.** Generated code stays Java 11-compilable by default and on the classic tier.
- **Affected audiences:** end users (new `@MapEnum` annotation) and strategy authors (additive SPI). No breaking
  changes.
