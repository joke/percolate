## MODIFIED Requirements

### Requirement: No silent sourcing — supply is directive-rooted only

Producer chains SHALL originate only from **declaration-rooted** supply — supply the developer wrote down.
There are exactly three declared origins:

1. source-path descent driven by a binding's `@Map` source path;
2. constants;
3. an **ambient binding** declared by an `@Ambient` parameter (see `ambient-parameters`).

Conversions over existing supply compose on top of these. There SHALL be no rule that invents supply for a
port no declaration feeds; such a port's Value remains unreachable by exhaustion (infinite extraction cost),
making its Operation unreachable.

An ambient binding is a declared origin precisely because `@Ambient` is written in the mapper signature. This
requirement SHALL NOT be read as licence to source a port from whatever happens to be in scope: binding a
call's arguments by matching the callee's parameter **names** against the enclosing method's parameters, or
by matching their **types** against in-scope values, is supply the developer did not write down and SHALL NOT
be introduced.

#### Scenario: Undeclared constructor parameter starves
- **WHEN** a constructor declares a port `country` and no directive declares a `country` binding
- **THEN** the port Value acquires no producers and the constructor Operation is unreachable

#### Scenario: An ambient binding is a declared origin
- **WHEN** a conversion method's `@Ambient Order order` port is fed from the enclosing scope's ambient binding
- **THEN** the supply is declaration-rooted, because `@Ambient` is declared in the mapper signature

#### Scenario: Name- or type-matched argument sourcing is not introduced
- **WHEN** a callable method declares a parameter that is neither directive-fed nor `@Ambient`
- **THEN** its port is NOT bound by matching the parameter's name or type against in-scope values

## ADDED Requirements

### Requirement: Scopes carry an ambient environment that child scopes inherit

Each `Scope` SHALL carry an **ambient environment**: a map from ambient key to the binding's `(type,
nullness)` and its `Value`. A `MethodScope` SHALL declare one entry per `@Ambient` parameter of its method. A
`ChildScope` SHALL **inherit** its parent scope's environment unchanged. The mapper-root scope SHALL declare
none.

Resolution of an `AMBIENT` port SHALL consume this environment uniformly, with no `instanceof` test on the
scope kind — mirroring how `inputDecls` is consumed today.

Because a child scope inherits its parent's environment, an ambient bound inside a container or element
transform resolves to the enclosing method's binding. The generated lambda closes over an effectively-final
parameter, so this requires no codegen support beyond rendering the argument.

#### Scenario: A method scope declares one entry per ambient parameter

- **WHEN** `OrderView map(Customer customer, @Ambient Order order)` is expanded
- **THEN** the method scope's ambient environment contains exactly one entry, under key `"order"`

#### Scenario: A child scope inherits its parent's ambient environment

- **WHEN** an element transform inside `Receipt map(Customer customer, @Ambient Order order)` reaches
  `default Line mapLine(CustomerOrder line, @Ambient Order order)`
- **THEN** the child scope resolves key `"order"` to the method scope's binding
- **AND** the generated code is `customer.getOrders().stream().map(line -> mapLine(line, order))…`

#### Scenario: Ambient resolution does not branch on scope kind

- **WHEN** an `AMBIENT` port is resolved in a method scope versus in a child (element) scope
- **THEN** the same environment-driven path is taken for both, with no `instanceof MethodScope` branch

#### Scenario: A method with no ambient parameters has an empty environment

- **WHEN** `Human map(Person p)` is expanded
- **THEN** its ambient environment is empty and any `AMBIENT` port reached from it reports an unbound key

### Requirement: A mapper method may declare any number of parameters

A mapper method SHALL be permitted to declare any number of parameters. Each parameter SHALL be an
independently named source root, and each `@Map` directive SHALL name in its source path's first segment the
parameter it descends from. No stage SHALL gate an abstract mapper method on its parameter count.

Two parameters of the **same type** SHALL be legal. Because supply is declaration-rooted and every source
path names its root, a second same-typed parameter introduces no ambiguity in source-path descent.

#### Scenario: Two parameters of different types each root their own directives

- **WHEN** a mapper declares

  ```java
  @Map(target = "customerName", source = "customer.name")
  @Map(target = "street",       source = "address.street")
  OrderView map(Customer customer, Address address);
  ```

- **THEN** `customerName` descends from the `customer` parameter root and `street` from the `address`
  parameter root
- **AND** the generated body is `new OrderView(customer.getName(), address.getStreet())`

#### Scenario: Two parameters of the same type are legal

- **WHEN** a mapper declares

  ```java
  @Map(target = "oldName", source = "before.name")
  @Map(target = "newName", source = "after.name")
  Diff compare(Person before, Person after);
  ```

- **THEN** the mapper compiles and each target descends from the parameter its source path names

#### Scenario: One sub-target draws from two parameter roots

- **WHEN** a mapper declares

  ```java
  @Map(target = "summary.customerName", source = "customer.name")
  @Map(target = "summary.street",       source = "address.street")
  OrderView map(Customer customer, Address address);
  ```

- **THEN** the `summary` sub-target is assembled from both parameter roots
- **AND** the generated body is `new OrderView(new Summary(customer.getName(), address.getStreet()))`

#### Scenario: An unknown source root is still rejected

- **WHEN** a directive's source path names a segment that matches no parameter of its method
- **THEN** the directive is diagnosed and dropped, exactly as for a single-parameter method

### Requirement: Type-matched source selection SHALL be deterministic

Both source-selection sites that choose an in-scope source by **type** rather than by name SHALL be
deterministic — never dependent on hash order or on incidental collection ordering. A second same-typed
parameter is what first makes that choice observable. The two sites are:

- materialising a matching scope input for a port with no directive-pinned source SHALL select by declaration
  order of the scope's input declarations;
- the in-scope source **types** offered to grounding-by-match SHALL be gathered in declaration order,
  declared inputs before discovered graph sources, so the bindings enumerated for a template port are
  produced in a stable order.

Where two candidates remain tied after cost pruning, the engine SHALL resolve the tie by that same
declaration order rather than leaving the selection unspecified.

#### Scenario: Two same-typed parameters select deterministically

- **WHEN** a port with no directive-pinned source matches both parameters of `Diff compare(Person before, Person after)`
- **THEN** the earlier-declared parameter is selected, and the same selection is made on every compilation

#### Scenario: Grounding enumerates bindings in a stable order

- **WHEN** a template port unifies against both parameters of `Diff compare(Person before, Person after)`
- **THEN** both bindings are over-emitted in declaration order and the extracted plan is identical across
  compilations
