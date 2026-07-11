## MODIFIED Requirements

### Requirement: Directive type

The `percolate-spi` module SHALL define a `io.github.joke.percolate.spi.Directive` type that exposes the relevant `@Map` configuration to strategies (source path / segment access, the author-declared `constant` and `defaultValue` attributes, and the author-declared **options** `format` and `zone`) WITHOUT exposing raw compiler internals as the primary surface. A strategy SHALL read its per-binding configuration from `Directive`; it SHALL NOT need to inspect an `AnnotationMirror` directly for the common cases.

`Directive` SHALL expose the directive's `constant`, `defaultValue`, `format`, and `zone` values to strategies. Each SHALL be reported **present** only when it is not equal to `Map.UNSET`; an empty string SHALL be reported as a present value, never as absent. `ConstantValue` reads `constant`, `NullnessCrossing` reads `defaultValue` (the `[coalesce]` crossing), and the temporal strategies read `format` and `zone`, through this surface.

#### Scenario: Directive hides compiler internals
- **WHEN** the `Directive` type is inspected
- **THEN** its public accessors expose `@Map` configuration through `Directive`'s own surface
- **AND** a strategy reading the source path or a declared attribute does not require importing `javax.lang.model` annotation-mirror types

#### Scenario: Directive exposes a present constant
- **WHEN** a strategy reads the `constant` of a `Directive` built from `@Map(target = "status", constant = "ACTIVE")`
- **THEN** it observes the value present as `"ACTIVE"`

#### Scenario: Directive reports an unspecified attribute as absent
- **WHEN** a strategy reads the `defaultValue` of a `Directive` built from `@Map(target = "x", source = "in.x")`
- **THEN** it observes the value absent (equal to `Map.UNSET`)

#### Scenario: Directive reports an empty-string attribute as present
- **WHEN** a strategy reads the `constant` of a `Directive` built from `@Map(target = "note", constant = "")`
- **THEN** it observes the value present as the empty string, not absent

#### Scenario: Directive exposes a present format and zone
- **WHEN** a strategy reads the `format` and `zone` of a `Directive` built from `@Map(target = "day", source = "in.ts", format = "yyyy-MM-dd", zone = "Europe/Berlin")`
- **THEN** it observes `format` present as `"yyyy-MM-dd"` and `zone` present as `"Europe/Berlin"`

#### Scenario: Directive reports an unspecified format and zone as absent
- **WHEN** a strategy reads the `format` and `zone` of a `Directive` built from `@Map(target = "x", source = "in.x")`
- **THEN** it observes both absent (each equal to `Map.UNSET`)

## ADDED Requirements

### Requirement: OperationSpec carries consumed option keys

An `OperationSpec` SHALL carry a set of **consumed option keys** — the `@Map` option keys the emitting strategy read to produce it (e.g. `"format"`, `"zone"`). The set SHALL be **additive and optional**: existing factory entry points that build a production without options SHALL remain source-compatible and yield an empty consumed set. The set is a neutral structural fact recorded by the strategy that read the option; the processor unions it over the winning plan to validate directive-option consumption (see the `directive-options` capability). Strategies stay myopic and receive no graph access.

#### Scenario: A production that read an option records its key
- **WHEN** a temporal format strategy produces a value by reading `@Map(format = …)`
- **THEN** the resulting `OperationSpec` carries `"format"` among its consumed option keys

#### Scenario: A production that read no option has an empty consumed set
- **WHEN** `WidenPrimitive` produces an `int → long` widening spec
- **THEN** the resulting `OperationSpec` carries an empty consumed-option-key set

### Requirement: OperationSpec may request a deduplicated class member

An `OperationSpec` MAY declare one or more **member requests**, each describing a class-level member the generated mapper needs (a field type, an initializer `CodeBlock`, and a content **dedup key**) plus a way to reference it from the operation's codegen. The request SHALL be **additive and optional**: a spec that needs only an inline expression declares none. The code-generation stage SHALL deduplicate member requests by their dedup key across all method bodies and emit each distinct member once (see the `code-generation` capability). A strategy that needs an inline (non-shared) value SHALL declare no member request and render it inline instead — this is how a thread-unsafe formatter stays per-call.

#### Scenario: A strategy requests a shared member
- **WHEN** a `java.time` format strategy produces a value using a shared `DateTimeFormatter`
- **THEN** the emitted `OperationSpec` declares a member request whose field type is `DateTimeFormatter`, whose initializer is `DateTimeFormatter.ofPattern("…")`, and whose codegen references the member

#### Scenario: A strategy that inlines requests no member
- **WHEN** a legacy-`Date` format strategy produces a value using a per-call `SimpleDateFormat`
- **THEN** the emitted `OperationSpec` declares no member request and renders `new SimpleDateFormat("…")` inline
