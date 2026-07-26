## MODIFIED Requirements

### Requirement: ExpansionStrategy interface

The `percolate-spi` module SHALL define a single Java interface `io.github.joke.percolate.spi.ExpansionStrategy` with the following shape:

```java
public interface ExpansionStrategy {
    default Stream<Offer> expand(ProduceDemand demand, ResolveCtx ctx) { return Stream.empty(); }
    default Stream<Offer> descend(DescendDemand demand, ResolveCtx ctx) { return Stream.empty(); }
    default int priority() { return 0; }
}
```

This is the sole strategy-author interface for expansion. A strategy SHALL override **exactly one** of `expand`
(a producer answering "what produces this demanded target?") or `descend` (an accessor answering "what does
reading this segment off this parent yield?"). The driver dispatches a produce demand to `expand` and a descend
demand to `descend`, and is the **sole invoker** of both — no helper invokes a strategy. Implementations SHALL
return zero or more `Offer`s, each either a production carrying an `OperationSpec` or a refusal carrying a
`Subject` and a message (see the `strategy-refusals` capability); an empty stream signals "this strategy does
not apply" and produces no diagnostic, and implementations MUST NOT throw on a non-applicable demand.
Implementations SHALL make a purely local decision from their demand (its typed fields, the `Directive`, and —
for `expand` — the declared-children set), SHALL NOT receive or traverse the graph, and SHALL NOT read a
candidate snapshot.

#### Scenario: ExpansionStrategy with no match returns empty
- **WHEN** an implementor decides nothing applies to the demand
- **THEN** the overridden method returns `Stream.empty()` and does not throw

#### Scenario: A strategy overrides exactly one of expand or descend
- **WHEN** a producer strategy (e.g. `ConstructorCall`) and an accessor strategy (e.g. `GetterPathResolver`) are
  inspected
- **THEN** the producer overrides `expand(ProduceDemand, ...)` and the accessor overrides
  `descend(DescendDemand, ...)`, each leaving the other defaulted to empty

#### Scenario: ExpansionStrategy priority defaults to zero
- **WHEN** an implementor does not override `priority()`
- **THEN** `priority()` returns `0`

#### Scenario: Both questions carry the same answer shape
- **WHEN** the two method signatures are compared
- **THEN** both return `Stream<Offer>`, so an accessor can refuse for the same reasons and through the same channel as a producer

### Requirement: Directive type

The `percolate-spi` module SHALL define a `io.github.joke.percolate.spi.Directive` type that exposes the
author-declared configuration in effect at a demand WITHOUT exposing raw compiler internals as the primary
surface. `Directive` SHALL expose exactly two things:

- **`sourcePath()`** — the structural source path the engine walks, as ordered segments, empty when none;
- **`inputs()`** — every other author-declared configuration value, as an ordered list of `DirectiveInput`.

A `DirectiveInput` SHALL carry a `key()` (the name under which a reader declared it), an optional scalar
`value()`, optional named `member(String)` accessors for a structured input, and a `subject()` — an **opaque**
position handle (see `strategy-refusals`). Convenience lookups by key MAY be provided.

The core SHALL NOT enumerate, name, or interpret any key. A strategy reads the keys it owns; a key it does not
recognise is simply not read. Adding a configuration member SHALL require no change to `Directive`, to the
engine, or to any core stage.

#### Scenario: Directive hides compiler internals
- **WHEN** the `Directive` type is inspected
- **THEN** its public accessors expose configuration through `Directive`'s own surface
- **AND** a strategy reading the source path or a declared input does not require importing `javax.lang.model` annotation-mirror types

#### Scenario: Directive exposes a present scalar input
- **WHEN** a strategy reads the input keyed `constant` from a directive declared by `@Map(target = "status", constant = "ACTIVE")`
- **THEN** it observes an input present with the value `"ACTIVE"`

#### Scenario: Directive reports an unwritten member as absent
- **WHEN** a strategy looks up the input keyed `defaultValue` on a directive declared by `@Map(target = "x", source = "in.x")`
- **THEN** no such input is present

#### Scenario: Directive reports an empty-string input as present
- **WHEN** a strategy reads the input keyed `constant` from a directive declared by `@Map(target = "note", constant = "")`
- **THEN** it observes an input present with the empty string as its value, not absent

#### Scenario: Directive exposes a structured input's members
- **WHEN** a strategy reads an input keyed `mapEnum` declared from `@MapEnum(source = "NEW", target = "CREATED")`
- **THEN** it reads `source` as `"NEW"` and `target` as `"CREATED"` through that input's member accessors

#### Scenario: Every input carries a position handle
- **WHEN** a strategy reads any `DirectiveInput`
- **THEN** it can obtain that input's `Subject` and pass it into a refusal, without being able to inspect the underlying element or annotation value

#### Scenario: The core names no key
- **WHEN** the `processor` module's sources are searched for directive key literals
- **THEN** no core class declares or compares against a configuration key name

### Requirement: OperationSpec carries consumed option keys

An `OperationSpec` SHALL carry a set of **consumed directive inputs** — the `DirectiveInput`s the emitting
strategy read to produce it. The set SHALL be **additive and optional**: existing factory entry points that
build a production without inputs SHALL remain source-compatible and yield an empty consumed set. The set is a
neutral structural fact recorded by the strategy that read the input; the processor unions it over the winning
plan to diagnose any declared-but-unconsumed input (see the `directive-options` capability). Consumption SHALL
be recorded **per input**, so one entry of a repeated structured input can be consumed while another is not.
Strategies stay myopic and receive no graph access.

#### Scenario: A production that read an input records it
- **WHEN** a temporal format strategy produces a value by reading the input keyed `format`
- **THEN** the resulting `OperationSpec` carries that `DirectiveInput` among its consumed inputs

#### Scenario: A production that read no input has an empty consumed set
- **WHEN** `WidenPrimitive` produces an `int → long` widening spec
- **THEN** the resulting `OperationSpec` carries an empty consumed set

#### Scenario: Consumption is per entry of a repeated input
- **WHEN** two `@MapEnum` entries are declared and the strategy can use only one
- **THEN** the emitted spec records exactly the used entry as consumed, leaving the other unconsumed

### Requirement: Demand decision context

The demand context SHALL come in two shapes, matching the two strategy questions. Both SHALL extend a common
`Demand` exposing the `Directive` in effect and a nullness oracle:

- a **produce demand** (`ProduceDemand`, handed to `expand`) additionally exposing: the demanded Value's type
  and nullness; the declared bindings at the current target level (for assembly); and the binding/slot name the
  demand serves.
- a **descend demand** (`DescendDemand`, handed to `descend`) additionally exposing: the concrete **parent
  type** (and nullness) being descended and the single source-path **segment** to resolve. It SHALL NOT pun the
  parent as a "target type": the produced output type is the strategy's answer, not a field of the demand.

The `Directive` a descend demand carries SHALL be the directive of the **binding whose source path is being
walked** — not a per-segment directive. Neither shape SHALL expose a candidate snapshot of in-scope source
Values (the engine sources inputs and grounds type-variable ports by matching), nor the graph, nor any handle to
traverse it.

#### Scenario: Assembly reads the goal spec from the produce demand
- **WHEN** `ConstructorCall` matches a produce demand
- **THEN** it reads the declared-children name set from the demand, not from a group

#### Scenario: A descend demand carries the parent type and segment, not a target pun
- **WHEN** `GetterPathResolver` is handed a descend demand for segment `name` on parent `Person`
- **THEN** it reads `Person` as the parent type and `name` as the segment, and its emitted output type is the
  accessor's return type — the demand carries no `targetType()` standing in for the parent

#### Scenario: An accessor reads the walked binding's directive
- **WHEN** an accessor is handed a descend demand for a segment of the source path declared by a binding
- **THEN** `directive()` returns that binding's directive, identical to the one the binding's produce demand carries

#### Scenario: Neither demand shape exposes candidates
- **WHEN** a strategy inspects its demand
- **THEN** there is no `candidates()` accessor; it cannot enumerate in-scope source Values

### Requirement: Port declares an explicit sourcing mode

Each `Port` of an `OperationSpec` SHALL declare how the engine binds its feeding `Value` through **two
orthogonal axes**, so the driver dispatches on declared facts and never reconstructs a port's intent from a
name-match or a boolean, and so no axis names a user-facing feature:

- a **selector**, one of `BY_TYPE` (the feeding `Value` is matched by type and assignment-compatible nullness)
  or `BY_NAME` (the feeding `Value` is the scope input published under the port's binding name);
- an **on-miss** rule, one of `DECLINE` (the operation does not apply), `MINT` (a fresh `FREE` intermediate of
  the port's type and nullness is minted at the output location and re-demanded) or `REQUIRE` (the port could
  not be sourced and this is an error, reported by the engine in port vocabulary).

`SUBTARGET` remains a distinct third case and is not a selection at all: the engine mints a fresh `FREE` demand
at the child location (the parent target path extended by the port's name) and re-demands it. Assembly
strategies stamp their parameter ports `SUBTARGET`.

Named factories SHALL keep call sites readable: a plainly-constructed concrete port SHALL be `BY_TYPE` + `MINT`
(the former `REUSE_OR_MINT` default, so existing concrete-port construction is source-unaffected); a reuse-only
port SHALL be `BY_TYPE` + `DECLINE`; a by-name port SHALL be `BY_NAME` + `REQUIRE` and SHALL carry a non-empty
binding name. A port SHALL NOT carry a field that is meaningless for its selector.

A strategy SHALL choose a port's axes as a purely local decision; they carry **no** graph or candidate access,
and the engine — not the strategy — owns the child location, the scope's named inputs, and every graph mutation.
Each axis SHALL remain **extensible**: a further selector or on-miss rule SHALL be addable without changing the
existing ones or the strategies that declare them.

#### Scenario: An assembly port is a sub-target
- **WHEN** `ConstructorCall` emits a constructor parameter port
- **THEN** the port is a `SUBTARGET` port, and the engine mints a child-target demand at the parent path extended by the port name

#### Scenario: A reuse-only port declines on a miss
- **WHEN** `DirectAssign`, a nullness crossing, or a container `unwrap` emits its consuming input port
- **THEN** the port is `BY_TYPE` with on-miss `DECLINE`, and the engine binds an in-scope source or the Operation does not apply (never minted)

#### Scenario: A default conversion port mints on a miss
- **WHEN** a unary conversion (e.g. `int→long`) emits its value port without specifying axes
- **THEN** the port is `BY_TYPE` with on-miss `MINT`, and the engine binds an in-scope source or mints a fresh intermediate at the output location

#### Scenario: A by-name port names its binding and requires it
- **WHEN** `MethodCallBridge` emits the port for a candidate parameter published under the binding name `order`
- **THEN** the port is `BY_NAME` with on-miss `REQUIRE` and carries the binding name `"order"`
- **AND** the engine, not the strategy, resolves that name against the enclosing scope's named inputs

#### Scenario: No port carries a field meaningless for its selector
- **WHEN** a `BY_TYPE` port is inspected
- **THEN** it exposes no binding-name field standing empty

#### Scenario: The axes name no feature
- **WHEN** the selector and on-miss values are enumerated
- **THEN** none of them names an annotation, a strategy, or a user-facing feature

### Requirement: Two strategy questions, both candidate-free and myopic

Every `ExpansionStrategy` SHALL answer one of **two** questions and return `Offer`s: a **produce** question
("what produces this demanded target?", via `expand`) or a **descend** question ("what does reading this segment
off this parent yield?", via `descend`). A producer is distinguished only by what it reads from its produce
demand — the `targetType` (conversions, containers) or the `declaredChildren` (assembly). An accessor reads the
parent type and segment of its descend demand, and MAY read the walked binding's directive. No strategy of
either kind reads a candidate snapshot to decide what to emit; the engine sources every input port. The
element-mapping case that needs a source element type declares a **type-variable port** (see
`polymorphic-conversion`), grounded by the engine, not enumerated by the strategy. These two questions are the
only expansion surfaces; there SHALL be no third, and both keep strategy decisions purely local (no graph, no
candidates).

#### Scenario: Producers and accessors both read no candidates
- **WHEN** any conversion/assembly/container producer or any accessor decides what to emit
- **THEN** it reads only its demand (a produce demand's target/nullness/directive/declared-children, or a descend
  demand's parent type, segment and directive) — never an in-scope candidate list

#### Scenario: Accessors answer the descend question, producers the produce question
- **WHEN** the strategy surface is inspected
- **THEN** conversions, containers, assembly, and nullness crossings answer `expand`; the getter / method / field
  accessors answer `descend`; no strategy answers both

#### Scenario: Refusing is not a third question
- **WHEN** a strategy refuses
- **THEN** it does so within one of the two questions, returning a refusal `Offer` — no separate validation or explanation entry point exists on the interface

## ADDED Requirements

### Requirement: Directive keys are declared by their owning strategy

Each strategy that reads a directive input SHALL declare the key it reads as its own constant, in the module
that owns the input's meaning. A key SHALL NOT be declared in the `processor` module, in `Directive`, or in any
core stage.

#### Scenario: A key constant lives with its reader
- **WHEN** the constant for the `format` key is located
- **THEN** it is declared in the temporal strategy that reads it, not in a core class

#### Scenario: A third-party key needs no registration
- **WHEN** a third-party strategy reads an input keyed by a name only it knows
- **THEN** no core registration, enum constant, or spec change is required for that key to travel with the demand

## REMOVED Requirements

### Requirement: Directive carries enum constant overrides

**Reason**: The requirement described a bespoke, typed `enumOverrides()` accessor on `Directive` for one
feature's annotation. Under the open directive bag every author-declared value — including a repeated
`@MapEnum` — travels as a `DirectiveInput` with named members, so a per-feature accessor would reintroduce the
closed, core-owned vocabulary the bag removes. Its behaviour is preserved generically by the "Directive type"
requirement's structured-input scenarios.

**Migration**: A strategy reading `directive.enumOverrides()` reads `directive.inputs("mapEnum")` instead, taking
each entry's `source` and `target` members. The declaration order guarantee is unchanged, and each entry now
additionally carries its own `Subject` for positioning and its own consumption record.
