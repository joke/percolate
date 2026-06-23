## MODIFIED Requirements

### Requirement: Child scopes render as lambda bodies

A scope-owning `Operation` in the plan SHALL render its child scope's extracted plan as the
per-element lambda body: the lambda **parameter name** SHALL come from the child scope's **element input
declaration** (its `ElementLocation` slot / element type), **independent of whether an element source
`Value` was materialised** — so an element that the child plan never sources still binds its lambda
parameter. The child return-root expression is the lambda result, and the owning Operation's codegen
weaves the container operation (`map`/`flatMap`/`mapPresence`) around it. Lambda-parameter naming is
otherwise unchanged (named after the element type, falling back to `element` when the type is
unavailable; made unique within the method body).

#### Scenario: Stream element mapping renders a lambda
- **WHEN** the plan contains a `map` Operation for `Stream<A> → Stream<B>` owning a child scope
- **THEN** the generated body contains the stream operation applied with a lambda whose body is
  the rendered child plan

#### Scenario: An unused element still binds its lambda parameter
- **WHEN** the plan contains a scope-owning Operation whose child plan maps each element to a value that does not reference the element
- **THEN** the generated lambda still declares its parameter, named from the element input declaration (e.g. `stream.map(element -> <constant>)`), even though no element param-root `Value` was materialised
