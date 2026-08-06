## ADDED Requirements

### Requirement: Null-marking is generated, not transcribed

The repository-wide JSpecify null-marking policy SHALL be stated by applying the
`io.github.joke.jspecify:processor` annotation processor, not by hand-writing one
`package-info.java` per package. A `package-info.java` whose entire content is `@NullMarked` SHALL
NOT be checked into the source tree where the processor covers it.

The processor SHALL be wired as both `annotationProcessor` and `testAnnotationProcessor` for every
module that compiles percolate's own Java, because test source sets need marking as much as main
source sets do.

The wiring SHALL be declared in each module's own `build.gradle`, never in the `percolate.conventions`
convention plugin. The convention plugin states what is true of every module; a module that must not
be null-marked is expressed as an absence in its own build file, not as a carve-out in the shared
plugin.

Modules containing vendored third-party sources SHALL NOT be wired, because a generated
`package-info` would assert a nullness contract over a package percolate does not own and ship it in
the published artifact. `lib/javapoet`, which vendors `com.palantir.javapoet.MethodSpec` verbatim and
relocates it, is such a module.

Its version SHALL be declared in the `dependencies` platform module and SHALL NOT be repeated in any
consuming module's `build.gradle`.

#### Scenario: The processor is wired for both source sets

- **WHEN** a module that compiles percolate's own Java is inspected
- **THEN** it declares `io.github.joke.jspecify:processor` on both `annotationProcessor` and
  `testAnnotationProcessor`

#### Scenario: The convention plugin carries no null-marking wiring

- **WHEN** `buildSrc/src/main/groovy/percolate.conventions.gradle` is inspected
- **THEN** it declares no dependency on `io.github.joke.jspecify:processor`
- **AND** it contains no module-name condition

#### Scenario: Vendored third-party sources are not null-marked

- **WHEN** `lib/javapoet/build.gradle` is inspected
- **THEN** it declares no dependency on `io.github.joke.jspecify:processor`
- **AND** no `package-info` is generated into `com.palantir.javapoet`

#### Scenario: The processor version lives in the platform

- **WHEN** any module's dependency on `io.github.joke.jspecify:processor` is inspected
- **THEN** it carries no version, and the version is declared in `dependencies/build.gradle`

#### Scenario: A covered package needs no checked-in package-info

- **WHEN** a package covered by the processor is compiled and no `package-info.java` exists for it in
  the source tree
- **THEN** a `package-info.java` carrying `@NullMarked` is generated for it
- **AND** the resulting `package-info.class` carries the `@NullMarked` annotation

#### Scenario: No boilerplate package-info remains in a converted package

- **WHEN** the source tree of a converted module is inspected
- **THEN** it contains no `package-info.java` whose only content is a `@NullMarked` annotation

### Requirement: A hand-written package-info is the opt-out

A checked-in `package-info.java` SHALL be the mechanism by which a package opts out of the generated
default. The processor SHALL skip any package whose `package-info` is part of the compilation, on the
classpath, or already written in an earlier round.

A package that must resolve as unmarked SHALL therefore declare `@NullUnmarked` in a checked-in
`package-info.java`.

Which packages retain a checked-in file is stated by rule in this specification, not by a comment
repeated in each file.

#### Scenario: A checked-in package-info suppresses generation

- **WHEN** a package has a checked-in `package-info.java`
- **THEN** no `package-info.java` is generated for that package
- **AND** the checked-in file's annotations are the ones in effect

#### Scenario: The deliberate unmarked package survives

- **WHEN** the `io.github.joke.percolate.docs.mapannotation` package is compiled
- **THEN** its checked-in `@NullUnmarked` `package-info.java` is unchanged and in effect
- **AND** nullness in that package resolves as `UNKNOWN`

#### Scenario: Retained files are exactly the rule's source sets

- **WHEN** the `package-info.java` files remaining in the source tree after conversion are listed
- **THEN** each lies in a source set that percolate's own processor compiles

### Requirement: Generated marks are only relied upon where percolate observes them

A source set that percolate's own annotation processor compiles SHALL keep its hand-written
`package-info.java`, and SHALL NOT rely on a generated mark.

percolate's own annotation processor resolves nullness by reading the annotation mirrors of a
`PackageElement`. A `package-info.java` created through the `Filer` is not entered until the
annotation-processing round *after* the one that wrote it, so a generated mark may be invisible to a
processor running in the same compilation.

**This has been measured, and percolate does NOT observe the generated mark.** With
`docs/nullness/OrderMapper` — a `@Nullable` source member crossing to a non-null target member —
deleting the checked-in `package-info.java` and letting the generator write it instead changed the
generated mapper from

```java
return new Order(Objects.requireNonNull(form.getTrackingCode(), "..."));
```

to

```java
return new Order(form.getTrackingCode());
```

The generated `package-info.java` was present in the output directory; percolate still resolved
`UNKNOWN` and dropped the guard. The failure is silent — the build stays green while a null flows
into a non-null target field.

The retained source sets are exactly the ones declaring `project(':percolate')` on an annotation
processor configuration:

- `spi/src/test/java`
- `strategies-builtin/src/test/java`
- `percolate-smoke/src/main/java`

Every other source set SHALL be converted, because no processor in it reads package-level nullness
during the compilation that generates the mark.

#### Scenario: A source set percolate's processor compiles keeps its package-info

- **WHEN** a module declares `project(':percolate')` on an annotation processor configuration for a
  source set
- **THEN** every package in that source set has a checked-in `package-info.java`

#### Scenario: A generated mark is not observed within the same compilation

- **WHEN** a package's `@NullMarked` is generated rather than checked in, and percolate's processor
  compiles that package
- **THEN** percolate resolves nullness as `UNKNOWN`, not `NON_NULL`

#### Scenario: Conversion does not change generated mapper output

- **WHEN** a module's mappers are generated before and after its `package-info.java` files are
  converted
- **THEN** the generated mapper sources are identical

### Requirement: The generator is consumed as a released artifact

The processor SHALL be consumed from its published coordinate, `io.github.joke.jspecify:processor`.
The build SHALL NOT include the generator's source repository as a composite build, and SHALL NOT
declare a dependency substitution for it, because a substitution masks a coordinate that does not
resolve without it.

#### Scenario: No composite build for the generator

- **WHEN** `settings.gradle` is inspected
- **THEN** it contains no `includeBuild` for the generator's source repository
- **AND** it declares no `dependencySubstitution` for the generator

#### Scenario: The coordinate is the published one

- **WHEN** the generator dependency is inspected
- **THEN** its group is `io.github.joke.jspecify`
