## ADDED Requirements

### Requirement: Conversion strategy unit spec presence and coverage

The two `type-conversion` built-ins SHALL each have a corresponding Spock specification following the established per-strategy pattern: `PrimitiveWrapperConversionSpec.groovy` and `WidenPrimitiveSpec.groovy`, each residing at `strategies-builtin/src/test/groovy/io/github/joke/percolate/spi/builtins/<SimpleName>Spec.groovy`, extending `spock.lang.Specification`, carrying `@spock.lang.Tag('unit')`, assembling its `ResolveCtx` through `ResolveCtxBuilder`, and sourcing every `TypeMirror` from the single `TypeUniverse` substrate.

Assertions SHALL be metadata-only per the existing *Assertion scope is metadata-only* requirement — specs SHALL assert on the emitted `ExpansionStep`'s `intent`, input type(s), output type, and weight, and on the presence/emptiness of the returned `Stream`. Specs SHALL NOT invoke codegen `render(...)` or pin `CodeBlock` output.

`PrimitiveWrapperConversionSpec` SHALL cover at minimum: a boxing happy path (target `Integer` ⇒ one `CONVERSION` step, input `int`, output `Integer`, weight `Weights.STEP`), an unboxing happy path (target `int` ⇒ input `Integer`, output `int`), and an empty-return precondition (target neither a wrapper nor a primitive with a wrapper).

`WidenPrimitiveSpec` SHALL cover at minimum: a widening happy path asserting the narrower-source steps emitted for a numeric target (e.g. target `long` ⇒ steps consuming `byte`, `short`, `char`, `int`, each output `long`, weight `Weights.STEP`), an IEEE precision-losing leg (e.g. a step consuming `long` to produce `double` exists), a `boolean`-target empty-return, and a narrowing empty-return (no step consuming `long` to produce `int`).

#### Scenario: conversion strategy specs are present and tagged
- **WHEN** `strategies-builtin/src/test/groovy/io/github/joke/percolate/spi/builtins/` is inspected
- **THEN** `PrimitiveWrapperConversionSpec.groovy` and `WidenPrimitiveSpec.groovy` are both present
- **AND** each extends `spock.lang.Specification` and carries `@spock.lang.Tag('unit')`

#### Scenario: PrimitiveWrapperConversionSpec covers boxing, unboxing, and a precondition
- **WHEN** `PrimitiveWrapperConversionSpec.groovy` is inspected
- **THEN** a feature method asserts that a wrapper target emits one `CONVERSION` step with the matching primitive input type and `Weights.STEP`
- **AND** a feature method asserts the unboxing direction (primitive target ⇒ wrapper input)
- **AND** a feature method asserts an empty `Stream` for a target that is neither a wrapper nor a primitive with a wrapper

#### Scenario: WidenPrimitiveSpec covers widening, an IEEE leg, and rejections
- **WHEN** `WidenPrimitiveSpec.groovy` is inspected
- **THEN** a feature method asserts the narrower-source `CONVERSION` steps emitted for a numeric target with output and weight pinned
- **AND** a feature method asserts a precision-losing IEEE leg is emitted (e.g. `long → double`)
- **AND** a feature method asserts an empty `Stream` for a `boolean` target
- **AND** a feature method asserts no step is emitted for a narrowing conversion (e.g. `long → int`)
