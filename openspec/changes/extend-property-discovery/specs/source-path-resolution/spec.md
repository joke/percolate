## ADDED Requirements

### Requirement: MethodPathResolver built-in

The `percolate-strategies-builtin` module SHALL ship `io.github.joke.percolate.spi.builtins.MethodPathResolver` implementing `PathSegmentResolver` and annotated `@AutoService(PathSegmentResolver.class)`.

`MethodPathResolver.resolve(parentType, segment, ctx)` SHALL:

1. Return `Optional.empty()` when `parentType.getKind() != TypeKind.DECLARED` or its element is not a `TypeElement`.
2. Probe for a zero-arg method named exactly `segment` on `parentType`, ignoring methods declared on `java.lang.Object`.
3. If not matched, return `Optional.empty()`.
4. On match, return `Optional.of(new ResolvedSegment(method.getReturnType(), codegen, Weights.STEP_METHOD))` where `codegen` renders `<slot-0>.<segment>()`.

`MethodPathResolver` SHALL apply uniformly to any `DECLARED` parent type — records, plain classes, interfaces, abstract classes. It SHALL NOT gate on `ElementKind.RECORD`. Records continue to work because their canonical accessors fit the `no-arg method whose simple name equals segment` predicate.

#### Scenario: MethodPathResolver matches a canonical record accessor
- **WHEN** `MethodPathResolver.resolve(<PointRecord>, "x", ctx)` is invoked and `PointRecord` is a `record PointRecord(int x, int y)`
- **THEN** the returned `Optional` is non-empty
- **AND** `returnType` is `int`
- **AND** the codegen renders `<slot>.x()`
- **AND** `weight` equals `Weights.STEP_METHOD`

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

## MODIFIED Requirements

### Requirement: GetterPathResolver built-in

The `percolate-strategies-builtin` module SHALL ship `io.github.joke.percolate.spi.builtins.GetterPathResolver` implementing `PathSegmentResolver` and annotated `@AutoService(PathSegmentResolver.class)`.

`GetterPathResolver.resolve(parentType, segment, ctx)` SHALL:

1. Return `Optional.empty()` when `parentType.getKind() != TypeKind.DECLARED` or its element is not a `TypeElement`.
2. Probe for a JavaBean accessor method on `parentType`, in this order:
   - `get<Segment>()` where `<Segment>` is `segment` with its first character upper-cased (zero parameters, non-Object-class).
   - `is<Segment>()` where `<Segment>` is as above (zero parameters, return type is `boolean` / `java.lang.Boolean`, non-Object-class).
3. If neither matches, return `Optional.empty()`.
4. On match, return `Optional.of(new ResolvedSegment(method.getReturnType(), codegen, Weights.STEP_GETTER))` where `codegen` renders `<slot-0>.<methodName>()`.

`GetterPathResolver` SHALL ignore methods declared on `java.lang.Object`.

#### Scenario: GetterPathResolver matches a JavaBean getter
- **WHEN** `GetterPathResolver.resolve(<Person>, "lastName", ctx)` is invoked and `Person` has `String getLastName()`
- **THEN** the returned `Optional` is non-empty
- **AND** `returnType` is `String`
- **AND** the codegen renders `<slot>.getLastName()`
- **AND** `weight` equals `Weights.STEP_GETTER`

#### Scenario: GetterPathResolver matches an `is` accessor for boolean
- **WHEN** `GetterPathResolver.resolve(<Person>, "active", ctx)` is invoked and `Person` has `boolean isActive()`
- **THEN** the returned `Optional` is non-empty
- **AND** the codegen renders `<slot>.isActive()`
- **AND** `weight` equals `Weights.STEP_GETTER`

#### Scenario: GetterPathResolver rejects parameterized overloads
- **WHEN** `GetterPathResolver.resolve(<Person>, "name", ctx)` is invoked and `Person` has both `String getName(String suffix)` and no zero-arg `getName()`
- **THEN** the returned `Optional` is empty

#### Scenario: GetterPathResolver ignores Object methods
- **WHEN** `GetterPathResolver.resolve(<Object>, "class", ctx)` is invoked
- **THEN** the returned `Optional` is empty even though `Object.getClass()` exists

#### Scenario: GetterPathResolver returns empty for non-declared parents
- **WHEN** `GetterPathResolver.resolve(<int[]>, "length", ctx)` is invoked (array, not declared)
- **THEN** the returned `Optional` is empty

### Requirement: FieldPathResolver built-in

The `percolate-strategies-builtin` module SHALL ship `io.github.joke.percolate.spi.builtins.FieldPathResolver` implementing `PathSegmentResolver` and annotated `@AutoService(PathSegmentResolver.class)`.

`FieldPathResolver.resolve(parentType, segment, ctx)` SHALL:

1. Return `Optional.empty()` when `parentType.getKind() != TypeKind.DECLARED` or its element is not a `TypeElement`.
2. Probe for a `VariableElement` on `parentType` whose `simpleName` equals `segment`, whose `ElementKind` is `FIELD`, and whose modifiers contain neither `PRIVATE` nor `STATIC`.
3. If not matched, return `Optional.empty()`.
4. On match, return `Optional.of(new ResolvedSegment(field.asType(), codegen, Weights.STEP_FIELD))` where `codegen` renders `<slot-0>.<segment>` (a field read with no parentheses).

#### Scenario: FieldPathResolver matches a public field
- **WHEN** `FieldPathResolver.resolve(<Box>, "value", ctx)` is invoked and `Box` has `public String value`
- **THEN** the returned `Optional` is non-empty
- **AND** the codegen renders `<slot>.value`
- **AND** `weight` equals `Weights.STEP_FIELD`

#### Scenario: FieldPathResolver rejects private fields
- **WHEN** `FieldPathResolver.resolve(<Person>, "lastName", ctx)` is invoked and `Person` has `private String lastName` (Lombok `@Value`)
- **THEN** the returned `Optional` is empty

#### Scenario: FieldPathResolver rejects static fields
- **WHEN** `FieldPathResolver.resolve(<Constants>, "DEFAULT", ctx)` is invoked and `Constants` has `public static String DEFAULT`
- **THEN** the returned `Optional` is empty

### Requirement: Resolver registration via ServiceLoader

`PathSegmentResolver` implementations in `percolate-strategies-builtin` SHALL be discoverable via `java.util.ServiceLoader.load(PathSegmentResolver.class, classLoader)` through their `@AutoService(PathSegmentResolver.class)` registration. The processor SHALL collect them into an immutable `List<PathSegmentResolver>` sorted by `Class.getName()` (class-name ascending) and inject that list at the expansion-time path-segment-group resolution entry point.

The class-name sort SHALL serve only as a stable iteration order for diagnostics and as a deterministic tie-breaker; it SHALL NOT determine resolver precedence. Precedence is encoded in each `ResolvedSegment`'s `weight` and is enforced by the selection algorithm in *Expansion-time path-segment-group resolution*.

#### Scenario: ServiceLoader discovers all three builtins
- **WHEN** `ServiceLoader.load(PathSegmentResolver.class, ProcessorModule.class.getClassLoader())` is invoked
- **THEN** the returned stream contains `FieldPathResolver`, `GetterPathResolver`, and `MethodPathResolver` (subset)

#### Scenario: Resolver list is sorted by class name
- **WHEN** the processor module's `pathSegmentResolvers()` provider is invoked
- **THEN** the returned list iterates in `Class.getName()` ascending order
- **AND** ties are impossible (class names are unique)

### Requirement: Expansion-time path-segment-group resolution

`ExpandGroupsPhase` SHALL invoke registered `PathSegmentResolver`s when it encounters a path-segment group during work-list processing. A group is recognised as a path-segment group by its structural shape (see `graph-expansion` capability — "Path-segment-group resolution via PathSegmentResolver" requirement): both `root.loc` and `slot.loc` are `SourceLocation`s, and `root.loc.path` extends `slot.loc.path` by exactly one segment.

The resolver invocation rule:

1. The slot MUST be typed (`slot.type.isPresent()`). If the slot is not yet typed, the path-segment group is not ready — the work-list's topological ordering guarantees the slot's own path-segment group has been processed first.
2. Iterate every injected `PathSegmentResolver` and collect each non-empty `Optional<ResolvedSegment>` returned by `resolve(slot.type.get(), appendedSegment, ctx)` into a candidate set.
3. If the candidate set is empty, record `GroupOutcome.unsatNoPlan(group, slot)` and return. The root remains untyped.
4. Otherwise select the candidate with the lowest `ResolvedSegment.weight`. Ties (two candidates with the same weight) SHALL be broken by the resolver list's pre-existing class-name ascending order; the engine SHALL NOT silently discard either candidate.
5. With the selected `ResolvedSegment` (call it `rs`):
   - Set the root's type via `root.setType(rs.getReturnType())` (in-place; instance identity preserved — see `graph-model`).
   - Emit a REALISED edge from `slot` to `root` with `weight = rs.getWeight()`, `codegen = rs.getCodegen()`, `strategyClassFqn = resolver.getClass().getName()` (where `resolver` is the resolver that produced `rs`).
   - Add the edge to the path-segment group's view via `group.addEdgeToView(edge)` so the group's `view.edgeSet()` reflects the just-emitted REALISED edge.
   - Record `GroupOutcome.sat(group)`.

`PathSegmentResolver` implementations SHALL NOT be invoked outside `ExpandGroupsPhase`'s path-segment-group expansion path. `SeedGraph` SHALL NOT invoke them (see `seed-graph` capability).

The graph traversal direction stays target-driven: `ExpandGroupsPhase` starts at the group's untyped root and asks "what produces this?" The resolver answers by inspecting the typed slot's type and the appended segment name (resolver-internal type inference is forward; engine-level traversal is backward). Per `feedback_never_forward_expansion.md`, this distinction is preserved.

#### Scenario: Two-segment source path resolves at expansion time
- **WHEN** a path-segment group `(root=src[person.lastName]:?, slot=src[person]:Person)` is drained from the work-list
- **AND** `GetterPathResolver.resolve(Person, "lastName", ctx)` returns `Optional.of(ResolvedSegment(String, codegen, Weights.STEP_GETTER))`
- **AND** no other resolver returns a non-empty match
- **THEN** the root's type is set to `Optional.of(String)` in place
- **AND** a REALISED edge `src[person] → src[person.lastName]:String` is emitted with the resolver's codegen
- **AND** the group's outcome is SAT

#### Scenario: Three-segment path resolves in topological layers
- **WHEN** two path-segment groups exist: `g_addr = (root=src[person.address]:?, slot=src[person]:Person)` and `g_street = (root=src[person.address.street]:?, slot=src[person.address]:?)`
- **AND** the work-list processes `g_addr` first (topological order)
- **THEN** `g_addr` SATs first, typing `src[person.address]` to `Address`
- **AND** when `g_street` is processed, its slot is now typed, so resolvers are invoked
- **AND** `g_street` SATs, typing `src[person.address.street]` to `String`

#### Scenario: Lowest-weight match wins when multiple resolvers match
- **WHEN** a path-segment group `(root=src[bean.value]:?, slot=src[bean]:Bean)` is drained
- **AND** `Bean` exposes `public String value` AND `String getValue()`
- **AND** `GetterPathResolver` returns a match with `weight = Weights.STEP_GETTER`
- **AND** `FieldPathResolver` returns a match with `weight = Weights.STEP_FIELD`
- **THEN** the engine selects the `GetterPathResolver` match
- **AND** the emitted REALISED edge's codegen renders `<slot>.getValue()`
- **AND** the edge's `strategyClassFqn` is `io.github.joke.percolate.spi.builtins.GetterPathResolver`

#### Scenario: No resolver matches; the path-segment group is UNSAT_NO_PLAN
- **WHEN** a path-segment group `(root=src[person.weirdSegment]:?, slot=src[person]:Person)` is drained
- **AND** no registered resolver matches `(Person, "weirdSegment", ctx)`
- **THEN** the group records `unsatNoPlan(group, slot)`
- **AND** the root remains untyped
- **AND** subsequent directive-binding groups depending on this root remain UNSAT

#### Scenario: Two directives sharing a prefix share the typed Node by instance identity
- **WHEN** two directives `@Map(source = "person.address.street")` and `@Map(source = "person.address.city")` are processed
- **THEN** `SeedGraph` registers one shared `src[person.address]:?` Node instance (via its prefix-sharing dedup)
- **AND** exactly one path-segment group is registered for the `Address`-segment (slot `src[person]`)
- **AND** when that group SATs, the shared Node's type is set in place — both downstream directives see the typed node

#### Scenario: Single-segment source path needs no resolver invocation
- **WHEN** a directive `@Map(target = "x", source = "person")` is processed and `person` is a parameter
- **THEN** no path-segment group is registered (the SEED edge is `src[person] → tgt[x]`, which is a directive-binding group, not a path-segment group)
- **AND** no resolver is invoked

### Requirement: Resolver priority determinism

When multiple `PathSegmentResolver` implementations match the same `(parentType, segment)` input, `ExpandGroupsPhase` SHALL select the candidate whose `ResolvedSegment.weight` is the lowest. Ties (two candidates returning the same numeric weight) SHALL be broken deterministically by the resolver's `Class.getName()` ascending order — preserving the existing list-iteration determinism for cases where two resolvers genuinely declare the same cost.

The default precedence for the three built-in resolvers — driven by the weight constants in `Weights` — is `GetterPathResolver < MethodPathResolver < FieldPathResolver`. A `getX()` accessor wins over a fluent `x()` method, which wins over a public field `x` on the same parent type.

Selection SHALL be byte-stable across multiple JVM runs given the same registered resolver set. A third-party resolver MAY position itself ahead of or behind the built-ins by choosing its own weight; it SHALL NOT need to alter `ProcessorModule.pathSegmentResolvers()`'s comparator to be granted higher precedence.

#### Scenario: Getter wins over field on the same parent
- **WHEN** the expansion phase invokes resolvers for `(<Bean>, "value", ctx)` where `Bean` has both `public String value` and `String getValue()`
- **THEN** the selected resolver is `GetterPathResolver`
- **AND** the registered group's codegen renders `<slot>.getValue()` (the getter)

#### Scenario: Method wins over field on the same parent
- **WHEN** the expansion phase invokes resolvers for `(<FluentBean>, "value", ctx)` where `FluentBean` has both `public String value` (field) and `String value()` (no-arg method) but no `getValue()`
- **THEN** the selected resolver is `MethodPathResolver`
- **AND** the registered group's codegen renders `<slot>.value()` (the method call)

#### Scenario: Resolver order is deterministic across runs
- **WHEN** the expansion phase runs twice against the same mapper input in two JVM invocations
- **THEN** the resulting typed source chains, REALISED edges, and registered groups are byte-identical (modulo node identityHashCode)

#### Scenario: External resolver outranks built-ins via lower weight
- **WHEN** a third-party `@AutoService(PathSegmentResolver.class)` resolver returns a match with `weight = 0`
- **AND** a built-in resolver also matches the same `(parentType, segment)` with its declared `STEP_*` weight (≥ 1)
- **THEN** the third-party resolver's match is selected

## REMOVED Requirements

### Requirement: RecordPathResolver built-in

**Reason**: Generalised into `MethodPathResolver` (see *MethodPathResolver built-in* above). The match predicate (`no-arg method whose simple name equals segment`) is identical for record canonical accessors and for fluent-style non-record methods; gating it on `ElementKind.RECORD` excluded a real access shape. Records are preserved by the generalised resolver because their canonical accessors fit the predicate.

**Migration**: Code that references the class `io.github.joke.percolate.spi.builtins.RecordPathResolver` by FQN MUST switch to `io.github.joke.percolate.spi.builtins.MethodPathResolver`. SPI-registered consumers using `ServiceLoader.load(PathSegmentResolver.class, …)` auto-migrate because the renamed class continues to be discovered via `@AutoService`. Tests referencing `RecordPathResolverSpec` MUST be renamed to `MethodPathResolverSpec` and broaden their fixture set per the `builtin-strategy-unit-tests` capability.
