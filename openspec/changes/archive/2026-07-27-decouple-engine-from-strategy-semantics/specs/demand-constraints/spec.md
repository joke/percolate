## ADDED Requirements

### Requirement: A demand's candidate set MAY be constrained by a contributed predicate

A `DirectiveReader` SHALL be able to attach one or more **constraints** to a demand through
`DirectiveSink.constrain(targetPath, constraint)`. A constraint SHALL decide, from a candidate `OperationSpec`
and its bound ports alone, whether that candidate is admissible at that demand.

The engine SHALL apply the constraints attached to a demand as a conjunction when landing each candidate, and
SHALL treat the predicate as **opaque** — it SHALL NOT interpret, inspect, or special-case any constraint, and
SHALL name no annotation member, strategy, or feature in doing so.

Constraints SHALL be **demand-scoped**. There SHALL be no constraint form expressing a condition over a plan, a
path, or the graph as a whole.

#### Scenario: A contributed constraint filters a demand's candidates
- **WHEN** a reader attaches a constraint to the demand for `tgt[name]` and three strategies offer productions there
- **THEN** only candidates satisfying the constraint are landed as operations

#### Scenario: The engine does not interpret a constraint
- **WHEN** the engine applies a constraint
- **THEN** it invokes the predicate and branches only on its answer, referencing no annotation member or strategy identity

#### Scenario: An unconstrained demand is unaffected
- **WHEN** no reader attaches a constraint to a demand
- **THEN** every candidate the strategy set offers is landed exactly as before

#### Scenario: Constraints cannot express a plan-wide condition
- **WHEN** the `DirectiveSink` surface is inspected
- **THEN** `constrain` is keyed by a target path, and no entry point accepts a constraint over a plan, a path, or the graph

### Requirement: A constrained-out candidate SHALL be recorded as a refusal

When a constraint refuses a candidate, the engine SHALL record a refusal on the demanded `Value` carrying the
constraint's own message and subject, exactly as a strategy-emitted refusal is recorded (see
`strategy-refusals`). It SHALL NOT emit a diagnostic during expansion.

#### Scenario: A refused candidate leaves an explanation behind
- **WHEN** a constraint refuses the only candidate for a demand
- **THEN** the demand acquires no producer, and its inadmissible list carries the constraint's refusal

#### Scenario: Contradictory constraints are explained by their refusals
- **WHEN** two constraints on one demand admit no common candidate
- **THEN** the demand is unrealised and every filtered candidate's refusal is recorded, so the rendered
  diagnostic names each reason rather than reporting only "no producer"

### Requirement: The engine's built-in landing refusals SHALL use the same primitive

The self-call rule SHALL be expressed as a constraint applied through the same mechanism, not as a bespoke guard
collaborator — that is, the engine's refusal to land an operation whose call target is the enclosing method and
whose argument binds that method's own parameter-root `Value`. Its behaviour SHALL be unchanged: the binding is
refused outright rather than deprioritised, and a self-call on a strict sub-part remains available.

There SHALL be exactly one admissibility mechanism in the engine. A second bespoke landing guard SHALL NOT be
introduced.

#### Scenario: The self-call rule still refuses a degenerate binding
- **WHEN** a container-return method would bridge to itself on its own whole parameter
- **THEN** the binding is refused and no operation is landed, exactly as before

#### Scenario: A self-call on a sub-part remains available
- **WHEN** a method calls itself on an accessor result or a container element
- **THEN** the operation lands, because the argument is not the scope's parameter-root `Value`

#### Scenario: One admissibility mechanism exists
- **WHEN** the expansion collaborators are inspected
- **THEN** candidate admissibility is decided in one place, and no second guard class filters landings

### Requirement: Admissibility SHALL NOT be expressible through operation weight

An operation's `weight` SHALL remain a **preference** input to the cost fold only. The engine SHALL NOT provide,
and no strategy or reader SHALL rely on, any weight value that expresses exclusion. A sentinel weight standing
for "present but unselectable" SHALL NOT exist.

#### Scenario: No sentinel weight remains
- **WHEN** the published `Weights` constants are enumerated
- **THEN** no constant and no predicate expresses an unrealisable or never-selectable weight

#### Scenario: Exclusion is unreachable through cost
- **WHEN** a candidate must be excluded rather than deprioritised
- **THEN** it is refused through the admissibility mechanism, because the cost order places totality above
  weight and cost composition would propagate any discount into unrelated selections
