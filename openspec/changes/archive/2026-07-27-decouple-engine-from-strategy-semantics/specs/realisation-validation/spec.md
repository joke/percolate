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

### Requirement: Diagnostics walk unsatisfied demands

`RealisationDiagnosticsStage` SHALL walk the unsatisfied demands after expansion: a method whose
**seeded return-root `Value`** (the graph-recorded return root per `graph-expansion`, **not** every
`Value` at the empty-path return location) is **unreachable** contributes a closest-miss message
naming the unresolved target. Over-emitted typed siblings that merely share the return location —
e.g. a dead `Set<E>`/`Optional<E>`/scalar `E` candidate minted while the real return is `List<E>` —
SHALL NOT be treated as return roots and SHALL NOT contribute a message. "Unsatisfied" SHALL mean
**infinite extraction cost** — a `Value` with no finite-cost producer — queried through the
extracted plan (`reachable(value) == false`); there is no stored SAT predicate and no group outcome
records.

The stage SHALL **record** what it collects as `Diagnostic` values on the `MapperContext` and SHALL emit
nothing itself; emission is owned by `MapperStep` and flushed at one point. The generic "no plan" message
SHALL be recorded as **transient**, since a later round may still realise the mapper. When the mapper already
carries an error (`ctx.hasErrors()`) the stage SHALL record nothing — a targeted earlier diagnostic already
explains the failure.

#### Scenario: Unsatisfiable method is recorded, not emitted in-stage
- **WHEN** expansion ends with a method's seeded return-root unreachable (infinite extraction cost)
- **THEN** `RealisationDiagnosticsStage` records one closest-miss message naming the unresolved
  return-root target (its location label) and the deepest-miss demand onto `MapperContext`
- **AND** it emits nothing through the `Messager` itself
- **AND** code generation skips the mapper without throwing

#### Scenario: The generic message is transient
- **WHEN** the stage records a "no plan" message and nothing else has recorded a permanent diagnostic
- **THEN** the mapper is deferred rather than consumed, and the message is not emitted in that round

#### Scenario: An earlier targeted diagnostic suppresses the recorded message
- **WHEN** the mapper already has an error (e.g. a constant coercion failure or a rejected declaration)
- **THEN** `RealisationDiagnosticsStage` records no "no plan" message (it returns early on `ctx.hasErrors()`)

#### Scenario: Dead typed siblings at the return location are not recorded
- **WHEN** a container-return method's seeded root `List<E>` is reachable but over-emission left
  unreachable typed siblings (`Set<E>`, `Optional<E>`, scalar `E`, the source-side element types, …)
  at the same return location
- **THEN** no `no plan for tgt[]` message is recorded for any of those siblings; only an unreachable
  *seeded* return root is ever recorded
