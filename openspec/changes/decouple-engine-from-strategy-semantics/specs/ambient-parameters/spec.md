## ADDED Requirements

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

## MODIFIED Requirements

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

### Requirement: An @Ambient parameter remains an ordinary @Map source

An annotated parameter SHALL remain an ordinary source root: a binding may descend a source path from it, and a
`BY_TYPE` port in that parameter's own scope may bind it by type. Publishing a name is **additive** — it adds a
by-name access path and descendant visibility, and removes nothing.

Because a parameter is declared exactly once, both access paths SHALL materialise at that one declaration's
location; a same-scope match dedups to the identical `Value` (there being one declaration, not an invariant
maintained between two), while a `BY_NAME` match from a descendant scope materialises its own `Value` at that
same location — a `Dep` edge never crosses a scope boundary, so a value consumed by a descendant's operation
cannot be the declaring scope's own `Value`.

#### Scenario: A named parameter is still a source root
- **WHEN** a binding declares a source path whose first segment is the annotated parameter's name
- **THEN** the path is walked from that parameter exactly as from any other

#### Scenario: A named parameter is still type-matchable in its own scope
- **WHEN** a `BY_TYPE` port in the declaring method's scope matches the annotated parameter's type
- **THEN** it binds that parameter

#### Scenario: Both access paths resolve to the declaration's one location
- **WHEN** the parameter is reached once by name from a child scope and once by type in its own scope
- **THEN** each materialises a `Value` at that declaration's location, in its own requesting scope

#### Scenario: A named parameter is not type-matchable from a descendant scope
- **WHEN** a `BY_TYPE` port in a child (element) scope matches the type of an enclosing method's named parameter
- **THEN** it does not bind it; inherited visibility widens by-name resolution only
