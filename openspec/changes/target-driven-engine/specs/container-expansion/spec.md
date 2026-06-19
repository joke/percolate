## ADDED Requirements

### Requirement: No engine-invented cross-paradigm bridges

The engine SHALL NOT synthesise a conversion between container paradigms that no strategy declared. A collection ↔ reactive bridge (e.g. a blocking `Flux → List` via `collectList().block()`) is produced only if a strategy explicitly emits it; absent that, the demand is reported unrealisable. This keeps reactive code from being silently given a blocking conversion.

#### Scenario: Reactive-to-collection is not auto-bridged
- **WHEN** a `List<B>` target is fed only by a `Flux<A>` source and no strategy declares the bridge
- **THEN** no blocking conversion is generated and the demand has no producer

## MODIFIED Requirements

### Requirement: Containers iterate and collect through an author-declared intermediate

A container SHALL expose its element sequence as an explicit intermediate `Value` of **its own declared type** (a JVM-collection container over `Stream<E>`; a reactive container over its own reactive type). Each container emits a plain `iterate` Operation `Cont<E> → Intermediate<E>` and (if it is a sequence — kind emergent from the presence of `collect`) a plain `collect` Operation `Intermediate<E> → Seq<E>`, **for its own kind only**, referencing no other container kind and **no privileged universal intermediate**. `java.util.stream.Stream` is one container's intermediate, not the engine's.

#### Scenario: A list iterates and a set collects through Stream
- **WHEN** a `Stream<E>` is demanded from a `List<E>` source, and a `Set<E>` from a `Stream<E>` source
- **THEN** the `List` container emits `iterate` (`.stream()`) and the `Set` container emits `collect` (`Collectors.toSet()`), each unaware of the other kind

#### Scenario: A reactive container uses its own intermediate
- **WHEN** a third-party `Flux` container is declared
- **THEN** it iterates/collects through its own reactive intermediate (not `java.util.stream.Stream`), with no engine change

### Requirement: Element mapping is a functor lift grounded by matching

The per-element transform SHALL be a generic functor lift declared per container: *given child `A → B`, produce `F<B> ← F<A>`*, emitted as a scope-owning `OperationSpec` whose input port is `F<A>` for a type variable `A` and whose child scope is `A → B`. The engine grounds `A` by **matching** the `F<A>` port against an in-scope concrete source (see `polymorphic-conversion` / `graph-expansion`), not by the strategy reading candidates and not by demanding an abstract type. The child scope then expands on the same work-list confined to the child scope.

#### Scenario: The map input element type is grounded from the source
- **WHEN** a `Stream<B>` is demanded and a `Stream<A> → Stream<B>` lift is offered with a `Stream<Person>` source in scope
- **THEN** the engine grounds `A := Person`, the child scope becomes `Person → B`, and `B` resolves on the work-list like a method return-root

#### Scenario: The lift names no specific container kind in its grounding
- **WHEN** the functor-lift strategy and its grounding are inspected
- **THEN** the lift declares `F<B> ← F<A>` for its own `F` and the grounding mechanic references no container kind by name

#### Scenario: Nested containers nest scopes
- **WHEN** the target is `List<List<B>>` from `List<List<A>>`
- **THEN** the chosen lift's child plan contains another scope-owning lift Operation
