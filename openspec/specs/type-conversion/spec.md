# Type Conversion Spec

## Purpose

This spec defines the built-in lossless primitive conversion strategies — boxing, unboxing, and widening — that let primitive, wrapper, and wider-numeric fields map automatically. The strategies are atomic and single-hop, authored target-to-source; the expansion engine composes them into the boxed-widening cross-products (`int → Long`, `Integer → long`, `Integer → Long`) by synthesizing intermediate type nodes (see `graph-expansion`). Narrowing/lossy conversions are deliberately out of scope and remain user-helper territory.

## Requirements

### Requirement: PrimitiveWrapperConversion built-in (boxing and unboxing)

The `percolate-strategies-builtin` module SHALL ship a public final class `PrimitiveWrapperConversion` registered via `@AutoService(ExpansionStrategy.class)`. It SHALL be authored **target-to-source**: it decides solely from the frontier's target type (it does not depend on the candidate `from` type) and emits a single `Intent.CONVERSION` `ExpansionStep` whose one input names the type the driver must reuse-or-synthesize.

- When the target type is a primitive wrapper `W` (`Integer`, `Long`, `Short`, `Byte`, `Character`, `Boolean`, `Double`, `Float`), it SHALL emit a step with input type `ctx.types().unboxedType(W)`, output `W`, weight `Weights.STEP`, and codegen rendering `W.valueOf(<input>)` (**boxing**, JLS 5.1.7).
- When the target type is a primitive `p` that has a wrapper, it SHALL emit a step with input type `ctx.types().boxedClass(p).asType()`, output `p`, weight `Weights.STEP`, and codegen rendering `<input>.<p>Value()` (e.g. `intValue()`, `charValue()`) (**unboxing**, JLS 5.1.8).
- Otherwise it SHALL emit nothing.

#### Scenario: boxing a primitive to its wrapper
- **WHEN** `PrimitiveWrapperConversion` is offered a frontier whose target type is `Integer`
- **THEN** it emits exactly one `ExpansionStep` with `intent == CONVERSION`
- **AND** the step's single input type is `int`
- **AND** the step's output type is `Integer`
- **AND** the step's weight is `Weights.STEP`

#### Scenario: unboxing a wrapper to its primitive
- **WHEN** `PrimitiveWrapperConversion` is offered a frontier whose target type is `int`
- **THEN** it emits exactly one `ExpansionStep` with `intent == CONVERSION`
- **AND** the step's single input type is `Integer`
- **AND** the step's output type is `int`

#### Scenario: no step for a non-wrapper, non-primitive target
- **WHEN** `PrimitiveWrapperConversion` is offered a frontier whose target type is `String`
- **THEN** it emits an empty `Stream`

### Requirement: WidenPrimitive built-in (JLS 5.1.2 widening primitive)

The `percolate-strategies-builtin` module SHALL ship a public final class `WidenPrimitive` registered via `@AutoService(ExpansionStrategy.class)`, authored **target-to-source**. When the frontier's target type is a primitive `p`, it SHALL emit one `Intent.CONVERSION` `ExpansionStep` per strictly-narrower primitive `q` that widens to `p` under JLS 5.1.2, each with input type `q`, output `p`, weight `Weights.STEP`, and codegen rendering `(p) <input>`. The widening relation SHALL be exactly:

| target `p` | narrower sources `q` |
|---|---|
| `short` | byte |
| `int` | byte, short, char |
| `long` | byte, short, char, int |
| `float` | byte, short, char, int, long |
| `double` | byte, short, char, int, long, float |

`boolean` SHALL appear in no row (it has no widening). `char` SHALL be a source only (`byte`/`short` do NOT widen to `char`). The full set includes the precision-losing IEEE legs `int → float`, `long → float`, and `long → double`, matching javac's implicit-assignment behaviour.

#### Scenario: single-step widening offers the narrower source
- **WHEN** `WidenPrimitive` is offered a frontier whose target type is `long`
- **THEN** it emits one `CONVERSION` step per narrower source `byte`, `short`, `char`, and `int`
- **AND** every emitted step has output type `long` and weight `Weights.STEP`

#### Scenario: no widening for a non-numeric or boolean target
- **WHEN** `WidenPrimitive` is offered a frontier whose target type is `boolean`
- **THEN** it emits an empty `Stream`

#### Scenario: char is a one-way source
- **WHEN** `WidenPrimitive` is offered a frontier whose target type is `char`
- **THEN** it emits an empty `Stream` (nothing widens to `char`)

### Requirement: Lossless cross-products compose through the conversion type-DAG

Given the engine's type-keyed `CONVERSION` synthesis and base-case reachability SAT (see `graph-expansion`), the two strategies SHALL compose target-to-source into the boxed-widening cross-products with no per-pair code: each cross-product resolves as a chain of `CONVERSION` edges through synthesized type-keyed intermediate nodes within a single group, satisfied only when a complete realised path to a source exists. No `type-conversion` strategy SHALL enumerate a cross-product directly.

#### Scenario: int to Long composes widen then box
- **WHEN** a mapping requires `int → Long`
- **THEN** the engine resolves it as `Long` ← `long` ← `int` (box demands a synthesized `long`; widen folds the `int` source into it)
- **AND** the realised path is two `CONVERSION` edges in one group

#### Scenario: Integer to Long composes unbox, widen, box
- **WHEN** a mapping requires `Integer → Long`
- **THEN** the engine resolves it as `Long` ← `long` ← `int` ← `Integer` through synthesized `long` and `int` nodes
- **AND** the realised path is three `CONVERSION` edges in one group

### Requirement: Lossless boundary — no narrowing conversions

Neither `PrimitiveWrapperConversion` nor `WidenPrimitive` SHALL emit a step for a narrowing or otherwise lossy conversion (`long → int`, `double → int`, `Integer → Byte`, `float → int`, …). Narrowing remains user-helper territory discovered via `MethodCallBridge`.

#### Scenario: narrowing primitive target yields no widening step
- **WHEN** `WidenPrimitive` is offered a frontier whose target type is `int` and the only candidate is `long`
- **THEN** no step is emitted that consumes `long` to produce `int`

#### Scenario: cross-wrapper narrowing does not compose
- **WHEN** a mapping requires `Integer → Byte`
- **THEN** the conversion strategies produce no resolving path (no lossless chain exists)

### Requirement: Conversion strategies register via ServiceLoader

`PrimitiveWrapperConversion` and `WidenPrimitive` SHALL each be annotated `@AutoService(ExpansionStrategy.class)` and SHALL be discoverable through the standard `ServiceLoader<ExpansionStrategy>` lookup alongside the other built-ins, with no kind-ordering.

#### Scenario: both conversion strategies are service-loadable
- **WHEN** `ServiceLoader.load(ExpansionStrategy.class)` is enumerated on the strategies-builtin classpath
- **THEN** instances of `PrimitiveWrapperConversion` and `WidenPrimitive` are present
