# Source Path Resolution Spec

## Purpose

This spec defines source-path-segment resolution: the `ExpansionStrategy` implementations (the `Getter` / `Method` / `Field` path resolvers) that turn one segment of a `@Map` source path (like `person.address.street`) into a typed accessor, plus the parameter-root base case that grounds a single-segment source against its own scope's parameters. Each resolver reads the segment to resolve from the demand context and emits an accessor `Operation` describing the typed access; the engine reaches the produced source `Value` through the ordinary demand work-list, with no dedicated path-resolution SPI or seed-time bridge round-trip.

## Requirements

### Requirement: GetterPathResolver built-in

The `percolate-strategies-builtin` module SHALL ship `io.github.joke.percolate.spi.builtins.GetterPathResolver` implementing `ExpansionStrategy` and annotated `@AutoService(ExpansionStrategy.class)`.

`GetterPathResolver.expand(demand, ctx)` SHALL read the single segment to resolve from `demand.directive()`'s source path (see "Path resolvers emit accessor Operations per segment") and, for the directive-pinned parent type (`demand.targetType()`, the parent the accessor surface pins), SHALL:

1. Produce no spec when the parent type's `getKind() != TypeKind.DECLARED` or its element is not a `TypeElement`.
2. Probe for a JavaBean accessor method on the parent type, in this order:
   - `get<Segment>()` where `<Segment>` is the segment with its first character upper-cased (zero parameters, non-Object-class).
   - `is<Segment>()` where `<Segment>` is as above (zero parameters, return type is `boolean` / `java.lang.Boolean`, non-Object-class).
3. If neither matches, produce no spec.
4. On match, emit a single one-port `OperationSpec` (`OperationSpec.of(...)`): label `<methodName>()`, one `Port` named `"value"` typed to the parent type (non-null), weight `Weights.STEP_GETTER`, output type the accessor's return type with nullness from the demand oracle (`demand.nullnessOf`), and codegen rendering `<parent>.<methodName>()`.

`GetterPathResolver` SHALL ignore methods declared on `java.lang.Object`.

#### Scenario: GetterPathResolver matches a JavaBean getter
- **WHEN** `GetterPathResolver` resolves the segment `lastName` against a `Person` candidate that has `String getLastName()`
- **THEN** it emits a single one-port `OperationSpec`
- **AND** the spec's output type is `String`
- **AND** the codegen renders `<parent>.getLastName()`
- **AND** the spec's `weight` equals `Weights.STEP_GETTER`
- **AND** the port is named `"value"` and typed to `Person`

#### Scenario: GetterPathResolver matches an `is` accessor for boolean
- **WHEN** `GetterPathResolver` resolves the segment `active` against a `Person` candidate that has `boolean isActive()`
- **THEN** it emits a single one-port `OperationSpec`
- **AND** the codegen renders `<parent>.isActive()`
- **AND** the spec's `weight` equals `Weights.STEP_GETTER`
- **AND** the spec's label is `isActive()`

#### Scenario: GetterPathResolver rejects parameterized overloads
- **WHEN** `GetterPathResolver` resolves the segment `name` against a `Person` candidate that has both `String getName(String suffix)` and no zero-arg `getName()`
- **THEN** it emits no spec

#### Scenario: GetterPathResolver ignores Object methods
- **WHEN** `GetterPathResolver` resolves the segment `class` against an `Object` candidate
- **THEN** it emits no spec even though `Object.getClass()` exists

#### Scenario: GetterPathResolver returns empty for non-declared parents
- **WHEN** `GetterPathResolver` resolves the segment `length` against an `int[]` candidate (array, not declared)
- **THEN** it emits no spec

### Requirement: MethodPathResolver built-in

The `percolate-strategies-builtin` module SHALL ship `io.github.joke.percolate.spi.builtins.MethodPathResolver` implementing `ExpansionStrategy` and annotated `@AutoService(ExpansionStrategy.class)`.

`MethodPathResolver.expand(demand, ctx)` SHALL read the single segment to resolve from `demand.directive()`'s source path and, for the directive-pinned parent type (`demand.targetType()`, the parent the accessor surface pins), SHALL:

1. Produce no spec when the parent type's `getKind() != TypeKind.DECLARED` or its element is not a `TypeElement`.
2. Probe for a zero-arg method named exactly the segment on the parent type, ignoring methods declared on `java.lang.Object`.
3. If not matched, produce no spec.
4. On match, emit a single one-port `OperationSpec` (`OperationSpec.of(...)`): label `<segment>()`, one `Port` named `"value"` typed to the parent type (non-null), weight `Weights.STEP_METHOD`, output type the method's return type with nullness from the demand oracle, and codegen rendering `<parent>.<segment>()`.

`MethodPathResolver` SHALL apply uniformly to any `DECLARED` parent type — records, plain classes, interfaces, abstract classes. It SHALL NOT gate on `ElementKind.RECORD`. Records continue to work because their canonical accessors fit the `no-arg method whose simple name equals segment` predicate.

#### Scenario: MethodPathResolver matches a canonical record accessor
- **WHEN** `MethodPathResolver` resolves the segment `x` against a `PointRecord` candidate that is a `record PointRecord(int x, int y)`
- **THEN** it emits a single one-port `OperationSpec`
- **AND** the spec's output type is `int`
- **AND** the codegen renders `<parent>.x()`
- **AND** the spec's `weight` equals `Weights.STEP_METHOD`
- **AND** the spec's label is `x()`

#### Scenario: MethodPathResolver matches a fluent-style accessor on a non-record class
- **WHEN** `MethodPathResolver` resolves the segment `street` against an `Address` candidate that is a plain class with `String street() { return street; }`
- **THEN** it emits a single one-port `OperationSpec`
- **AND** the spec's output type is `String`
- **AND** the codegen renders `<parent>.street()`

#### Scenario: MethodPathResolver rejects parameterised methods
- **WHEN** `MethodPathResolver` resolves the segment `name` against a `Person` candidate that has only `String name(String suffix)` and no zero-arg overload
- **THEN** it emits no spec

#### Scenario: MethodPathResolver ignores Object methods
- **WHEN** `MethodPathResolver` resolves the segment `toString` against an `Object` candidate
- **THEN** it emits no spec even though `Object.toString()` exists

#### Scenario: MethodPathResolver returns empty for non-declared parents
- **WHEN** `MethodPathResolver` resolves the segment `length` against an `int[]` candidate (array, not declared)
- **THEN** it emits no spec

### Requirement: FieldPathResolver built-in

The `percolate-strategies-builtin` module SHALL ship `io.github.joke.percolate.spi.builtins.FieldPathResolver` implementing `ExpansionStrategy` and annotated `@AutoService(ExpansionStrategy.class)`.

`FieldPathResolver.expand(demand, ctx)` SHALL read the single segment to resolve from `demand.directive()`'s source path and, for the directive-pinned parent type (`demand.targetType()`, the parent the accessor surface pins), SHALL:

1. Produce no spec when the parent type's `getKind() != TypeKind.DECLARED` or its element is not a `TypeElement`.
2. Probe for a `VariableElement` on the parent type whose `simpleName` equals the segment, whose `ElementKind` is `FIELD`, and whose modifiers contain neither `PRIVATE` nor `STATIC`.
3. If not matched, produce no spec.
4. On match, emit a single one-port `OperationSpec` (`OperationSpec.of(...)`): label `.<segment>` (a leading-dot field-read label, e.g. `.value`), one `Port` named `"value"` typed to the parent type (non-null), weight `Weights.STEP_FIELD`, output type the field's type (`field.asType()`) with nullness from the demand oracle, and codegen rendering `<parent>.<segment>` (a field read with no parentheses).

#### Scenario: FieldPathResolver matches a public field
- **WHEN** `FieldPathResolver` resolves the segment `value` against a `Box` candidate that has `public String value`
- **THEN** it emits a single one-port `OperationSpec`
- **AND** the codegen renders `<parent>.value`
- **AND** the spec's `weight` equals `Weights.STEP_FIELD`
- **AND** the port is named `"value"` and typed to `Box`

#### Scenario: FieldPathResolver rejects private fields
- **WHEN** `FieldPathResolver` resolves the segment `lastName` against a `Person` candidate that has `private String lastName` (Lombok `@Value`)
- **THEN** it emits no spec

#### Scenario: FieldPathResolver rejects static fields
- **WHEN** `FieldPathResolver` resolves the segment `DEFAULT` against a `Constants` candidate that has `public static String DEFAULT`
- **THEN** it emits no spec

### Requirement: Weight constants for path-segment access

The `percolate-spi` module's `io.github.joke.percolate.spi.Weights` class SHALL declare three `public static final int` constants encoding the relative cost of the three built-in path-segment access shapes:

```java
public static final int STEP_GETTER = 1;   // getX() / isX()
public static final int STEP_METHOD = 2;   // foo() — records or fluent style
public static final int STEP_FIELD  = 3;   // direct field access
```

The numeric ordering encodes precedence: lower number means "preferred when multiple resolvers match the same `(parentType, segment)` pair." The existing `Weights.STEP = 1` constant SHALL remain in the class for backwards compatibility with external resolvers and other call sites; the three built-in resolvers SHALL switch to the new constants.

#### Scenario: Weight constants declare the access-shape ordering
- **WHEN** `Weights.STEP_GETTER`, `Weights.STEP_METHOD`, and `Weights.STEP_FIELD` are inspected
- **THEN** `Weights.STEP_GETTER < Weights.STEP_METHOD < Weights.STEP_FIELD`
- **AND** all three are `public static final int`

#### Scenario: Pre-existing Weights.STEP remains for external compatibility
- **WHEN** `Weights.STEP` is referenced from any non-built-in resolver
- **THEN** the constant still resolves and retains its value `1`

### Requirement: Path resolvers emit accessor Operations per segment

Source-path descent SHALL be a **forward, target-bound** walk driven by the driver (see `graph-expansion`
"Forward target-bound descent walks a directive path"): the driver walks the named path root→leaf and at each
segment dispatches the accessor (`descend`) strategy set (getter / method / field), which emits one unary
accessor `Operation` producing that segment's `Value` from its parent's. Each parent segment's accessor SHALL be
landed **before** its child segment's, and the child's accessor SHALL be dispatched against the parent type
**read off the parent `Value` just landed** — never a separately predicted type. There SHALL be no backward
parent re-demand, no eager whole-path materialisation, no descent-private strategy dispatch or typing memo, and
**no forward typing helper**: a segment's type falls out of landing its accessor. Resolver matching rules,
accessibility checks, and weights carry over unchanged. When several resolvers match one segment, the driver
SHALL over-emit them all and plan extraction SHALL prune by weight — never `findFirst`/registration order.

#### Scenario: Two-segment path yields two accessor Operations, landed parent-first
- **WHEN** the binding's source path is `address.street` from parameter `p`
- **THEN** the supply chain is `p → [getAddress()] → (address) → [getStreet()] → (street)` with each accessor an
  Operation carrying its resolver's weight
- **AND** `getAddress()` is landed before `getStreet()`, and `getStreet()` is dispatched against the concrete
  type of the landed `address` `Value` — not a predicted type, and not via any `resolveAccessor` helper or typing
  memo

#### Scenario: An ambiguous segment prefers the getter by cost
- **WHEN** a segment `name` matches both `getName()` and a public field `name`
- **THEN** both accessor Operations are over-emitted and plan extraction selects `getName()` by its lower
  `STEP_GETTER` weight, independent of `ServiceLoader` order

### Requirement: Input-root base case resolves against the Value's own scope

A single-segment `SourceLocation` (and, in a child scope, the element root) SHALL be a `LEAF` base case
materialised **lazily** on first reference, typed from the **input declaration of the demand's own scope** —
never a global current-method, and **without branching on the scope kind**. For a method scope the declaration
comes from a parameter (typed from the signature); for a child scope it is the element input (typed by
grounding-at-land when the owning Operation lands). A forward descent SHALL root at this scope input and read the
root type from the scope's input declaration. Because a child scope is created only when its owning Operation
lands — after grounding — the root type is always concrete by the time any descent in that scope runs. There
SHALL be no scope-kind branch in descent-root typing, source-binding, or grounding, and **no surviving
`AccessorResolver` component**. An input SHALL NOT be pre-seeded before expansion; when nothing references it, no
`Value` for it exists.

#### Scenario: Single-segment path binds the lazily-materialised input leaf
- **WHEN** a binding's source is just `p`
- **THEN** the supply is the input-root `LEAF` `Value` of the scope that owns the demand, created on first
  reference rather than pre-seeded, resolved through the scope's input declaration

#### Scenario: Descent-root typing does not branch on scope kind
- **WHEN** a descent's root segment is typed in a method scope versus a child (element) scope
- **THEN** both read the root type from the scope's input declaration, with no `instanceof MethodScope` branch
  and no `AccessorResolver`

#### Scenario: A child-scope descent root is concrete at scope birth
- **WHEN** a container element transform whose element type was bound by grounding-by-match descends a path from
  the element root
- **THEN** the element root's type is already concrete (fixed when the owning Operation landed), so the descent
  reads it directly with no wait for grounding
