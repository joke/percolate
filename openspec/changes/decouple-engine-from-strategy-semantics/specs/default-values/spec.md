## MODIFIED Requirements

### Requirement: A default on a non-absent source is a dead default

A `defaultValue` SHALL only fire when the source can be absent. When the source resolves to a `NON_NULL`
reference scalar or a primitive (which can never be absent), no nullness crossing is demanded, so no strategy is
ever asked and none can refuse: the default is dead and nothing fails.

The processor SHALL therefore diagnose a dead default through the **generic consumption rail**: the
`defaultValue` input is declared and no operation in the winning plan stamps it consumed, so it is reported as
having had no effect, positioned at that input's own `Subject`.

No core stage SHALL independently decide when a default can fire, and no stage SHALL reimplement absence
semantics.

#### Scenario: Default on a NON_NULL source is reported by the rail
- **WHEN** a binding declares a `defaultValue` for a source that resolves `NON_NULL`
- **THEN** no crossing consumes the input, and an error is reported positioned at the `defaultValue` literal

#### Scenario: Default on a primitive source is reported by the rail
- **WHEN** a binding's source resolves to a primitive type
- **THEN** the `defaultValue` input is unconsumed and is reported

#### Scenario: Default on a nullable or Optional source is accepted
- **WHEN** a binding's source resolves to a `@Nullable` reference scalar or an `Optional<T>`
- **THEN** the crossing consumes the input and no diagnostic is reported

#### Scenario: No core stage owns absence semantics
- **WHEN** the processor's validation stages are inspected
- **THEN** none tests whether a source can be absent in order to judge a default


### Requirement: @Map defaultValue member declares an absent-source fallback

`@Map` SHALL expose an optional `String defaultValue()` member with an empty-string default. Presence SHALL be decided by whether the member was **written**, never by comparing against that default: a written `defaultValue` declares a fallback used **only when the source value is absent**. The binding's source path is unchanged; the default never replaces a present source value. An empty-string default (`defaultValue = ""`) SHALL be a legitimate present value, distinct from absent.

#### Scenario: A present default declares a fallback for an existing source
- **WHEN** an abstract method is annotated with `@Map(target = "name", source = "in.name", defaultValue = "unknown")`
- **THEN** the binding retains its source path `in.name`
- **AND** carries a `defaultValue` input whose value is `"unknown"`

#### Scenario: Empty-string default is present, not absent
- **WHEN** a directive declares `@Map(target = "note", source = "in.note", defaultValue = "")`
- **THEN** the binding carries a `defaultValue` input whose value is the empty string
- **AND** it is NOT treated as absent, because the member was written
## ADDED Requirements

### Requirement: An uncoercible default is refused by the crossing strategy

The crossing strategy SHALL refuse, with a message naming the value and the type and carrying the `defaultValue`
input's `Subject`, when a `defaultValue` is declared for a demand it recognises but the literal cannot be coerced
to the target type. It SHALL NOT emit a diagnostic itself.

#### Scenario: An uncoercible default is refused with a reason
- **WHEN** a binding declares `defaultValue = "abc"` for a nullable `Integer` target
- **THEN** the crossing strategy returns a refusal naming the value and the type, positioned at the `defaultValue` literal

#### Scenario: A coercible default produces the coalesce and stamps its input
- **WHEN** a binding declares a coercible `defaultValue` for a nullable source
- **THEN** the crossing produces the coalesce operation and stamps the `defaultValue` input consumed
