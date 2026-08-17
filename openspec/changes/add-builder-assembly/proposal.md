## Why

Percolate's read side is rich — getters, records, fields, fluent methods, nested paths — but its **write side is
one strategy wide**: `ConstructorCall` is the only way to assemble a target. Any type built through a builder
(Lombok `@Builder`, protobuf messages, AutoValue, hand-written fluent builders) simply cannot be a mapping target
today. That is an adoption wall, and it is more basic than anything on the planned feature roadmap.

Builders are also the cheapest possible feature to add: a builder call is a single expression with N inputs, which
is structurally identical to a constructor call. The engine needs to learn nothing.

## What Changes

- Four new built-in `ExpansionStrategy` implementations in `strategies-builtin`, one per builder convention:
  `FluentBuilder` (`builder()` / `.name(v)` / `build()`), `ProtobufBuilder` (`newBuilder()` / `.setName(v)`),
  `WithBuilder` (`builder()` / `.withName(v)`), and `SideLocatedBuilder` (`new MyClassBuilder()` / `.name(v)`).
- Each emits **one n-ary `OperationSpec`** with a `Port.subTarget` per declared child and a single chained output
  expression — the same shape `ConstructorCall` emits. The expansion engine gains **no** knowledge of builders;
  from its point of view there is no builder, only an operation with ports.
- Builder assembly gates on `declaredChildren ⊆ builderSetterNames` (**subset**), because builder setters are
  optional, where `ConstructorCall` gates on set **equality**, because constructor parameters are mandatory.
  `ConstructorCall`'s existing empty-declaration bail is preserved so an empty declaration never vacuously
  assembles.
- A new `percolate.construction.preference` compile-time switch (`constructor` default, or `builder`) settles the
  case where a target offers both forms — Lombok emits an all-args constructor alongside its builder, so the two
  gates genuinely overlap. Each assembly strategy reads the flag and prices **itself**; neither knows the other
  exists, so strategy myopia is preserved and the choice stays a cost preference that can never exclude.
- **BREAKING** (third-party strategy authors only) `ResolveCtx.configuredTimeZone()` and
  `BodyRenderContext.switchStyle()` are **removed** and replaced by one generic keyed lookup,
  `ResolveCtx.option(String key)`. The generic seam had begun accreting one bespoke accessor per feature; a third
  one for `construction.preference` would have entrenched the pattern. The temporal zone bridge and the enum
  switch-form selection migrate onto the generic lookup. No user-facing option name changes.
- Third-party pluggability requires no new SPI surface: a company with an in-house builder convention registers
  its own `@AutoService(ExpansionStrategy.class)` implementation, exactly as `percolate-reactor` does today. No
  builder-specific SPI type is introduced.

Explicitly **out of scope**: setter-based assembly of mutable beans (`new T(); t.setX(…)`), which needs an output
shape percolate does not have — a statement sequence over a materialised local — and is its own change.

## Capabilities

### New Capabilities

- `builder-assembly`: builder-based target assembly — the single-operation constraint and why chained setter
  steps are forbidden, the subset gate and its empty-declaration guard, the four shipped conventions and their
  discovery rules, the chained-expression codegen with its wrap markers and call ordering, and the
  `construction.preference` weight pricing that arbitrates against constructor assembly.

### Modified Capabilities

- `expansion-strategy-spi`: `ResolveCtx` gains the generic `option(String)` lookup and loses
  `configuredTimeZone()`; the `BodyCodegen` render context loses `switchStyle()` and routes the read through the
  seam; the enumerated built-in strategy roster and the ServiceLoader smoke assertion grow by the four builder
  strategies.
- `graph-expansion`: the assembly gate requirement — today "a constructor is a candidate iff its parameter-name
  set equals the declared-children set" — is restated so each assembly strategy declares its own gate, admitting
  the builder's subset rule alongside the constructor's equality rule.
- `processor-options`: `percolate.construction.preference` is declared and exposed on `ProcessorOptions`.
- `temporal-conversion`: zone resolution reads `percolate.time.zone` through the generic option lookup rather
  than a bespoke seam accessor. Resolution precedence and behaviour are unchanged.
- `enum-conversion`: switch-form selection reads `percolate.switch.style` through the generic option lookup
  rather than a bespoke render-context accessor. The selected forms are unchanged.
- `builtin-strategy-unit-tests`: the enumerated per-strategy spec roster grows by `FluentBuilderSpec`,
  `ProtobufBuilderSpec`, `WithBuilderSpec`, and `SideLocatedBuilderSpec`, and the feature-package organisation
  covers them.
- `user-manual`: a builder-assembly page joins the manual, single-sourced from a compiling fixture with
  doc-tagged generated output, and the compile-time switches reference documents
  `percolate.construction.preference`.

## Impact

**Modules.** `percolate-spi` (`ResolveCtx.option`, two accessors deleted, `BodyRenderContext`);
`percolate-processor` (`ProcessorOptions`/`ProcessorOptionsReader` new key, `CompileResolveCtx`,
`BodyRenderContextFactory`/`Impl`); `percolate-strategies-builtin` (four strategies, `ConstructorCall` weight
pricing, temporal and enum option migration, unit specs, e2e, docs page); `docs` (nav entry).

**Affected audiences.**
- *Mapper authors* — purely additive. Builder-built targets become mappable; existing mappers are unaffected, and
  `constructor` remains the default preference so no existing generated code changes.
- *Third-party `ExpansionStrategy` authors* — one breaking SPI change. Any strategy calling
  `ResolveCtx.configuredTimeZone()` or `BodyRenderContext.switchStyle()` must move to `option(key)`. In-tree,
  only the temporal and enum built-ins are affected. `percolate-reactor` and `percolate-reactor-blocking` call
  neither and need no change.
- *Percolate maintainers* — the option seam stops growing a method per feature, so every future option-reading
  feature is SPI-neutral.

**No engine impact.** The expansion driver, cost fold, plan extraction, and code-generation stage are unchanged;
the four strategies are indistinguishable from `ConstructorCall` at the operation boundary.
