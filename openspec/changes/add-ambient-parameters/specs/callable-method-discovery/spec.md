## MODIFIED Requirements

### Requirement: Multi-parameter methods are filtered

`DiscoverCallableMethodsStage` SHALL keep only methods whose **non-ambient** declared-parameter count is
**exactly one**: a method qualifies iff `getParameters().size() - <count of parameters annotated @Ambient> == 1`.
Any method with zero non-ambient parameters, or with more than one, SHALL be excluded. `@Ambient` parameters
SHALL NOT participate in this decision — they are sourced from the ambient environment rather than from the
mapped value, so a method carrying them is still structurally a one-port bridge (`MethodCallBridge` consumes
only single-mapped-parameter candidates; see `ambient-parameters` and expansion-strategy-spi).

The former deferral of multi-parameter methods to a future multi-argument assembly strategy (an
`AssemblyStrategy` analogous to `ConstructorCall` but over a callable method) is **withdrawn**. That approach
is explicitly rejected: nested-target assembly through `ConstructorCall` plus single-argument delegation
already express multi-argument construction from mapped sources, and the remaining need — threading a value
that is not derived from the mapped object — is served by `@Ambient` without a new assembly strategy.

#### Scenario: Two-parameter method with no ambient is excluded
- **WHEN** the mapper declares `Pet adopt(Dog d, Owner o)`
- **THEN** the produced index does NOT contain a `MethodCandidate` for `adopt(Dog, Owner)`

#### Scenario: Single-parameter method is included
- **WHEN** the mapper declares `Pet adopt(Dog d)`
- **THEN** the produced index contains a `MethodCandidate` for `adopt(Dog)`

#### Scenario: Zero-parameter method is excluded
- **WHEN** the mapper declares `Pet stray()`
- **THEN** the produced index does NOT contain a `MethodCandidate` for `stray()` (its non-ambient parameter count is not exactly one)

#### Scenario: Two-parameter method with one ambient is included
- **WHEN** the mapper declares `default Price mapPrice(Integer taxFactor, @Ambient Order order)`
- **THEN** the produced index contains a `MethodCandidate` for it, because its non-ambient parameter count is exactly one

#### Scenario: Several ambients still leave one mapped parameter
- **WHEN** the mapper declares `default Price mapPrice(Integer taxFactor, @Ambient Order order, @Ambient Locale locale)`
- **THEN** the produced index contains a `MethodCandidate` for it

#### Scenario: An all-ambient method is excluded
- **WHEN** the mapper declares `default Price mapPrice(@Ambient Order order)`
- **THEN** the produced index does NOT contain a `MethodCandidate` for it, because its non-ambient parameter count is zero

#### Scenario: Two mapped parameters plus an ambient is excluded
- **WHEN** the mapper declares `default Price mapPrice(Integer taxFactor, Integer discount, @Ambient Order order)`
- **THEN** the produced index does NOT contain a `MethodCandidate` for it, because its non-ambient parameter count is two
