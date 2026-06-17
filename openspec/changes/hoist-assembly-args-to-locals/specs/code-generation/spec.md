## ADDED Requirements

### Requirement: Assembly arguments hoist to local variables

The generate stage SHALL materialise a plan `Value` as a local variable declaration
(`<Type> <name> = <expr>;`), emitted before the statement that uses it and referenced by `<name>`
at each use site, **iff** that `Value` feeds a port of an **n-ary** `Operation`
(`Operation.getPorts().size() >= 2`) — that is, the arguments of multi-argument constructor / assembly
calls become named locals. A `Value` whose only consumers are single-port Operations (container
`iterate` / `collect` / `flatMap` / `wrap` / `unwrap`, conversions, accessors, nullness crossings)
SHALL remain inline, so a fluent container pipeline still renders as one threaded chain (consistent
with "Stream stages render as a threaded pipeline").

A `Value` with **no chosen producer** — a bare parameter root or an element-lambda variable, which
already renders as a simple name — SHALL NOT be hoisted, even when it feeds an n-ary port; aliasing it
into `Person v = person;` would add only noise.

The method return-root `Value` SHALL render inline as the `return` expression — never as a trailing
temporary (`return new Human(v0, v1, v2);`, not `Human v = ...; return v;`).

Local declarations within a scope SHALL be emitted in dependency (topological) order, so every
variable is declared before its first reference; the plan is acyclic, so a post-order walk of the
chosen-producer DAG yields such an order.

The hoist decision and variable naming SHALL be a **pure function of the extracted plan**, implemented
as a separable helper within the generate stage. It SHALL NOT mutate the `MapperGraph` or the
`ExtractedPlan`, SHALL NOT add a codegen intermediate representation, and SHALL be invisible to
strategies (a strategy still receives its operand `CodeBlock`s through `IncomingValues` and cannot
observe whether an operand is an inline expression or a variable reference).

#### Scenario: Constructor arguments become locals
- **WHEN** the chosen producer of `mapAddress`'s return-root is `new Human.Address(int number, String street)` (two ports)
- **THEN** the body declares a local for each argument (e.g. `int <n1> = address.getNumber();` and `String <n2> = address.getStreet();`) before `return new Human.Address(<n1>, <n2>);`

#### Scenario: Single-port chains stay inline
- **WHEN** the plan for a target is a container pipeline `wrap ⟵ collect ⟵ map ⟵ flatMap ⟵ iterate`, every stage single-port
- **THEN** the pipeline renders as one fluent chain with no intermediate locals between the stages
- **AND** only the chain's overall result is hoisted, and only when it feeds an n-ary assembly port

#### Scenario: Return-root renders inline
- **WHEN** the return-root's chosen producer is an n-ary constructor
- **THEN** the body's final statement is `return new <Type>(<arg references>);`, with no `<Type> v = ...; return v;` temporary

#### Scenario: Hoisting is invisible to strategies
- **WHEN** an n-ary Operation's `OperationCodegen.render(IncomingValues)` is invoked for a hoisted argument
- **THEN** the `IncomingValues` operand it reads is a variable-reference `CodeBlock`, and the strategy makes no distinction from an inline-expression operand

#### Scenario: A bare parameter argument is not aliased
- **WHEN** a two-arg producer `new Pair(Person, Person)` is fed directly by the parameter Values `person` and `person2` (no chosen producer)
- **THEN** the body renders `return new Pair(person, person2);` with no `Person v = person;` aliasing statements

### Requirement: A shared Value is materialised once

The generate stage SHALL render a `Value` consumed by more than one in-plan port exactly once — as a
single local variable referenced at each use site — rather than re-rendering (and so re-evaluating)
its producing expression per use. A shared `Value` SHALL be hoisted by virtue of being shared, even
when it would otherwise render inline. The generator SHALL NOT rely on accessor idempotency to excuse
duplicate evaluation.

#### Scenario: A value feeding two ports is emitted once
- **WHEN** one `Value` `name:String` feeds ports of two in-plan Operations
- **THEN** the generated code declares one local for it and references that local at both use sites, evaluating the producing expression exactly once

## MODIFIED Requirements

### Requirement: Method bodies render by walking the extracted plan

`BuildMethodBodies` SHALL compose each method body by walking the extracted plan view
(`plan-extraction`) from the method's return-root `Value`: render the Value's chosen producer
`Operation` by invoking its codegen with `IncomingValues` keyed by **port name**, where each incoming
value is the recursively materialised port `Value` — a **variable reference** when that `Value` is
hoisted to a local (see "Assembly arguments hoist to local variables"), otherwise its inline
expression. Producer identity is structural — the generator SHALL NOT infer it from shared codegen
instances, edge labels, or any grouping label, and SHALL NOT read `Nullability` to decide wiring.

#### Scenario: Fan-in renders from the chosen producer
- **WHEN** the return-root's chosen producer is `new Address(int,String)` with ports `number`,
  `street`
- **THEN** the body renders that Operation's codegen once, with incoming values keyed `number` and
  `street`

#### Scenario: No group or label reads
- **WHEN** the source of `BuildMethodBodies` is inspected
- **THEN** it references no grouping label, no group id, and no edge-carried consumer slot
