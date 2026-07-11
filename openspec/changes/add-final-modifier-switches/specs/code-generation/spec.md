## MODIFIED Requirements

### Requirement: Generated class shape

For every `@Mapper`-annotated interface (or abstract class) `<Name>` for which `GenerateStage` emits code, the generated `JavaFile` SHALL contain a single top-level type with the following shape:

- **Package**: the same package as the `@Mapper`-annotated type.
- **Class name**: `<Name>Impl` where `<Name>` is the simple name of the `@Mapper`-annotated type.
- **Visibility**: `public`.
- **Modifier**: `final` **iff** the `-Apercolate.classes.final` compiler option is `true`; absent by default. (Default changed: previously the class was unconditionally `final`.)
- **Annotations**: exactly one — `@javax.annotation.processing.Generated("io.github.joke.percolate")`.
- **Supertype clause**: `implements <Name>` (or `extends <Name>` if the `@Mapper` is an abstract class — slice-1 fixtures use interfaces).
- **Constructor**: exactly one `public` constructor. In slice 1 the constructor has an empty parameter list and an empty body. Future slices may add constructor parameters (one per nested-mapper dependency) without altering visibility, name, or count.
- **Methods**: one `@Override`-annotated `public` method per abstract method discovered in the source `<Name>`, with the same signature (return type, parameter types and names) as the abstract method, additionally `final` iff `-Apercolate.methods.final` is `true`, and with each parameter additionally `final` iff `-Apercolate.parameters.final` is `true`.

The class SHALL declare no nested types and no instance fields. It MAY declare `private static final` fields **only** as strategy-requested hoisted members (see the member-hoisting requirement); a mapper whose strategies request no members SHALL declare no fields. Imports SHALL be managed by JavaPoet — no fully-qualified class references SHALL appear in the rendered source for any type that can be imported.

#### Scenario: Generated class for a trivial mapper with no finality options set

- **WHEN** the processor runs against an interface `package com.example; @Mapper public interface PersonMapper { Human map(Person person); }` whose graph is fully realised, with none of `percolate.classes.final`, `percolate.methods.final`, `percolate.parameters.final` set
- **THEN** the `Filer` receives a `JavaFile` whose package is `com.example` and whose top-level type is `PersonMapperImpl`
- **AND** the type is declared `public class PersonMapperImpl implements PersonMapper` (no `final` on the class)
- **AND** the type carries exactly the annotation `@Generated("io.github.joke.percolate")` (resolved to `javax.annotation.processing.Generated`)
- **AND** the type declares one public no-arg constructor with an empty body
- **AND** the type declares one method `@Override public Human map(Person person)` (no `final` on the method, no `final` on the parameter)
- **AND** the type declares no fields (no strategy requested a member)

#### Scenario: Imports are managed by JavaPoet

- **WHEN** the rendered source of a generated `<Name>Impl` is inspected
- **THEN** no `import` line declares a class from the same package as the generated type
- **AND** every reference to a class outside `java.lang` resolves through an `import` line or through a `ClassName`/`TypeName` JavaPoet construct
- **AND** no rendered source line contains the substring `java.lang.` (the JavaPoet-managed default import covers it)

#### Scenario: percolate.classes.final renders a final class

- **WHEN** the processor runs against `PersonMapper` with `-Apercolate.classes.final=true`
- **THEN** the generated type is declared `public final class PersonMapperImpl implements PersonMapper`

#### Scenario: percolate.methods.final renders final methods

- **WHEN** the processor runs against `PersonMapper` with `-Apercolate.methods.final=true`
- **THEN** the generated method is declared `@Override public final Human map(Person person)`

#### Scenario: percolate.parameters.final renders final parameters

- **WHEN** the processor runs against `PersonMapper` with `-Apercolate.parameters.final=true`
- **THEN** the generated method's parameter is declared `final Person person`

#### Scenario: The three finality switches compose independently

- **WHEN** the processor runs against `PersonMapper` with `-Apercolate.classes.final=true` and `-Apercolate.methods.final=true` but `-Apercolate.parameters.final` unset
- **THEN** the generated class is `final`, the generated method is `final`, and its parameter carries no `final` modifier
