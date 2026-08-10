## MODIFIED Requirements

### Requirement: Engine internals are encapsulated from other modules

No production or test class outside the `processor` module SHALL depend on any `processor` `internal`
package. Other modules SHALL reach the engine only through its public surface and through `spi`. This makes
"reaching into engine internals" a build-checkable violation rather than a matter of convention.

The rule SHALL exist exactly once, in the shared architecture-rule library, and SHALL be evaluated inside
each module that applies the rule runner against that module's own source-set output — not by a central
module reaching into sibling modules' build output directories, and not by a per-module specification that
wraps a shared rule builder. The earlier arrangement, in which `architecture-tests` published an
`EncapsulationRules` builder as `testFixtures` and `strategies-builtin`, `reactor`, and `reactor-blocking`
each carried a near-identical `EngineEncapsulationSpec` around it, SHALL be replaced: one library rule now
runs everywhere the runner is applied, which is strictly broader coverage than the three modules that
previously carried a copy.

#### Scenario: External code may not import engine internals
- **WHEN** any module that applies the rule runner — including `strategies-builtin`, `reactor`, and
  `reactor-blocking` — runs its architecture check against its own classes
- **THEN** no class in that module depends on a `processor` `internal` package, and a newly introduced such
  dependency fails that module's own build

#### Scenario: The rule is shared, not duplicated per module
- **WHEN** the rule-construction logic backing this check is located
- **THEN** it exists once as a rule in the `archRules` library, and no module declares a specification,
  fixture, or builder of its own for it

#### Scenario: No test fixture carries the rule
- **WHEN** `architecture-tests` is inspected
- **THEN** it publishes no `testFixtures` variant and no `EncapsulationRules` class, and no module depends
  on such a fixture

### Requirement: Engine internal methods are never private

The architecture suite SHALL enforce that no method declared by any class under the repo root package
`io.github.joke.percolate` carries the `private` modifier, with a single exception: sources under
`io.github.joke.percolate.lib..` (shaded third-party dependencies relocated to avoid processorpath clashes)
are not percolate's own code and SHALL NOT be evaluated. The rationale is a testability constraint, not a
style preference: a `private` method is statically dispatched (`invokespecial`) and cannot be intercepted by
any Spock/Mockito test double — even the inline mock maker — so it is not individually testable.
Compiler-synthetic members (lambda and `access$` bridges), private constructors, and generated
(`@Generated`/Lombok) members SHALL be exempt.

Because the rule is evaluated per source set inside each module against that module's own output, its
"repo-wide" scope SHALL be achieved by every module applying the runner, rather than by one central
evaluation over a union classpath filtered down with build-output path predicates.

#### Scenario: A private method anywhere in percolate's own code fails the build
- **WHEN** any class under `io.github.joke.percolate` (outside `io.github.joke.percolate.lib..`) declares a
  `private` method
- **THEN** its owning module's architecture check fails, flagging the method

#### Scenario: A private builtin-strategy method fails the build
- **WHEN** a class in `io.github.joke.percolate.spi.builtins..` declares a `private` method
- **THEN** the `strategies-builtin` architecture check fails, flagging the method

#### Scenario: Synthetic members and private constructors are exempt
- **WHEN** the architecture suite analyses a class that uses lambdas or declares a private constructor
- **THEN** the synthetic lambda/`access$` methods and the private constructor do not trip the rule

#### Scenario: Shaded third-party sources are exempt
- **WHEN** the architecture suite evaluates a module's classes
- **THEN** classes under `io.github.joke.percolate.lib..` are never evaluated, because the module that
  vendors them does not apply the rule runner and shaded classes are not part of any other module's own
  source-set output

### Requirement: Protected methods unused by any subclass are marked for testing

The architecture suite SHALL enforce that a `protected` method is either a genuine inheritance extension
point or is explicitly marked as a test-only visibility widening. A `protected` method with no concrete
method body (abstract) is exempt, since every concrete subclass must override it by construction. A concrete
`protected` method SHALL be considered a genuine extension point when at least one subclass, in production
code, either declares an override of it or contains a call whose target resolves to it. A `protected` method
that has no such production-subclass usage MUST carry the `org.jetbrains.annotations.VisibleForTesting`
annotation, documenting that its visibility exists only to be reachable from a test subclass rather than
from any real inheritance use. Subclassing that exists only in test sources does not count as
production-subclass usage. Compiler-synthetic members and Lombok-generated members (e.g.
`@EqualsAndHashCode`'s `canEqual`) are exempt outright, matching the private-method rule's own
synthetic/generated exemptions — such a method has no source declaration to carry the annotation on.

A concrete `protected` method on the **published `spi` surface** — the `io.github.joke.percolate.spi`
package and its subpackages, excluding the internal `io.github.joke.percolate.spi.builtins..` — SHALL be
exempt outright. Two reasons compel it. First, mechanically: subclass discovery requires the subclass to be
visible, and every cross-module base class in the repository (`spi.Container`, `spi.Accessor`,
`spi.Conversion`) is subclassed only by *downstream* modules that are not on `spi`'s own classpath, so a
per-module evaluation can never see them. Second, semantically: on a published extension-point API
`protected` **is** the contract offered to third-party strategy authors, not a widening used to dodge the
no-private-methods rule, which is the abuse this requirement exists to catch. Annotating such a method
`@VisibleForTesting` would record a falsehood and degrade the annotation everywhere else it appears. This
exemption matches in shape the published-`spi` exemption the no-static rule already carries.

The exemption's cost is bounded and known: exactly two methods lose automated coverage —
`spi.Container#containerOf` and `spi.Container#wrapNullness`, whose sole production overriders are
`ArrayContainer` and `OptionalContainer` in `strategies-builtin`. Every other concrete `protected` method in
`spi` already carries `@VisibleForTesting`, and every leaf override in the strategy modules is annotated on
a `final` class.

#### Scenario: An unused, unannotated protected method fails the build
- **WHEN** a class outside the published `spi` surface declares a concrete `protected` method that no
  subclass in production code overrides or calls, and the method carries no `@VisibleForTesting` annotation
- **THEN** the architecture suite fails, flagging the method

#### Scenario: A genuine extension point passes without annotation
- **WHEN** a class declares a concrete `protected` method and at least one production-code subclass in the
  same module overrides it or calls it
- **THEN** the architecture suite passes for that method, whether or not it is annotated

#### Scenario: An annotated test-only protected method passes
- **WHEN** a class declares a concrete `protected` method with no production-subclass usage, and the method
  is annotated `@VisibleForTesting`
- **THEN** the architecture suite passes for that method

#### Scenario: A test-only subclass does not count as usage
- **WHEN** a `protected` method outside the published `spi` surface is overridden or called only by a
  subclass that exists in test sources, and the method carries no `@VisibleForTesting` annotation
- **THEN** the architecture suite fails, flagging the method as unused by any production subclass

#### Scenario: An abstract protected method is exempt
- **WHEN** a class declares an abstract `protected` method
- **THEN** the architecture suite does not require `@VisibleForTesting` on it, regardless of subclass usage

#### Scenario: A synthetic or Lombok-generated protected method is exempt
- **WHEN** a `protected` method is compiler-synthetic (e.g. a Groovy metaclass accessor) or Lombok-generated
  (e.g. `canEqual`)
- **THEN** the architecture suite does not require `@VisibleForTesting` on it, regardless of subclass usage

#### Scenario: A published spi template-method hook is exempt
- **WHEN** `spi.Container#containerOf` or `spi.Container#wrapNullness` is evaluated, whose only production
  overriders live in `strategies-builtin` and are therefore invisible to `spi`'s own evaluation
- **THEN** the architecture suite passes for it without an annotation, because `protected` on the published
  `spi` surface is the extension contract rather than a test-only widening

#### Scenario: The internal builtins package is not covered by the spi exemption
- **WHEN** a class in `io.github.joke.percolate.spi.builtins..` declares an unused, unannotated concrete
  `protected` method
- **THEN** the architecture suite fails, because `spi.builtins` is an internal module rather than the
  published contract, despite sharing the `spi` package root

## REMOVED Requirements

### Requirement: Structural naming and acyclicity are enforced

**Reason**: The requirement bundled two unrelated rules with different fates. The `*Stage` naming rule
survives unchanged and is re-stated as its own requirement (see ADDED below). The package-acyclicity rule is
deleted as redundant: its slice pattern `io.github.joke.percolate.(*)..` cuts on the first package segment,
producing one slice per module, so `processor.internal.graph` and `processor.internal.stages` were always
the same slice and intra-module cycles were never checked. What it did check — cross-module cycles — is
already forbidden twice over by the inter-module layering rules (`spi` depends on neither engine nor
strategy; the engine has no edge to any strategy; `annotations` depends on nothing; the harness is
strategy-agnostic) together with the acyclic Gradle module dependency graph. It enforced nothing the suite
does not already enforce, and per-source-set evaluation would have rendered it fully vacuous in any case.

**Migration**: None required. The naming half continues to be enforced by the ADDED requirement below.
Cross-module acyclicity continues to be enforced by the inter-module layering requirement and by Gradle's
own dependency graph. A future change may reintroduce a cycle rule sliced *below* the module level
(e.g. `io.github.joke.percolate.processor.(*)..`), which would be new enforcement rather than a
reinstatement.

## ADDED Requirements

### Requirement: Stage implementations are named with a Stage suffix

The architecture suite SHALL enforce that every class implementing the engine's `Stage` type has a simple
name ending in `Stage`. The rule SHALL be evaluated in the module that declares the implementation, and
SHALL resolve the `Stage` supertype through the evaluating module's classpath rather than requiring the
engine's classes to be imported alongside it.

#### Scenario: Stage implementations are named *Stage
- **WHEN** the architecture suite finds classes implementing `Stage`
- **THEN** each has a simple name ending in `Stage`

#### Scenario: A misnamed Stage implementation fails the build
- **WHEN** a class implements `Stage` under a name that does not end in `Stage`
- **THEN** its owning module's architecture check fails, flagging the class
