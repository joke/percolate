# Expansion Strategy SPI Spec (delta)

## MODIFIED Requirements

### Requirement: ExpansionStrategy interface

The `percolate-spi` module SHALL define a single Java interface `io.github.joke.percolate.spi.ExpansionStrategy`
with the following shape:

```java
public interface ExpansionStrategy {
    default Stream<OperationSpec> expand(ProduceDemand demand, ResolveCtx ctx) { return Stream.empty(); }
    default Stream<OperationSpec> descend(DescendDemand demand, ResolveCtx ctx) { return Stream.empty(); }
    default int priority() { return 0; }
}
```

This is the sole strategy-author interface for expansion. A strategy SHALL override **exactly one** of `expand`
(a producer answering "what produces this demanded target?") or `descend` (an accessor answering "what does
reading this segment off this parent yield?"). The driver dispatches a produce demand to `expand` and a descent
obligation to `descend`, and is the **sole invoker** of both — no helper invokes a strategy. Implementations
SHALL return zero or more `OperationSpec`s; an empty stream signals "this strategy does not apply", and
implementations MUST NOT throw on a non-applicable demand. Implementations SHALL make a purely local decision
from their demand (its typed fields, the `@Map` directive, and — for `expand` — the declared-children set), SHALL
NOT receive or traverse the graph, and SHALL NOT read a candidate snapshot.

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

### Requirement: Demand decision context

The demand context SHALL come in two shapes, matching the two strategy questions:

- a **produce demand** (`ProduceDemand`, handed to `expand`) exposing: the demanded Value's type and nullness;
  the binding `Directive` in effect (its `defaultValue` / `constant`, carried by the work-list, see
  `graph-expansion`); the declared bindings at the current target level (for assembly); the binding/slot name the
  demand serves; and a nullness oracle.
- a **descend demand** (`DescendDemand`, handed to `descend`) exposing: the concrete **parent type** (and
  nullness) being descended and the single source-path **segment** to resolve, plus a nullness oracle. It SHALL
  NOT pun the parent as a "target type": the produced output type is the strategy's answer, not a field of the
  demand.

Neither shape SHALL expose a candidate snapshot of in-scope source Values (the engine sources inputs and grounds
type-variable ports by matching), nor the graph, nor any handle to traverse it.

#### Scenario: Assembly reads the goal spec from the produce demand
- **WHEN** `ConstructorCall` matches a produce demand
- **THEN** it reads the declared-children name set from the demand, not from a group

#### Scenario: A descend demand carries the parent type and segment, not a target pun
- **WHEN** `GetterPathResolver` is handed a descend demand for segment `name` on parent `Person`
- **THEN** it reads `Person` as the parent type and `name` as the segment, and its emitted output type is the
  accessor's return type — the demand carries no `targetType()` standing in for the parent

#### Scenario: Neither demand shape exposes candidates
- **WHEN** a strategy inspects its demand
- **THEN** there is no `candidates()` accessor; it cannot enumerate in-scope source Values

### Requirement: Nullness crossings and source accessors are strategies

Nullness crossings and source accessors SHALL be ordinary `ExpansionStrategy` implementations, not
engine-resident productions, registered through the existing `ServiceLoader` / `@AutoService` mechanism. A
crossing strategy is a **producer** (overriding `expand`): keyed on the demanded target it over-emits the
crossings that can produce it — a partial `[requireNonNull]` for a `NON_NULL` reference-scalar demand and (with a
declared `defaultValue`) total `[coalesce]` forms — each over a **reuse-only** input port the driver binds to the
in-scope nullable scalar / `Optional<T>` source (or the operation does not apply); it reads the slot name and any
`defaultValue` from the produce demand and reads no candidate. A source accessor is an **accessor** (overriding
`descend`): given a concrete parent type and one segment it emits one unary accessor `Operation` whose output
type it discovers; the driver lands it forward (parent already materialised) and walks to the next segment. An
accessor SHALL NOT re-demand a shallower `SourceLocation` (no backward parent re-demand).

#### Scenario: requireNonNull is a service-loadable producer strategy
- **WHEN** `ServiceLoader.load(ExpansionStrategy.class)` is enumerated
- **THEN** the nullness-crossing strategy is present, and for a `NON_NULL` reference-scalar produce demand it
  emits `[requireNonNull]` (and `[coalesce]` when the directive declares a default) over reuse-only ports

#### Scenario: An accessor descends from an already-landed parent
- **WHEN** the driver descends the `street` segment with the `p.address` `Value` already landed
- **THEN** an accessor strategy emits the `getStreet()` Operation producing `SourceLocation[p, address, street]`
  from the landed `address` parent, with no re-demand of `SourceLocation[p, address]` and no eager whole-path
  materialization

## REMOVED Requirements

### Requirement: One uniform target-driven strategy surface

**Reason**: Forward target-bound descent cannot be phrased as "what produces this demanded target?" — when the
driver descends a segment off a known parent, the child's type is unknown until the accessor answers, so there is
no demanded-target type to key on. The single-question surface is replaced by a two-question surface (produce +
descend). Strategy myopia and candidate-freedom are preserved.

**Migration**: See the new requirement "Two strategy questions, both candidate-free and myopic". Producers keep
answering "what produces this target?" via `expand(ProduceDemand)`; accessors move to `descend(DescendDemand)`
("what does reading this segment off this parent yield?"). No strategy reads candidates in either form.

## ADDED Requirements

### Requirement: Two strategy questions, both candidate-free and myopic

Every `ExpansionStrategy` SHALL answer one of **two** questions and return `OperationSpec`s: a **produce**
question ("what produces this demanded target?", via `expand`) or a **descend** question ("what does reading this
segment off this parent yield?", via `descend`). A producer is distinguished only by what it reads from its
produce demand — the `targetType` (conversions, containers) or the `declaredChildren` (assembly). An accessor
reads only the parent type and segment of its descend demand. No strategy of either kind reads a candidate
snapshot to decide what to emit; the engine sources every input port. The element-mapping case that needs a
source element type declares a **type-variable port** (see `polymorphic-conversion`), grounded by the engine, not
enumerated by the strategy. These two questions are the only expansion surfaces; there SHALL be no third, and
both keep strategy decisions purely local (no graph, no candidates).

#### Scenario: Producers and accessors both read no candidates
- **WHEN** any conversion/assembly/container producer or any accessor decides what to emit
- **THEN** it reads only its demand (a produce demand's target/nullness/directive/declared-children, or a descend
  demand's parent type and segment) — never an in-scope candidate list

#### Scenario: Accessors answer the descend question, producers the produce question
- **WHEN** the strategy surface is inspected
- **THEN** conversions, containers, assembly, and nullness crossings answer `expand`; the getter / method / field
  accessors answer `descend`; no strategy answers both
