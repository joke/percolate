## ADDED Requirements

### Requirement: Mapping annotations are read only by directive readers

No class in the `processor` module SHALL depend on a mapping annotation — `@Map`, `@MapList`, `@MapEnum`,
`@MapEnumList`, or `@Ambient`. The single permitted exception SHALL be the processor's `@Mapper` step, which
reads `@Mapper` to decide which types to generate for.

An architecture test SHALL enforce this over the whole `processor` module, matching the annotations by their
exact package rather than by a prefix, so it cannot silently pass by matching nothing. Its failure message
SHALL state the rule it protects: user-facing annotations are read at the directive-reader boundary and never
inside the processor.

#### Scenario: No processor class imports a mapping annotation
- **WHEN** the architecture test analyses the `processor` module
- **THEN** it finds no dependency on `@Map`, `@MapList`, `@MapEnum`, `@MapEnumList`, or `@Ambient`

#### Scenario: The mapper step may read @Mapper
- **WHEN** the architecture test analyses the class implementing the `@Mapper` processing step
- **THEN** its dependency on `@Mapper` is permitted

#### Scenario: The rule matches the annotations exactly
- **WHEN** the rule's package predicate is inspected
- **THEN** it matches the annotations' own package without a trailing wildcard, so it selects the annotation types and not every percolate class

### Requirement: The engine reads no annotations at all

No class in the engine SHALL call `getAnnotationMirrors()`, `getAnnotation(Class)`, or otherwise inspect an
annotation — neither the graph package, nor the expansion collaborators, nor the generation collaborators.
Annotation reading belongs to the directive readers and to the single nullability resolver, both outside the
engine.

This is stated separately from the rule above because an annotation can be matched by name without importing
it, which an import-based rule cannot catch.

#### Scenario: The engine calls no annotation-reading API
- **WHEN** the architecture test analyses the engine packages
- **THEN** it finds no call to `getAnnotationMirrors()` or `getAnnotation(Class)`

#### Scenario: The nullability resolver is unaffected
- **WHEN** the architecture test analyses the nullability resolver
- **THEN** its annotation reading is permitted, because it lies outside the engine packages

#### Scenario: A name-matched annotation is caught
- **WHEN** an engine class matches an annotation by simple name without importing it
- **THEN** the architecture test fails, because the rule targets the reading API rather than the import

## MODIFIED Requirements

### Requirement: Strategies stay myopic

An `ExpansionStrategy` implementation SHALL NOT depend on the engine's internal packages, SHALL NOT receive or
traverse the graph, and SHALL NOT read a candidate snapshot. A strategy decides locally from its `Demand` and its
`ResolveCtx` and returns `Offer`s as plain data. Returning a **refusal** is returning data and SHALL NOT be
treated as a side effect; a strategy SHALL NOT emit a diagnostic, write to the `Messager`, or mutate any shared
state.

A `DirectiveReader` is explicitly **not** myopic in the same sense — it sees a whole mapper method — but it SHALL
likewise depend on no engine internal package and SHALL communicate only through its `DirectiveSink`.

#### Scenario: A strategy reaches no engine internal
- **WHEN** the architecture test analyses the strategy modules
- **THEN** no strategy class depends on an engine internal package

#### Scenario: A strategy emits no diagnostic
- **WHEN** the architecture test analyses the strategy modules
- **THEN** no strategy class depends on the `Messager` or on the processor's diagnostic types

#### Scenario: A reader reaches no engine internal
- **WHEN** the architecture test analyses the directive readers
- **THEN** no reader depends on an engine internal package, and each communicates only through its sink
