## ADDED Requirements

### Requirement: Methods are static only in a genuine static context

A `static` method is dispatched by `INVOKESTATIC` and therefore cannot be intercepted by an ordinary test double, which makes it the same testability hole the no-private-methods rule was written to close. Percolate's own code SHALL declare a method `static` only where a genuine static context requires it, and SHALL prefer a `protected` instance method otherwise. `static` SHALL NOT be used to hide a self-call from a `Spy`'s strict interaction checking — that case is served by declaring the interaction.

The permitted static contexts are:

- a **public** static method on the published `spi` surface, which third-party strategy authors already call and which cannot be converted without breaking the API — this covers the static factories on `Port`, `PortType`, `OperationSpec`, `Offer`, `Nullability`, `DirectiveInput`, and `ChildScopeSpec`, plus the whole of `LiteralCoercion` and `Subjects`
- a Dagger `@Provides` method
- vendored third-party sources under `lib/`
- a context where an instance is genuinely unavailable, such as a `main` entry point or a static initializer helper

#### Scenario: A helper is an instance method, not a static
- **WHEN** logic is extracted from a method into a helper on the same class
- **THEN** the helper is declared `protected`, carrying `@VisibleForTesting` if no production subclass uses it, rather than `static`

#### Scenario: A static introduced to dodge a Spy is rejected
- **WHEN** a helper is declared `static` so that its self-call escapes a spied subject's `0 * _`
- **THEN** the declaration is rejected in favour of an instance method with the self-call declared in the spec

#### Scenario: The published spi statics are retained
- **WHEN** `spi`'s public static factories are reviewed under this requirement
- **THEN** they remain static, because converting them is a breaking change for third-party strategy authors, and only non-public statics such as `Nullability.either` are in scope for conversion

#### Scenario: Dagger and vendored code are exempt
- **WHEN** `ProcessorModule`'s `@Provides` methods or `lib/javapoet` sources are reviewed
- **THEN** their static methods remain, as framework-mandated and third-party code respectively
