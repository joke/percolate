## ADDED Requirements

### Requirement: Dot-separated source strings are parsed into chain segments
`BuildGraphStage` SHALL split the `source` string from `@Map` annotations on `"."` to produce an ordered list of property name segments. Each segment SHALL become a `SourcePropertyNode` connected by `AccessEdge`s in the symbolic graph.

#### Scenario: Single-segment source (no dots)
- **WHEN** `@Map(source = "name", target = "name")` is processed
- **THEN** the graph SHALL contain `SourceRootNode → AccessEdge → SourcePropertyNode("name") → MappingEdge → TargetPropertyNode("name")`

#### Scenario: Two-segment source chain
- **WHEN** `@Map(source = "customer.name", target = "customerName")` is processed
- **THEN** the graph SHALL contain `SourceRootNode → AccessEdge → SourcePropertyNode("customer") → AccessEdge → SourcePropertyNode("name") → MappingEdge → TargetPropertyNode("customerName")`

#### Scenario: Three-segment source chain
- **WHEN** `@Map(source = "order.customer.email", target = "email")` is processed
- **THEN** the graph SHALL contain a chain of three `SourcePropertyNode`s connected by `AccessEdge`s: `"order"` → `"customer"` → `"email"`, with the final node connected to `TargetPropertyNode("email")` via `MappingEdge`

### Requirement: Chain segments sharing a prefix reuse nodes
When multiple `@Map` directives have source chains sharing a common prefix, the shared `SourcePropertyNode` instances SHALL be reused. New `AccessEdge`s SHALL branch from the shared node to diverging segments.

#### Scenario: Fan-out from shared prefix
- **WHEN** `@Map(source = "customer.address", target = "addr")` and `@Map(source = "customer.phone", target = "phone")` are processed
- **THEN** the graph SHALL have one `SourcePropertyNode("customer")` with `AccessEdge`s to both `SourcePropertyNode("address")` and `SourcePropertyNode("phone")`

#### Scenario: Different chain lengths sharing prefix
- **WHEN** `@Map(source = "customer.name", target = "name")` and `@Map(source = "customer.address.street", target = "street")` are processed
- **THEN** `SourcePropertyNode("customer")` SHALL have `AccessEdge`s to `SourcePropertyNode("name")` and `SourcePropertyNode("address")`, and `SourcePropertyNode("address")` SHALL have an `AccessEdge` to `SourcePropertyNode("street")`

### Requirement: ResolveTransformsStage resolves chain segments sequentially
`ResolveTransformsStage` SHALL resolve each `AccessEdge` in a source chain by determining the type at the parent node, running property discovery on that type, and looking up the child segment's property name. The accessor and type from each resolved segment feed into the next segment's resolution.

#### Scenario: Resolve two-segment chain with getters
- **WHEN** source type `Order` has getter `getCustomer()` returning `Customer`, and `Customer` has getter `getName()` returning `String`
- **THEN** resolving chain `"customer.name"` SHALL discover `GetterAccessor` for `customer` on `Order`, then `GetterAccessor` for `name` on `Customer`, with final resolved type `String`

#### Scenario: Resolve chain with mixed getter and field access
- **WHEN** source type `Order` has getter `getCustomer()` returning `Customer`, and `Customer` has public field `String name`
- **THEN** resolving chain `"customer.name"` SHALL discover `GetterAccessor` for `customer` and `FieldReadAccessor` for `name`, with final resolved type `String`

#### Scenario: Intermediate type is Optional
- **WHEN** source type `Order` has getter `getCustomer()` returning `Optional<Customer>`, and `Customer` has getter `getAddress()` returning `Address`
- **THEN** resolving chain `"customer.address"` SHALL report the chain's source type as `Optional<Customer>` for the first segment, and the type transform pipeline SHALL handle the Optional unwrapping to reach `Customer` before resolving `address`

### Requirement: Chain resolution failure records context for diagnostics
When a chain segment cannot be resolved (property not found on the intermediate type), the resolution SHALL record the segment name, the segment index within the chain, the full chain string, the type that was searched, and the available property names on that type. The resolution SHALL NOT produce an error directly — `ValidateTransformsStage` SHALL produce the diagnostic.

#### Scenario: Second segment not found
- **WHEN** chain `"customer.adress.street"` is resolved and `Customer` has no property `adress` but has `address`
- **THEN** the resolution failure SHALL record segment name `"adress"`, segment index `1`, full chain `"customer.adress.street"`, searched type `Customer`, and available properties including `"address"`

#### Scenario: First segment not found
- **WHEN** chain `"custmer.name"` is resolved and source type `Order` has no property `custmer` but has `customer`
- **THEN** the resolution failure SHALL record segment name `"custmer"`, segment index `0`, full chain `"custmer.name"`, searched type `Order`, and available properties including `"customer"`
