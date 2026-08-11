# Architecture Rule Distribution Spec

## Purpose

Defines how percolate's architecture rules are authored, distributed, evaluated, and proven. Rules are
written exactly once as `ArchRulesService` implementations in the `archRules` source set of the unpublished
`architecture-tests` module, published as a separate jar variant with a generated `META-INF/services`
registry, and `ServiceLoader`-discovered by the `com.netflix.nebula.archrules.runner` plugin inside each
consuming module, which evaluates them per source set against that module's own classes.

This replaces two hand-built mechanisms that both failed open: a central suite that imported every module
off a union `testImplementation` classpath and filtered it back down with build-output path-string
predicates, and a `testFixtures`-published rule builder that three strategy modules each wrapped in a
near-identical specification. Source-set selection replaces the path predicates, and one library rule
replaces the copies.

The library's scope is bounded by what a whole-source-set bytecode view can see that a single compilation
unit cannot: **edges between types and packages**. Properties of one declaration — a method's visibility,
its `static` modifier, a class's method count — are owned by PMD under `method-shape-analysis` and are not
authored here. That line is not a preference: per-source-set evaluation cannot resolve a subclass across a
module boundary, and `spi` is a published contract whose implementors sit outside the build entirely, so a
rule phrased over the set of subclasses has no answer available to it here.

Two properties carry the whole arrangement. Enforcement is a **separate task** from evaluation -
`checkArchRules<SourceSet>` only writes a report and passes regardless - so the failure threshold must be
set explicitly or every rule silently degrades to console decoration. And because per-source-set evaluation
means most rules match no classes in most modules, every rule must tolerate an empty match, which makes a
mistyped coordinate indistinguishable from a clean result; each rule therefore carries a negative fixture
that proves it still fires.

## Requirements

### Requirement: Architecture rules are authored once in a rules library

Percolate's architecture rules SHALL be authored exactly once, as `ArchRulesService` implementations in the
`archRules` source set of a single rules-library module (`architecture-tests`), which applies
`com.netflix.nebula.archrules.library`. No rule SHALL be re-implemented, copied, or wrapped per consuming
module. Rules SHALL be grouped into cohesive service classes by subject rather than one class per rule, and
each SHALL be exposed from `getRules()` under a stable rule name so that per-rule configuration remains
addressable.

Every rule in the library SHALL constrain a **relationship between types or packages** — a dependency, a
package coordinate, or a type name — which is what a whole-source-set bytecode view can see and a
single-compilation-unit analyser cannot. A rule constraining a **property of one declaration**, such as a
method's visibility or its `static` modifier, SHALL NOT be authored here; it belongs to PMD under the
`method-shape-analysis` capability. This keeps the library free of any rule whose correctness depends on
resolving subclasses, which per-source-set evaluation cannot do across a module boundary.

The library SHALL be authored in Java, not Groovy: the `archRules` variant is consumed as a plain jar by the
runner and carries no Groovy runtime. The module SHALL NOT be published to Maven Central.

#### Scenario: Every rule has exactly one authoring site

- **WHEN** the repository is searched for ArchUnit rule construction
- **THEN** every rule is constructed in `architecture-tests/src/archRules/java`, and no module declares a
  rule of its own

#### Scenario: Rules are grouped by subject

- **WHEN** the `ArchRulesService` implementations are inspected
- **THEN** rules are grouped by subject — module layering, engine encapsulation, and type boundaries —
  rather than one class per rule, and each rule is keyed by a stable name

#### Scenario: No rule constrains a single declaration's shape

- **WHEN** the `ArchRulesService` implementations are inspected
- **THEN** none constrains a method's visibility, its `static` modifier, or a class's method count, and none
  calls `getAllSubclasses` or otherwise depends on resolving types outside the evaluated source set

#### Scenario: The rules library is not published

- **WHEN** the publishing configuration is inspected
- **THEN** `architecture-tests` declares no Maven publication and contributes no artifact to the Central
  Portal deployment

### Requirement: Rules are evaluated per source set inside each consuming module

Each module with production sources SHALL apply `com.netflix.nebula.archrules.runner` and evaluate the rule
library against **its own** source-set output, rather than a central module importing every sibling's
classes off a union classpath. The runner's generated `checkArchRules<SourceSet>` tasks SHALL run as part of
`check`.

No module SHALL declare a `testImplementation` dependency on a sibling module for the purpose of
architecture checking, and no rule evaluation SHALL read another module's build output directory.

#### Scenario: Each module checks its own classes

- **WHEN** `check` runs for `spi`, `processor`, `strategies-builtin`, `reactor`, `reactor-blocking`,
  `annotations`, `test-foundation`, or `percolate`
- **THEN** that module evaluates the shared rule library against its own compiled classes, and its build
  fails if a rule is violated

#### Scenario: The rules library holds no dependency on the modules it judges

- **WHEN** `architecture-tests/build.gradle` is inspected
- **THEN** it declares no dependency on `spi`, `processor`, `strategies-builtin`, `reactor`,
  `reactor-blocking`, or `test-foundation`

#### Scenario: Test and fixture sources are excluded by source-set selection

- **WHEN** the runner configuration is inspected
- **THEN** `test` and `testFixtures` are excluded by name via `skipSourceSet`, and no rule filters them by
  matching against a build-output path string

### Requirement: Runner enrollment is declared by each module, never by the conventions plugin

The `com.netflix.nebula.archrules.runner` and `com.netflix.nebula.archrules.library` plugins SHALL be
configured centrally in `percolate.conventions.gradle` through `pluginManager.withPlugin`, and their
versions SHALL be declared in `settings.gradle`'s `pluginManagement` block. Enrollment SHALL be local: a
module opts in by declaring the plugin id in its own `plugins {}` block. The conventions plugin SHALL NOT
name any module, maintain an opt-out list, or branch on a project name.

A module that must not run the rules SHALL simply not declare the plugin. This SHALL apply to
`architecture-tests` (which would otherwise evaluate its own rules against itself and create a dependency
cycle), to `lib:javapoet` (whose vendored `com.palantir.javapoet` overlay depends on its own package and
would trip the JavaPoet relocation rule), and to the `java-platform` modules `bom` and `dependencies`.

#### Scenario: The conventions plugin names no module

- **WHEN** `buildSrc/src/main/groovy/percolate.conventions.gradle` is inspected
- **THEN** its archrules configuration branches on no project name and maintains no module list

#### Scenario: The rules library does not run its own rules

- **WHEN** `architecture-tests/build.gradle` is inspected
- **THEN** it applies `com.netflix.nebula.archrules.library` and does not apply
  `com.netflix.nebula.archrules.runner`

#### Scenario: The vendored JavaPoet overlay is not rule-checked

- **WHEN** `lib/javapoet/build.gradle` is inspected
- **THEN** it applies neither archrules plugin, so its `com.palantir.javapoet.MethodSpec` overlay is never
  evaluated against the relocation rule

### Requirement: Rule violations fail the build

The runner SHALL be configured with a failure threshold that causes **every** rule failure to fail the
build. The plugin's default — failing on no priority, reporting to the console only — SHALL NOT be relied
on. Percolate's architecture rules are load-bearing guards, not advisory guidance, and a configuration that
reports a violation without failing SHALL be treated as a defect.

Per-rule priority tuning SHALL NOT be used to exempt a rule from failing. A rule that is not worth failing
on SHALL be deleted instead.

#### Scenario: A violation fails check

- **WHEN** a class is introduced that violates any rule in the library
- **THEN** that module's `check` fails, rather than printing a console summary and passing

#### Scenario: The threshold is set explicitly

- **WHEN** the archrules configuration in `percolate.conventions.gradle` is inspected
- **THEN** it sets a failure threshold of `LOW`, so rules at every priority fail the build

### Requirement: Every rule is proven to fire by a negative fixture

Because per-source-set evaluation means most rules match no classes in most modules, every rule SHALL set
`allowEmptyShould(true)` to avoid failing on an empty match. That setting makes a mis-typed selector match
nothing everywhere and pass silently, so each rule SHALL additionally be covered by at least one **negative**
test in the `archRulesTest` source set: a deliberately-violating fixture checked against the rule, asserting
that a violation is reported.

The positive direction SHALL be considered proven by `check` passing against the repository itself and SHALL
NOT require a separate passing fixture per rule.

#### Scenario: Each rule has a violating fixture

- **WHEN** the `archRulesTest` source set is inspected
- **THEN** every rule exposed by the library has at least one test that runs it against a fixture designed
  to violate it and asserts the violation is reported

#### Scenario: A rule that silently matches nothing is caught

- **WHEN** a rule's package selector is changed to one that matches no class
- **THEN** its negative test fails, because the violating fixture is no longer flagged

#### Scenario: Empty matches do not fail a rule

- **WHEN** a rule scoped to one module is evaluated against a module containing no matching class
- **THEN** the rule passes rather than failing on an empty match

### Requirement: Console reports are aggregated at the root

The root project SHALL apply `com.netflix.nebula.archrules.aggregate` so that rule results across all
enrolled modules are collected into a single console report, rather than being read per module from
scattered task output.

#### Scenario: The aggregate plugin is applied at the root

- **WHEN** the root `build.gradle` is inspected
- **THEN** it applies `com.netflix.nebula.archrules.aggregate`

#### Scenario: An aggregated report spans every enrolled module

- **WHEN** the aggregate console report task runs
- **THEN** it reports rule results from every module that applies the runner
