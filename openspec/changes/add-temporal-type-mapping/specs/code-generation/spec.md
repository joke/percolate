## MODIFIED Requirements

### Requirement: Generated class shape

For every `@Mapper`-annotated interface (or abstract class) `<Name>` for which `GenerateStage` emits code, the generated `JavaFile` SHALL contain a single top-level type with the following shape:

- **Package**: the same package as the `@Mapper`-annotated type.
- **Class name**: `<Name>Impl` where `<Name>` is the simple name of the `@Mapper`-annotated type.
- **Visibility**: `public`.
- **Modifier**: `final`.
- **Annotations**: exactly one — `@javax.annotation.processing.Generated("io.github.joke.percolate")`.
- **Supertype clause**: `implements <Name>` (or `extends <Name>` if the `@Mapper` is an abstract class — slice-1 fixtures use interfaces).
- **Constructor**: exactly one `public` constructor. In slice 1 the constructor has an empty parameter list and an empty body. Future slices may add constructor parameters (one per nested-mapper dependency) without altering visibility, name, or count.
- **Methods**: one `@Override`-annotated `public` method per abstract method discovered in the source `<Name>`, with the same signature (return type, parameter types and names) as the abstract method.

The class SHALL declare no nested types and no instance fields. It MAY declare `private static final` fields **only** as strategy-requested hoisted members (see the member-hoisting requirement); a mapper whose strategies request no members SHALL declare no fields. Imports SHALL be managed by JavaPoet — no fully-qualified class references SHALL appear in the rendered source for any type that can be imported.

#### Scenario: Generated class for a trivial mapper

- **WHEN** the processor runs against an interface `package com.example; @Mapper public interface PersonMapper { Human map(Person person); }` whose graph is fully realised
- **THEN** the `Filer` receives a `JavaFile` whose package is `com.example` and whose top-level type is `PersonMapperImpl`
- **AND** the type is declared `public final class PersonMapperImpl implements PersonMapper`
- **AND** the type carries exactly the annotation `@Generated("io.github.joke.percolate")` (resolved to `javax.annotation.processing.Generated`)
- **AND** the type declares one public no-arg constructor with an empty body
- **AND** the type declares one method `@Override public Human map(Person person)`
- **AND** the type declares no fields (no strategy requested a member)

#### Scenario: Imports are managed by JavaPoet

- **WHEN** the rendered source of a generated `<Name>Impl` is inspected
- **THEN** no `import` line declares a class from the same package as the generated type
- **AND** every reference to a class outside `java.lang` resolves through an `import` line or through a `ClassName`/`TypeName` JavaPoet construct
- **AND** no rendered source line contains the substring `java.lang.` (the JavaPoet-managed default import covers it)

## ADDED Requirements

### Requirement: Strategy-requested class members are hoisted and deduplicated

When operations in the extracted plan declare member requests (see the `expansion-strategy-spi` capability), `GenerateStage` SHALL collect them during the same recursive plan walk that gathers hoisted locals, **deduplicate** them by their content dedup key across all method bodies of the generated type, allocate a unique class-scope name per distinct member (via a class-scoped `NameAllocator`, the sibling of the method-scoped local allocator), and emit each distinct member once on the generated type as a `private static final` field with the requested type and initializer. Each requesting operation's codegen SHALL reference the member by its allocated name through the same indirection used for a hoisted local, so the composer holds zero field syntax. Member collection SHALL mutate neither the `MapperGraph` nor the `ExtractedPlan`.

#### Scenario: Two methods sharing a formatter emit one field

- **WHEN** two mapper methods both parse with `@Map(format = "yyyy-MM-dd")` into `java.time` targets
- **THEN** the generated type declares exactly one `private static final DateTimeFormatter` field initialized with `DateTimeFormatter.ofPattern("yyyy-MM-dd")`
- **AND** both method bodies reference that single field

#### Scenario: Distinct patterns emit distinct fields

- **WHEN** one method uses `@Map(format = "yyyy-MM-dd")` and another uses `@Map(format = "dd.MM.yyyy")`, both into `java.time` targets
- **THEN** the generated type declares two distinct `private static final DateTimeFormatter` fields with distinct names

#### Scenario: An inline production requests no field

- **WHEN** a mapper method formats a `java.util.Date` with a per-call `SimpleDateFormat`
- **THEN** the generated type declares no `SimpleDateFormat` field and the method body constructs it inline
