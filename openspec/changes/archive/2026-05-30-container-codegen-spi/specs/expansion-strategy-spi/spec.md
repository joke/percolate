## MODIFIED Requirements

### Requirement: BridgeStep result type

The percolate-spi module SHALL define an immutable Lombok `@Value` type `io.github.joke.percolate.spi.BridgeStep` with these fields, in this order:

- `TypeMirror inputType` — the type the strategy consumes.
- `TypeMirror outputType` — the type the strategy produces.
- `int weight` — the cost; documented to use values from `Weights`.
- `Codegen codegen` — the codegen handle the step attaches to its realised edge. For a scalar bridge this is an `EdgeCodegen` (rendered as one expression). For a container bridge (sequence collect/iterate or wrapper wrap/unwrap) this is the container's **codegen provider** (`SequenceContainer`/`WrapperContainer`, which `implement ContainerCodegen`/`WrapperCodegen`); the realised edge carries the provider plus the step's operation, and the composer asks the provider for the paradigm-appropriate snippet (see the `graph-model` Edge modification). `EdgeCodegen` and the container handles are all `Codegen`.
- `ScopeTransition scopeTransition` — how this step relates to element scope. Default `ScopeTransition.PRESERVING`.
- `String elementRole` — the role name for the element scope this step participates in. Consulted only when `scopeTransition != PRESERVING`. Default `"element"`.

For a direct same-scope bridge (DirectAssign, MethodCallBridge, GetterPathResolver, conversion strategies), `scopeTransition = PRESERVING` and `codegen` is an `EdgeCodegen`.

For a scope-entering bridge (a container's iterate/unwrap — `IterableUnwrap`-style for sequences, `OptionalUnwrap`-style for wrappers, including developer `FluxContainer`/`MonoContainer`), `scopeTransition = ENTERING`; the output is at `ElementLocation(elementRole)` and `codegen` is the container provider.

For a scope-exiting bridge (a container's collect — `SetCollect`/`ListCollect`-style, including developer `FluxContainer`/`MonoContainer`), `scopeTransition = EXITING`; the input is at `ElementLocation(elementRole)` and `codegen` is the container provider.

#### Scenario: BridgeStep exposes its six fields
- **WHEN** a `BridgeStep` is constructed with `inputType`, `outputType`, `weight`, `codegen`, `scopeTransition`, and `elementRole`
- **THEN** `getInputType()`, `getOutputType()`, `getWeight()`, `getCodegen()`, `getScopeTransition()`, and `getElementRole()` return those values
- **AND** `getCodegen()` returns a `Codegen` (an `EdgeCodegen` for a scalar step, a container provider for a container step)

#### Scenario: BridgeStep is value-equal
- **WHEN** two `BridgeStep` instances are constructed with equal field values
- **THEN** they are `equal` and have equal `hashCode`s

#### Scenario: A container BridgeStep carries the container provider
- **WHEN** a `SequenceContainer`/`WrapperContainer` base emits a collect/iterate/wrap/unwrap step
- **THEN** the step's `codegen` is the container instance itself (a `ContainerCodegen`/`WrapperCodegen`)
- **AND** the realised edge built from it carries that provider plus the step's operation

#### Scenario: BridgeStep with ENTERING scope identifies a scope-enter bridge
- **WHEN** a `BridgeStep` is constructed with `scopeTransition = ScopeTransition.ENTERING`, `elementRole = "element"`, `inputType = List<String>`, and `outputType = String`
- **THEN** the driver allocates the bridge's output node at `ElementLocation("element")`

#### Scenario: BridgeStep with EXITING scope identifies a scope-exit bridge
- **WHEN** a `BridgeStep` is constructed with `scopeTransition = ScopeTransition.EXITING`, `elementRole = "element"`, `inputType = String`, and `outputType = Set<String>`
- **THEN** the driver requires the bridge's input node to be at `ElementLocation("element")` (allocating fresh if necessary)
