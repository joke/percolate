# test-double-conventions Specification

## Purpose

Defines how a spec isolates its subject, so that "individually testable" — the property the no-private-methods, unused-protected and no-static rules exist to guarantee at the production end — is actually exercised at the test end. Collaborators are mocked rather than faked; every module can mock a final class and stub a static, from one configuration rather than a per-module copy that reaches one module of six; every protected method earns its own feature method rather than being covered incidentally through a caller; and a self-call on a spied subject is declared, never hidden behind `static`. That last one is a correction: the opposite advice was once recorded here and produced a meaningful share of the repo's static methods. Completeness is verified by pitest thresholds, not by a static rule — the fact lives half in Java bytecode and half in Groovy, and Spock's AST transform leaves no call edge to analyse.
## Requirements
### Requirement: Test doubles are mocks unless a real instance is structurally required

Specs SHALL isolate a subject with Spock `Mock()`, `Stub()`, or `Spy()` rather than a hand-written fake. A hand-written double SHALL be introduced only where the collaborator must be a real instance for structural reasons — chiefly the compile-e2e harness in `test-foundation`, whose doubles are discovered by `ServiceLoader` and instantiated by the annotation-processing round rather than injected by the spec. A fake introduced merely because mocking was inconvenient SHALL NOT be accepted.

#### Scenario: A collaborator is mocked, not faked
- **WHEN** a spec needs to control a collaborator's behaviour
- **THEN** it declares `Type collaborator = Mock()` and stubs the interactions, rather than defining a fake implementation class

#### Scenario: The ServiceLoader harness keeps its real doubles
- **WHEN** `test-foundation`'s `FakeStrategy` and `PercolateCompiler` are reviewed against this requirement
- **THEN** they remain, because a `ServiceLoader`-discovered strategy cannot be supplied to the compile round as a mock

### Requirement: Every module can mock final classes and final methods

Every module with a Spock suite SHALL run with the mockito mock maker (`spock.mock.MockMakers.mockito`) as its preferred mock maker, so that `final` classes and `final` methods — including Lombok `@Value` types and package-private `final` stages — are mockable and spyable without per-spec configuration. No module SHALL be unable to mock a final type merely because its configuration was never propagated.

#### Scenario: A Lombok @Value type is mocked in any module
- **WHEN** a spec in `spi`, `processor`, `strategies-builtin`, `reactor`, `reactor-blocking`, `annotations`, or `architecture-tests` declares `Mock()` for a `final` class
- **THEN** the mock is created successfully rather than failing with a mock-maker capability error

#### Scenario: The mockito runtime dependency is present wherever it is required
- **WHEN** a module's test runtime classpath is resolved
- **THEN** it contains mockito, so the configured preferred mock maker is available rather than silently falling back

### Requirement: Protected methods are spy-tested in isolation

Every concrete `protected` method SHALL be exercised directly through a `Spy()` of its declaring class, not only incidentally through a caller. A method extracted purely to satisfy the no-private-methods rule SHALL still receive its own feature method. Completeness of this coverage SHALL be verified by the existing pitest thresholds rather than by a static analysis rule.

#### Scenario: An extracted helper gets its own feature method
- **WHEN** a helper is extracted from a larger method onto the same class
- **THEN** a feature method spies the declaring class and calls the helper directly, asserting its result or interactions

#### Scenario: Spy coverage is measured by mutation testing
- **WHEN** a protected method is never exercised in isolation
- **THEN** its surviving mutants push the module below the 85/95/90 thresholds and fail `check`, rather than being caught by a dedicated lint rule

### Requirement: A self-call on a spied subject is declared, never hidden

When a spied subject calls its own method, the spec SHALL declare that interaction explicitly. Making the callee `static` so that `INVOKESTATIC` bypasses the spy's interception SHALL NOT be used to keep a strict `0 * _` satisfied. The declaration order is significant: Spock matches each invocation against interactions in declaration order and the first non-saturated match wins, so the specific self-call SHALL be declared before the subject-scoped wildcard.

The canonical shape is:

```groovy
when:
subject.call(x)

then:
1 * subject.f(_)
1 * subject._
0 * _
```

`1 * subject._` absorbs the entry call from the `when:` block, so the spec does not restate it. This is a deliberate carve-out from the convention forbidding a bare `_`: that convention targets **argument** wildcards, whereas this is a **method** wildcard scoped to a single mock and bounded by an exact cardinality, so an undeclared additional self-call still falls through to `0 * _`.

#### Scenario: An extracted self-call is declared rather than hidden
- **WHEN** a helper is extracted onto a class whose spec spies it with a strict `0 * _`
- **THEN** the spec adds `1 * subject.helper(...)` ahead of `1 * subject._`, and the helper stays a non-static instance method

#### Scenario: An undeclared self-call still fails
- **WHEN** the subject makes a self-call that the spec declares neither specifically nor within the wildcard's cardinality
- **THEN** the trailing `0 * _` fails the feature method

#### Scenario: The idiom survives future convention review
- **WHEN** the Spock convention guidance is consulted
- **THEN** it records both this idiom and the reason `1 * subject._` is exempt from the bare-`_` prohibition, so the static workaround is not reintroduced

### Requirement: Residual statics are stubbed with SpyStatic

Where a method legitimately remains `static`, a spec that needs to control it SHALL stub it via Spock's `SpyStatic(Class)` rather than exercising the real implementation incidentally or restructuring production code to route around it. Because `SpyStatic` is provided only by the mockito mock maker and Mockito's static mocking is confined to the registering thread, Spock's in-JVM parallel execution SHALL remain disabled wherever `SpyStatic` is used.

#### Scenario: A published static helper is stubbed
- **WHEN** a spec needs `spi.LiteralCoercion` or `spi.Subjects` to return a controlled value
- **THEN** it registers `SpyStatic(LiteralCoercion)` and stubs the call, rather than constructing inputs that coax the real implementation into the desired result

#### Scenario: Static stubbing keeps its thread guarantee
- **WHEN** a module uses `SpyStatic`
- **THEN** Spock's `runner { parallel { enabled false } }` remains in effect for that module, since Mockito's static mock is registered per-thread
