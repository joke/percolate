## MODIFIED Requirements

### Requirement: The generated switch form is selected by the strategy from switch.style

The generated conversion SHALL render as a classic switch statement (Java 11-compatible) or a modern switch
expression, chosen by the `switch.style` option. `AUTO` SHALL select the modern arrow expression when the target
`SourceVersion` is Java 14 or later and the classic statement otherwise. `CLASSIC` SHALL always render the classic
statement; `ARROW` SHALL always render the modern expression. This selection SHALL be made by the enum conversion
strategy; the engine SHALL make no part of it.

The strategy SHALL obtain the option value through the generic seam lookup
`ResolveCtx.option("percolate.switch.style")`, reached from its render context's `resolveCtx()`, and SHALL parse
the raw value itself (an absent or unrecognised value meaning `AUTO`). It SHALL NOT read a feature-named accessor;
`BodyRenderContext.switchStyle()` no longer exists. Every selected form and rendered outcome is unchanged — only
the read path moves.

#### Scenario: AUTO on a Java 11 target renders a classic switch statement
- **WHEN** `switch.style` is absent (AUTO) and the target is Java 11
- **THEN** the generated body is a classic switch statement compilable on Java 11

#### Scenario: AUTO on a Java 17 target renders a modern switch expression
- **WHEN** `switch.style` is absent (AUTO) and the target is Java 17
- **THEN** the generated body is a modern arrow switch expression

#### Scenario: Explicit CLASSIC renders the classic statement regardless of target
- **WHEN** `switch.style` is `CLASSIC` and the target is Java 17
- **THEN** the generated body is a classic switch statement

#### Scenario: The style is read through the generic seam
- **WHEN** the enum conversion codegen resolves the effective switch style
- **THEN** it reads the raw value via `resolveCtx().option("percolate.switch.style")` and parses it itself
- **AND** its source contains no call to a `switchStyle()` accessor on the render context

#### Scenario: An unrecognised style value behaves as AUTO
- **WHEN** `-Apercolate.switch.style=nonsense` is set and the target is Java 17
- **THEN** the strategy treats the style as `AUTO` and renders a modern arrow switch expression
