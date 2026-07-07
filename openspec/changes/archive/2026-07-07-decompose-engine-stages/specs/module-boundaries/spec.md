## ADDED Requirements

### Requirement: Engine internal methods are never private

The architecture suite SHALL enforce that no method declared by a class in `io.github.joke.percolate.processor.internal..` carries the `private` modifier. The rationale is a testability constraint, not a style preference: a `private` method is statically dispatched (`invokespecial`) and cannot be intercepted by any Spock/Mockito test double — even the inline mock maker — so it is not individually testable. Compiler-synthetic members (lambda and `access$` bridges), private constructors, and generated (`@Generated`/Lombok) members SHALL be exempt.

#### Scenario: A private engine-internal method fails the build
- **WHEN** a class in an engine `internal` package declares a `private` method
- **THEN** the architecture suite fails, flagging the method

#### Scenario: Synthetic members and private constructors are exempt
- **WHEN** the architecture suite analyses an engine `internal` class that uses lambdas or declares a private constructor
- **THEN** the synthetic lambda/`access$` methods and the private constructor do not trip the rule

### Requirement: Engine internal classes stay within a size ceiling

The architecture suite SHALL enforce a ceiling on the size of each class in `io.github.joke.percolate.processor.internal..` — a bound on method count, or an equivalent weighted-method-complexity or class-length metric — so that no class accretes responsibilities. This rule SHALL be **co-enforced** with the no-private rule: the no-private rule alone is satisfied by exposing a monolith's internals as package-private members, so the size ceiling is required to force separable logic into new small classes rather than exposed helpers.

#### Scenario: An oversized engine-internal class fails the build
- **WHEN** a class in an engine `internal` package exceeds the configured size ceiling
- **THEN** the architecture suite fails, flagging the class

#### Scenario: The decomposed stages pass both structural rules
- **WHEN** the architecture suite analyses the decomposed engine `internal` packages
- **THEN** no class declares a `private` method and no class exceeds the size ceiling
