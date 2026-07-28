## ADDED Requirements

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
