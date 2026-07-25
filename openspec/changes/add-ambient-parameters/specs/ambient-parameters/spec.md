## ADDED Requirements

### Requirement: The @Ambient annotation

The `annotations` module SHALL ship `io.github.joke.percolate.Ambient`, a `@Documented` annotation with
`@Target(PARAMETER)` and `@Retention(CLASS)`, declaring a single optional member:

```java
String value() default "";
```

An empty `value()` means the binding key is the parameter's own simple name. A non-empty `value()` overrides
the key. The annotation SHALL NOT be applicable to methods, types, or fields.

#### Scenario: The annotation targets parameters only

- **WHEN** `@Ambient` is applied to a method rather than a parameter
- **THEN** the compilation fails with javac's own `@Target` error, before any percolate stage runs

#### Scenario: The default key is the parameter name

- **WHEN** a mapper declares `OrderView map(Customer customer, @Ambient Order order)`
- **THEN** the ambient binding's key is `"order"`

#### Scenario: An explicit value overrides the key

- **WHEN** a mapper declares `OrderView map(Customer customer, @Ambient("ctx") Order order)`
- **THEN** the ambient binding's key is `"ctx"` and no binding is published under `"order"`

### Requirement: An @Ambient parameter is bound from the enclosing environment and republished

An `@Ambient` parameter SHALL play both roles, determined by position rather than by a second annotation:

- **Bound** — when the enclosing scope's ambient environment offers a binding under the parameter's key, the
  parameter's port SHALL be fed from that binding.
- **Published** — the parameter SHALL be published into the ambient environment of its own scope under its
  key, whether or not it was bound from an enclosing environment.

A top-level mapper method has no enclosing environment, so its `@Ambient` parameters are supplied by the
caller and are pure providers. A method reached from within another method's plan binds from that method's
environment and republishes the same value for its own subtree. There SHALL be no provider/consumer
annotation split and no scope-kind branch in either behaviour.

Because a consumer republishes the **same** value under the **same** key, a nested `@Ambient` parameter can
never shadow an inherited binding of a different value; a same-key collision within one signature is instead
governed by "Duplicate ambient keys SHALL be rejected".

#### Scenario: A top-level ambient parameter is a pure provider

- **WHEN** `OrderView map(Customer customer, @Ambient Order order)` is generated
- **THEN** `order` is supplied by the caller and published into the method scope's ambient environment under
  key `"order"`

#### Scenario: A nested ambient parameter binds from the enclosing environment

- **WHEN** `map(Customer, @Ambient Order order)` reaches
  `default Address mapAddress(CustomerAddress a, @Ambient Order order)`
- **THEN** `mapAddress`'s `order` port is fed from the enclosing environment's `"order"` binding
- **AND** the generated call is `mapAddress(customer.getAddress(), order)`

#### Scenario: A bound ambient parameter is republished for its own subtree

- **WHEN** an abstract mapper method that declares `@Ambient Order order` is itself generated
- **THEN** `order` is available under key `"order"` to every ambient port within that method's own plan

### Requirement: Ambient bindings SHALL be keyed by name with the type verified

An ambient binding SHALL be identified by its **key** alone. The declared type SHALL be **verified** against
the binding's type, and SHALL NOT form part of the key.

When an ambient port's key resolves to a binding whose type is not assignable to the port's declared type,
the processor SHALL report an error naming the key, the declared type at the binding site, and the declared
type at the consuming site. It SHALL NOT treat the mismatch as a non-match.

A composite key encoding both name and type SHALL NOT be used, because it converts this type error into a
silent non-match.

#### Scenario: A matching key with a compatible type binds

- **WHEN** `map(Customer c, @Ambient Person simon)` reaches a method declaring `@Ambient Person simon`
- **THEN** the port is bound from the `"simon"` binding

#### Scenario: A renamed consumer parameter binds through an explicit key

- **WHEN** `map(Customer c, @Ambient Person simon)` reaches a method declaring `@Ambient("simon") Person p`
- **THEN** the port is bound from the `"simon"` binding and the generated argument is `simon`

#### Scenario: A same-key type mismatch is an error, not a non-match

- **WHEN** `map(Customer c, @Ambient Person simon)` reaches
  `default Address mapAddress(CustomerAddress a, @Ambient Customer simon)`
- **THEN** the processor reports an error naming the key `simon`, the bound type `Person`, and the consuming
  type `Customer`
- **AND** the error is positioned at the consuming `@Ambient` annotation
- **AND** `mapAddress` is NOT silently treated as inapplicable

#### Scenario: Two same-typed ambients are distinguished by key

- **WHEN** a mapper declares `Diff map(@Ambient Person simon, @Ambient Person alice)`
- **THEN** both bindings are published, under keys `"simon"` and `"alice"` respectively, and neither shadows
  the other

### Requirement: Duplicate ambient keys SHALL be rejected

Two `@Ambient` parameters of one method that resolve to the same key SHALL be reported as an error naming the
key, positioned at the second occurrence. This SHALL hold whether the collision arises from two identical
parameter names (impossible in Java), from an explicit `value()` colliding with another parameter's name, or
from two explicit `value()`s that are equal.

#### Scenario: Two explicit keys collide

- **WHEN** a mapper declares `Diff map(@Ambient("ctx") Person a, @Ambient("ctx") Order b)`
- **THEN** the processor reports a duplicate-ambient-key error naming `ctx`, positioned at the second
  `@Ambient`

#### Scenario: An explicit key collides with another parameter's name

- **WHEN** a mapper declares `Diff map(@Ambient Person order, @Ambient("order") Order o)`
- **THEN** the processor reports a duplicate-ambient-key error naming `order`

### Requirement: An unmatched ambient port SHALL fail loudly

An `AMBIENT` port whose key resolves to no binding SHALL be reported as an error naming the unbound key and
the method that declares it, rather than resolved by any fallback.

The port SHALL NOT behave as `Port.Sourcing.REUSE` does — it SHALL NOT decline so that the Operation merely
does not apply. A quiet non-application would silently deselect a conversion method and yield a different
plan, or surface as an unrelated "no plan" diagnostic positioned away from the mistake.

#### Scenario: An unbound ambient key is an error

- **WHEN** `default Price mapPrice(Integer taxFactor, @Ambient Order order)` is reachable from a mapper
  method that declares no ambient under key `"order"`
- **THEN** the processor reports an error naming the unbound key `order`
- **AND** it does NOT report a generic unrealisable-target diagnostic instead

#### Scenario: An unbound ambient does not silently select a different producer

- **WHEN** a demanded type is producible both by a conversion method with an unbound ambient port and by a
  costlier alternative producer
- **THEN** the unbound ambient is reported as an error rather than the costlier alternative being selected
  silently

### Requirement: MethodCallBridge SHALL emit ambient ports beside its single mapped port

`MethodCallBridge` SHALL treat a candidate method's `@Ambient` parameters as ambient ports and its single
non-ambient parameter as the mapped port. The emitted `OperationSpec` SHALL carry one port per declared
parameter, in **declaration order**: the mapped parameter's port with its existing sourcing, and each
`@Ambient` parameter's port with sourcing `AMBIENT` and the parameter's key.

The strategy SHALL remain myopic: it SHALL stamp the mode and key and SHALL NOT resolve the binding, consult
the ambient environment, or access the graph.

Rendering SHALL emit arguments **positionally in declaration order**. `renderCodegen` SHALL NOT assume a
single input.

#### Scenario: A conversion method with one ambient emits two ports

- **WHEN** `MethodCallBridge` builds a spec for
  `default Price mapPrice(Integer taxFactor, @Ambient Order order)`
- **THEN** the spec carries two ports in declaration order
- **AND** the `taxFactor` port keeps its existing sourcing mode
- **AND** the `order` port has sourcing `AMBIENT` and key `"order"`

#### Scenario: Ambient arguments render in declaration order

- **WHEN** the above operation renders
- **THEN** the emitted call is `mapPrice(<taxFactor expression>, order)` with the arguments in declaration
  order
- **AND** the rendering does not call `inputs.single()`

#### Scenario: An ambient-first signature still renders in declaration order

- **WHEN** a candidate declares its ambient parameter first, e.g.
  `default Price mapPrice(@Ambient Order order, Integer taxFactor)`
- **THEN** the emitted call is `mapPrice(order, <taxFactor expression>)`

#### Scenario: The bridge does not resolve the binding itself

- **WHEN** the source of `MethodCallBridge` is reviewed
- **THEN** it contains no ambient-environment lookup and no graph access; it only stamps the mode and key

### Requirement: An @Ambient parameter remains an ordinary @Map source

An `@Ambient` parameter SHALL remain usable as the root segment of a `@Map` source path, exactly as any other
parameter. There SHALL be no rule excluding ambient parameters from source resolution, because supply is
declaration-rooted: nothing is a source unless a `@Map` names it.

#### Scenario: An ambient parameter roots a source path

- **WHEN** a mapper declares

  ```java
  @Map(target = "address",  source = "customer.address")
  @Map(target = "orderRef", source = "order.id")
  OrderView map(Customer customer, @Ambient Order order);
  ```

- **THEN** `orderRef` is produced by descending `order.getId()`
- **AND** `order` is simultaneously published as an ambient binding under key `"order"`

#### Scenario: ValidateSourceParametersStage accepts an ambient parameter as a source root

- **WHEN** a `@Map` source's first segment names an `@Ambient` parameter
- **THEN** the directive is accepted, with no ambient-specific exclusion

### Requirement: Ambient keys depend on declared parameter names

Ambient keying SHALL rely on `VariableElement.getSimpleName()`, exactly as `@Map` source-path roots already
do. A mapper inheriting an abstract method from a **compiled** dependency built without `-parameters`
therefore sees synthesised `arg0`-style names. This SHALL produce a legible failure — an unbound-key or
duplicate-key error naming the synthesised name — rather than silently binding the wrong value. Repairing the
underlying `-parameters` exposure is out of scope for this capability.

#### Scenario: A synthesised parameter name yields a legible error

- **WHEN** an `@Ambient` parameter's name is only available as `arg0` because its declaring class was
  compiled without `-parameters`, and a consumer expects a meaningful key
- **THEN** the processor reports an unbound-ambient-key error naming the key the consumer asked for
- **AND** it does NOT bind a differently-named ambient of a compatible type
