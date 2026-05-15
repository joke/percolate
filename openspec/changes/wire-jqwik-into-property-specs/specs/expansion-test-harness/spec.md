## MODIFIED Requirements

### Requirement: jqwik property tests

The processor module SHALL contain jqwik-driven property specs (Groovy sources) at `processor/src/test/groovy/io/github/joke/percolate/processor/stages/expand/properties/` covering the algebraic contract of `expand`. Each spec is a Groovy class named `*Spec.groovy` that `extends ExpansionPropertyBase` (which itself `extends spock.lang.Specification`) — Spec naming and `Specification` inheritance match the rest of the test layer; jqwik provides the property-test engine via `@net.jqwik.api.Property`-annotated methods on the same class. The seven specs are:

- **`DeterminismSpec`**: `expand(g, S) == expand(g, S)` across repeated invocation.
- **`IdempotenceSpec`**: `expand(expand(g, S), S) == expand(g, S)`.
- **`OrderIndependenceSpec`**: for any permutation `S'` of `S`, `expand(g, S) == expand(g, S')`.
- **`MonotonicitySpec`**: for any `S₁ ⊆ S₂`, `edges(expand(g, S₁)) ⊆ edges(expand(g, S₂))`.
- **`IdentityCollapseSpec`**: across many generated seed/strategy pairs, no two distinct nodes share `(scope, location, type)`.
- **`DisjointAdditivitySpec`**: for disjoint `g₁`, `g₂`, `expand(g₁ ⊕ g₂, S) == expand(g₁, S) ⊕ expand(g₂, S)`.
- **`EmptyStrategyIdentitySpec`**: `expand(g, ∅)` equals `g` modulo non-strategy phases.

All property specs SHALL use `ExpansionHarness.expand(seed, ..., lists)` (**explicit mode**) with strategy lists drawn from the **fake-strategy alphabet** defined under "Fake strategies as the engine-algebra input alphabet". Property specs SHALL NOT use SPI-loaded production strategies — production strategy correctness is asserted by `ExpansionCapabilitiesSpec`, separately, so that a regression in one production strategy does not silently invalidate the engine algebra tests.

`@Property` method bodies SHALL be plain Groovy assertions (not Spock `given:/when:/then:` blocks — those AST transforms are not visible to jqwik's JUnit Platform engine). Spock feature methods (`def 'name'() { ... }`) MAY co-exist on the same class for non-property assertions.

Generators SHALL be `@net.jqwik.api.Provide` methods on `ExpansionPropertyBase` and SHALL produce `MapperGraph` instances directly (via `GraphFixtures` or inline construction) constrained to `TypeUniverse`. Generators SHALL NOT route through a `MapperSpec` / `SeedDsl` indirection. Plain `java.util.Random` is not a substitute because it cannot reproduce a shrunk counterexample from a pinned seed. Each `@Provide` SHALL construct a fresh `MapperGraph` per invocation (jqwik may call generators repeatedly during shrinking; mutable graphs MUST NOT be shared across iterations).

Fakes drawn into the property alphabet SHALL be stateless across invocations. `DivergentBridge` (which holds an `AtomicInteger` counter to force divergence) SHALL NOT appear in any `@Provide` method's output; it is reserved for `ExpansionFailureModesSpec` only.

#### Scenario: Seven property specs exist
- **WHEN** the directory `processor/src/test/groovy/.../expand/properties/` is inspected
- **THEN** it contains Groovy class files for each of the seven specs listed above
- **AND** each declares at least one method annotated with `net.jqwik.api.@Property`

#### Scenario: Properties extend the property base
- **WHEN** the source of any property spec is inspected
- **THEN** the class `extends ExpansionPropertyBase`
- **AND** `ExpansionPropertyBase` itself `extends spock.lang.Specification`

#### Scenario: Properties use explicit mode with fakes
- **WHEN** the source of any property spec is inspected
- **THEN** every `ExpansionHarness.expand(...)` call passes explicit strategy lists
- **AND** every strategy passed is an instance of a class under `processor/src/test/groovy/.../properties/fakes/`

#### Scenario: DivergentBridge is excluded from the property alphabet
- **WHEN** the `@Provide` methods on `ExpansionPropertyBase` are inspected
- **THEN** none returns an `Arbitrary<List<Bridge>>` (or `Arbitrary<Bridge>`) whose output may contain a `DivergentBridge` instance

### Requirement: jqwik configuration

`processor/build.gradle` SHALL configure jqwik with a default `@Property(tries = 500)` and store a property database under `build/jqwik-database` so that shrunken counterexamples are replayed deterministically. The default-tries configuration SHALL live as a class-level `@net.jqwik.api.PropertyDefaults(tries = 500)` annotation on `ExpansionPropertyBase` — not on individual specs, not in `jqwik.properties`. Individual `@Property` methods MAY override `tries` for slow properties; the base-class value is the project-wide default.

Each `@Property` method on a property spec SHALL pin a default seed via `@Property(seed = '<long>')` (jqwik requires a `long`-parseable string) to make CI failures locally reproducible on fresh databases.

#### Scenario: jqwik database is configured
- **WHEN** `processor/build.gradle` is inspected
- **THEN** jqwik's database directory is configured to `build/jqwik-database` (via `-Djqwik.database.directory=...` or equivalent)

#### Scenario: PropertyDefaults lives on the base class
- **WHEN** the source of `ExpansionPropertyBase` is inspected
- **THEN** the class carries `@net.jqwik.api.PropertyDefaults(tries = 500)`
- **AND** no individual property spec re-declares the same default

#### Scenario: Properties pin seeds
- **WHEN** any `@Property`-annotated method under `expand/properties/` is inspected
- **THEN** its `@Property` annotation specifies an explicit `seed` value

## ADDED Requirements

### Requirement: ExpansionPropertyBase shared base class

The processor module SHALL contain `processor/src/test/groovy/io/github/joke/percolate/processor/stages/expand/properties/ExpansionPropertyBase.groovy`, an `abstract` Groovy class that:

- `extends spock.lang.Specification` so subclasses participate in Spock's tag/timeout infrastructure.
- Carries `@spock.lang.Tag('unit')` and `@spock.lang.Timeout(60)` at the class level so all subclasses inherit the same test-tier classification.
- Carries `@net.jqwik.api.PropertyDefaults(tries = 500)` so all `@Property` methods on subclasses inherit the default tries count.
- Defines `@net.jqwik.api.Provide` methods that return `net.jqwik.api.Arbitrary` instances for the inputs consumed by the seven property specs: at minimum `seedGraphs()` → `Arbitrary<MapperGraph>`, `fakeBridges()` → `Arbitrary<List<Bridge>>`, `fakeSourceSteps()` → `Arbitrary<List<SourceStep>>`, `fakeGroupTargets()` → `Arbitrary<List<GroupTarget>>`.

`ExpansionPropertyBase` SHALL be the single source of generator definitions. Individual property specs SHALL NOT define their own `@Provide` methods or maintain parallel `java.util.Random`-driven generators. The previously-existing `GraphGenerator.groovy` (which used `java.util.Random`) SHALL be removed.

Property specs reference inherited generators via `@net.jqwik.api.ForAll('seedGraphs')`, `@ForAll('fakeBridges')`, etc., on their `@Property` method parameters.

#### Scenario: Base class exists and is inherited
- **WHEN** the source tree is inspected
- **THEN** `ExpansionPropertyBase.groovy` exists at `processor/src/test/groovy/io/github/joke/percolate/processor/stages/expand/properties/`
- **AND** each of the seven property specs declares `extends ExpansionPropertyBase`

#### Scenario: Base class carries the shared annotations
- **WHEN** the source of `ExpansionPropertyBase` is inspected
- **THEN** the class carries `@spock.lang.Tag('unit')`, `@spock.lang.Timeout(60)`, and `@net.jqwik.api.PropertyDefaults(tries = 500)`
- **AND** the class declares `@Provide` methods returning `Arbitrary<MapperGraph>`, `Arbitrary<List<Bridge>>`, `Arbitrary<List<SourceStep>>`, and `Arbitrary<List<GroupTarget>>`

#### Scenario: GraphGenerator is removed
- **WHEN** the source tree is inspected
- **THEN** no file named `GraphGenerator.groovy` (or any class that uses `java.util.Random` to generate property-test inputs) exists under `processor/src/test/groovy/.../properties/`

#### Scenario: Generators produce fresh graphs per invocation
- **WHEN** an `@Provide Arbitrary<MapperGraph>` is sampled multiple times
- **THEN** each sample returns a freshly constructed `MapperGraph` instance
- **AND** no two samples share the same node or edge references

### Requirement: Property specs use ForAll on inherited generators

Each `@Property` method on a property spec SHALL declare its inputs via `@net.jqwik.api.ForAll('<providerMethodName>')` parameters that reference the `@Provide` methods on `ExpansionPropertyBase`. Inline `@ForAll`-without-name (which falls back to jqwik's built-in arbitraries) SHALL NOT appear for `MapperGraph`, `List<Bridge>`, `List<SourceStep>`, or `List<GroupTarget>` parameters — those types have no useful built-in arbitrary.

#### Scenario: ForAll references named provider
- **WHEN** the source of any property spec is inspected
- **THEN** every `MapperGraph`, `List<Bridge>`, `List<SourceStep>`, and `List<GroupTarget>` parameter on a `@Property` method carries a `@ForAll('<name>')` annotation
- **AND** the named `<name>` matches a `@Provide` method on `ExpansionPropertyBase`
