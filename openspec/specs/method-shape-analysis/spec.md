# Method Shape Analysis Spec

## Purpose

Defines who owns percolate's testability doctrine — *every authored method must be interceptable by a test
double, so it can be tested on its own rather than only through whatever calls it* — and how each of its
invariants is enforced.

The doctrine itself is unchanged from the ArchUnit rules it replaces; only the owner and the phrasing move.
What forced the move is that one of the rules had no answerable question. Asking whether *no* subclass uses
a `protected` method is absence of evidence over a set that must be enumerated to be trusted, and it cannot
be enumerated: per-source-set evaluation cannot see a sibling module's subclasses, and `spi` is a published
contract whose implementors sit outside the build entirely. Widening the import scope buys two methods and
still leaves third-party implementors unreachable. So inference is deleted rather than relocated, and a
`protected` method now **declares** its kind.

With that rule gone, the whole family collapses to properties of a single declaration, which is exactly what
PMD reads. Every invariant here is therefore checked per declaration against authored source: reported at
its own line, surfaced in the IDE, configurable through ruleset `<properties>`, and — because Gradle feeds
PMD `allJava` rather than bytecode — needing none of the synthetic, bridge, or `@Generated` filtering the
bytecode form required. Edges between types and packages stay with ArchUnit under `module-boundaries` and
`architecture-rule-distribution`. No invariant has two owners, because two owners means two exemption sets,
and exemption sets drift.

## Requirements

### Requirement: Method shape is owned by PMD, module edges by ArchUnit

Percolate's static-analysis invariants SHALL be partitioned by what each analyser can observe. A **property
of a single declaration** — a method's visibility, its `static` modifier, the markers it carries — SHALL be
enforced by PMD, which reads authored source one compilation unit at a time. A **relationship between
types or packages** — inter-module layering, `processor.internal` encapsulation, strategy myopia, the
`javax.lang.model.util` confinement, the JavaPoet relocation, and `*Stage` naming — SHALL be enforced by
ArchUnit, which reads a whole source set's bytecode and is the only one of the two that can see an edge.

No invariant SHALL be enforced by both. Two owners means two exemption sets, which drift.

#### Scenario: The rule library carries no method-shape rule

- **WHEN** the `ArchRulesService` implementations in `architecture-tests/src/archRules/java` are inspected
- **THEN** no rule constrains a method's visibility, its `static` modifier, or a class's method count, and
  every remaining rule constrains a dependency, a package coordinate, or a type name

#### Scenario: A method-shape violation is reported by PMD alone

- **WHEN** a `private` method is added to any module's main sources and `check` runs
- **THEN** PMD fails the build reporting the file and line, and no `checkArchRules` task reports it

#### Scenario: A module-edge violation is reported by ArchUnit alone

- **WHEN** a class in `processor` is made to depend on a strategy module and `check` runs
- **THEN** `checkArchRulesMain` fails for that module, and PMD reports nothing, because the edge is invisible
  to a single-compilation-unit analyser

### Requirement: A protected method declares whether it is a test seam or an extension point

Every `protected` method in percolate's own sources SHALL carry exactly one of two markers:
`@VisibleForTesting` when the visibility was widened to create a test seam, or
`@ApiStatus.OverrideOnly` when the method is a genuine extension point offered to implementors. An
unmarked `protected` method SHALL fail the build.

The kind SHALL be **declared, never inferred**. Inferring it from the absence of subclass usage requires
enumerating every subclass, which is impossible for `spi` — a published contract whose implementors are
outside any scope the build can import — and was already impossible across module boundaries under
per-source-set evaluation. Because the declaration needs no import scope, it holds identically at module,
repository and third-party-consumer range.

This is strictly stronger than the inference it replaces, which passed whenever *any* subclass happened to
override the method, and so could not tell a designed hook from an accidental one.

`@ApiStatus.OverrideOnly` SHALL be sourced from `org.jetbrains.annotations`, the same artifact that supplies
`@VisibleForTesting`; a module gaining the annotation SHALL declare that dependency.

#### Scenario: An unmarked protected method fails the build

- **WHEN** a `protected` method carrying neither marker is compiled in any module
- **THEN** PMD reports it at its declaration line and `check` fails

#### Scenario: A published extension point is marked as such

- **WHEN** `Container#containerOf`, `Container#wrapNullness`, or any `protected abstract` hook on the
  published `spi` surface is inspected
- **THEN** it carries `@ApiStatus.OverrideOnly`, and no exemption is needed for it anywhere in the build

#### Scenario: A test seam is marked as such

- **WHEN** a `protected` method exists only so a spied subject can intercept a self-call
- **THEN** it carries `@VisibleForTesting`, and the marker records that the widening is not an extension point

#### Scenario: The marker is not inferred from subclass usage

- **WHEN** the analysis configuration is inspected
- **THEN** no rule resolves subclasses, imports a sibling module's classes, or treats an override as evidence
  of intent

### Requirement: Methods are never private, and static only in a genuine static context

No method in percolate's own sources SHALL be declared `private`: a `private` method is dispatched by
`invokespecial`, cannot be intercepted by a test double, and so is testable only through whatever calls it.
Constructors SHALL remain out of scope — a private constructor is required by the utility-class shape and is
never spied.

No method SHALL be `static` outside a context that compels it. The permitted contexts SHALL be matched as
**shapes**, never as name lists, so they cannot quietly accumulate members: a stateless utility holder
(including Lombok's `@UtilityClass`), a named constructor returning its own declaring type or an interface
that type implements, a framework-mandated static such as a Dagger `@Provides` method, the compiler-generated
enum `values`/`valueOf` pair, and `main`.

A named constructor is permitted because a test double over it could only return what the constructor it
wraps already returns; there is nothing to intercept.

#### Scenario: A private method fails the build

- **WHEN** a `private` method is declared in any module's main sources
- **THEN** PMD reports it and `check` fails

#### Scenario: A private constructor passes

- **WHEN** a class declares a `private` constructor to prevent instantiation
- **THEN** no violation is reported

#### Scenario: A named constructor passes

- **WHEN** a `static` factory returns its own declaring type, as `OperationSpec.of` and `Port.byType` do
- **THEN** no violation is reported

#### Scenario: A utility holder passes

- **WHEN** a class carries Lombok's `@UtilityClass`, as `LiteralCoercion` does
- **THEN** its `static` methods are not reported

#### Scenario: An arbitrary static helper fails the build

- **WHEN** a `static` method is declared that is none of the permitted shapes
- **THEN** PMD reports it and `check` fails

### Requirement: Package-private methods are marked as deliberate seams

Because no method may be `private`, package-private SHALL be the canonical form for an internal method, and
every package-private method SHALL carry `@VisibleForTesting` to record that the widened visibility is a
deliberate test seam rather than a forgotten modifier.

The consequence is accepted knowingly: the annotation ceases to discriminate *among* package-private methods
and instead states that every internal method is a declared seam. The `protected` pair above stays
discriminating, because both of its markers remain deliberate choices between two meanings.

#### Scenario: An unmarked package-private method fails the build

- **WHEN** a package-private method without `@VisibleForTesting` is compiled
- **THEN** PMD reports it and `check` fails

#### Scenario: Overrides and test methods are exempt

- **WHEN** the method is an `@Override`, or a JUnit test or lifecycle method
- **THEN** no violation is reported, because its visibility is not the author's to choose or the annotation
  would be nonsense

### Requirement: Class size carries no automated ceiling; method shape carries the load

No ceiling on methods per class SHALL be enforced. `TooManyMethods` SHALL remain excluded, as
`joke-strict.xml` leaves it, and no percolate-local re-enable SHALL exist.

The rule the ceiling was meant to co-enforce is not abandoned; the goal is pursued through a different and
stronger lever. A method count is a blunt proxy: it counts declarations without regard to what they contain,
so it is equally satisfied by a class of many tidy methods — exactly the shape this repository wants — and
breached by one that has simply grown honest seams. What actually resists the "expose the monolith as
package-private members" loophole is pressure on *method* shape, not class arity: the rules of this
capability push toward small, tidy, individually testable methods, and a class whose methods are each small
and named is decomposable by inspection whether it has twelve of them or thirty. A cap on the count would
work against that, penalising the very extraction it was introduced to encourage.

The residual risk — a class accreting responsibilities without any single method growing — SHALL be a
review-caught convention rather than an automated gate.

#### Scenario: No class-size rule is configured

- **WHEN** the PMD configuration is inspected
- **THEN** `TooManyMethods` is not enabled, and no `maxmethods` property is set anywhere

#### Scenario: A large class of tidy methods passes

- **WHEN** a class declares many methods, each small and individually testable
- **THEN** no violation is reported on the ground of method count alone

### Requirement: The ruleset is consumed as a versioned artifact, composed locally

The PMD ruleset SHALL be consumed as a published artifact (`io.github.joke.pmd:rules`) resolved on the `pmd`
configuration, referenced by its classpath resource name, and pinned to a released version — never a
snapshot, which would let the rules under which the build passes change without a commit.

Because Gradle's `ruleSets` cannot subtract from a referenced ruleset, any percolate-local exclusion or
property override SHALL live in a single local ruleset file that references the published one. That file
SHALL carry only percolate-local composition; a rule's own logic SHALL NOT be reimplemented there. If no
local composition is required, the file SHALL NOT exist.

#### Scenario: The ruleset artifact is pinned to a release

- **WHEN** `percolate.conventions.gradle` is inspected
- **THEN** the `pmd` configuration declares `io.github.joke.pmd:rules` at a released version, and no
  `-SNAPSHOT` coordinate appears

#### Scenario: No local composition, no local file

- **WHEN** the PMD configuration is inspected and percolate requires no exclusion or property override
- **THEN** `ruleSets` names the published ruleset resource directly and no local ruleset file exists

#### Scenario: Configuration stays central

- **WHEN** any module's `build.gradle` is inspected
- **THEN** it configures no PMD setting of its own, and names no rule — the conventions plugin holds the
  whole configuration
