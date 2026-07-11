## Why

Generated mapper output today is unconditionally `final` on the class and never `final` on methods or
parameters, with no way for a consumer to change that shape. Some style guides and teams want `final`
applied more broadly (methods, parameters) or want the freedom to subclass a generated mapper (which
today's hard-coded `final class` forbids). The existing `-Apercolate.locals.final` /
`-Apercolate.locals.var` switches already establish the `percolate.<scope>.<style>` naming pattern and the
"default off, opt-in adds the modifier" convention for hoisted locals — this change extends that same
pattern to the class itself and to each generated method's declaration and parameters.

## What Changes

- Add `-Apercolate.parameters.final=true` — when set, every parameter of a generated method's signature is
  declared `final`. Default `false` (unchanged: no `final` on parameters).
- Add `-Apercolate.methods.final=true` — when set, every generated `@Override` method is declared `final`
  in addition to `public`. Default `false` (unchanged: no `final` on methods).
- Add `-Apercolate.classes.final=true` — when set, the generated `<Mapper>Impl` class is declared `final`.
  Default `false`. **BREAKING**: today the generated class is *unconditionally* `final`; after this change
  a consumer who does not set this option gets a non-final class. Anyone relying on the generated class
  being final (e.g. to guarantee it cannot be subclassed) must set `-Apercolate.classes.final=true`.
- Refactor `ProcessorOptions` from a hand-written positional `@Value` constructor to a Lombok `@Builder`,
  since the option count is growing past what a positional constructor can stay readable at.
- `percolate.locals.final` and `percolate.locals.var` are unchanged (already fit the naming pattern).

## Capabilities

### New Capabilities

(none — this change only modifies existing capabilities)

### Modified Capabilities

- `processor-options`: three new options (`percolate.parameters.final`, `percolate.methods.final`,
  `percolate.classes.final`) parsed onto `ProcessorOptions` and declared via `getSupportedOptions()`.
- `code-generation`: the "Generated class shape" requirement's class-modifier invariant becomes
  conditional on `percolate.classes.final` (default non-final); the method and parameter modifiers become
  conditional on `percolate.methods.final` / `percolate.parameters.final` respectively.
- `user-manual`: the compile-time-switches reference page documents the three new options alongside the
  existing `percolate.locals.*` entries.

## Impact

- `processor/src/main/java/io/github/joke/percolate/processor/ProcessorOptions.java` — new fields, new
  option-key constants, `@Builder` refactor of construction.
- `processor/src/main/java/io/github/joke/percolate/processor/PercolateProcessor.java` —
  `getSupportedOptions()` grows three entries.
- `processor/src/main/java/io/github/joke/percolate/processor/internal/stages/generate/AssembleMapperType.java`
  — conditional `Modifier.FINAL` on the class, each generated method, and each generated parameter.
- Docs: the `processor` module's compile-time-switches manual page and its single-sourced generated-output
  fixtures gain the three new switches.
- No engine, SPI, or strategy code is touched — this is confined to the `processor` module's final
  generate-stage assembly and its options plumbing.
