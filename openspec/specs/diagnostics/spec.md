# Diagnostics Spec

## Purpose

Diagnostics are **values**, not emissions. A stage or strategy records a `Diagnostic` — severity, an opaque `Subject` position, a message, and whether the cause is permanent or transient — on the per-mapper `MapperContext`; nothing writes to the `Messager` mid-pipeline. A single `DiagnosticEmitter` resolves each `Subject` back to its `Element`/`AnnotationMirror`/`AnnotationValue` (falling back to the mapper type) and flushes a mapper's collected diagnostics once its outcome is final, so IDEs still underline the exact source token. Deferrability is a property of the individual diagnostic: a mapper is deferred to a later round only while it is unrealised **and** every diagnostic it collected is transient.

## Requirements

### Requirement: Errors SHALL include source-position information for IDEs

Errors SHALL be recorded with a `Subject` that resolves to the offending `Element`, the relevant
`AnnotationMirror`, and the relevant `AnnotationValue`, so that IDEs can underline the exact source token. A
diagnostic whose cause has no written token SHALL carry `Subjects.none()` and be positioned at the mapper type.

#### Scenario: Duplicate target error points at the literal

- **WHEN** a duplicate binding at one target path is reported
- **THEN** the emitted `Messager.printMessage` invocation receives the method `Element`, the annotation `AnnotationMirror`, and the `AnnotationValue` for the offending member
- **AND** an IDE consuming the diagnostic underlines the `"<value>"` literal, not the enclosing method

#### Scenario: Errors against an Element with no annotation context still carry the element

- **WHEN** a diagnostic is recorded against an `Element` with no relevant `AnnotationMirror`
- **THEN** the emitted `Messager.printMessage` invocation receives the `Element` and the message; the `AnnotationMirror` and `AnnotationValue` arguments are absent (null)

### Requirement: A diagnostic SHALL be a value whose position is independent of its attribution

The processor SHALL represent a diagnostic as a value carrying a **severity**, an opaque **position**
(`Subject`), a **message**, and a **permanent** flag. A diagnostic SHALL be **attributed** to the unit of work
that produced it — the mapper whose pipeline was running — by being collected on that mapper's context, never
by inference from the position's enclosing element.

Position and attribution SHALL be independent: a diagnostic MAY be positioned at any element, including a
parameter of an inherited method declared in another compilation unit, without affecting which mapper it is
attributed to.

#### Scenario: A diagnostic positioned outside the mapper is still attributed to it
- **WHEN** a diagnostic is positioned at a parameter of an inherited candidate method declared in a supertype in another compilation unit
- **THEN** it is attributed to the mapper being processed, and that mapper reports having errors

#### Scenario: Attribution does not depend on element containment
- **WHEN** the attribution of a diagnostic is determined
- **THEN** no `Element.getEnclosingElement()` comparison participates in the decision

#### Scenario: A mapper's error state is a query on its own collection
- **WHEN** a stage asks whether the mapper it is processing already has errors
- **THEN** the answer is derived from that mapper's collected diagnostics, with no global registry consulted

### Requirement: Diagnostics SHALL be emitted at one flush point

Exactly one component SHALL write to the `Messager`. Stages, strategies, readers, and the engine SHALL record
diagnostic values and SHALL NOT emit. The emitter SHALL resolve each diagnostic's `Subject` into the
`Element`, `AnnotationMirror`, and `AnnotationValue` the `Messager` requires, falling back to the mapper
`TypeElement` for a subject that names no token.

Diagnostics for one mapper SHALL be emitted together once that mapper's outcome is final. Emission order across
mappers is not specified.

#### Scenario: Only the emitter writes to the Messager
- **WHEN** the processor's sources are searched for `Messager` usage
- **THEN** exactly one class invokes `printMessage`

#### Scenario: A positioned subject resolves to its token
- **WHEN** a diagnostic carries a subject built from an element, mirror, and annotation value
- **THEN** the emitted message carries all three, so an IDE underlines the written literal

#### Scenario: A subject naming no token falls back to the mapper type
- **WHEN** a diagnostic carries `Subjects.none()`
- **THEN** the emitted message is positioned at the mapper `TypeElement`

#### Scenario: A pipeline failure still reports what was found
- **WHEN** a stage throws part-way through a mapper's pipeline
- **THEN** the diagnostics collected before the failure are still emitted

### Requirement: Deferrability SHALL be a property of the individual diagnostic

Each diagnostic SHALL be **transient** by default, meaning its cause may disappear in a later processing round.
A producer MAY mark a diagnostic **permanent**, meaning its cause holds in every round.

A mapper SHALL be deferred to a later round if and only if it is unrealised **and** every diagnostic recorded
for it is transient. A realised mapper SHALL never be deferred, and its diagnostics SHALL be emitted regardless
of their flag.

#### Scenario: A permanent diagnostic consumes the mapper
- **WHEN** a mapper is unrealised and one recorded diagnostic is marked permanent
- **THEN** the mapper is consumed and every recorded diagnostic is emitted

#### Scenario: An all-transient unrealised mapper is deferred
- **WHEN** a mapper is unrealised and every recorded diagnostic is transient
- **THEN** the mapper is deferred, nothing is emitted, and it is reprocessed in a later round

#### Scenario: A mapper realising on a later round reports nothing
- **WHEN** a mapper is deferred in one round because an upstream processor has not yet generated a member, and realises in a later round
- **THEN** no diagnostic recorded in the earlier round is emitted

#### Scenario: A realised mapper emits its diagnostics
- **WHEN** a mapper realises but a declared input was unconsumed
- **THEN** the mapper is consumed and the unconsumed-input diagnostic is emitted, whatever its transience

#### Scenario: A mapper still deferred at the end of processing reports
- **WHEN** processing ends with a mapper still deferred
- **THEN** its recorded diagnostics are emitted on the final round
