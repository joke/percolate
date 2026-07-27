## ADDED Requirements

### Requirement: The validation stages hold no strategy semantics

Every validation stage remaining in the `processor` module SHALL satisfy the test: *with no SPI strategy and no
directive reader on the processor path, would this check still be meaningful?* A stage that re-derives a
strategy's decision, reimplements a strategy's rules, or interprets a mapping annotation's members SHALL NOT
exist in the `processor` module.

#### Scenario: No stage re-derives a strategy decision
- **WHEN** the validation stages are inspected
- **THEN** none walks a candidate set a strategy considers, and none reimplements a rule a strategy applies

#### Scenario: No stage interprets a mapping annotation
- **WHEN** the validation stages are inspected
- **THEN** none reads `@Map`, `@MapEnum`, or `@Ambient`, and none tests how their members combine

#### Scenario: The surviving checks are engine-owned or protocol-owned
- **WHEN** the remaining checks are enumerated
- **THEN** each is about the engine's own contract (a duplicate binding at a path, a source path that roots
  nowhere, two scope inputs sharing one name, a declared input nothing consumed, an unrealised demand, a class
  member declared twice with different definitions)

### Requirement: Two scope inputs of one method SHALL NOT share a name

The core SHALL emit one error for each scope input published under a name an earlier scope input of the same
method already carries, positioned at the offending parameter and naming both the name and the method. A scope
input's name is the parameter's own simple name unless a `DirectiveReader` published an override.

The rule SHALL be stated as a property of the engine's own by-name selection — a name that selects two things
selects neither — and SHALL name no annotation, so it holds identically for a third-party reader.

#### Scenario: Two published names collide
- **WHEN** two parameters of one method are published under the name `ctx`
- **THEN** one error is emitted, positioned at the second parameter and naming `ctx`

#### Scenario: A published name collides with a parameter's own simple name
- **WHEN** a reader renames one parameter to another parameter's own simple name
- **THEN** one error is emitted naming that name

#### Scenario: Distinct names produce no error
- **WHEN** every scope input of a method carries a distinct name
- **THEN** no error is emitted

## MODIFIED Requirements

### Requirement: A method SHALL NOT have duplicate @Map targets

On a single abstract method, two bindings MUST NOT be declared at the same target path, regardless of which
`DirectiveReader` declared them. The core SHALL emit one error for each duplicate occurrence beyond the first,
positioned at the offending binding's `Subject`. The rule is a property of the sink, not of any annotation: it
holds when one reader declares both and when two different readers collide, and the message SHALL name neither.

#### Scenario: Two bindings at the same target produce one error

- **WHEN** a method's readers declare bindings at target path `lastName` twice
- **THEN** one error is emitted for the second occurrence

#### Scenario: Three bindings at the same target produce two errors

- **WHEN** a method's readers declare three bindings at target path `name`
- **THEN** two errors are emitted — one per duplicate beyond the first

#### Scenario: Different targets produce no error

- **WHEN** a method's readers declare bindings at `a` and `b`
- **THEN** no error is emitted

#### Scenario: Two readers colliding at one path is an error

- **WHEN** two different `DirectiveReader`s each declare a binding at target path `name` on one method
- **THEN** one error is emitted, and the message names neither annotation

### Requirement: Duplicate-target errors SHALL point at the offending target literal

Each duplicate-binding error SHALL be positioned at the offending binding's own `Subject`, so that an IDE
underlines the duplicated declaration, not the one that was kept.

#### Scenario: Error carries the offending binding's subject

- **WHEN** a duplicate binding at target path `lastName` is reported
- **THEN** the diagnostic carries the second binding's subject, not the first's

### Requirement: Every @Map directive's source first segment SHALL name a method parameter

This check SHALL apply only to bindings that declare a source path; a binding with no source path SHALL be
skipped entirely. The first segment of every declared source path MUST name a scope input of the method. The
core SHALL emit one error for each binding whose first segment names no scope input, positioned at the
binding's `Subject`.

The rule SHALL be stated as a property of the engine's own forward walk — a path it cannot begin — and SHALL
name no annotation.

#### Scenario: Source first segment matching a parameter produces no error

- **WHEN** a binding on `Human map(Person person)` declares the source path `["person", "first"]`
- **THEN** no error is emitted

#### Scenario: Source first segment naming no parameter produces an error

- **WHEN** a binding on `Human map(Person person)` declares the source path `["custmer", "name"]`
- **THEN** one error is emitted naming the unresolvable first segment

#### Scenario: A binding with no source path is skipped

- **WHEN** a binding declares no source path
- **THEN** the check does not apply and no error is emitted

## REMOVED Requirements

### Requirement: A @Map directive SHALL declare exactly one of source or constant

**Reason**: This is a rule about how `@Map`'s own members combine, and `@Map`'s meaning now lives with the
`DirectiveReader` that owns it rather than in the `processor` module. Applying the capability's own test — with
no reader on the path, `@Map` declares nothing and the rule has nothing to constrain — it fails, so it moves
rather than remaining in core.

**Migration**: The rule is restated in the `directive-reading` capability as an obligation on the owning
reader, which declines to bind and reports the contradiction positioned at the offending member. The
diagnostic's position and message are preserved; only its owner changes.

### Requirement: A defaultValue SHALL accompany a source

**Reason**: As above — a dependency between two `@Map` members is the annotation's own shape rule and belongs
to the reader that owns the annotation.

**Migration**: Restated in `directive-reading` as an obligation on the owning reader, positioned at the
`defaultValue` member.

### Requirement: ValidateSourceParametersStage SHALL NOT halt the pipeline

**Reason**: The non-halting guarantee is no longer a per-stage property to restate. Under the diagnostic model
every check records values and none emits or halts; the pipeline runs to completion for every mapper and the
emitter decides what is reported. Naming one stage in a requirement also outlives the stage itself.

**Migration**: The guarantee is subsumed by the `diagnostics` capability's requirement that diagnostics are
recorded and emitted at one flush point. The equivalent scenarios — other methods and other mappers continue —
are preserved there and in `processor`.

### Requirement: ValidateNoDuplicateTargetsStage SHALL NOT halt the pipeline

**Reason**: Same as above.

**Migration**: Same as above.
