## ADDED Requirements

### Requirement: TypeProbe type-introspection helper

The `percolate-spi` module SHALL ship a public utility `io.github.joke.percolate.spi.TypeProbe` exposing the general type-introspection primitives every strategy otherwise re-rolls: `asTypeElement(TypeMirror)`, `isType(TypeMirror, fqn)`, `isEnum(TypeMirror)`, and `simpleName(TypeMirror)`. `TypeProbe` SHALL hold only general primitives; the container-flavoured `Containers` helper SHALL delegate its declared-type checks to it without duplicating the FQN-match logic.

#### Scenario: Containers delegates to TypeProbe
- **WHEN** `Containers.isOptional`/`isStream`/`isList`/`isSet` resolve a type
- **THEN** the FQN/erasure match is performed by `TypeProbe.isType`, not re-implemented in `Containers`

### Requirement: One uniform target-driven strategy surface

Every `ExpansionStrategy` SHALL answer a single question — "what produces this demanded target?" — and return `OperationSpec`s. A strategy is distinguished only by **what it reads from the demand**: the `targetType` (conversions, containers), the `declaredChildren` (assembly), or the `directive` source segment (accessors). No strategy reads a candidate snapshot to decide what to emit; the engine sources every input port. The element-mapping case that needs a source element type declares a **type-variable port** (see `polymorphic-conversion`), grounded by the engine, not enumerated by the strategy.

#### Scenario: A producer reads no candidates
- **WHEN** any conversion/assembly/accessor/container strategy decides what to emit
- **THEN** it reads only the demanded target, its nullness, the directive, and the declared children — never an in-scope candidate list

### Requirement: Source-facing SourceProjection SPI

The `percolate-spi` module SHALL ship a second author interface, `io.github.joke.percolate.spi.SourceProjection`, parallel to `ExpansionStrategy` and discovered the same way (`ServiceLoader`). Where an `ExpansionStrategy` answers "what produces this **target**?", a `SourceProjection` answers "what other types may this in-scope **source** be viewed as?" — its only effect is to contribute extra grounding-match candidates (design D8). A projection SHALL be consulted **only** to widen grounding-by-match's match set; it SHALL NOT receive or traverse the graph, and it SHALL return an empty stream for a source it does not recognise. The engine SHALL consume the projected types **structurally** (unifying them like any other source type) and SHALL name no container kind.

#### Scenario: A collection source is projected to its element stream
- **WHEN** a `List<X>` source is in scope and a stream container's `SourceProjection` is registered
- **THEN** the projection contributes `Stream<X>`, so a type-variable `Stream<A>` element-map port grounds `A := X` and the concrete `Stream<X>` is produced target-driven by the container's own `iterate`

#### Scenario: An unrecognised source projects nothing
- **WHEN** a `SourceProjection` is handed a source it does not recognise
- **THEN** it returns an empty stream and contributes no grounding candidate (no bridge is invented)

## MODIFIED Requirements

### Requirement: Strategy author mixins

The `percolate-spi` module SHALL provide the abstract `Container` base for declaring a container in one class, plus archetype convenience bases for the recurring target-driven shapes (conversion, accessor) — all on the single uniform `ExpansionStrategy.produce` surface. The `Container` base SHALL implement **both** `ExpansionStrategy` (its kind-local target-driven ops) and `SourceProjection` (projecting its kind to its own intermediate), so a container author still writes a single class. There SHALL be **no candidate-iterating mixin**: the former `CombinatorialMatch` (whose default `expand` iterated `demand.candidates()` and delegated to a per-`(from,to)` method) is removed, because the engine, not the strategy, sources inputs. A container declares its type predicate, element extractor, kind-local operation snippets, and its functor-lift `map` over its own intermediate; the base emits target-driven `OperationSpec`s (the lift carrying a type-variable input port).

#### Scenario: No candidate-iterating mixin exists
- **WHEN** the `io.github.joke.percolate.spi` package is inspected
- **THEN** there is no `CombinatorialMatch` (or any mixin whose default `expand` iterates `demand.candidates()`)

#### Scenario: A container is authored on the uniform surface
- **WHEN** a developer declares a container
- **THEN** it extends `Container`, supplying `matches`/`element`, its kind-local snippets, and its functor-lift `map`, and it reads no candidates

### Requirement: Demand decision context

Strategies SHALL receive a demand context exposing: the demanded Value's type and nullness; the binding `Directive` in effect (carried by the work-list, see `graph-expansion`); the declared bindings at the current target level (for assembly strategies); the **binding/slot name** the demand serves (so a crossing strategy can name it, e.g. in a `requireNonNull` message); and a nullness oracle. The context SHALL NOT expose a candidate snapshot of in-scope source Values: sourcing inputs is the engine's job (it binds each `OperationSpec` port to an in-scope source or a fresh intermediate, and grounds type-variable ports by matching). The context exposes neither the graph nor any handle to traverse it.

#### Scenario: Assembly reads the goal spec from the context
- **WHEN** `ConstructorCall` matches a demand
- **THEN** it reads the declared-children name set from the demand context, not from a group

#### Scenario: The demand context exposes no candidates
- **WHEN** a strategy inspects its demand context
- **THEN** there is no `candidates()` accessor; the strategy cannot enumerate in-scope source Values
