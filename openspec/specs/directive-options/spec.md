# Directive Options Spec

## Purpose

Defines how a directive's **inputs** (every written annotation member beyond the source path) are declared by the author, consumed by a strategy, and validated: a strategy that reads an input to produce its `OperationSpec` stamps that input **instance** consumed, and the processor diagnoses, as a generic `declared − consumed` difference over inputs, any declared input the winning plan never consumed — knowing no key, annotation or feature by name.

## Requirements

### Requirement: Directive options are declared by the author and consumed by a strategy

A directive SHALL carry **inputs** beyond its structural source path — every author-declared configuration
value a reader attached to the binding (see `directive-reading`). An input is **declared present** exactly when
a reader attached it; there is no sentinel comparison and no core-owned list of which inputs exist. A strategy
that reads an input to produce its `OperationSpec` SHALL **stamp that input** onto the spec it emits (the
consumer declares consumption; no other component infers it). Strategies remain myopic: a strategy stamps only
inputs it actually read from the demand's `Directive`.

Consumption SHALL be recorded per **input instance**, not per key, so one entry of a repeated structured input
can be consumed while a sibling entry is not.

#### Scenario: A strategy stamps the input it consumed
- **WHEN** a temporal format strategy produces a value by reading the input keyed `format`
- **THEN** the emitted `OperationSpec` carries that input among its consumed inputs

#### Scenario: A strategy that read no input stamps nothing
- **WHEN** `DirectAssign` produces a `String` target from a `String` source under a directive with no inputs
- **THEN** the emitted `OperationSpec` carries an empty consumed set

#### Scenario: One entry of a repeated input is consumed, another is not
- **WHEN** two structured entries are declared under one key and the strategy can use only the first
- **THEN** the emitted spec stamps the first entry consumed and leaves the second unconsumed

#### Scenario: A new input needs no registration
- **WHEN** a reader attaches an input under a key no core class knows
- **THEN** it participates in declaration and consumption with no core change

### Requirement: Declared options unconsumed by the winning plan are diagnosed

After plan extraction, the processor SHALL compute the **consumed set** as the union of consumed inputs stamped
on the operations of the **winning** (chosen-producer) plan for a binding, and SHALL compute the **declared
set** as every input attached to that binding. Every input in `declared − consumed` SHALL be reported as a
compile **error** at that input's own `Subject`, stating that it was declared and had no effect. An input
consumed by a non-winning candidate SHALL NOT count as consumed — the diagnostic reflects what the generated
code actually does.

The comparison SHALL be a set difference over opaque inputs. The stage performing it SHALL NOT name, enumerate,
or interpret any key, and SHALL contain no key constants.

#### Scenario: A misapplied input is reported
- **WHEN** a binding declares an input keyed `zone` producing a `String` target, and the winning plan is a plain
  `String → String` assignment that consumed nothing
- **THEN** the processor reports a compile error positioned at that input, stating it had no effect

#### Scenario: A consumed input raises no diagnostic
- **WHEN** a binding declares an input keyed `zone` producing a `LocalDate` from an `Instant`, and the winning
  plan crosses the zone bridge which stamped that input consumed
- **THEN** no unconsumed-input diagnostic is reported

#### Scenario: An absent input is never diagnosed
- **WHEN** a binding declares no inputs
- **THEN** the declared set is empty and no unconsumed-input diagnostic is possible

#### Scenario: An unconsumed structured entry is reported at its own token
- **WHEN** a `@MapEnum` entry names a target constant the strategy cannot use, so it is never stamped
- **THEN** an error is reported positioned at that entry, and the sibling entries that were consumed are not reported

#### Scenario: The consumption stage names no key
- **WHEN** the stage computing `declared − consumed` is inspected
- **THEN** it declares no key constant and branches on no key name
