# Source Path Resolution Spec

## Purpose

This spec defines source-path-segment resolution: the `ExpansionStrategy` implementations (the `Getter` / `Method` / `Field` path resolvers) that turn one segment of a `@Map` source path (like `person.address.street`) into a typed accessor, plus the parameter-root base case that grounds a single-segment source against its own method's parameters. Each resolver reads the segment to resolve from `frontier.directive()` and emits a `BOUNDARY` `ExpansionStep` describing the typed access; the engine reaches the produced source node through the ordinary cross-group fixed-point loop, with no dedicated path-resolution SPI or seed-time bridge round-trip.

## Requirements

### Requirement: Path resolution as a unified ExpansionStrategy

Source-path-segment resolution SHALL be performed by `ExpansionStrategy` implementations (the `Getter` / `Method` / `Field` path resolvers) rather than a dedicated `PathSegmentResolver` SPI. Each resolver SHALL read the segment to resolve from `frontier.directive()` (and the frontier's position within the directive's source path) instead of receiving a `segment` parameter, and SHALL emit a `BOUNDARY` `ExpansionStep` describing the typed access for that segment. The seed-time typing outcome (a typed source node per resolvable segment) SHALL be preserved.

Resolvers SHALL register via `@AutoService(ExpansionStrategy.class)` and SHALL be subject to the same single-list, `priority()`-then-FQN ordering as every other strategy.

#### Scenario: a path resolver reads its segment from the directive
- **WHEN** a path resolver's `expand(frontier, ctx)` is invoked for a frontier mid-way along a `@Map` source path
- **THEN** the resolver obtains the segment to resolve from `frontier.directive()`
- **AND** does not receive a `segment` method parameter

#### Scenario: a resolved segment is a boundary step
- **WHEN** a path resolver resolves a segment access
- **THEN** it emits an `ExpansionStep` with `intent == BOUNDARY`
- **AND** the step's slot describes the parent value the access reads from

### Requirement: Parameter-root base case resolves against the node's own method scope

A single-segment source node (a `src[param]` root) SHALL be recognised as satisfied-by-parameter using the method carried by **the node's own scope** (its `MethodScope`), never a process-global "current method". Because the driver expands every method's groups together in one cross-group fixed-point loop, a base case keyed on a single shared current method would satisfy only one method's parameter roots and strand the rest; resolving against the node's own scope keeps multi-method mappers correct regardless of expansion order. The same node-scope resolution SHALL be used wherever a parameter root must be recovered (producer-scope recovery and failing-slot diagnostics).

#### Scenario: a parameter root is satisfied in a multi-method mapper
- **WHEN** a mapper declares two methods and the driver expands their groups in one fixed-point loop
- **THEN** each method's single-segment source roots are satisfied against that method's own parameters
- **AND** the outcome does not depend on which method's seed edge the driver visited first

#### Scenario: a single-segment source naming no parameter of its own method is not a root
- **WHEN** a single-segment source node's segment matches no parameter of the method in its own scope
- **THEN** it is not treated as a parameter-root base case

### Requirement: GetterPathResolver built-in

The `percolate-strategies-builtin` module SHALL ship `io.github.joke.percolate.spi.builtins.GetterPathResolver` implementing `ExpansionStrategy` and annotated `@AutoService(ExpansionStrategy.class)`.

`GetterPathResolver.expand(frontier, ctx)` SHALL read the single segment to resolve from `frontier.directive()` (see "Path resolution as a unified ExpansionStrategy") and, for each candidate parent type, SHALL:

1. Produce no step when the parent type's `getKind() != TypeKind.DECLARED` or its element is not a `TypeElement`.
2. Probe for a JavaBean accessor method on the parent type, in this order:
   - `get<Segment>()` where `<Segment>` is the segment with its first character upper-cased (zero parameters, non-Object-class).
   - `is<Segment>()` where `<Segment>` is as above (zero parameters, return type is `boolean` / `java.lang.Boolean`, non-Object-class).
3. If neither matches, produce no step.
4. On match, emit a single `BOUNDARY` `ExpansionStep` (via `ExpansionStep.boundary(...)`) whose one slot is the parent value — a `Slot` named `"value"`, typed to the parent type, weighted `Weights.STEP_GETTER`, carrying the matched accessor `ExecutableElement` as its `producedFrom` — whose `output` is the accessor's return type, whose `weight` is `Weights.STEP_GETTER`, and whose codegen renders `<parent>.<methodName>()`.

`GetterPathResolver` SHALL ignore methods declared on `java.lang.Object`.

#### Scenario: GetterPathResolver matches a JavaBean getter
- **WHEN** `GetterPathResolver` resolves the segment `lastName` against a `Person` candidate that has `String getLastName()`
- **THEN** it emits a single `BOUNDARY` `ExpansionStep`
- **AND** the step's `output` is `String`
- **AND** the codegen renders `<parent>.getLastName()`
- **AND** the step's `weight` equals `Weights.STEP_GETTER`
- **AND** the slot's `producedFrom` is the `ExecutableElement` for `getLastName()`

#### Scenario: GetterPathResolver matches an `is` accessor for boolean
- **WHEN** `GetterPathResolver` resolves the segment `active` against a `Person` candidate that has `boolean isActive()`
- **THEN** it emits a single `BOUNDARY` `ExpansionStep`
- **AND** the codegen renders `<parent>.isActive()`
- **AND** the step's `weight` equals `Weights.STEP_GETTER`
- **AND** the slot's `producedFrom` is the `ExecutableElement` for `isActive()`

#### Scenario: GetterPathResolver rejects parameterized overloads
- **WHEN** `GetterPathResolver` resolves the segment `name` against a `Person` candidate that has both `String getName(String suffix)` and no zero-arg `getName()`
- **THEN** it emits no step

#### Scenario: GetterPathResolver ignores Object methods
- **WHEN** `GetterPathResolver` resolves the segment `class` against an `Object` candidate
- **THEN** it emits no step even though `Object.getClass()` exists

#### Scenario: GetterPathResolver returns empty for non-declared parents
- **WHEN** `GetterPathResolver` resolves the segment `length` against an `int[]` candidate (array, not declared)
- **THEN** it emits no step

### Requirement: MethodPathResolver built-in

The `percolate-strategies-builtin` module SHALL ship `io.github.joke.percolate.spi.builtins.MethodPathResolver` implementing `ExpansionStrategy` and annotated `@AutoService(ExpansionStrategy.class)`.

`MethodPathResolver.expand(frontier, ctx)` SHALL read the single segment to resolve from `frontier.directive()` and, for each candidate parent type, SHALL:

1. Produce no step when the parent type's `getKind() != TypeKind.DECLARED` or its element is not a `TypeElement`.
2. Probe for a zero-arg method named exactly the segment on the parent type, ignoring methods declared on `java.lang.Object`.
3. If not matched, produce no step.
4. On match, emit a single `BOUNDARY` `ExpansionStep` (via `ExpansionStep.boundary(...)`) whose one slot is the parent value — a `Slot` named `"value"`, typed to the parent type, weighted `Weights.STEP_METHOD`, carrying the matched `ExecutableElement` as its `producedFrom` — whose `output` is the method's return type, whose `weight` is `Weights.STEP_METHOD`, and whose codegen renders `<parent>.<segment>()`.

`MethodPathResolver` SHALL apply uniformly to any `DECLARED` parent type — records, plain classes, interfaces, abstract classes. It SHALL NOT gate on `ElementKind.RECORD`. Records continue to work because their canonical accessors fit the `no-arg method whose simple name equals segment` predicate.

#### Scenario: MethodPathResolver matches a canonical record accessor
- **WHEN** `MethodPathResolver` resolves the segment `x` against a `PointRecord` candidate that is a `record PointRecord(int x, int y)`
- **THEN** it emits a single `BOUNDARY` `ExpansionStep`
- **AND** the step's `output` is `int`
- **AND** the codegen renders `<parent>.x()`
- **AND** the step's `weight` equals `Weights.STEP_METHOD`
- **AND** the slot's `producedFrom` is the `ExecutableElement` for the canonical accessor `x()`

#### Scenario: MethodPathResolver matches a fluent-style accessor on a non-record class
- **WHEN** `MethodPathResolver` resolves the segment `street` against an `Address` candidate that is a plain class with `String street() { return street; }`
- **THEN** it emits a single `BOUNDARY` `ExpansionStep`
- **AND** the step's `output` is `String`
- **AND** the codegen renders `<parent>.street()`

#### Scenario: MethodPathResolver rejects parameterised methods
- **WHEN** `MethodPathResolver` resolves the segment `name` against a `Person` candidate that has only `String name(String suffix)` and no zero-arg overload
- **THEN** it emits no step

#### Scenario: MethodPathResolver ignores Object methods
- **WHEN** `MethodPathResolver` resolves the segment `toString` against an `Object` candidate
- **THEN** it emits no step even though `Object.toString()` exists

#### Scenario: MethodPathResolver returns empty for non-declared parents
- **WHEN** `MethodPathResolver` resolves the segment `length` against an `int[]` candidate (array, not declared)
- **THEN** it emits no step

### Requirement: FieldPathResolver built-in

The `percolate-strategies-builtin` module SHALL ship `io.github.joke.percolate.spi.builtins.FieldPathResolver` implementing `ExpansionStrategy` and annotated `@AutoService(ExpansionStrategy.class)`.

`FieldPathResolver.expand(frontier, ctx)` SHALL read the single segment to resolve from `frontier.directive()` and, for each candidate parent type, SHALL:

1. Produce no step when the parent type's `getKind() != TypeKind.DECLARED` or its element is not a `TypeElement`.
2. Probe for a `VariableElement` on the parent type whose `simpleName` equals the segment, whose `ElementKind` is `FIELD`, and whose modifiers contain neither `PRIVATE` nor `STATIC`.
3. If not matched, produce no step.
4. On match, emit a single `BOUNDARY` `ExpansionStep` (via `ExpansionStep.boundary(...)`) whose one slot is the parent value — a `Slot` named `"value"`, typed to the parent type, weighted `Weights.STEP_FIELD`, carrying the matched `VariableElement` as its `producedFrom` — whose `output` is the field's type (`field.asType()`), whose `weight` is `Weights.STEP_FIELD`, and whose codegen renders `<parent>.<segment>` (a field read with no parentheses).

#### Scenario: FieldPathResolver matches a public field
- **WHEN** `FieldPathResolver` resolves the segment `value` against a `Box` candidate that has `public String value`
- **THEN** it emits a single `BOUNDARY` `ExpansionStep`
- **AND** the codegen renders `<parent>.value`
- **AND** the step's `weight` equals `Weights.STEP_FIELD`
- **AND** the slot's `producedFrom` is the `VariableElement` for `Box.value`

#### Scenario: FieldPathResolver rejects private fields
- **WHEN** `FieldPathResolver` resolves the segment `lastName` against a `Person` candidate that has `private String lastName` (Lombok `@Value`)
- **THEN** it emits no step

#### Scenario: FieldPathResolver rejects static fields
- **WHEN** `FieldPathResolver` resolves the segment `DEFAULT` against a `Constants` candidate that has `public static String DEFAULT`
- **THEN** it emits no step

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
