## MODIFIED Requirements

### Requirement: BridgeStep result type

The processor SHALL define an immutable Lombok `@Value` type `io.github.joke.percolate.processor.spi.BridgeStep` with these fields, in this order:

- `TypeMirror inputType` — the type the strategy consumes.
- `TypeMirror outputType` — the type the strategy produces.
- `int weight` — the cost; documented to use values from `Weights`.
- `EdgeCodegen codegen` — the codegen lambda that renders the step.
- `List<ElementSeed> elementSeeds` — zero or more inner-scope conversion requests the driver SHALL spawn alongside the outer REALISED edge produced from this step. Empty for same-location bridge steps (existing strategies); non-empty for container "map" steps that need element-scope sub-conversions.

For a direct bridge (the strategy emits an edge between the two endpoints of the seed query), `inputType` equals the seed's source-side type and `outputType` equals the seed's target-side type.

For a chain-step bridge (the strategy emits an edge that requires an upstream value of a type not present in the seed's source side), `inputType` is the type the strategy needs and `outputType` is the type the strategy produces. The driver materialises the input and output endpoints accordingly (see `graph-expansion`).

For a container "map" step (the strategy emits an outer edge between two container-typed nodes, requiring an inner per-element sub-conversion), `inputType` and `outputType` describe the OUTER container endpoints; the inner element-level conversion is described by `elementSeeds` and resolved by the driver and downstream strategies through the same fixed-point machinery.

`elementSeeds` SHALL be a defensive copy held by the value type; mutations to a caller-passed list after construction SHALL NOT affect the constructed step.

#### Scenario: BridgeStep exposes its five fields
- **WHEN** a `BridgeStep` is constructed with `inputType`, `outputType`, `weight`, `codegen`, and `elementSeeds`
- **THEN** `getInputType()`, `getOutputType()`, `getWeight()`, `getCodegen()`, and `getElementSeeds()` return those values

#### Scenario: BridgeStep is value-equal
- **WHEN** two `BridgeStep` instances are constructed with equal field values (including equal `elementSeeds` lists)
- **THEN** they are `equal` and have equal `hashCode`s

#### Scenario: BridgeStep with empty elementSeeds behaves as a same-location step
- **WHEN** a `BridgeStep` is constructed with `elementSeeds = List.of()`
- **THEN** the step describes a single-edge emission with no inner element-scope work
- **AND** the driver applies the unified edge-emission rule without spawning element nodes

#### Scenario: BridgeStep with non-empty elementSeeds describes a container map
- **WHEN** a `BridgeStep` is constructed with `elementSeeds = [ElementSeed("element", innerIn, innerOut)]`
- **THEN** the driver emits the outer REALISED edge between the step's `inputType` and `outputType` endpoints AND spawns a pair of parent-linked element nodes plus a SEED between them (see `graph-expansion`)

## ADDED Requirements

### Requirement: ElementSeed result type

The processor SHALL define an immutable Lombok `@Value` type
`io.github.joke.percolate.processor.spi.ElementSeed` with three fields:

- `String role` — a stable discriminator naming the element scope's
  role within its container. The default value for single-element-
  scope containers is the literal string `"element"`. Future multi-
  role containers (e.g., `Map<K,V>` with `"key"` and `"value"`) use
  this field to distinguish their scopes.
- `TypeMirror inputType` — the type of the element-scope value
  flowing FROM the container's input side.
- `TypeMirror outputType` — the type of the element-scope value
  flowing INTO the container's output side.

`ElementSeed` SHALL be value-equal by all three fields. Two
`ElementSeed`s with the same role but different types are distinct
seeds and produce distinct element-scope SEEDs in the graph.

The `role` field SHALL be a non-empty `String`. The driver uses the
role as part of the `ElementLocation` segment and consequently as
part of `Node.id()` for element nodes; an empty role would produce
ambiguous ids.

#### Scenario: ElementSeed exposes its three fields
- **WHEN** an `ElementSeed` is constructed with `role`, `inputType`, and `outputType`
- **THEN** `getRole()`, `getInputType()`, and `getOutputType()` return those values

#### Scenario: ElementSeed is value-equal
- **WHEN** two `ElementSeed` instances are constructed with equal field values
- **THEN** they are `equal` and have equal `hashCode`s

#### Scenario: ElementSeed default role for single-scope containers is "element"
- **WHEN** a container `Bridge` strategy ships for a single-element-scope container (Optional, List, Set, …)
- **THEN** its emitted `ElementSeed`s SHALL carry `role = "element"`

### Requirement: Weights.CONTAINER constant

The processor SHALL extend `io.github.joke.percolate.processor.spi.Weights`
with a new constant `CONTAINER` (a positive `int`) representing the
base cost of a container-shaped hop.

For v1, `Weights.CONTAINER` SHALL equal `2`, slightly heavier than
`Weights.STEP` and `Weights.METHOD` (both `1`) and cheaper than
`Weights.EXPENSIVE` (`3`). Container built-in strategies
(`OptionalWrap`, `OptionalUnwrap`, `OptionalMap`, `ListWrap`,
`ListMap`, `SetWrap`, `SetMap`) SHALL use this constant unmodified
as the `weight` field of every emitted `BridgeStep`. No per-shape
weight variation is defined for v1.

#### Scenario: Weights.CONTAINER exists and is positive
- **WHEN** the source of `Weights` is inspected
- **THEN** the class declares a public static final int `CONTAINER`
- **AND** `Weights.CONTAINER > 0`

#### Scenario: Weights.CONTAINER value
- **WHEN** `Weights.CONTAINER` is read
- **THEN** the value is `2`
