## ADDED Requirements

### Requirement: SetterAssembly's unit spec covers its gate, its member request, and its rank pricing

`SetterAssemblySpec` SHALL cover, at minimum, the strategy's positive discovery scenario, every rejection branch of its gate, its member request, and its self-pricing, exercised against a **mocked `ResolveCtx`** seam with no javac and no `ResolveCtxBuilder`.

The required scenarios are: a bean with a no-argument constructor and matching setters yields one `OperationSpec` with one sub-target port per declared child; a missing, private, or parameterised-only constructor yields no offer; an abstract target yields no offer; a declared child with no matching setter yields no offer; a declared-children set that is a strict subset of the available setters still yields an offer carrying only the declared ports; an empty declared-children set yields no offer; an inherited setter matches; a `this`-returning setter matches; the emitted spec declares one `MemberRequest.method` whose dedup key distinguishes the setter form; and the emitted weight follows the strategy's rank under at least three option values.

#### Scenario: The spec asserts the containment gate both ways
- **WHEN** `SetterAssemblySpec` is inspected
- **THEN** it contains a feature asserting an offer when the declared children are a strict subset of the setters
- **AND** a feature asserting no offer when a declared child has no matching setter

#### Scenario: The spec asserts every constructor rejection branch
- **WHEN** `SetterAssemblySpec` is inspected
- **THEN** it contains features asserting no offer for an absent no-argument constructor, for a private one, and for an abstract target

#### Scenario: The spec asserts the empty-declaration bail
- **WHEN** `SetterAssemblySpec` is inspected
- **THEN** it contains a feature asserting that an empty declared-children set yields no offer

#### Scenario: The spec asserts the setter match rules
- **WHEN** `SetterAssemblySpec` is inspected
- **THEN** it contains a feature asserting that an inherited setter matches
- **AND** a feature asserting that a `this`-returning setter matches

#### Scenario: The spec asserts the member request
- **WHEN** `SetterAssemblySpec` is inspected
- **THEN** it contains a feature asserting that the emitted spec declares exactly one `MemberRequest.method`
- **AND** a feature asserting that the dedup key distinguishes the setter form, the target, and the ordered children

#### Scenario: The spec asserts its rank pricing
- **WHEN** `SetterAssemblySpec` is inspected
- **THEN** it contains features asserting the emitted weight for an unset option, for a value ranking the setter form first, and for a value ranking it last
- **AND** the option value is supplied by stubbing the mocked seam's `option(String)` lookup

#### Scenario: The spec uses the mocked seam only
- **WHEN** `SetterAssemblySpec` is inspected
- **THEN** it stubs `ResolveCtx` as a mock and constructs no `Types`/`Elements` pair

### Requirement: The construction preference parser is covered by its own unit spec

The shared parser that turns the `percolate.construction.preference` option into a per-form weight SHALL carry its own unit specification, covering the parse and the rank-to-weight mapping independently of any strategy.

The required scenarios are: an absent option yields the default order; a one-token value ranks that form first and appends the rest in default order; a multi-token value is honoured in order; an unrecognised token is ignored; surrounding whitespace is tolerated; and the three forms always receive three distinct weights.

#### Scenario: The parser spec covers the effective order
- **WHEN** the construction preference parser's spec is inspected
- **THEN** it contains features for an absent option, a one-token value, a multi-token value, and an unrecognised token

#### Scenario: The parser spec asserts weight distinctness
- **WHEN** the construction preference parser's spec is inspected
- **THEN** it contains a feature asserting that the three forms receive three distinct weights for every covered option value

## MODIFIED Requirements

### Requirement: Builder strategy unit specs cover discovery, the subset gate, and pricing

Each builder strategy's unit specification SHALL cover, at minimum, its positive discovery scenario, its rejection scenarios, the containment gate, and its self-pricing, exercised against a **mocked `ResolveCtx`** seam with no javac and no `ResolveCtxBuilder`.

The required scenarios per builder strategy are: a target matching its convention yields one `OperationSpec` with one sub-target port per declared child; a target whose entry point, builder type, or `build()` method is private or absent yields no offer; a declared child with no matching setter yields no offer; a declared-children set that is a strict subset of the available setters still yields an offer carrying only the declared ports; an empty declared-children set yields no offer; and the emitted weight follows the builder form's rank under the `construction.preference` option — `Weights.STEP` when the option ranks the builder form first, and the weight of its rank otherwise.

#### Scenario: A builder spec asserts the containment gate both ways
- **WHEN** `FluentBuilderSpec` is inspected
- **THEN** it contains a feature asserting an offer when the declared children are a strict subset of the setters
- **AND** a feature asserting no offer when a declared child has no matching setter

#### Scenario: A builder spec asserts the empty-declaration bail
- **WHEN** any builder strategy spec is inspected
- **THEN** it contains a feature asserting that an empty declared-children set yields no offer

#### Scenario: A builder spec asserts its self-pricing under several preferences
- **WHEN** any builder strategy spec is inspected
- **THEN** it contains features asserting the emitted weight under a `construction.preference` value ranking the builder form first and under one ranking it lower
- **AND** the option value is supplied by stubbing the mocked seam's `option(String)` lookup

#### Scenario: Builder specs use the mocked seam only
- **WHEN** the four builder strategy specs are inspected
- **THEN** each stubs `ResolveCtx` as a mock and constructs no `Types`/`Elements` pair

### Requirement: ConstructorCall's unit spec covers its self-pricing

`ConstructorCallSpec` SHALL additionally cover that `ConstructorCall` derives its own weight from the `construction.preference` option through the shared parser — `Weights.STEP` when the option ranks the constructor form first (including when unset) and the weight of its rank otherwise — read through the mocked seam's `option(String)` lookup.

#### Scenario: ConstructorCall prices itself from the option
- **WHEN** `ConstructorCallSpec` is inspected
- **THEN** it contains features asserting the emitted weight for an unset option, for a value ranking the constructor form first, and for a value ranking it lower

#### Scenario: ConstructorCall names no other assembly form
- **WHEN** `ConstructorCall`'s source is inspected
- **THEN** it references no builder strategy class and names no other form's option token
