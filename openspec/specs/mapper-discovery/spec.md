# Mapper Discovery Spec

## Purpose

`DiscoverAbstractMethodsStage` is the first pipeline stage. It walks a `@Mapper`-annotated `TypeElement` (and its supertypes) and produces a `MapperShape` carrying the type plus the list of abstract methods that need to be realised. It ignores default, concrete, static, private, and `Object`-inherited methods, and substitutes generic type arguments so inherited methods are reported with concrete parameter and return types.

## Requirements

### Requirement: Abstract methods on a @Mapper type SHALL be discovered

`DiscoverAbstractMethodsStage` SHALL accept a `@Mapper`-annotated `TypeElement` and produce a `MapperShape` carrying the type and the list of abstract methods that the type declares or inherits.

#### Scenario: Locally declared abstract method is discovered

- **WHEN** the `@Mapper` interface declares `Human mapHuman(Person person);`
- **THEN** the resulting `MapperShape.abstractMethods` contains an `ExecutableElement` for `mapHuman`

#### Scenario: Multiple methods are discovered in declaration order

- **WHEN** the `@Mapper` interface declares two abstract methods `mapA` then `mapB`
- **THEN** the resulting `MapperShape.abstractMethods` contains both, with `mapA` ordered before `mapB`

### Requirement: Inherited abstract methods SHALL be included with generic substitution

Discovery SHALL include abstract methods inherited from super-interfaces and abstract super-classes. Generic type parameters SHALL be substituted with the type arguments supplied by the discovered `@Mapper`.

#### Scenario: Generic super-interface contributes a substituted abstract method

- **WHEN** there exists `interface BaseMapper<I, O> { O map(I input); }`
- **AND** the `@Mapper` is `interface PersonMapper extends BaseMapper<Person, Human> {}`
- **THEN** `MapperShape.abstractMethods` contains an `ExecutableElement` for `map` whose return type is `Human` and whose single parameter type is `Person`

#### Scenario: Abstract super-class contributes inherited abstract methods

- **WHEN** there exists `abstract class BaseMapper { abstract Human mapHuman(Person p); }`
- **AND** the `@Mapper` is `abstract class PersonMapper extends BaseMapper {}`
- **THEN** `MapperShape.abstractMethods` contains an `ExecutableElement` for `mapHuman`

### Requirement: Implemented methods SHALL NOT be discovered

Default methods, concrete super-class methods, and any abstract method that has been overridden by a non-abstract subtype member SHALL NOT appear in `MapperShape.abstractMethods`.

#### Scenario: Default method is skipped

- **WHEN** the `@Mapper` interface declares `default Human helper(Person p) { return null; }`
- **THEN** `helper` is not present in `MapperShape.abstractMethods`

#### Scenario: Concrete method on an abstract super-class is skipped

- **WHEN** an abstract `@Mapper` extends a class that provides a concrete (non-abstract) method `Person identity(Person p)`
- **THEN** `identity` is not present in `MapperShape.abstractMethods`

#### Scenario: Default method on a sub-interface implements an abstract from a parent

- **WHEN** there exists `interface A { Human map(Person p); }`
- **AND** there exists `interface B extends A { default Human map(Person p) { return new Human(); } }`
- **AND** the `@Mapper` is `interface PersonMapper extends B {}`
- **THEN** `map` is not present in `MapperShape.abstractMethods`

### Requirement: Object methods SHALL NOT be discovered

Methods inherited from `java.lang.Object` (`equals`, `hashCode`, `toString`, etc.) SHALL NOT appear in `MapperShape.abstractMethods`, even if they are abstract on the type's hierarchy.

#### Scenario: equals/hashCode/toString on an abstract class are skipped

- **WHEN** the `@Mapper` is an abstract class that re-declares `equals`, `hashCode`, or `toString` as abstract
- **THEN** none of those methods appear in `MapperShape.abstractMethods`

### Requirement: Static and private methods SHALL NOT be discovered

Static methods and private methods on the `@Mapper` type or its hierarchy SHALL NOT appear in `MapperShape.abstractMethods`.

#### Scenario: Static interface method is skipped

- **WHEN** the `@Mapper` interface declares a static factory method
- **THEN** the static method is not present in `MapperShape.abstractMethods`

#### Scenario: Private interface method is skipped

- **WHEN** the `@Mapper` interface declares a private helper method
- **THEN** the private method is not present in `MapperShape.abstractMethods`

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
