# Diagnostics Spec

## Purpose

The `Diagnostics` facility centralises error and warning emission for the processor pipeline. It records which `Element`s have been "scarred" so downstream stages can short-circuit on already-broken inputs, attaches the `AnnotationMirror` and `AnnotationValue` context required for IDEs to underline the exact source token, and resets its scarring state at the start of each processor round so incremental compilation stays correct.

## Requirements

### Requirement: Errors SHALL include source-position information for IDEs

Errors emitted via `Diagnostics` SHALL be reported with the offending `Element`, the relevant `AnnotationMirror`, and the relevant `AnnotationValue` so that IDEs can underline the exact source token.

#### Scenario: Duplicate target error points at the literal

- **WHEN** a stage emits an error for a duplicate `target` on a `@Map` annotation via `Diagnostics.error(...)`
- **THEN** the underlying `Messager.printMessage` invocation receives the method `Element`, the `@Map` `AnnotationMirror`, and the `AnnotationValue` for `target`
- **AND** an IDE consuming the diagnostic underlines the `"<value>"` literal of `target = "..."`, not the enclosing method

#### Scenario: Errors against an Element with no annotation context still carry the element

- **WHEN** a stage emits an error against an `Element` without a relevant `AnnotationMirror`
- **THEN** the underlying `Messager.printMessage` invocation receives the `Element` and the message; the `AnnotationMirror` and `AnnotationValue` arguments are absent (null)

### Requirement: Diagnostics SHALL track which elements have prior errors

`Diagnostics` SHALL provide a `hasErrorsFor(Element)` predicate that returns `true` for any `Element` that has had an error emitted against it (or against an element it transitively contains) within the current round.

#### Scenario: Querying after an error returns true

- **WHEN** an error has been emitted against `ExecutableElement` `m`
- **THEN** `hasErrorsFor(m)` returns `true`

#### Scenario: Containment propagates scarring

- **WHEN** an error has been emitted against `ExecutableElement` `m` enclosed in `TypeElement` `t`
- **THEN** `hasErrorsFor(t)` returns `true`

#### Scenario: Unrelated elements are not scarred

- **WHEN** an error has been emitted against `ExecutableElement` `m1`
- **AND** `m2` is a sibling method on the same enclosing type
- **THEN** `hasErrorsFor(m2)` returns `false`

### Requirement: Diagnostics state SHALL reset between processor rounds

`Diagnostics` SHALL expose a `reset()` operation that clears all per-round error tracking. The processor framework SHALL invoke `reset()` at the start of each round before any stage runs.

#### Scenario: Reset clears scarring

- **WHEN** an error has been emitted against `Element` `e` in round 1
- **AND** `Diagnostics.reset()` is invoked
- **THEN** `hasErrorsFor(e)` returns `false`

#### Scenario: MapperStep invokes reset at round start

- **WHEN** `MapperStep.process(elementsByAnnotation)` begins handling a round
- **THEN** `Diagnostics.reset()` is called before any pipeline stage executes for that round
