## MODIFIED Requirements

### Requirement: An uncovered source constant fails the compile

Every constant of the source enum SHALL be covered by either a same-name target constant or a `@MapEnum` override. A
source constant covered by neither SHALL cause the build to fail, and SHALL do so as a **positioned compile error**
in every case — the strategy SHALL NOT throw during code generation.

The check SHALL be expressed as a **bound on the strategy's type-variable source port**, so an uncoverable grounding
is refused before it competes on cost. On a Java 14+ target the generated modern switch expression SHALL still omit
a `default` clause, so the compiler's own exhaustiveness check remains a second line of defence.

#### Scenario: Uncovered constant is refused at grounding
- **WHEN** `MyStatus` declares `NEW`, `COMPLETED`, `CANCELLED`, and only `NEW` and `COMPLETED` are covered
- **THEN** the strategy's bound refuses that grounding, no operation is landed, and the recorded refusal names the uncovered `CANCELLED`

#### Scenario: The refusal is reported as a compile error on either tier
- **WHEN** the target is Java 11 or Java 17 with the same enums and coverage
- **THEN** the processor reports a compile error naming the uncovered `CANCELLED` constant, and the processor does not throw

#### Scenario: The modern tier keeps its exhaustiveness check
- **WHEN** a conversion is generated for a Java 17 target
- **THEN** the generated switch expression carries no `default` clause

## ADDED Requirements

### Requirement: A non-enum grounded source is refused, not thrown

The strategy SHALL bound its type-variable source port so a non-enum source is refused at grounding time, rather
than grounding against every in-scope source type unchecked. The strategy SHALL NOT throw when rendering, and a
mapper whose only candidate source is a non-enum type SHALL produce a positioned compile error rather than a
processor crash.

#### Scenario: A non-enum source cannot be grounded
- **WHEN** a mapper declares `Status map(String tag)` and the enum strategy is asked to produce `Status`
- **THEN** the `String` source is refused by the bound, no operation is landed, and the processor does not throw

#### Scenario: The failure is a compile error
- **WHEN** no in-scope source can ground the enum conversion
- **THEN** a compile error is reported naming the demand, and no implementation is generated

#### Scenario: The strategy throws no IllegalStateException
- **WHEN** the strategy's sources are inspected
- **THEN** neither the grounding check nor the coverage check is expressed as a thrown exception

### Requirement: Override entries are read as directive inputs and stamped per entry

The strategy SHALL read its override table as repeated structured directive inputs, each carrying a `source`
member, a `target` member, and its own `Subject`. It SHALL stamp **consumed** exactly those entries it used, so an
entry naming a constant it cannot use is left unconsumed and reported by the generic consumption rail, positioned
at that entry's own token.

The strategy SHALL NOT be accompanied by any core stage that checks override names against enum constants.

#### Scenario: A usable entry is stamped consumed
- **WHEN** an override names a real source constant and a real target constant
- **THEN** the emitted spec stamps that entry consumed and no diagnostic is reported for it

#### Scenario: An entry naming an unknown target constant is reported by the rail
- **WHEN** an override names a target constant the target enum does not declare
- **THEN** the strategy does not stamp that entry, and the consumption rail reports it as having no effect, positioned at that entry

#### Scenario: Sibling entries are unaffected
- **WHEN** one of three override entries is unusable
- **THEN** only that entry is reported; the two consumed entries produce no diagnostic

#### Scenario: No core stage checks override names
- **WHEN** the processor's validation stages are inspected
- **THEN** none reads an override table or compares a name against an enum's constants

### Requirement: Removing the strategy removes its messages

With the enum strategy absent from the processor path, no diagnostic about enum-constant names SHALL be produced.
A declared but unread override table SHALL still be reported generically as having had no effect, by the
consumption rail.

#### Scenario: Constant-name messages leave with the strategy
- **WHEN** the enum strategy is not on the processor path
- **THEN** no diagnostic names an enum constant or an override's validity

#### Scenario: The rail still reports an unread table
- **WHEN** the enum strategy is not on the processor path and a method declares override entries
- **THEN** each entry is reported as declared-but-unconsumed, positioned at its own token
