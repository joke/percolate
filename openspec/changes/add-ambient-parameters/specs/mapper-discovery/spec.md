## ADDED Requirements

### Requirement: Discovery SHALL NOT gate on parameter count

`DiscoverAbstractMethodsStage` SHALL discover an abstract mapper method regardless of how many parameters it
declares, including zero. Parameter count SHALL play no part in `AbstractMethodFilter`'s decision, which
turns only on the method being abstract and not inherited from `java.lang.Object`.

`@Ambient` parameters SHALL be discovered as ordinary parameters here; the ambient reading of the annotation
belongs to later stages (see `ambient-parameters`).

#### Scenario: A two-parameter abstract method is discovered

- **WHEN** the `@Mapper` interface declares `OrderView map(Customer customer, Address address);`
- **THEN** `MapperShape.abstractMethods` contains an `ExecutableElement` for `map`

#### Scenario: A method whose parameters share a type is discovered

- **WHEN** the `@Mapper` interface declares `Diff compare(Person before, Person after);`
- **THEN** `MapperShape.abstractMethods` contains an `ExecutableElement` for `compare`

#### Scenario: A method with an @Ambient parameter is discovered unchanged

- **WHEN** the `@Mapper` interface declares `OrderView map(Customer customer, @Ambient Order order);`
- **THEN** `MapperShape.abstractMethods` contains an `ExecutableElement` for `map`, with both parameters
  present

#### Scenario: An inherited multi-parameter abstract method is discovered with substitution

- **WHEN** there exists `interface BaseMapper<A, B, O> { O combine(A a, B b); }`
- **AND** the `@Mapper` is `interface OrderMapper extends BaseMapper<Customer, Address, OrderView> {}`
- **THEN** `MapperShape.abstractMethods` contains an `ExecutableElement` for `combine` whose return type is
  `OrderView` and whose parameter types are `Customer` and `Address`
