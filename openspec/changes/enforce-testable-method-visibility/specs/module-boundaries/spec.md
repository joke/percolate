## MODIFIED Requirements

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

## ADDED Requirements

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
