# Enum Conversion Spec

## Purpose

Defines enum-to-enum mapping: a user-declared conversion method whose body percolate generates, with automatic
same-name constant matching, `@MapEnum` per-constant overrides, and compile-time coverage safety (javac
exhaustiveness on a Java 14+ target, percolate's own coverage validation on the Java 11 classic tier). The
conversion is realised as an ordinary `ExpansionStrategy` via graph expansion — no dedicated processor stage.

## Requirements

### Requirement: The @MapEnum annotation

The `annotations` module SHALL provide a public, repeatable `@MapEnum` annotation targeting methods, with two
`String` members `source` and `target` naming one source enum constant and the target enum constant it maps to. It
SHALL be retained at least until annotation processing (`CLASS` retention) and be documented. Its container
annotation SHALL allow multiple `@MapEnum` declarations on one method.

#### Scenario: @MapEnum is repeatable on a method
- **WHEN** a mapper method is annotated with `@MapEnum(source = "NEW", target = "CREATED")` and
  `@MapEnum(source = "COMPLETED", target = "FULFILLED")`
- **THEN** both declarations are retained on that method and readable by the processor

### Requirement: Enum-to-enum conversion via a declared conversion method

The processor SHALL generate the body of an abstract single-parameter `@Mapper` method whose parameter type and
return type are both `enum` declarations, producing the return enum from the parameter enum. A bean mapper that maps
a member of the target enum type from a source of the parameter enum type SHALL be satisfied by calling that declared
method, through the existing callable-method discovery and method-call bridge — enum conversion introduces no new
bridge machinery and no dedicated processor stage.

#### Scenario: An abstract enum-to-enum method gets a generated body
- **WHEN** a `@Mapper` declares `OrderStatus toStatus(MyStatus s)` with both types `enum`
- **THEN** the processor generates an implementation of `toStatus` that returns an `OrderStatus` for its `MyStatus`
  argument

#### Scenario: A bean member bridges through the declared enum method
- **WHEN** the same mapper also declares `Order map(OrderDto dto)` where `Order.status` is `OrderStatus` and
  `OrderDto.status` is `MyStatus`
- **THEN** the generated `map` produces `order.status` by calling `toStatus(dto.getStatus())`, reusing the existing
  method-call bridge

### Requirement: Same-named constants map automatically

The generated conversion SHALL map a source constant whose simple name equals a constant name declared on the
target enum to that identically-named target constant, without any `@MapEnum` directive.

#### Scenario: Mirrored enums map by name with no directives
- **WHEN** `Status` and `StatusDto` both declare exactly `CREATED`, `FULFILLED`, `ARCHIVED` and the method
  `Status toStatus(StatusDto s)` carries no `@MapEnum`
- **THEN** the generated body maps each `StatusDto` constant to the identically-named `Status` constant

### Requirement: @MapEnum overrides same-name matching

The processor SHALL read the `@MapEnum` directives declared on the conversion method and map each named source
constant to its named target constant. An explicit `@MapEnum` for a source constant SHALL take precedence over any
same-name match for that source constant. A `@MapEnum` whose `source` or `target` does not name a real constant of
the respective enum SHALL be reported as a compile error.

#### Scenario: Differently-named constants map via @MapEnum
- **WHEN** `MyStatus` declares `NEW`, `COMPLETED`, the method `OrderStatus toStatus(MyStatus s)` carries
  `@MapEnum(source = "NEW", target = "CREATED")` and `@MapEnum(source = "COMPLETED", target = "FULFILLED")`
- **THEN** the generated body maps `NEW → CREATED` and `COMPLETED → FULFILLED`

#### Scenario: @MapEnum naming a non-existent constant is an error
- **WHEN** a `@MapEnum(source = "NEW", target = "DELIVERED")` names `DELIVERED`, which `OrderStatus` does not declare
- **THEN** the compile fails with a diagnostic identifying the unknown constant

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

### Requirement: Extra target constants are permitted

A target enum MAY declare constants that no source constant maps to. Such unreachable target constants SHALL NOT be
an error.

#### Scenario: A target-only constant is allowed
- **WHEN** `OrderStatus` declares `CREATED`, `FULFILLED`, `ARCHIVED` and no source constant maps to `ARCHIVED`
- **THEN** the conversion compiles and `ARCHIVED` is simply never produced

### Requirement: The generated switch form is selected by the strategy from switch.style

The generated conversion SHALL render as a classic switch statement (Java 11-compatible) or a modern switch
expression, chosen by the `switch.style` option. `AUTO` SHALL select the modern arrow expression when the target
`SourceVersion` is Java 14 or later and the classic statement otherwise. `CLASSIC` SHALL always render the classic
statement; `ARROW` SHALL always render the modern expression. This selection SHALL be made by the enum conversion
strategy; the engine SHALL make no part of it.

#### Scenario: AUTO on a Java 11 target renders a classic switch statement
- **WHEN** `switch.style` is absent (AUTO) and the target is Java 11
- **THEN** the generated body is a classic switch statement compilable on Java 11

#### Scenario: AUTO on a Java 17 target renders a modern switch expression
- **WHEN** `switch.style` is absent (AUTO) and the target is Java 17
- **THEN** the generated body is a modern arrow switch expression

#### Scenario: Explicit CLASSIC renders the classic statement regardless of target
- **WHEN** `switch.style` is `CLASSIC` and the target is Java 17
- **THEN** the generated body is a classic switch statement

### Requirement: Enum conversion is realised as a graph-expansion strategy

Enum conversion SHALL be produced by an `ExpansionStrategy` through ordinary graph expansion, not by any dedicated
processor stage. The strategy SHALL obtain the concrete source enum by declaring a type-variable input port that the
engine grounds against the in-scope source; it SHALL emit a production only when both the demanded target and the
grounded source are enum declarations.

#### Scenario: The source enum is obtained by grounding, not by a stage
- **WHEN** the demand to produce the target enum is expanded
- **THEN** the strategy declares a type-variable input port that grounds to the in-scope source enum, and no
  enum-specific processor pipeline stage participates

#### Scenario: A non-enum grounded source yields no production
- **WHEN** the strategy's type-variable port would ground against a non-enum source
- **THEN** the strategy emits no production for that binding

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
