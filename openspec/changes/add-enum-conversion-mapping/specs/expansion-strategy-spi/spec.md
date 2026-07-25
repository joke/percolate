## ADDED Requirements

### Requirement: BodyCodegen complete-body codegen shape

The `io.github.joke.percolate.spi` codegen surface SHALL provide a `BodyCodegen` shape, a sibling of
`OperationCodegen`, whose render returns a **complete method body** (a statement sequence, e.g. a classic `switch`
statement, or a single `return` of an expression) rather than a bare inline expression. `BodyCodegen` SHALL be
additive: it does not replace `OperationCodegen`, and the existing `OperationCodegen` (scalar `render`) /
`ScopeCodegen` (`weave`) split is unchanged. A strategy signals that its production renders as a whole body by
supplying a `BodyCodegen`; the engine dispatches on which shape the strategy supplied and makes no code-generation
choice of its own.

#### Scenario: BodyCodegen exists alongside OperationCodegen
- **WHEN** the `io.github.joke.percolate.spi` codegen surface is inspected
- **THEN** it declares `BodyCodegen` returning a complete method body, in addition to `OperationCodegen` and
  `ScopeCodegen`

#### Scenario: A production may carry a BodyCodegen instead of an OperationCodegen
- **WHEN** a strategy produces an `OperationSpec` whose codegen is a `BodyCodegen`
- **THEN** the spec is valid and the engine renders it as a whole body (see `code-generation`)

### Requirement: Source-aware render context for BodyCodegen

A `BodyCodegen` SHALL render against a context that is a **superset of `IncomingValues`** — carrying the same
port-keyed incoming expressions — and that additionally exposes, per port, the **grounded concrete `TypeMirror`**
bound to that port, a `ResolveCtx`, the effective `switch.style` value, and the target `SourceVersion`. This lets a
conversion whose emitted text depends on the source's shape (e.g. enumerating a source enum's constants) read the
grounded source type and choose its rendering, while remaining myopic: the context exposes only the resolved types of
the operation's own ports, never a graph or candidate-Value snapshot. `OperationCodegen.render` SHALL continue to
receive only `IncomingValues`.

#### Scenario: BodyCodegen context surfaces the grounded source type
- **WHEN** a `BodyCodegen` renders for a production whose input port grounded to `MyStatus`
- **THEN** its context returns the grounded `TypeMirror` `MyStatus` for that port, plus a `ResolveCtx`, the effective
  `switch.style`, and the target `SourceVersion`

#### Scenario: The context exposes no graph or candidate snapshot
- **WHEN** the `BodyCodegen` render context is inspected
- **THEN** it exposes only the operation's own port expressions and grounded port types, with no in-scope source
  Value snapshot and no engine/graph surface

#### Scenario: OperationCodegen still receives only IncomingValues
- **WHEN** an `OperationCodegen` production renders
- **THEN** its sole render argument remains an `IncomingValues`, unchanged by this capability

### Requirement: Directive carries enum constant overrides

The `Directive` type SHALL additionally carry an **enum override table** — an ordered set of source-constant-name to
target-constant-name pairs sourced from `@MapEnum` — travelling with the demand exactly as the existing directive
members do (never stamped on a produced Value). The table SHALL be empty when no `@MapEnum` is in effect, and its
presence SHALL NOT affect any non-enum production. Existing `Directive` members and their behaviour are unchanged.

#### Scenario: Directive exposes the @MapEnum override pairs
- **WHEN** the conversion method carries `@MapEnum(source = "NEW", target = "CREATED")`
- **THEN** the `Directive` in effect for that method's return demand exposes the pair `NEW → CREATED` in its enum
  override table

#### Scenario: No @MapEnum yields an empty override table
- **WHEN** no `@MapEnum` is declared
- **THEN** the `Directive`'s enum override table is empty and no non-enum production observes any change
