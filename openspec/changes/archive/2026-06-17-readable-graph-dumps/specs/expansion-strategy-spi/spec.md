## MODIFIED Requirements

### Requirement: OperationSpec result type

A strategy match SHALL produce an `OperationSpec`: a **required, human-readable `label`** describing
the production, the operation's codegen, its weight, its ordered port signature (per port: name,
declared `TypeMirror`, declared `Nullability`), the produced output type and nullness, and optionally
a child-scope declaration (container element mapping: element-in and element-out types). The `label`
SHALL be a fully-typed description the strategy composes from its match (e.g. `int→long`,
`new Address(int, String)`, `getStreet()`, `"ACTIVE"`, `map`); conversions SHALL use the glyph arrow
`→`. The spec is plain data; the driver turns it into one atomic `AddOperation` delta. Strategies
receive no graph access and stay myopic.

#### Scenario: Spec is plain data
- **WHEN** a strategy returns an `OperationSpec`
- **THEN** it contains a label, codegen, weight, ports, output typing, and optional child-scope
  declaration, and exposes no graph or engine surface

#### Scenario: Label is a typed production description
- **WHEN** `WidenPrimitive` produces an `int`-to-`long` widening spec
- **THEN** the spec's `label` is `int→long` (using the glyph arrow), not a codegen class name

#### Scenario: Codegen render contract survives
- **WHEN** an Operation's codegen renders
- **THEN** it implements `render(IncomingValues)` with incoming values keyed by port name

## ADDED Requirements

### Requirement: Codegen surface omits VarNames and LoopContainerCodegen

The `io.github.joke.percolate.spi` codegen surface SHALL NOT declare a `VarNames` type or a
`LoopContainerCodegen` type. `OperationCodegen.render` SHALL take only `IncomingValues`; there SHALL
be no `VarNames` parameter threaded through the render contract. The `Codegen` marker and its
`OperationCodegen` (scalar `render`) / `ScopeCodegen` (`weave`) split are retained.

#### Scenario: VarNames does not exist in the SPI
- **WHEN** the `io.github.joke.percolate.spi` package is inspected
- **THEN** no `VarNames` type exists, and no `render` signature references it

#### Scenario: LoopContainerCodegen does not exist
- **WHEN** the `io.github.joke.percolate.spi` package is inspected
- **THEN** no `LoopContainerCodegen` type exists
