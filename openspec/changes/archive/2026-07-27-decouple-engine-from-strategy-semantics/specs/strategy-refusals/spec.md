## ADDED Requirements

### Requirement: A strategy's answer SHALL be three-valued

`ExpansionStrategy.expand` and `ExpansionStrategy.descend` SHALL return `Stream<Offer>`, where an `Offer` is
either a **production** (carrying an `OperationSpec`) or a **refusal** (carrying a `Subject` and a message). An
empty stream SHALL continue to mean "this strategy does not apply" and SHALL NOT produce any diagnostic.

A refusal SHALL mean "this demand is mine, and I cannot serve it, because …". A strategy SHALL emit a refusal
only when it recognises the demand as its own; a strategy that does not apply SHALL emit nothing.
Implementations MUST NOT throw on a non-applicable demand, and MUST NOT throw to report a refusal.

`Offer` SHALL be a closed (pseudo-sealed) two-case shape, constructed via `Offer.of(OperationSpec)` and
`Offer.refusal(Subject, String)`.

#### Scenario: A non-applicable strategy stays silent
- **WHEN** `EnumConversion` is handed a demand whose target is not an enum
- **THEN** it returns `Stream.empty()` and no diagnostic is produced for that demand

#### Scenario: An applicable strategy that cannot serve the demand refuses with a reason
- **WHEN** `ConstantValue` is handed a demand whose directive declares `constant = "abc"` and whose target type is `int`
- **THEN** it returns a single `Offer.refusal` whose subject is the `constant` input's subject and whose message names the literal and the target type

#### Scenario: A production is carried by an offer
- **WHEN** `WidenPrimitive` produces an `int → long` widening
- **THEN** it returns a single `Offer.of(spec)` carrying the `OperationSpec` it would previously have returned directly

### Requirement: Refusals SHALL be anchored on the demanded Value

A refusal SHALL be recorded on the demanded `Value` it concerns, in an **inadmissible** list beside that
`Value`'s producers. A refused production SHALL NOT become an `Operation` vertex, SHALL NOT participate in the
cost fold, and SHALL NOT be reachable by plan extraction or code generation.

The recorded shape SHALL name no strategy, feature, or annotation: it SHALL carry only a `Subject` and a
message. `MapperGraph` SHALL NOT gain any per-feature collection.

#### Scenario: A refusal lands on the demand it concerns
- **WHEN** a strategy refuses a demand for `tgt[age]`
- **THEN** the `Value` for `tgt[age]` carries that refusal in its inadmissible list
- **AND** no `Operation` vertex is created for the refused production

#### Scenario: Code generation cannot reach a refusal
- **WHEN** the extracted plan is walked for code generation
- **THEN** no refusal is encountered, because refusals are not `Operation` vertices

#### Scenario: The graph carries no feature-named rejection collection
- **WHEN** `MapperGraph` is inspected
- **THEN** it declares no field, method, or parameter naming a specific strategy, annotation, or feature

### Requirement: Refusals SHALL render at the deepest unsatisfied miss

When a return root is unreachable, the realisation renderer SHALL descend the deepest unsatisfied port chain as
it does today and, at the miss, SHALL report the refusals recorded on that `Value` **instead of** the generic
"no producer" message. When the miss carries no refusal, the generic message SHALL be reported unchanged.

Refusals recorded on a `Value` that is reachable, or that is not on the deepest-miss chain of an unreachable
return root, SHALL NOT be reported. Two refusals against the same `Subject` SHALL both be reported; only
byte-identical messages SHALL be collapsed.

#### Scenario: A refusal replaces the generic message
- **WHEN** a return root is unreachable and the deepest miss carries a refusal "cannot coerce 'abc' to int"
- **THEN** that message is reported, positioned at the refusal's subject
- **AND** the generic "no plan for …" message is not reported for that root

#### Scenario: A miss with no refusal keeps the generic message
- **WHEN** a return root is unreachable and the deepest miss carries no refusal
- **THEN** the generic "no plan for … has no producer in the graph" message is reported unchanged

#### Scenario: A refusal on an over-emitted intermediate is not reported
- **WHEN** a strategy refuses a minted intermediate `Value` that is not on the deepest-miss chain
- **THEN** that refusal produces no diagnostic

#### Scenario: Two distinct refusals against one subject both surface
- **WHEN** two strategies refuse the same `DirectiveInput` with different messages
- **THEN** both messages are reported, each positioned at that input

#### Scenario: Identical refusal text is reported once
- **WHEN** two refusals carry the same subject and byte-identical message text
- **THEN** exactly one diagnostic is reported

### Requirement: Subject SHALL be an opaque position handle constructed only by the SPI

The `percolate-spi` module SHALL define a `Subject` marker type and a `Subjects` factory exposing
`Subjects.of(Element, AnnotationMirror, AnnotationValue)` and `Subjects.none()`. The representation behind a
`Subject` SHALL be owned by the processor; no strategy SHALL be able to inspect or destructure one.

A strategy SHALL obtain a `Subject` only from a `DirectiveInput` it was handed, or from `Subjects.none()`; it
SHALL NOT construct a positioned subject itself. `Subjects.none()` SHALL resolve to the mapper type at emission
time.

#### Scenario: A strategy passes through a subject it received
- **WHEN** a strategy refuses because of a directive input it read
- **THEN** it passes that input's `subject()` into the refusal unchanged

#### Scenario: A refusal with no owning token falls back to the mapper type
- **WHEN** a refusal carries `Subjects.none()`
- **THEN** the emitted diagnostic is positioned at the mapper `TypeElement`

#### Scenario: Subject exposes no accessors to strategies
- **WHEN** the `Subject` type is inspected
- **THEN** it declares no accessor returning an `Element`, `AnnotationMirror`, or `AnnotationValue`

### Requirement: A type variable MAY carry a bound that refuses a grounding

`PortType.variable(int index, Bound bound)` SHALL exist beside `PortType.variable(int index)`, where `Bound`
answers, for one candidate source type and a `ResolveCtx`, either that the grounding is admissible or a
`Refusal` explaining why it is not. The engine's unifier SHALL consult the bound when binding a variable, and
SHALL NOT instantiate a spec for a refused grounding.

An unbounded variable SHALL behave exactly as today. A bound SHALL be evaluated before the grounded spec
competes on cost, so a refused grounding never wins a plan and never reaches code generation.

#### Scenario: A bound rejects an inadmissible source type
- **WHEN** `EnumConversion` declares a variable port bounded to enum sources and a `String` source is offered for grounding
- **THEN** no grounded spec is instantiated for that source, and the bound's refusal is recorded on the demand

#### Scenario: A bound rejects a structurally admissible but unusable source
- **WHEN** `EnumConversion`'s bound finds a source enum constant that no `@MapEnum` override and no same-name match covers
- **THEN** the grounding is refused with a message naming the uncovered constants, and no operation is landed

#### Scenario: An unbounded variable is unaffected
- **WHEN** a strategy declares `PortType.variable(0)` with no bound
- **THEN** grounding-by-match behaves exactly as before, binding any declared or array source type

#### Scenario: A refused grounding never reaches code generation
- **WHEN** a bound refuses every in-scope source for a strategy's variable port
- **THEN** that strategy contributes no operation to the graph and code generation is never invoked for it
