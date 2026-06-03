# Source Path Resolution Spec

## Purpose

This spec defines the `PathSegmentResolver` SPI and the seed-time path-walking algorithm in `SeedGraph` that types multi-segment source paths and registers per-segment `ExpansionGroup`s. Before this capability, multi-segment source paths (like `person.address.street`) were left as untyped seed chains and resolved during expansion by the now-removed `GetterRead` `Bridge`. With path resolvers, the seed-time graph already contains typed source nodes for every resolvable segment plus a registered group per segment, and expansion can immediately reach them without a bridge round-trip.

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

The `percolate-strategies-builtin` module SHALL ship `io.github.joke.percolate.spi.builtins.GetterPathResolver` implementing `PathSegmentResolver` and annotated `@AutoService(PathSegmentResolver.class)`.

`GetterPathResolver.resolve(parentType, segment, ctx)` SHALL:

1. Return `Optional.empty()` when `parentType.getKind() != TypeKind.DECLARED` or its element is not a `TypeElement`.
2. Probe for a JavaBean accessor method on `parentType`, in this order:
   - `get<Segment>()` where `<Segment>` is `segment` with its first character upper-cased (zero parameters, non-Object-class).
   - `is<Segment>()` where `<Segment>` is as above (zero parameters, return type is `boolean` / `java.lang.Boolean`, non-Object-class).
3. If neither matches, return `Optional.empty()`.
4. On match, return `Optional.of(new ResolvedSegment(method.getReturnType(), codegen, Weights.STEP_GETTER, method))` where `codegen` renders `<slot-0>.<methodName>()` and `method` is the matched accessor `ExecutableElement` passed as `producedFrom`.

`GetterPathResolver` SHALL ignore methods declared on `java.lang.Object`.

#### Scenario: GetterPathResolver matches a JavaBean getter
- **WHEN** `GetterPathResolver.resolve(<Person>, "lastName", ctx)` is invoked and `Person` has `String getLastName()`
- **THEN** the returned `Optional` is non-empty
- **AND** `returnType` is `String`
- **AND** the codegen renders `<slot>.getLastName()`
- **AND** `weight` equals `Weights.STEP_GETTER`
- **AND** `producedFrom` is the `ExecutableElement` for `getLastName()`

#### Scenario: GetterPathResolver matches an `is` accessor for boolean
- **WHEN** `GetterPathResolver.resolve(<Person>, "active", ctx)` is invoked and `Person` has `boolean isActive()`
- **THEN** the returned `Optional` is non-empty
- **AND** the codegen renders `<slot>.isActive()`
- **AND** `weight` equals `Weights.STEP_GETTER`
- **AND** `producedFrom` is the `ExecutableElement` for `isActive()`

#### Scenario: GetterPathResolver rejects parameterized overloads
- **WHEN** `GetterPathResolver.resolve(<Person>, "name", ctx)` is invoked and `Person` has both `String getName(String suffix)` and no zero-arg `getName()`
- **THEN** the returned `Optional` is empty

#### Scenario: GetterPathResolver ignores Object methods
- **WHEN** `GetterPathResolver.resolve(<Object>, "class", ctx)` is invoked
- **THEN** the returned `Optional` is empty even though `Object.getClass()` exists

#### Scenario: GetterPathResolver returns empty for non-declared parents
- **WHEN** `GetterPathResolver.resolve(<int[]>, "length", ctx)` is invoked (array, not declared)
- **THEN** the returned `Optional` is empty

### Requirement: MethodPathResolver built-in

The `percolate-strategies-builtin` module SHALL ship `io.github.joke.percolate.spi.builtins.MethodPathResolver` implementing `PathSegmentResolver` and annotated `@AutoService(PathSegmentResolver.class)`.

`MethodPathResolver.resolve(parentType, segment, ctx)` SHALL:

1. Return `Optional.empty()` when `parentType.getKind() != TypeKind.DECLARED` or its element is not a `TypeElement`.
2. Probe for a zero-arg method named exactly `segment` on `parentType`, ignoring methods declared on `java.lang.Object`.
3. If not matched, return `Optional.empty()`.
4. On match, return `Optional.of(new ResolvedSegment(method.getReturnType(), codegen, Weights.STEP_METHOD, method))` where `codegen` renders `<slot-0>.<segment>()` and `method` is the matched `ExecutableElement` passed as `producedFrom`.

`MethodPathResolver` SHALL apply uniformly to any `DECLARED` parent type — records, plain classes, interfaces, abstract classes. It SHALL NOT gate on `ElementKind.RECORD`. Records continue to work because their canonical accessors fit the `no-arg method whose simple name equals segment` predicate.

#### Scenario: MethodPathResolver matches a canonical record accessor
- **WHEN** `MethodPathResolver.resolve(<PointRecord>, "x", ctx)` is invoked and `PointRecord` is a `record PointRecord(int x, int y)`
- **THEN** the returned `Optional` is non-empty
- **AND** `returnType` is `int`
- **AND** the codegen renders `<slot>.x()`
- **AND** `weight` equals `Weights.STEP_METHOD`
- **AND** `producedFrom` is the `ExecutableElement` for the canonical accessor `x()`

#### Scenario: MethodPathResolver matches a fluent-style accessor on a non-record class
- **WHEN** `MethodPathResolver.resolve(<Address>, "street", ctx)` is invoked and `Address` is a plain class with `String street() { return street; }`
- **THEN** the returned `Optional` is non-empty
- **AND** `returnType` is `String`
- **AND** the codegen renders `<slot>.street()`

#### Scenario: MethodPathResolver rejects parameterised methods
- **WHEN** `MethodPathResolver.resolve(<Person>, "name", ctx)` is invoked and `Person` has only `String name(String suffix)` and no zero-arg overload
- **THEN** the returned `Optional` is empty

#### Scenario: MethodPathResolver ignores Object methods
- **WHEN** `MethodPathResolver.resolve(<Object>, "toString", ctx)` is invoked
- **THEN** the returned `Optional` is empty even though `Object.toString()` exists

#### Scenario: MethodPathResolver returns empty for non-declared parents
- **WHEN** `MethodPathResolver.resolve(<int[]>, "length", ctx)` is invoked (array, not declared)
- **THEN** the returned `Optional` is empty

### Requirement: FieldPathResolver built-in

The `percolate-strategies-builtin` module SHALL ship `io.github.joke.percolate.spi.builtins.FieldPathResolver` implementing `PathSegmentResolver` and annotated `@AutoService(PathSegmentResolver.class)`.

`FieldPathResolver.resolve(parentType, segment, ctx)` SHALL:

1. Return `Optional.empty()` when `parentType.getKind() != TypeKind.DECLARED` or its element is not a `TypeElement`.
2. Probe for a `VariableElement` on `parentType` whose `simpleName` equals `segment`, whose `ElementKind` is `FIELD`, and whose modifiers contain neither `PRIVATE` nor `STATIC`.
3. If not matched, return `Optional.empty()`.
4. On match, return `Optional.of(new ResolvedSegment(field.asType(), codegen, Weights.STEP_FIELD, field))` where `codegen` renders `<slot-0>.<segment>` (a field read with no parentheses) and `field` is the matched `VariableElement` passed as `producedFrom`.

#### Scenario: FieldPathResolver matches a public field
- **WHEN** `FieldPathResolver.resolve(<Box>, "value", ctx)` is invoked and `Box` has `public String value`
- **THEN** the returned `Optional` is non-empty
- **AND** the codegen renders `<slot>.value`
- **AND** `weight` equals `Weights.STEP_FIELD`
- **AND** `producedFrom` is the `VariableElement` for `Box.value`

#### Scenario: FieldPathResolver rejects private fields
- **WHEN** `FieldPathResolver.resolve(<Person>, "lastName", ctx)` is invoked and `Person` has `private String lastName` (Lombok `@Value`)
- **THEN** the returned `Optional` is empty

#### Scenario: FieldPathResolver rejects static fields
- **WHEN** `FieldPathResolver.resolve(<Constants>, "DEFAULT", ctx)` is invoked and `Constants` has `public static String DEFAULT`
- **THEN** the returned `Optional` is empty

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
