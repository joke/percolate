## MODIFIED Requirements

### Requirement: Engine internal methods are never private

The architecture suite SHALL enforce that no method declared by a class in a **decomposed package** carries the `private` modifier. The decomposed packages are `io.github.joke.percolate.processor.internal..` (the engine) **and `io.github.joke.percolate.spi.builtins..` (the built-in strategies)**. The rationale is a testability constraint, not a style preference: a `private` method is statically dispatched (`invokespecial`) and cannot be intercepted by any Spock/Mockito test double — even the inline mock maker — so it is not individually testable. Compiler-synthetic members (lambda and `access$` bridges), private constructors, and generated (`@Generated`/Lombok) members SHALL be exempt.

#### Scenario: A private engine-internal method fails the build
- **WHEN** a class in an engine `internal` package declares a `private` method
- **THEN** the architecture suite fails, flagging the method

#### Scenario: A private builtin-strategy method fails the build
- **WHEN** a class in `io.github.joke.percolate.spi.builtins..` declares a `private` method
- **THEN** the architecture suite fails, flagging the method

#### Scenario: Synthetic members and private constructors are exempt
- **WHEN** the architecture suite analyses a decomposed class that uses lambdas or declares a private constructor
- **THEN** the synthetic lambda/`access$` methods and the private constructor do not trip the rule

### Requirement: Engine internal classes stay within a size ceiling

The architecture suite SHALL enforce a ceiling on the size of each class in the **decomposed packages** — `io.github.joke.percolate.processor.internal..` **and `io.github.joke.percolate.spi.builtins..`** — a bound on method count, or an equivalent weighted-method-complexity or class-length metric — so that no class accretes responsibilities. This rule SHALL be **co-enforced** with the no-private rule: the no-private rule alone is satisfied by exposing a monolith's internals as package-private members, so the size ceiling is required to force separable logic into new small classes rather than exposed helpers.

#### Scenario: An oversized decomposed class fails the build
- **WHEN** a class in a decomposed package exceeds the configured size ceiling
- **THEN** the architecture suite fails, flagging the class

#### Scenario: The decomposed stages and strategies pass both structural rules
- **WHEN** the architecture suite analyses the decomposed engine `internal` packages and the `spi.builtins` strategy package
- **THEN** no class declares a `private` method and no class exceeds the size ceiling

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
