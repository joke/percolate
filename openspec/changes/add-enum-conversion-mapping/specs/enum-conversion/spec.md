## ADDED Requirements

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

For a source constant whose simple name equals a constant name declared on the target enum, the generated conversion
SHALL map the source constant to that identically-named target constant without any `@MapEnum` directive.

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
source constant covered by neither SHALL cause the build to fail. On a Java 14+ target the generated modern switch
expression SHALL omit a `default` clause, so the compiler's own exhaustiveness check rejects the uncovered constant;
on the Java 11 classic tier — where the compiler does not check statement-switch totality — the processor SHALL
report a diagnostic naming the uncovered source constant.

#### Scenario: Uncovered constant fails on the modern tier via exhaustiveness
- **WHEN** the target is Java 17, `MyStatus` declares `NEW`, `COMPLETED`, `CANCELLED`, and only `NEW` and `COMPLETED`
  are covered
- **THEN** the generated switch expression carries no `default` and the compile fails on the uncovered `CANCELLED`

#### Scenario: Uncovered constant fails on the classic tier via a diagnostic
- **WHEN** the target is Java 11 with the same enums and coverage
- **THEN** the processor reports a compile error naming the uncovered `CANCELLED` constant

### Requirement: Extra target constants are permitted

A target enum MAY declare constants that no source constant maps to. Such unreachable target constants SHALL NOT be
an error.

#### Scenario: A target-only constant is allowed
- **WHEN** `OrderStatus` declares `CREATED`, `FULFILLED`, `ARCHIVED` and no source constant maps to `ARCHIVED`
- **THEN** the conversion compiles and `ARCHIVED` is simply never produced

### Requirement: The generated switch form is selected by the strategy from switch.style

The generated conversion SHALL render as a classic switch statement (Java 11-compatible) or a modern arrow switch
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
