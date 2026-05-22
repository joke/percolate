## MODIFIED Requirements

### Requirement: Per-strategy unit spec presence, naming and location

Every concrete `@AutoService` implementation of `io.github.joke.percolate.spi.Bridge`, `io.github.joke.percolate.spi.SourceStep`, or `io.github.joke.percolate.spi.GroupTarget` shipped from the `percolate-strategies-builtin` module SHALL have a corresponding Spock specification named `<StrategyClassSimpleName>Spec.groovy`.

The spec SHALL reside at `strategies-builtin/src/test/groovy/io/github/joke/percolate/spi/builtins/<StrategyClassSimpleName>Spec.groovy` (mirroring the strategy's main-source package). The spec SHALL `extend spock.lang.Specification` and SHALL carry the annotation `@spock.lang.Tag('unit')` at the class level.

For the eleven built-ins shipped after `split-container-bridges`, the eleven required specs are: `DirectAssignSpec`, `MethodCallBridgeSpec`, `ConstructorCallSpec`, `IterableUnwrapSpec`, `OptionalUnwrapSpec`, `OptionalWrapSpec`, `ListWrapSpec`, `SetWrapSpec`, `OptionalCollectSpec`, `SetCollectSpec`, `ListCollectSpec`, `ArrayCollectSpec`.

The specs `SetMapSpec`, `ListMapSpec`, `OptionalMapSpec`, and `GetterReadSpec` SHALL NOT exist after this change — the corresponding strategies are removed (`SetMap` / `ListMap` / `OptionalMap` by this change; `GetterRead` by `bind-seed-chain-realisation`).

#### Scenario: Every builtin has a matching spec
- **WHEN** the contents of `strategies-builtin/src/main/java/io/github/joke/percolate/spi/builtins/` and `strategies-builtin/src/test/groovy/io/github/joke/percolate/spi/builtins/` are inspected
- **THEN** for every public final class in the main tree annotated with `@AutoService(Bridge.class)`, `@AutoService(SourceStep.class)`, or `@AutoService(GroupTarget.class)`, a sibling `<SimpleName>Spec.groovy` file exists in the test tree

#### Scenario: Removed strategies have no specs
- **WHEN** the `strategies-builtin/src/test/groovy/io/github/joke/percolate/spi/builtins/` directory is inspected
- **THEN** no file named `SetMapSpec.groovy`, `ListMapSpec.groovy`, `OptionalMapSpec.groovy`, or `GetterReadSpec.groovy` exists

#### Scenario: Specs are tagged as unit
- **WHEN** any one of the twelve required strategy specs is inspected
- **THEN** the class is annotated with `@spock.lang.Tag('unit')` (the Spock-package `Tag`, not `org.junit.jupiter.api.Tag`)
- **AND** the class extends `spock.lang.Specification`

### Requirement: Per-strategy scenario coverage

For each of the twelve strategy specs, the spec SHALL include at minimum:

- **Empty-return scenarios.** One Spock feature method per declared precondition demonstrating that the strategy returns an empty `Stream<BridgeStep>` / `Stream<Step>` / `Optional.empty()` when that precondition is not met. Examples: `IterableUnwrap` returns empty when the source is not an iterable/array (and when the source is an `Optional`, which is owned by `OptionalUnwrap`); `OptionalUnwrap` returns empty when the source is not an `Optional`; `*Collect` bridges return empty when the target is not the matching container; `ConstructorCall` returns empty when target tails are empty or no matching constructor exists.

- **Happy-path scenarios.** At least one feature method that exercises the strategy on inputs that satisfy all preconditions, asserting on the returned step's metadata (`inputType`, `outputType`, `weight`, `scopeTransition`, and `elementRole` where applicable).

- **Branch scenarios.** For strategies whose behaviour differs across multiple accepted input shapes or member-resolution paths, one feature method per distinguishable branch. Examples: `IterableUnwrap` must cover array input, `List<E>` input, `Set<E>` input, and explicitly decline `Optional<E>` input; `ConstructorCall` must cover the constructor-by-parameter-name path and the constructor-by-arity-and-fields path.

- **Scope-transition scenarios.** For strategies that declare a non-`PRESERVING` scope transition (every `*Unwrap` and every `*Collect`), at least one feature method asserting the step's `scopeTransition` value matches the strategy's declared role (`ENTERING` for `*Unwrap`; `EXITING` for `*Collect`). Element role SHALL be asserted as `"element"` for built-ins.

#### Scenario: Every strategy spec exercises at least one precondition failure
- **WHEN** any strategy spec is inspected
- **THEN** at least one feature method named to indicate a precondition (e.g. `'returns empty when source is not an Iterable'`) asserts an empty `Stream` / empty `Optional` result

#### Scenario: Iterable bridges cover all input shapes
- **WHEN** `IterableUnwrapSpec` is inspected
- **THEN** there exists a feature method exercising array input
- **AND** a feature method exercising `Iterable` (e.g. `List<E>`) input
- **AND** a feature method exercising `Set<E>` input
- **AND** a feature method exercising `Optional<E>` input that pins an empty-result outcome (Optional is owned by `OptionalUnwrap`)

#### Scenario: Scope-bearing bridges assert their scope transition
- **WHEN** any of the `*Unwrap` or `*Collect` specs is inspected
- **THEN** at least one feature method asserts the returned `BridgeStep`'s `scopeTransition` equals the strategy's declared value
- **AND** the same feature method asserts the returned `BridgeStep`'s `elementRole` equals `"element"`

### Requirement: Pinning policy for current-behaviour findings

Where a unit spec discovers behaviour that is unintentional or worth questioning but lies outside the scope of the current change to fix, the spec SHALL pin the current behaviour with a feature method whose name signals the pinning intent (e.g. `'pins current behaviour: MethodCallBridge accepts subtypeDistance == 0 for non-assignable inputs'`).

Adjacent to the feature method's `then:` block, the spec SHALL place a single-line source comment of the form `// FOLLOW-UP: <one-line summary>` so that the audit trail is discoverable by the next maintainer reading the test.

The `ListMap`-accepts-`Optional`-inputs finding pinned by the prior `ListMapSpec` SHALL NOT be carried forward — `ListMap` is deleted by this change; the same shape pattern is now expressed through the orthogonal `IterableUnwrap` + `*Collect` chain, and `IterableUnwrap` explicitly declines `Optional` input (see the per-strategy coverage requirement). The `MethodCallBridge.subtypeDistance` finding pinned by `MethodCallBridgeSpec` SHALL be carried forward unchanged.

#### Scenario: Known findings are pinned with FOLLOW-UP markers
- **WHEN** `MethodCallBridgeSpec` is inspected
- **THEN** at least one feature method demonstrates that `subtypeDistance` returns `0` for both same-type and non-assignable inputs, and pins the resulting `weight` in the returned `BridgeStep`
- **AND** a `// FOLLOW-UP:` source comment appears within twenty lines of that feature method
