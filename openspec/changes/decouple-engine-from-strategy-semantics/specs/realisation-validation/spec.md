## ADDED Requirements

### Requirement: The realisation renderer is the single renderer for refusals

The realisation renderer SHALL be the only component that turns recorded refusals into diagnostics. Having
descended to the deepest unsatisfied miss for an unreachable return root, it SHALL report the refusals recorded
on that `Value` **instead of** its generic "no producer" message, each positioned at the refusal's own
`Subject`. When the miss carries no refusal, the generic message SHALL be reported unchanged.

The renderer SHALL treat a refusal as opaque text plus a position; it SHALL NOT inspect, classify, or
special-case a refusal by its origin.

#### Scenario: Refusals replace the generic message at the miss
- **WHEN** the deepest miss for an unreachable return root carries two refusals
- **THEN** both are reported, each at its own subject, and the generic "no plan for …" message is not reported for that root

#### Scenario: A miss with no refusal keeps the generic message
- **WHEN** the deepest miss carries no refusal
- **THEN** the generic message naming the miss and its type is reported unchanged

#### Scenario: The renderer does not classify refusals
- **WHEN** the renderer processes a refusal
- **THEN** it reads only the message and the subject, branching on neither the producing strategy nor the refusal's cause

#### Scenario: Refusals off the miss chain are not rendered
- **WHEN** a reachable `Value` carries a refusal
- **THEN** no diagnostic is produced for it

## MODIFIED Requirements

### Requirement: Diagnostics anchor on the MapperContext.mapperType

The realisation renderer SHALL **record** its outcome as diagnostic values on the per-mapper context rather
than emitting, marked **transient** — an unreachable demand may become reachable in a later round once an
upstream processor has generated the missing member.

The generic "no plan" message, which is not tied to a single binding, SHALL carry `Subjects.none()` and
therefore resolve to the mapper `TypeElement` at emission: with the demand work-list model an unreachable
target often crosses multiple bindings and conversion hops, so the mapper class name is the stable locus. A
rendered **refusal**, by contrast, SHALL carry the subject the refusal itself supplied and is not anchored at
the mapper type.

#### Scenario: The generic message anchors at the mapper type
- **WHEN** a recorded generic realisation diagnostic is emitted
- **THEN** it is positioned at the mapper's `TypeElement`

#### Scenario: A rendered refusal anchors at its own subject
- **WHEN** a recorded refusal is emitted
- **THEN** it is positioned at the token the refusal's subject names, which may lie outside the mapper type

#### Scenario: Realisation diagnostics are transient
- **WHEN** the renderer records a generic "no plan" outcome
- **THEN** the diagnostic is marked transient, so an otherwise clean mapper is deferred to a later round
