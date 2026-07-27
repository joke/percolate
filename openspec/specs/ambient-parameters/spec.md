# Ambient Parameters Spec

## Purpose

This spec defines `@Ambient`, the parameter annotation that threads a value through a mapper's plan by name
rather than by structural descent: a `DirectiveReader` publishes each annotated parameter as a **named scope
input with inherited visibility**, and a candidate method's annotated parameter is requested through a
`BY_NAME`/`REQUIRE` port the engine resolves against the requesting scope and then its nearest ancestor. The
engine knows only that mechanism and names this feature nowhere. It covers the annotation itself, the reader
that owns its meaning, name-based resolution with type verification, duplicate-name and unresolvable-name
reporting, how `MethodCallBridge` declares by-name ports, and that an `@Ambient` parameter remains an ordinary
`@Map` source root.

## Requirements

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

### Requirement: @Ambient's meaning is owned by a directive reader

`@Ambient` SHALL be interpreted by a `DirectiveReader` shipped beside the built-in strategies, which publishes
each annotated mapper-method parameter as a **named scope input with inherited visibility**. No class in the
`processor` module SHALL read `@Ambient`.

The engine SHALL know only the mechanism — a scope input that carries a name and is visible to descendant
scopes — and SHALL use no term naming this feature in any type, field, method, parameter, or enum constant.

#### Scenario: The reader publishes the parameter as a named inherited input
- **WHEN** the reader processes `OrderView map(Customer customer, @Ambient Order order)`
- **THEN** it publishes the `order` parameter as a scope input named `"order"` with inherited visibility

#### Scenario: The processor module reads no @Ambient
- **WHEN** the `processor` module's sources are inspected
- **THEN** no class imports `@Ambient` or resolves its key

#### Scenario: The engine's vocabulary names no feature
- **WHEN** the engine's port and scope types are inspected
- **THEN** no type, field, method, parameter, or enum constant is named after this feature

### Requirement: An @Ambient parameter is published for its own subtree and requested by name

An `@Ambient` parameter SHALL play both roles, determined by position rather than by a second annotation:

- **Published** — the reader publishes the parameter as a named, inherited scope input of the method's own
  scope, whether or not an enclosing scope declares the same name.
- **Requested** — a candidate method's annotated parameter is declared as a `BY_NAME`/`REQUIRE` port, which the
  engine resolves against the requesting scope's own declarations and then the nearest ancestor's inherited
  declarations.

A top-level mapper method has no enclosing scope, so its `@Ambient` parameters are supplied by the caller and
are pure providers. A method reached from within another method's plan is fed by name from the nearest
enclosing declaration, and a generated method republishes its own parameters for its own subtree. There SHALL be
no provider/consumer annotation split and no scope-kind branch in either behaviour.

Because resolution walks to the **nearest** declaration of the name, a nested `@Ambient` parameter can never
shadow an inherited declaration with a value of a different name; a same-name collision within one signature is
instead governed by "Duplicate ambient keys SHALL be rejected".

#### Scenario: A top-level ambient parameter is a pure provider

- **WHEN** `OrderView map(Customer customer, @Ambient Order order)` is generated
- **THEN** `order` is supplied by the caller and published as a named inherited input of that method's scope
  under the name `"order"`

#### Scenario: A nested ambient parameter is fed from the enclosing declaration

- **WHEN** `map(Customer, @Ambient Order order)` reaches
  `default Address mapAddress(CustomerAddress a, @Ambient Order order)`
- **THEN** `mapAddress`'s `order` port resolves by name to the enclosing scope's `"order"` declaration
- **AND** the generated call is `mapAddress(customer.getAddress(), order)`

#### Scenario: A generated method republishes its own parameters for its subtree

- **WHEN** an abstract mapper method that declares `@Ambient Order order` is itself generated
- **THEN** `order` is resolvable by name from every `BY_NAME` port within that method's own plan

### Requirement: A candidate's named parameter is requested through a BY_NAME port

A strategy that offers a production calling a method with a named-binding parameter SHALL declare that
parameter's port with the `BY_NAME` selector and on-miss `REQUIRE`, carrying the binding name. The strategy
SHALL read the annotation itself — it is SPI-side — and SHALL NOT consult the scope's named inputs; the engine
resolves the name.

#### Scenario: MethodCallBridge declares a by-name port
- **WHEN** `MethodCallBridge` offers a call to a candidate whose parameter is annotated
- **THEN** it emits a `BY_NAME`/`REQUIRE` port carrying that parameter's binding name, and resolves nothing itself

#### Scenario: The strategy reads the annotation directly
- **WHEN** `MethodCallBridge` determines a candidate parameter's binding name
- **THEN** it reads the annotation itself rather than asking the type-query seam

### Requirement: Ambient bindings SHALL be keyed by name with the type verified

A named scope input SHALL be resolved by **name**, and the resolved binding's type SHALL be verified as
assignable to the requesting port's declared type. The name SHALL NOT encode the type, and the type SHALL NOT
participate in selecting which binding is resolved.

Resolution SHALL search the requesting scope's own declarations first, then the nearest ancestor scope
declaring the name with inherited visibility. A name that resolves to nothing, and a name that resolves to a
binding whose type does not verify, SHALL both refuse the candidate with an engine-worded reason (see
`graph-expansion`).

#### Scenario: A binding is selected by name, not type
- **WHEN** two named inputs of the same type are published under different names
- **THEN** a `BY_NAME` port resolves the one matching its binding name

#### Scenario: A type mismatch refuses the candidate
- **WHEN** a `BY_NAME` port declares a type to which the resolved binding's type is not assignable
- **THEN** the candidate is refused with a message naming both types

#### Scenario: Resolution walks to the nearest ancestor
- **WHEN** a child scope requests a name its own declarations do not publish
- **THEN** the nearest ancestor scope declaring that name with inherited visibility supplies the binding

#### Scenario: A matching key with a compatible type binds

- **WHEN** `map(Customer c, @Ambient Person simon)` reaches a method declaring `@Ambient Person simon`
- **THEN** the port is bound from the `"simon"` binding

#### Scenario: A renamed consumer parameter binds through an explicit key

- **WHEN** `map(Customer c, @Ambient Person simon)` reaches a method declaring `@Ambient("simon") Person p`
- **THEN** the port is bound from the `"simon"` binding and the generated argument is `simon`

#### Scenario: A same-name type mismatch is a refusal, not a non-match

- **WHEN** `map(Customer c, @Ambient Person simon)` reaches
  `default Address mapAddress(CustomerAddress a, @Ambient Customer simon)`
- **THEN** the candidate is refused with a reason naming the binding name `simon`, the resolved type `Person`,
  and the requested type `Customer`, positioned at the spec's call target
- **AND** the refusal is rendered by the realisation renderer rather than being reported during expansion
- **AND** `mapAddress` is NOT silently treated as inapplicable

#### Scenario: Two same-typed ambients are distinguished by key

- **WHEN** a mapper declares `Diff map(@Ambient Person simon, @Ambient Person alice)`
- **THEN** both bindings are published, under keys `"simon"` and `"alice"` respectively, and neither shadows
  the other

### Requirement: Duplicate ambient keys SHALL be rejected

Two scope inputs of one method published under the same name SHALL be reported as an error naming that name and
the method, positioned at each occurrence after the first. This is the **engine's own** rule about its own scope
inputs, not a rule about `@Ambient`: a name is how a `BY_NAME` port selects, so two inputs sharing one make that
selection ambiguous, and the message SHALL name no annotation. It SHALL hold whether the collision arises from
an explicit `value()` colliding with another parameter's own simple name, from two explicit `value()`s that are
equal, or from any other `DirectiveReader` publishing a colliding name.

#### Scenario: Two explicit keys collide

- **WHEN** a mapper declares `Diff map(@Ambient("ctx") Person a, @Ambient("ctx") Order b)`
- **THEN** a duplicate-scope-input error naming `ctx` is reported, positioned at the second parameter

#### Scenario: An explicit key collides with another parameter's name

- **WHEN** a mapper declares `Diff map(@Ambient Person order, @Ambient("order") Order o)`
- **THEN** a duplicate-scope-input error naming `order` is reported

#### Scenario: The message names no annotation

- **WHEN** the reported message is inspected
- **THEN** it speaks of a duplicate scope input, so the same check serves any reader that publishes a name

### Requirement: An unmatched ambient port SHALL fail loudly

A `BY_NAME` port with on-miss `REQUIRE` that cannot be sourced SHALL refuse the candidate and record a reason,
rather than declining silently as a `BY_TYPE`/`DECLINE` port does. The reason SHALL be recorded by the engine at
the moment of the refusal, in port vocabulary, and SHALL be rendered by the realisation renderer.

No stage SHALL independently re-derive which candidates a strategy considered in order to produce this
message. The engine SHALL report only about specifications a strategy actually offered, so a candidate no
strategy offers can never produce a diagnostic.

#### Scenario: An unresolvable name is reported
- **WHEN** a candidate's `BY_NAME`/`REQUIRE` port names a binding no enclosing scope publishes
- **THEN** a diagnostic naming the port and the binding name is reported

#### Scenario: A candidate no strategy offered produces no diagnostic
- **WHEN** a callable method carrying a named parameter is excluded by every strategy before it is offered
- **THEN** no diagnostic mentions it, because nothing re-walks the candidate index

#### Scenario: No stage re-derives the candidate set
- **WHEN** the validation stages are inspected
- **THEN** none queries the callable-method index to reconstruct what a strategy would have considered

#### Scenario: An unresolvable name is not silently answered by a different producer

- **WHEN** a demanded type is producible both by a conversion method with an unresolvable `BY_NAME` port and by
  a costlier alternative producer
- **THEN** the refusal is recorded and rendered rather than the costlier alternative being selected silently

### Requirement: MethodCallBridge SHALL emit by-name ports beside its single mapped port

`MethodCallBridge` SHALL treat a candidate method's `@Ambient` parameters as by-name ports and its single
non-annotated parameter as the mapped port. The emitted `OperationSpec` SHALL carry one port per declared
parameter, in **declaration order**: the mapped parameter's port with its existing selector and on-miss rule,
and each `@Ambient` parameter's port declared `BY_NAME`/`REQUIRE` carrying that parameter's binding name.

The strategy SHALL remain myopic: it SHALL stamp the axes and the binding name and SHALL NOT resolve the
binding, consult the scope's named inputs, or access the graph.

Rendering SHALL emit arguments **positionally in declaration order**. `renderCodegen` SHALL NOT assume a
single input.

#### Scenario: A conversion method with one ambient emits two ports

- **WHEN** `MethodCallBridge` builds a spec for
  `default Price mapPrice(Integer taxFactor, @Ambient Order order)`
- **THEN** the spec carries two ports in declaration order
- **AND** the `taxFactor` port keeps its existing selector and on-miss rule
- **AND** the `order` port is `BY_NAME`/`REQUIRE` with binding name `"order"`

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
- **THEN** it contains no named-input lookup and no graph access; it only stamps the axes and the binding name

### Requirement: An @Ambient parameter remains an ordinary @Map source

An annotated parameter SHALL remain an ordinary source root: a binding may descend a source path from it, and a
`BY_TYPE` port in that parameter's own scope may bind it by type. Publishing a name is **additive** — it adds a
by-name access path and descendant visibility, and removes nothing. There SHALL be no rule excluding such a
parameter from source resolution, because supply is declaration-rooted: nothing is a source unless a binding
names it.

Because a parameter is declared exactly once, both access paths SHALL materialise at that one declaration's
location; a same-scope match dedups to the identical `Value` (there being one declaration, not an invariant
maintained between two), while a `BY_NAME` match from a descendant scope materialises its own `Value` at that
same location — a `Dep` edge never crosses a scope boundary, so a value consumed by a descendant's operation
cannot be the declaring scope's own `Value`.

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

#### Scenario: A named parameter is still type-matchable in its own scope

- **WHEN** a `BY_TYPE` port in the declaring method's scope matches the annotated parameter's type
- **THEN** it binds that parameter

#### Scenario: Both access paths resolve to the declaration's one location

- **WHEN** the parameter is reached once by name from a child scope and once by type in its own scope
- **THEN** each materialises a `Value` at that declaration's location, in its own requesting scope

#### Scenario: A named parameter is not type-matchable from a descendant scope

- **WHEN** a `BY_TYPE` port in a child (element) scope matches the type of an enclosing method's named parameter
- **THEN** it does not bind it; inherited visibility widens by-name resolution only

### Requirement: Ambient keys depend on declared parameter names

Ambient keying SHALL rely on `VariableElement.getSimpleName()`, exactly as `@Map` source-path roots already
do. A mapper inheriting an abstract method from a **compiled** dependency built without `-parameters`
therefore sees synthesised `arg0`-style names. This SHALL produce a legible failure — an unresolvable-name
refusal or a duplicate-scope-input error naming the synthesised name — rather than silently binding the wrong
value. Repairing the
underlying `-parameters` exposure is out of scope for this capability.

#### Scenario: A synthesised parameter name yields a legible error

- **WHEN** an `@Ambient` parameter's name is only available as `arg0` because its declaring class was
  compiled without `-parameters`, and a consumer expects a meaningful key
- **THEN** the refusal names the binding name the consumer asked for
- **AND** it does NOT bind a differently-named input of a compatible type
