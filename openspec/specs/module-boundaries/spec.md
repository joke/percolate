# Module Boundaries Spec

## Purpose

Defines the declared, build-enforced separation between percolate's modules, so the boundaries the dependency graph and conventions imply cannot silently erode. The `processor` engine partitions its packages into a public surface and an `internal` region. The genuinely cross-module rules — the inter-module layering, the rule that the engine↔strategy line is crossed only through `spi`, strategy myopia (no graph dependency), `*Stage` naming, and acyclicity — run centrally in a dedicated unpublished `architecture-tests` module (ArchUnit) that imports every module's classes. The rule that no code outside the engine reaches into `processor` internals instead runs inside each owning strategy module against its own already-available classpath, built on a shared ArchUnit rule `architecture-tests` publishes as `testFixtures` — no module needs to reach into a sibling's build output to check it. Engine-contract tests live in `processor` against a `FakeStrategy`, not in a strategy module. ArchUnit was chosen over Jigsaw because the leaks are test-scope and build-config and the rules are convention-level, which JPMS cannot express on this annotation-processor + Groovy/Spock + compile-testing stack.

Alongside the module rules sit three method-shape rules with a single shared purpose: every authored method must be interceptable by a test double, so it can be tested on its own rather than only through whatever calls it. A method is not private (`invokespecial` cannot be intercepted), not `static` (neither can `invokestatic`), and a `protected` method that no production subclass uses carries `@VisibleForTesting` to record that the widening is a test seam rather than an extension point. The exemptions are shapes, never name lists, so they cannot quietly accumulate members.
## Requirements
### Requirement: The processor declares an explicit api/internal boundary

The `processor` engine SHALL partition its packages into a narrow public surface and an `internal` region. Engine machinery that other modules have no business touching — the bipartite graph, the expansion/discovery/generate stages, and plan-extraction internals — SHALL reside under a package segment named `internal`. The packages other modules legitimately depend on (the processor entry point and any types it must expose) SHALL remain outside `internal`. The split SHALL be a structural refactor only: it SHALL NOT change the processor's runtime behaviour, the generated-mapper contract, or the annotations/processing surface a consumer sees.

#### Scenario: Engine internals live under an internal segment
- **WHEN** the `processor` module's packages are inspected
- **THEN** the graph, stages, and plan-extraction implementation types reside in a package whose name contains an `internal` segment, and the processor's externally-used surface does not

#### Scenario: The split changes no behaviour
- **WHEN** the engine's end-to-end and unit suites run after the package move
- **THEN** they pass unchanged, confirming the refactor altered package placement only, not generated output or processing behaviour

### Requirement: Engine internals are encapsulated from other modules

No production or test class outside the `processor` module SHALL depend on any `processor` `internal`
package. Other modules SHALL reach the engine only through its public surface and through `spi`. This makes
"reaching into engine internals" a build-checkable violation rather than a matter of convention. The check
SHALL run in each module that can see the engine's test classpath without cross-project probing —
`strategies-builtin`, `reactor`, and `reactor-blocking` each run it against their own main and test classes
— built on a single shared ArchUnit rule published as `architecture-tests`' `testFixtures`, rather than as
one central check in `architecture-tests` reaching into sibling modules' build output directories.

#### Scenario: External code may not import engine internals
- **WHEN** each of `strategies-builtin`, `reactor`, and `reactor-blocking` runs its own architecture check
  against its own main and test classes
- **THEN** no class in that module — production or test — depends on a `processor` `internal` package, and a
  newly introduced such dependency fails that module's own build

#### Scenario: The rule is shared, not duplicated per module
- **WHEN** the rule-construction logic backing this check is located
- **THEN** it exists once, published as `architecture-tests`' `testFixtures`, and each consuming module's
  own spec calls into it rather than re-implementing the ArchUnit rule

### Requirement: Engine internal methods are never private

The architecture suite SHALL enforce that no method declared by any class under the repo root package `io.github.joke.percolate` carries the `private` modifier, with a single exception: sources under `io.github.joke.percolate.lib..` (shaded third-party dependencies relocated to avoid processorpath clashes) are excluded from import entirely, since they are not percolate's own code. The rationale is a testability constraint, not a style preference: a `private` method is statically dispatched (`invokespecial`) and cannot be intercepted by any Spock/Mockito test double — even the inline mock maker — so it is not individually testable. Compiler-synthetic members (lambda and `access$` bridges), private constructors, and generated (`@Generated`/Lombok) members SHALL be exempt.

#### Scenario: A private method anywhere in percolate's own code fails the build
- **WHEN** any class under `io.github.joke.percolate` (outside `io.github.joke.percolate.lib..`) declares a `private` method
- **THEN** the architecture suite fails, flagging the method

#### Scenario: A private builtin-strategy method fails the build
- **WHEN** a class in `io.github.joke.percolate.spi.builtins..` declares a `private` method
- **THEN** the architecture suite fails, flagging the method

#### Scenario: Synthetic members and private constructors are exempt
- **WHEN** the architecture suite analyses a class that uses lambdas or declares a private constructor
- **THEN** the synthetic lambda/`access$` methods and the private constructor do not trip the rule

#### Scenario: Shaded third-party sources are exempt
- **WHEN** the architecture suite imports percolate's classes
- **THEN** classes under `io.github.joke.percolate.lib..` are excluded from import and never evaluated by this rule

### Requirement: Protected methods unused by any subclass are marked for testing

The architecture suite SHALL enforce that a `protected` method is either a genuine inheritance extension point or is explicitly marked as a test-only visibility widening. A `protected` method with no concrete method body (abstract) is exempt, since every concrete subclass must override it by construction. A concrete `protected` method SHALL be considered a genuine extension point when at least one subclass, in production code, either declares an override of it or contains a call whose target resolves to it. A `protected` method that has no such production-subclass usage MUST carry the `org.jetbrains.annotations.VisibleForTesting` annotation, documenting that its visibility exists only to be reachable from a test subclass rather than from any real inheritance use. Subclassing that exists only in test sources does not count as production-subclass usage. Compiler-synthetic members and Lombok-generated members (e.g. `@EqualsAndHashCode`'s `canEqual`) are exempt outright, matching the private-method rule's own synthetic/generated exemptions — such a method has no source declaration to carry the annotation on.

#### Scenario: An unused, unannotated protected method fails the build
- **WHEN** a class declares a concrete `protected` method that no subclass in production code overrides or calls, and the method carries no `@VisibleForTesting` annotation
- **THEN** the architecture suite fails, flagging the method

#### Scenario: A genuine extension point passes without annotation
- **WHEN** a class declares a concrete `protected` method and at least one production-code subclass overrides it or calls it
- **THEN** the architecture suite passes for that method, whether or not it is annotated

#### Scenario: An annotated test-only protected method passes
- **WHEN** a class declares a concrete `protected` method with no production-subclass usage, and the method is annotated `@VisibleForTesting`
- **THEN** the architecture suite passes for that method

#### Scenario: A test-only subclass does not count as usage
- **WHEN** a `protected` method is overridden or called only by a subclass that exists in test sources, and the method carries no `@VisibleForTesting` annotation
- **THEN** the architecture suite fails, flagging the method as unused by any production subclass

#### Scenario: An abstract protected method is exempt
- **WHEN** a class declares an abstract `protected` method
- **THEN** the architecture suite does not require `@VisibleForTesting` on it, regardless of subclass usage

#### Scenario: A synthetic or Lombok-generated protected method is exempt
- **WHEN** a `protected` method is compiler-synthetic (e.g. a Groovy metaclass accessor) or Lombok-generated (e.g. `canEqual`)
- **THEN** the architecture suite does not require `@VisibleForTesting` on it, regardless of subclass usage

### Requirement: Engine internal classes stay within a size ceiling

The architecture suite SHALL enforce a ceiling on the size of each class in the **decomposed packages** — `io.github.joke.percolate.processor.internal..` **and `io.github.joke.percolate.spi.builtins..`** — a bound on method count, or an equivalent weighted-method-complexity or class-length metric — so that no class accretes responsibilities. This rule SHALL be **co-enforced** with the no-private rule: the no-private rule alone is satisfied by exposing a monolith's internals as package-private members, so the size ceiling is required to force separable logic into new small classes rather than exposed helpers.

#### Scenario: An oversized decomposed class fails the build
- **WHEN** a class in a decomposed package exceeds the configured size ceiling
- **THEN** the architecture suite fails, flagging the class

#### Scenario: The decomposed stages and strategies pass both structural rules
- **WHEN** the architecture suite analyses the decomposed engine `internal` packages and the `spi.builtins` strategy package
- **THEN** no class declares a `private` method and no class exceeds the size ceiling

### Requirement: Inter-module dependencies obey the declared layering

The architecture suite SHALL enforce the module layering. `annotations` SHALL depend on nothing within percolate. `spi` SHALL depend only on `annotations`. `processor` SHALL depend on `spi` (and `annotations`) and SHALL NOT depend on any strategy module (`strategies-builtin`, `reactor`, `reactor-blocking`) in any scope. Strategy modules SHALL depend on `spi` in production and MAY depend on `processor` and `test-foundation` only in test scope. `test-foundation` SHALL depend on `processor` and SHALL NOT depend on any strategy module.

#### Scenario: The engine has no edge to any strategy
- **WHEN** the architecture suite analyses `processor`'s production and test classes
- **THEN** none of them depend on a class in any strategy module

#### Scenario: The harness stays strategy-agnostic
- **WHEN** the architecture suite analyses `test-foundation`
- **THEN** it depends on `processor` but on no strategy module

#### Scenario: A strategy's production code reaches the engine only through spi
- **WHEN** the architecture suite analyses a strategy module's production classes
- **THEN** they depend on `spi` and on the consumer's own types, and not on `processor` at all in production scope

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

### Requirement: Structural naming and acyclicity are enforced

The architecture suite SHALL enforce that every class implementing the engine's `Stage` type is named with a `*Stage` suffix, and that no package participates in a dependency cycle.

#### Scenario: Stage implementations are named *Stage
- **WHEN** the architecture suite finds classes implementing `Stage`
- **THEN** each has a simple name ending in `Stage`

#### Scenario: There are no package cycles
- **WHEN** the architecture suite slices percolate's packages
- **THEN** it finds no cyclic dependency between them

### Requirement: javax.lang.model.util (Types/Elements) is confined to the type-boundary packages

The architecture suite SHALL enforce that `javax.lang.model.util.Types` and `javax.lang.model.util.Elements`
— the two compiler-service classes that need a live compile environment to answer — are depended on **only**
by the enumerated type-boundary regions:

- the bare `io.github.joke.percolate.processor` package (the Dagger wiring — `ProcessorModule`/`MapperStep`
  and their generated `*_Factory`/`DaggerProcessorComponent` siblings, which mention `Types`/`Elements` in
  constructor/field types purely as DI plumbing),
- `processor.internal.stages.expand` (the type-query seam implementation, `CompileResolveCtx`),
- `processor.internal.stages.discover` (the discovery adapter),
- `processor.internal.stages.generate` (codegen emission),
- `processor.nullability` (the nullability resolver), and
- the `io.github.joke.percolate.spi.ResolveCtx` interface itself, which declares `types()`/`elements()` so
  that a real-javac production implementation (`CompileResolveCtx`) can answer every seam question by
  delegating through them; strategy and engine *production* code never calls them, and no test now
  constructs a `ResolveCtx` over a `Types`/`Elements` pair (`ResolveCtxBuilder` is deleted and the
  `strategies-builtin` unit specs mock the seam).

This is deliberately narrower than a blanket ban on all of `javax.lang.model` (which would also outlaw
holding `TypeMirror`/`TypeElement`/`Element` values as opaque pass-through tokens everywhere — the design
this rule protects): only the two compiler-**service** classes are confined; a `TypeMirror` or `Element`
value may be held, typed, and passed by any engine or strategy class, so long as no `Types`/`Elements`
method is invoked outside the boundary. Everywhere else — the engine graph/stages/plan-extraction, the
strategies (`strategies-builtin`, `reactor`, `reactor-blocking`), and the `Containers`/`TypeProbe` helpers —
asks its type questions through the `ResolveCtx` seam instead. A newly introduced `Types`/`Elements`
dependency outside the enumerated boundary SHALL fail the build.

#### Scenario: Engine and strategies depend on no Types/Elements
- **WHEN** the architecture suite analyses the engine graph/stages/plan packages (outside the enumerated
  boundary sub-packages), every strategy module, and the `Containers`/`TypeProbe` helpers
- **THEN** none of them depends on `javax.lang.model.util.Types` or `javax.lang.model.util.Elements`; type
  questions are routed through the `ResolveCtx` seam

#### Scenario: The boundary packages and ResolveCtx may depend on Types/Elements
- **WHEN** the architecture suite analyses the bare `processor` package, the seam implementation, the
  discovery adapter, the codegen-emit package, the nullability resolver, and the `ResolveCtx` interface
- **THEN** their dependency on `javax.lang.model.util.Types`/`Elements` is permitted

#### Scenario: A new leak outside the boundary fails the build
- **WHEN** a class outside the enumerated boundary is given a new dependency on `javax.lang.model.util.Types` or `Elements`
- **THEN** the architecture suite fails, flagging the out-of-boundary dependency

### Requirement: Engine-contract tests do not live in a strategy module

An end-to-end test that asserts engine behaviour — expansion, graph self-seeding, demand-driven leaf minting, weaving, cost selection, or realisation diagnostics — SHALL NOT reside in a strategy module. Such a test SHALL live in `processor` and SHALL be driven by a `FakeStrategy` rather than by ServiceLoading the real builtins. In particular, `SelfSeedExpansionSpec` SHALL be relocated from `strategies-builtin` into `processor`, and any sibling spec that the engine-internals encapsulation rule now makes illegal in a strategy module SHALL move with it.

#### Scenario: The self-seeding expansion spec is an engine test in the engine module
- **WHEN** the test suites are located after this change
- **THEN** `SelfSeedExpansionSpec` resides in `processor`, drives expansion through a `FakeStrategy`, and `strategies-builtin` no longer hosts any engine-only expansion spec

#### Scenario: A strategy module hosts only its own atom and output tests
- **WHEN** the remaining end-to-end specs in `strategies-builtin` are inspected
- **THEN** each asserts a builtin strategy's own atom, output, or targeted diagnostic — not a pure engine contract that the encapsulation rule would forbid

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

### Requirement: Methods are static only in a genuine static context

A `static` method is dispatched by `INVOKESTATIC` and therefore cannot be intercepted by an ordinary test double, which makes it the same testability hole the no-private-methods rule was written to close. Percolate's own code SHALL declare a method `static` only where a genuine static context requires it, and SHALL prefer a `protected` instance method otherwise. `static` SHALL NOT be used to hide a self-call from a `Spy`'s strict interaction checking — that case is served by declaring the interaction.

The permitted static contexts are:

- a **public** static method on the published `spi` surface, which third-party strategy authors already call and which cannot be converted without breaking the API — this covers the static factories on `Port`, `PortType`, `OperationSpec`, `Offer`, `Nullability`, `DirectiveInput`, and `ChildScopeSpec`, plus the whole of `LiteralCoercion` and `Subjects`
- a Dagger `@Provides` method
- vendored third-party sources under `lib/`
- a context where an instance is genuinely unavailable, such as a `main` entry point or a static initializer helper
- a method that reads or writes a `static` field of its own class, which has no instance to belong to
- a **named constructor**: a static whose whole body constructs and returns its own declaring type (or an interface that type implements). There is no instance to hang it on and nothing to intercept — a double over it could only return what the constructor already returns
- a **stateless all-static utility holder** — a `final` class with a private constructor, no instance state, and nothing but static members, annotated `@UtilityClass`. Such a class is a coherent grouping of pure functions with no instance to spy and nothing to inject, and is stubbed with `SpyStatic` instead

A utility holder SHALL group functions that genuinely belong together. A holder SHALL NOT be created for a single function, and a method SHALL NOT be moved into one, in order to escape this requirement. Conversely, a method SHALL NOT be declared `static` merely because it happens not to touch instance state: on a class that has instances, testability outranks that observation and the method stays an instance method.

#### Scenario: A helper is an instance method, not a static
- **WHEN** logic is extracted from a method into a helper on the same class
- **THEN** the helper is declared `protected`, carrying `@VisibleForTesting` if no production subclass uses it, rather than `static`

#### Scenario: A method that could be static stays an instance method
- **WHEN** a method on an instantiable class touches no instance field
- **THEN** it remains an instance method, because interception by a test double outweighs the fact that it could be static

#### Scenario: A named constructor keeps its static
- **WHEN** `Diagnostic.error`, `Cost.finite`, `Dep.port`, or `AccessPath.of` is reviewed under this requirement
- **THEN** it remains static, because its body is a single construction of its own type and a test double over it could return nothing else

#### Scenario: A factory carrying real logic is still decomposed
- **WHEN** a static factory returning its own type carries decisions in its helpers, as `GoalSpec.from` and `HoistPlan.forMethod` do
- **THEN** those helpers move to an injectable factory collaborator, even though the automated rule cannot distinguish them from a bare named constructor and does not flag them

#### Scenario: A stateless utility holder keeps its statics
- **WHEN** `Labels`, `Reactors`, `Blockings`, `LiteralCoercion`, or `PercolateCompiler` is reviewed under this requirement
- **THEN** its methods remain static, because the class is a `@UtilityClass`-shaped stateless holder with no instance to spy, and specs control it with `SpyStatic`

#### Scenario: A utility holder is not a testing escape hatch
- **WHEN** a single method is moved to a new all-static holder so that it need not be spy-tested
- **THEN** the extraction is rejected, because the exemption covers cohesive stateless function groups, not per-method escape hatches

#### Scenario: A static introduced to dodge a Spy is rejected
- **WHEN** a helper is declared `static` so that its self-call escapes a spied subject's `0 * _`
- **THEN** the declaration is rejected in favour of an instance method with the self-call declared in the spec

#### Scenario: The published spi statics are retained
- **WHEN** `spi`'s public static factories are reviewed under this requirement
- **THEN** they remain static, because converting them is a breaking change for third-party strategy authors, and only non-public statics on a class that has instances — such as `Nullability.either` — are in scope for conversion

#### Scenario: Dagger and vendored code are exempt
- **WHEN** `ProcessorModule`'s `@Provides` methods or `lib/javapoet` sources are reviewed
- **THEN** their static methods remain, as framework-mandated and third-party code respectively
