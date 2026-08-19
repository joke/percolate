## ADDED Requirements

### Requirement: Setter assembly is a single n-ary operation

A setter-assembled target SHALL be produced by exactly **one** `OperationSpec` carrying one `Port.subTarget` per declared child. The strategy SHALL NOT decompose the assembly into separate operations for the constructor call, the individual setter calls, and the result.

The reason is structural, not stylistic. Totality is enforced only by sub-target ports on a single operation. An unsatisfied port makes the plan partial, and `Cost` is the lexicographic vector `(partials, weight)` in which partials dominate absolutely. If each setter were its own operation, omitting one would not be a partial. It would be a shorter and cheaper plan, so the minimum-cost fold would drop a declared mapping in silence. This is the same rule the `builder-assembly` capability states.

#### Scenario: Every declared child becomes a sub-target port of one operation
- **WHEN** `SetterAssembly` matches a demand for `Person` whose declared children are `{name, age}`
- **THEN** it emits exactly one `OperationSpec` whose output type is `Person`
- **AND** that spec carries exactly two ports, `Port.subTarget("name", …)` and `Port.subTarget("age", …)`

#### Scenario: A declared child cannot be optimised out of the plan
- **WHEN** the plan for a setter-assembled `Person` with declared children `{name, age}` is extracted
- **AND** no producer exists for `age`
- **THEN** the operation is partial rather than a cheaper complete plan that omits the `age` setter

#### Scenario: No per-setter operation is emitted
- **WHEN** the operations emitted for a setter-assembled demand are inspected
- **THEN** no `OperationSpec` represents a single setter call

### Requirement: Setter assembly gates on a no-argument constructor and containment

`SetterAssembly` SHALL offer an operation only when every one of the following holds for the demanded type: the demand declares at least one child, the type is a non-abstract class, the type declares a non-private no-argument constructor, and every declared child has a matching non-private setter.

A matching setter is a method named `set` followed by the child name in camel case, taking exactly one parameter. Its **return type SHALL NOT be part of the match**, so a `void` setter and a `this`-returning setter both match.

The gate is **containment**, not the set equality `ConstructorCall` uses. A bean normally exposes far more setters than a mapping declares, and the surplus setters SHALL be left uncalled.

Inherited setters SHALL match. The strategy reads members through `ResolveCtx.membersOf`, which reports declared and inherited members alike.

The empty-declaration bail SHALL be kept: a demand with no declared children yields no offer, so an empty declared set can never satisfy a leaf demand through a no-argument constructor that happens to exist.

#### Scenario: A declared subset of the setters still assembles
- **WHEN** `Person` exposes `setName`, `setAge` and `setNickname`, and the declared children are `{name, age}`
- **THEN** one `OperationSpec` is offered carrying only the `name` and `age` sub-target ports
- **AND** the generated code never calls `setNickname`

#### Scenario: A declared child with no setter yields no offer
- **WHEN** the declared children are `{name, missing}` and `Person` declares no `setMissing`
- **THEN** `SetterAssembly` offers nothing

#### Scenario: A missing no-argument constructor yields no offer
- **WHEN** `Person` declares only an all-arguments constructor
- **THEN** `SetterAssembly` offers nothing

#### Scenario: A private no-argument constructor yields no offer
- **WHEN** `Person` declares a `private Person()` and nothing else
- **THEN** `SetterAssembly` offers nothing

#### Scenario: An abstract target yields no offer
- **WHEN** the demanded type is an abstract class or an interface
- **THEN** `SetterAssembly` offers nothing

#### Scenario: An empty declaration never assembles
- **WHEN** the demand declares no children
- **THEN** `SetterAssembly` offers nothing

#### Scenario: An inherited setter matches
- **WHEN** `Employee` extends `Person`, declares `setSalary`, and inherits `setName`
- **AND** the declared children are `{name, salary}`
- **THEN** one `OperationSpec` is offered carrying both sub-target ports

#### Scenario: A this-returning setter matches
- **WHEN** `Person.setName(String)` returns `Person` rather than `void`
- **THEN** it matches the declared child `name`
- **AND** the generated helper discards its result

### Requirement: Setter assembly renders one call to a generated helper method

The operation's `OperationCodegen` SHALL render exactly one method call. The setter statements SHALL live in a class member the operation requests through `MemberRequest.method`, so the operation still renders a single expression and the SPI gains no composable statement shape.

The requested method SHALL declare the target type as its return type, one parameter per declared child in declared order typed by that child's setter parameter, and a body that constructs the target through its no-argument constructor, calls each setter in declared order, and returns the constructed value. The body SHALL allocate its local name through a `NameAllocator`, because a bean may declare a property named after that local.

Each port's type SHALL be the matching setter's parameter type, and each port's nullness SHALL resolve through the demand's nullness oracle, exactly as the other assembly forms do.

#### Scenario: The operation renders a single call
- **WHEN** the codegen of a setter-assembly operation for `Person` renders
- **THEN** it emits one call to the requested member, passing one argument per declared child in declared order

#### Scenario: The requested member carries the setter sequence
- **WHEN** the member `SetterAssembly` requests for `Person` with declared children `{name, age}` is inspected
- **THEN** its return type is `Person`
- **AND** its parameters are typed by `setName` and `setAge` in that order
- **AND** its body constructs a `Person`, calls `setName` then `setAge`, and returns the constructed value

#### Scenario: The local name never collides with a property
- **WHEN** `Person` declares a property named after the helper's local
- **THEN** the generated helper body names its local differently and still compiles

#### Scenario: Port types come from the setters
- **WHEN** `setAge(int)` matches the declared child `age`
- **THEN** the `age` sub-target port carries type `int`

### Requirement: The requested helper member's dedup identity carries the assembly form

The member request's dedup key SHALL be derived from the assembly form, the target type, and the ordered declared child names. Two demands for the same target with the same declared children in the same order SHALL therefore share one generated helper, and two demands that differ in any of the three SHALL receive distinct helpers.

The parameter types SHALL NOT contribute to the key, because they follow from the target and the children.

The form SHALL be part of the key from the outset, so a later assembly form that produces the same target from the same children can request a distinct member without renaming this one.

#### Scenario: Two methods assembling the same bean share one helper
- **WHEN** two mapper methods both assemble `Person` from declared children `{name, age}`
- **THEN** the generated type declares exactly one helper method
- **AND** both method bodies call it

#### Scenario: A different child set yields a distinct helper
- **WHEN** one method assembles `Person` from `{name, age}` and another from `{name}`
- **THEN** the generated type declares two distinct helper methods

#### Scenario: The form is part of the key
- **WHEN** the dedup key of a setter-assembly member request is inspected
- **THEN** it distinguishes the setter form from any other assembly form for the same target and children

### Requirement: The engine gains no knowledge of setters

The expansion engine, the cost fold, plan extraction, and the code-generation stage SHALL remain free of setter vocabulary. A setter-assembly operation SHALL be indistinguishable from a constructor operation at the operation boundary. No engine class SHALL reference a setter, a bean convention, or the `percolate.construction.preference` option.

#### Scenario: The engine treats a setter operation like any other assembly
- **WHEN** the driver lands the `OperationSpec` of a setter assembly
- **THEN** it applies the same port binding, cost fold, and plan extraction it applies to a constructor operation

#### Scenario: No engine class names the setter form
- **WHEN** the processor's engine sources are inspected
- **THEN** none references `SetterAssembly`, a `set` prefix convention, or the construction preference option

### Requirement: Setter assembly ships as one built-in strategy without a convention hierarchy

`SetterAssembly` SHALL ship in `percolate-strategies-builtin` as a `final` class implementing `ExpansionStrategy` directly and registered through the ordinary service loader. It SHALL NOT introduce an abstract convention base, because it ships exactly one convention.

The JavaBean `setX` naming SHALL be the only supported convention. No other mutator naming is recognised.

#### Scenario: The strategy is a plain final built-in
- **WHEN** `SetterAssembly` is inspected
- **THEN** it is a `final` class implementing `ExpansionStrategy` and declares no abstract convention hook

#### Scenario: Only the setX convention is recognised
- **WHEN** `Person` exposes a mutator named `name(String)` rather than `setName(String)`
- **THEN** the declared child `name` finds no matching setter and the strategy offers nothing

### Requirement: Setter assembly introduces no setter-specific SPI type

Setter assembly SHALL be expressible with the existing SPI surface plus the method member request. No SPI type named for setters, beans, or mutators SHALL be added.

#### Scenario: No setter-named SPI type exists
- **WHEN** `percolate-spi` is inspected after this change
- **THEN** it declares no type whose name refers to setters, beans, or mutators
