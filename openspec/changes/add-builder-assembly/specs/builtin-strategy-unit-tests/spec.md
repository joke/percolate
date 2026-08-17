## MODIFIED Requirements

### Requirement: Per-strategy unit spec presence, naming and location

Every concrete `@AutoService(io.github.joke.percolate.spi.ExpansionStrategy.class)` implementation shipped from the `percolate-strategies-builtin` module SHALL have a corresponding Spock specification named `<StrategyClassSimpleName>Spec.groovy`. (`ExpansionStrategy` is the single strategy SPI interface; the former `Bridge` / `GroupTarget` / `PathSegmentResolver` interfaces are removed.)

The spec SHALL reside at `strategies-builtin/src/test/groovy/io/github/joke/percolate/spi/builtins/<StrategyClassSimpleName>Spec.groovy` (mirroring the strategy's main-source package). The spec SHALL `extend spock.lang.Specification` and SHALL carry the annotation `@spock.lang.Tag('unit')` at the class level.

The shipped built-in strategies and their required specs are: `DirectAssignSpec`, `MethodCallBridgeSpec`, `ConstructorCallSpec`, `FluentBuilderSpec`, `ProtobufBuilderSpec`, `WithBuilderSpec`, `SideLocatedBuilderSpec`, `WidenPrimitiveSpec`, `PrimitiveWrapperConversionSpec`, `ConstantValueSpec`, `NullnessCrossingSpec`, `StreamMapSpec`, `OptionalContainerSpec`, `ListContainerSpec`, `SetContainerSpec`, `ArrayContainerSpec`, `GetterPathResolverSpec`, `FieldPathResolverSpec`, and `MethodPathResolverSpec`.

The superseded per-operation container specs (`IterableUnwrapSpec`, `OptionalUnwrapSpec`, `OptionalWrapSpec`, `ListWrapSpec`, `SetWrapSpec`, `OptionalCollectSpec`, `SetCollectSpec`, `ListCollectSpec`, `ArrayCollectSpec`, `SetMapSpec`, `ListMapSpec`, `OptionalMapSpec`), and `GetterReadSpec` / `SingletonSpec` / `RecordPathResolverSpec`, SHALL NOT exist — the corresponding strategies were folded into the one-class-per-container-type strategies or otherwise removed.

#### Scenario: Every builtin has a matching spec
- **WHEN** the contents of `strategies-builtin/src/main/java/io/github/joke/percolate/spi/builtins/` and `strategies-builtin/src/test/groovy/io/github/joke/percolate/spi/builtins/` are inspected
- **THEN** for every concrete class in the main tree annotated with `@AutoService(ExpansionStrategy.class)`, a sibling `<SimpleName>Spec.groovy` file exists in the test tree

#### Scenario: The built-in strategy specs are present
- **WHEN** the test tree is inspected
- **THEN** `DirectAssignSpec`, `MethodCallBridgeSpec`, `ConstructorCallSpec`, `FluentBuilderSpec`, `ProtobufBuilderSpec`, `WithBuilderSpec`, `SideLocatedBuilderSpec`, `WidenPrimitiveSpec`, `PrimitiveWrapperConversionSpec`, `ConstantValueSpec`, `NullnessCrossingSpec`, `StreamMapSpec`, `OptionalContainerSpec`, `ListContainerSpec`, `SetContainerSpec`, `ArrayContainerSpec`, `GetterPathResolverSpec`, `FieldPathResolverSpec`, and `MethodPathResolverSpec` are all present
- **AND** each extends `spock.lang.Specification` and carries `@spock.lang.Tag('unit')`

#### Scenario: Removed strategies have no specs
- **WHEN** the test tree is inspected
- **THEN** no file named `IterableUnwrapSpec.groovy`, `OptionalUnwrapSpec.groovy`, `OptionalWrapSpec.groovy`, `ListWrapSpec.groovy`, `SetWrapSpec.groovy`, `OptionalCollectSpec.groovy`, `SetCollectSpec.groovy`, `ListCollectSpec.groovy`, `ArrayCollectSpec.groovy`, `SetMapSpec.groovy`, `ListMapSpec.groovy`, `OptionalMapSpec.groovy`, `GetterReadSpec.groovy`, or `RecordPathResolverSpec.groovy` exists

#### Scenario: Specs are tagged as unit
- **WHEN** any one of the required strategy specs is inspected
- **THEN** the class is annotated with `@spock.lang.Tag('unit')` (the Spock-package `Tag`, not `org.junit.jupiter.api.Tag`)
- **AND** the class extends `spock.lang.Specification`

## ADDED Requirements

### Requirement: Builder strategy unit specs cover discovery, the subset gate, and pricing

Each builder strategy's unit specification SHALL cover, at minimum, its positive discovery scenario, its rejection scenarios, the containment gate, and its self-pricing, exercised against a **mocked `ResolveCtx`** seam with no javac and no `ResolveCtxBuilder`.

The required scenarios per builder strategy are: a target matching its convention yields one `OperationSpec` with one sub-target port per declared child; a target whose entry point, builder type, or `build()` method is private or absent yields no offer; a declared child with no matching setter yields no offer; a declared-children set that is a strict subset of the available setters still yields an offer carrying only the declared ports; an empty declared-children set yields no offer; and the emitted weight is `Weights.STEP` when the option resolves to its own form and `Weights.EXPENSIVE` otherwise.

#### Scenario: A builder spec asserts the containment gate both ways
- **WHEN** `FluentBuilderSpec` is inspected
- **THEN** it contains a feature asserting an offer when the declared children are a strict subset of the setters
- **AND** a feature asserting no offer when a declared child has no matching setter

#### Scenario: A builder spec asserts the empty-declaration bail
- **WHEN** any builder strategy spec is inspected
- **THEN** it contains a feature asserting that an empty declared-children set yields no offer

#### Scenario: A builder spec asserts its self-pricing under both preferences
- **WHEN** any builder strategy spec is inspected
- **THEN** it contains features asserting the emitted weight under `construction.preference` resolving to `builder` and to `constructor`
- **AND** the option value is supplied by stubbing the mocked seam's `option(String)` lookup

#### Scenario: Builder specs use the mocked seam only
- **WHEN** the four builder strategy specs are inspected
- **THEN** each stubs `ResolveCtx` as a mock and constructs no `Types`/`Elements` pair

### Requirement: ConstructorCall's unit spec covers its self-pricing

`ConstructorCallSpec` SHALL additionally cover that `ConstructorCall` derives its own weight from the `construction.preference` option — `Weights.STEP` when the option resolves to `constructor` (including when unset) and `Weights.EXPENSIVE` when it resolves to `builder` — read through the mocked seam's `option(String)` lookup.

#### Scenario: ConstructorCall prices itself from the option
- **WHEN** `ConstructorCallSpec` is inspected
- **THEN** it contains features asserting the emitted weight for an unset option, for `constructor`, and for `builder`

#### Scenario: ConstructorCall names no other assembly strategy
- **WHEN** `ConstructorCall`'s source is inspected
- **THEN** it references no builder strategy class
