# Polymorphic Conversion Spec

## Purpose

This spec defines the type-variable port mechanic and the engine's grounding-by-match: an `OperationSpec` port may carry a type variable (e.g. `F<A>`), which the engine grounds by **unifying** the port against an in-scope concrete source, substituting the variable across the spec's output and child scope, and instantiating concretely — one instantiation per matching source, the rest cost-pruned. An abstract type never enters the work-list. Element mapping (`map`/`flatMap`/`mapPresence`) is expressed in this model as a generic functor lift over each container's own intermediate, so collection and reactive paradigms ride the identical mechanic with no engine change, and the engine invents no bridge no strategy declared.

## Requirements

### Requirement: A port type may carry a type variable

The SPI SHALL permit an `OperationSpec` `Port` type to contain a **type variable** (e.g. `F<A>` where `A` is an unbound variable). A strategy uses a type-variable port to declare a **generic** production — one whose concrete input type is determined by the source it binds, not enumerable from the target alone. The output type and child scope of the same `OperationSpec` MAY reference the same variable; grounding the variable SHALL instantiate the whole spec consistently.

#### Scenario: A functor-lift map declares a type-variable input
- **WHEN** an element-mapping strategy emits a `map` for demand `F<B>`
- **THEN** it emits one `OperationSpec` whose output is `F<B>`, whose input port type is `F<A>` for a fresh type variable `A`, and whose child scope is `A → B`

### Requirement: Type-variable ports are sourced by grounding-by-match, never by abstract demand

The engine SHALL source a type-variable port by **matching** it against an in-scope concrete source: it unifies the port type against each available source `Value` type, and for every match it grounds the variable to the source's concrete type, substitutes that type across the `OperationSpec`'s output and child scope, and instantiates the Operation concretely. The engine SHALL NOT place an unbound (type-variable) type on the work-list — only concrete-typed `Value`s are ever demanded. When several sources match, the engine instantiates one Operation per match (over-emit) and prunes the unreachable ones by cost; it never chooses among them.

#### Scenario: The element type is grounded from a concrete source
- **WHEN** `Set<PersonView>` is demanded and a generic `Set<B> ← Set<A>` map is offered, with a `Set<Person>` source in scope
- **THEN** the engine unifies `Set<A>` against `Set<Person>`, grounds `A := Person`, and lands a concrete `Set<Person> → Set<PersonView>` Operation whose child scope is `Person → PersonView`
- **AND** the concrete `Set<Person>` (not an abstract `Set<A>`) is what enters the work-list

#### Scenario: No abstract type ever enters the work-list
- **WHEN** any type-variable port is sourced
- **THEN** the variable is grounded by a match before any demand is enqueued
- **AND** no `Value` is ever created for an unbound type variable

#### Scenario: Multiple matching sources over-emit and prune
- **WHEN** two in-scope sources `Set<Person>` and `Set<Pet>` both unify with a `Set<A>` port whose target element is `PersonView`
- **THEN** the engine instantiates a map per source and lets cost extraction keep only the reachable/realisable one
- **AND** the engine applies no preference of its own between them

### Requirement: Grounding-by-match is a type-system mechanic, agnostic of any SPI

The grounding-by-match unification SHALL operate purely on the type system (same-type, erasure, type-argument structure) and SHALL NOT reference any specific container or conversion kind. It is the direct generalisation of grounding a conversion's input type from a declaration (as a method-call strategy grounds its parameter type from a method signature) — here grounded from a concrete source value instead.

#### Scenario: The mechanic does not name a container kind
- **WHEN** the grounding-by-match implementation is inspected
- **THEN** it references no `Stream`/`Optional`/`Set`/`Flux` type by name; it unifies generic parameterised types structurally

### Requirement: Element mapping is a functor lift over an author-declared intermediate

Element mapping (`map`/`flatMap`/`mapPresence`) SHALL be expressed as a generic functor lift — *given child `A → B`, produce `F<B> ← F<A>`* — declared by each container over its **own** intermediate type, with no privileged universal intermediate. The engine SHALL compose lifts from different containers only through types a strategy actually produces; it SHALL NOT assume `java.util.stream.Stream` (or any single type) as a shared intermediate.

#### Scenario: Two paradigms use the identical mechanism
- **WHEN** a built-in declares `Stream<B> ← Stream<A>` and a third party declares `Flux<B> ← Flux<A>`, each as a functor lift
- **THEN** both are sourced by the same grounding-by-match mechanic with no engine change distinguishing them

### Requirement: The engine invents no bridges

The engine SHALL only ever build Operations that a strategy emitted; it SHALL NOT synthesise a conversion no SPI declared. In particular, no cross-paradigm bridge (e.g. a blocking `Flux → List`) is ever auto-generated: absent a declared edge, the demand is simply reported unrealisable.

#### Scenario: An undeclared cross-paradigm conversion is not invented
- **WHEN** `List<B>` is demanded from a `Flux<A>` source and no strategy declares a `Flux → List` edge
- **THEN** the engine produces no blocking conversion and reports no producer for the demand
- **AND** it does not fabricate a `collectList().block()` Operation

### Requirement: Type-variable instantiation terminates

Grounding-by-match SHALL terminate: instantiations are bounded by the finite in-scope source set, deduped by the grounded concrete type, and collapsed by the `(scope, location, type, nullness)` Value dedup; nested generic grounding SHALL bound its recursion depth.

#### Scenario: A round-trip does not loop
- **WHEN** grounding a type-variable port could re-derive a Value already grounded at the same location and type
- **THEN** Value dedup collapses it and expansion converges (no unbounded instantiation)
