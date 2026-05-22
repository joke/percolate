## REMOVED Requirements

### Requirement: OptionalMap built-in

**Reason**: The fused container-map bridge pattern is retired. The semantic of "transform `Optional<X>` into `Optional<Y>` element-by-element" is now expressed compositionally as `OptionalUnwrap` (scope-enter into element scope) + a per-element transformation bridge + `OptionalCollect` (scope-exit collecting 0-or-1 element back into `Optional<Y>`). The intermediate `OptionalMap` bridge with its `ElementSeed` declaration is structurally redundant.

**Migration**: Mapper authors who relied on a directive like `@Map(target = "opt", source = "src.opt")` mapping `Optional<X>` to `Optional<Y>` SHALL get the same chain via the new built-ins automatically — `OptionalUnwrap` enters element scope on the source, `MethodCallBridge` (or the appropriate user transformation) maps `X → Y` in element scope, and `OptionalCollect` exits back to `Optional<Y>`. No user-side code change is required if the relevant `MethodCallBridge` candidate exists; the engine picks the chain.

### Requirement: ListMap built-in

**Reason**: Same as `OptionalMap`. The fused `ListMap` is retired; the semantic of mapping `List<X> → List<Y>` is expressed compositionally as `IterableUnwrap` (scope-enter on the source list) + per-element transformation + `ListCollect` (scope-exit collecting into the target list).

**Migration**: Same as `OptionalMap`. Engine picks the chain automatically when the transformation bridges are available.

### Requirement: SetMap built-in

**Reason**: Same as `OptionalMap` / `ListMap`. The fused `SetMap` is retired; `IterableUnwrap` + per-element transformation + `SetCollect` provides the equivalent chain.

**Migration**: Same as the others.

### Requirement: Container-map codegen seam deferred

**Reason**: This requirement (in the existing main spec) described the codegen contract of the fused `*Map` bridges' outer REALISED edge, with their `throw new UnsupportedOperationException("rendered by the codegen capability; element-scope inlining is not implemented in this change")` stub. With the `*Map` bridges deleted, there is no outer container-map edge to attach codegen to. The new `*Collect` bridges' codegen lambdas remain pass-through placeholders for now (the future codegen capability will materialise iteration style).

**Migration**: Any test or doc referencing "the outer container-map edge's codegen throws UnsupportedOperationException" SHALL be deleted. The placeholder codegen behaviour on `*Collect` / `*Unwrap` lambdas (pass-through `$L`) is documented in their respective built-in requirements.

## MODIFIED Requirements

### Requirement: OptionalUnwrap built-in

The `percolate-strategies-builtin` module SHALL ship `io.github.joke.percolate.spi.builtins.OptionalUnwrap` implementing `Bridge` and annotated `@AutoService(Bridge.class)`.

`OptionalUnwrap.bridge(from, to, ctx)` SHALL emit a single `BridgeStep` when `from` is an `Optional<X>` declared type AND `to` is the element type `X` (`ctx.types().isSameType(to, Containers.typeArgument(from, 0))`). The emitted step SHALL have:

- `inputType` = `from` itself (the `Optional<X>` type).
- `outputType` = the type argument `X` of `from`.
- `weight` = `Weights.CONTAINER`.
- `codegen` = a pass-through lambda rendering `<input>` (the single incoming variable supplied by `IncomingValues.single()`). The actual `.orElse(null)` / `.stream().findFirst()` / `.ifPresent(...)` materialisation is deferred to a future codegen capability that selects iteration style.
- `scopeTransition` = `ScopeTransition.ENTERING`.
- `elementRole` = `"element"`.

When `from` is not an `Optional`, or when `to` is not the type argument of `from`, `OptionalUnwrap.bridge` SHALL return `Stream.empty()`.

**This is a breaking change**: previously `OptionalUnwrap` produced a regular-scope nullable value via `.orElse(null)` codegen. Now it produces an element-scope `X` value with placeholder codegen; materialisation is a codegen-time decision. Chains that previously closed `Optional<X> → X` in one regular-scope hop now require a downstream element-scope-to-regular-scope bridge (deferred as out of scope; see `[[split-container-bridges/design.md#non-goals]]`).

#### Scenario: OptionalUnwrap emits for Optional source
- **WHEN** `OptionalUnwrap.bridge(<Optional<Dog>>, <Dog>, ctx)` is invoked
- **THEN** the stream contains one `BridgeStep`
- **AND** the step's `inputType` is `<Optional<Dog>>` and `outputType` is `<Dog>`
- **AND** the step's `weight` equals `Weights.CONTAINER`
- **AND** the step's `scopeTransition` equals `ScopeTransition.ENTERING`
- **AND** the step's `elementRole` equals `"element"`
- **AND** the step's `codegen` renders `<inputVar>` (pass-through) for any input variable name

#### Scenario: OptionalUnwrap declines non-Optional source
- **WHEN** `OptionalUnwrap.bridge(<Dog>, <Pet>, ctx)` is invoked
- **THEN** the result is `Stream.empty()`

#### Scenario: OptionalUnwrap declines when target type does not match element type
- **WHEN** `OptionalUnwrap.bridge(<Optional<Dog>>, <Pet>, ctx)` is invoked (and `Dog` is not `Pet`)
- **THEN** the result is `Stream.empty()`

### Requirement: OptionalWrap built-in

The `percolate-strategies-builtin` module SHALL ship `io.github.joke.percolate.spi.builtins.OptionalWrap` implementing `Bridge` and annotated `@AutoService(Bridge.class)`.

`OptionalWrap.bridge(from, to, ctx)` SHALL emit a single `BridgeStep` when `to` is an `Optional<X>` declared type AND `from` is the element type `X`. The emitted step SHALL have:

- `inputType` = the type argument `X` of `to`.
- `outputType` = `to` itself (the `Optional<X>` type).
- `weight` = `Weights.CONTAINER`.
- `codegen` = a lambda rendering `Optional.ofNullable(<input>)` where `<input>` is the single incoming variable.
- `scopeTransition` = `ScopeTransition.PRESERVING`.
- `elementRole` = `"element"` (default; not consulted for PRESERVING).

When `to` is not an `Optional`, `OptionalWrap.bridge` SHALL return `Stream.empty()`.

`OptionalWrap` covers the regular-scope `T → Optional<T>` direct case (e.g., `tgt[firstName]:Optional<String> ← src[person2.first]:String` where the source is a regular nullable value). It coexists with `OptionalCollect` (scope-exit); the engine picks via weight tie-break (`OptionalWrap.weight == OptionalCollect.weight == Weights.CONTAINER`).

#### Scenario: OptionalWrap emits for Optional target
- **WHEN** `OptionalWrap.bridge(<Dog>, <Optional<Dog>>, ctx)` is invoked
- **THEN** the stream contains one `BridgeStep`
- **AND** the step's `inputType` is `<Dog>` and `outputType` is `<Optional<Dog>>`
- **AND** the step's `weight` equals `Weights.CONTAINER`
- **AND** the step's `scopeTransition` equals `ScopeTransition.PRESERVING`
- **AND** the step's `codegen` renders `Optional.ofNullable(<inputVar>)`

#### Scenario: OptionalWrap declines non-Optional target
- **WHEN** `OptionalWrap.bridge(<Dog>, <Pet>, ctx)` is invoked
- **THEN** the result is `Stream.empty()`

### Requirement: ListWrap built-in

The `percolate-strategies-builtin` module SHALL ship `io.github.joke.percolate.spi.builtins.ListWrap` implementing `Bridge` and annotated `@AutoService(Bridge.class)`.

`ListWrap.bridge(from, to, ctx)` SHALL emit a single `BridgeStep` when `to` is a `List<X>` declared type AND `from` is the element type `X`. The emitted step SHALL have:

- `inputType` = the type argument `X` of `to`.
- `outputType` = `to`.
- `weight` = `Weights.CONTAINER`.
- `codegen` = a lambda rendering `List.of(<input>)`.
- `scopeTransition` = `ScopeTransition.PRESERVING`.
- `elementRole` = `"element"` (default).

This covers the regular-scope `T → List<T>` singleton case. It coexists with `ListCollect` (scope-exit); engine picks via weight tie-break.

#### Scenario: ListWrap emits for List target with singleton element
- **WHEN** `ListWrap.bridge(<Dog>, <List<Dog>>, ctx)` is invoked
- **THEN** the stream contains one `BridgeStep` whose `inputType` is `<Dog>`, `outputType` is `<List<Dog>>`, `scopeTransition` is `PRESERVING`, and `codegen` renders `List.of(<inputVar>)`

#### Scenario: ListWrap declines non-List target
- **WHEN** `ListWrap.bridge(<Dog>, <Pet>, ctx)` is invoked
- **THEN** the result is `Stream.empty()`

### Requirement: SetWrap built-in

The `percolate-strategies-builtin` module SHALL ship `io.github.joke.percolate.spi.builtins.SetWrap` implementing `Bridge` and annotated `@AutoService(Bridge.class)`.

`SetWrap.bridge(from, to, ctx)` SHALL emit a single `BridgeStep` when `to` is a `Set<X>` declared type AND `from` is the element type `X`. The emitted step SHALL have:

- `inputType` = the type argument `X` of `to`.
- `outputType` = `to`.
- `weight` = `Weights.CONTAINER`.
- `codegen` = a lambda rendering `Set.of(<input>)`.
- `scopeTransition` = `ScopeTransition.PRESERVING`.
- `elementRole` = `"element"` (default).

This covers the regular-scope `T → Set<T>` singleton case. Coexists with `SetCollect` (scope-exit); engine picks via weight tie-break.

#### Scenario: SetWrap emits for Set target with singleton element
- **WHEN** `SetWrap.bridge(<Dog>, <Set<Dog>>, ctx)` is invoked
- **THEN** the stream contains one `BridgeStep` whose `inputType` is `<Dog>`, `outputType` is `<Set<Dog>>`, `scopeTransition` is `PRESERVING`, and `codegen` renders `Set.of(<inputVar>)`

#### Scenario: SetWrap declines non-Set target
- **WHEN** `SetWrap.bridge(<Dog>, <Pet>, ctx)` is invoked
- **THEN** the result is `Stream.empty()`

### Requirement: Container strategies registered via AutoService

The `percolate-strategies-builtin` module SHALL ship the following container `Bridge` strategies, each annotated `@AutoService(Bridge.class)`:

- `OptionalUnwrap` (ENTERING, scope-enter from `Optional<T>` into element scope `T`)
- `OptionalWrap` (PRESERVING, regular T → `Optional<T>` via `ofNullable`)
- `OptionalCollect` (EXITING, element-scope T → regular `Optional<T>`)
- `ListWrap` (PRESERVING, regular T → `List<T>` via `List.of`)
- `ListCollect` (EXITING, element-scope T → regular `List<T>` via collectors)
- `SetWrap` (PRESERVING, regular T → `Set<T>` via `Set.of`)
- `SetCollect` (EXITING, element-scope T → regular `Set<T>` via collectors)
- `ArrayCollect` (EXITING, element-scope T → regular `T[]`)
- `IterableUnwrap` (ENTERING, scope-enter from `Iterable<T>` or `T[]` into element scope `T`)

The strategies `OptionalMap`, `ListMap`, `SetMap` SHALL NOT exist after this change (deleted).

#### Scenario: All container strategies declare @AutoService(Bridge.class)
- **WHEN** the sources of the listed container bridges are inspected
- **THEN** each class carries `@AutoService(Bridge.class)`

#### Scenario: Removed container strategies do not exist
- **WHEN** the `strategies-builtin/src/main/java/io/github/joke/percolate/spi/builtins/` directory is inspected
- **THEN** no source file `OptionalMap.java`, `ListMap.java`, or `SetMap.java` exists
- **AND** no `META-INF/services/io.github.joke.percolate.spi.Bridge` entry references those classes after rebuild

## ADDED Requirements

### Requirement: IterableUnwrap built-in

The `percolate-strategies-builtin` module SHALL ship `io.github.joke.percolate.spi.builtins.IterableUnwrap` implementing `Bridge` and annotated `@AutoService(Bridge.class)`.

`IterableUnwrap.bridge(from, to, ctx)` SHALL emit a single `BridgeStep` when:

- `from` is either an `Iterable<X>` declared type (or a subtype: `Collection`, `List`, `Set`, `Queue`, `Deque`, etc.) or an array type `X[]`, AND
- `to` is the element type `X` (`ctx.types().isSameType(to, Containers.elementType(from, ctx))` for iterables, or `ctx.types().isSameType(to, Containers.arrayComponentType(from))` for arrays).

`from` MUST NOT be an `Optional` (Optional handling is owned by `OptionalUnwrap`).

The emitted step SHALL have:

- `inputType` = `from`.
- `outputType` = the element type of `from`.
- `weight` = `Weights.CONTAINER`.
- `codegen` = a pass-through lambda rendering `<input>` (the surrounding iteration code is provided by the future codegen capability's iteration-style strategy).
- `scopeTransition` = `ScopeTransition.ENTERING`.
- `elementRole` = `"element"`.

When `from` is neither an iterable nor an array (or is an `Optional`), or when `to` is not the element type, `IterableUnwrap.bridge` SHALL return `Stream.empty()`.

#### Scenario: IterableUnwrap emits for List source
- **WHEN** `IterableUnwrap.bridge(<List<Dog>>, <Dog>, ctx)` is invoked
- **THEN** the stream contains one `BridgeStep`
- **AND** the step's `inputType` is `<List<Dog>>` and `outputType` is `<Dog>`
- **AND** the step's `scopeTransition` equals `ScopeTransition.ENTERING`
- **AND** the step's `elementRole` equals `"element"`
- **AND** the step's `codegen` renders `<inputVar>` pass-through

#### Scenario: IterableUnwrap emits for Set source
- **WHEN** `IterableUnwrap.bridge(<Set<Dog>>, <Dog>, ctx)` is invoked
- **THEN** the stream contains one `BridgeStep` whose `inputType` is `<Set<Dog>>` and `outputType` is `<Dog>`

#### Scenario: IterableUnwrap emits for array source
- **WHEN** `IterableUnwrap.bridge(<Dog[]>, <Dog>, ctx)` is invoked
- **THEN** the stream contains one `BridgeStep` whose `inputType` is `<Dog[]>` and `outputType` is `<Dog>`

#### Scenario: IterableUnwrap declines Optional source
- **WHEN** `IterableUnwrap.bridge(<Optional<Dog>>, <Dog>, ctx)` is invoked
- **THEN** the result is `Stream.empty()` (Optional is handled by `OptionalUnwrap`)

#### Scenario: IterableUnwrap declines non-iterable source
- **WHEN** `IterableUnwrap.bridge(<Dog>, <Pet>, ctx)` is invoked
- **THEN** the result is `Stream.empty()`

### Requirement: OptionalCollect built-in

The `percolate-strategies-builtin` module SHALL ship `io.github.joke.percolate.spi.builtins.OptionalCollect` implementing `Bridge` and annotated `@AutoService(Bridge.class)`.

`OptionalCollect.bridge(from, to, ctx)` SHALL emit a single `BridgeStep` when `to` is an `Optional<X>` declared type AND `from` is the element type `X`. The emitted step SHALL have:

- `inputType` = `X`.
- `outputType` = `Optional<X>`.
- `weight` = `Weights.CONTAINER`.
- `codegen` = pass-through `<input>` (codegen capability materialises as `<input>.findFirst()` or equivalent).
- `scopeTransition` = `ScopeTransition.EXITING`.
- `elementRole` = `"element"`.

#### Scenario: OptionalCollect emits for Optional target with element source
- **WHEN** `OptionalCollect.bridge(<Dog>, <Optional<Dog>>, ctx)` is invoked
- **THEN** the stream contains one `BridgeStep` whose `inputType` is `<Dog>`, `outputType` is `<Optional<Dog>>`, `scopeTransition` is `EXITING`, and `elementRole` is `"element"`

#### Scenario: OptionalCollect declines non-Optional target
- **WHEN** `OptionalCollect.bridge(<Dog>, <Set<Dog>>, ctx)` is invoked
- **THEN** the result is `Stream.empty()`

### Requirement: SetCollect built-in

The `percolate-strategies-builtin` module SHALL ship `io.github.joke.percolate.spi.builtins.SetCollect` implementing `Bridge` and annotated `@AutoService(Bridge.class)`.

`SetCollect.bridge(from, to, ctx)` SHALL emit a single `BridgeStep` when `to` is a `Set<X>` declared type AND `from` is the element type `X`. The emitted step SHALL have:

- `inputType` = `X`.
- `outputType` = `Set<X>`.
- `weight` = `Weights.CONTAINER`.
- `codegen` = pass-through `<input>` (codegen capability materialises as `.collect(toSet())` or equivalent).
- `scopeTransition` = `ScopeTransition.EXITING`.
- `elementRole` = `"element"`.

#### Scenario: SetCollect emits for Set target with element source
- **WHEN** `SetCollect.bridge(<Dog>, <Set<Dog>>, ctx)` is invoked
- **THEN** the stream contains one `BridgeStep` whose `scopeTransition` is `EXITING` and `elementRole` is `"element"`

#### Scenario: SetCollect declines non-Set target
- **WHEN** `SetCollect.bridge(<Dog>, <List<Dog>>, ctx)` is invoked
- **THEN** the result is `Stream.empty()`

### Requirement: ListCollect built-in

The `percolate-strategies-builtin` module SHALL ship `io.github.joke.percolate.spi.builtins.ListCollect` implementing `Bridge` and annotated `@AutoService(Bridge.class)`.

`ListCollect.bridge(from, to, ctx)` SHALL emit a single `BridgeStep` when `to` is a `List<X>` declared type AND `from` is `X`. Step fields: `inputType = X`, `outputType = List<X>`, `weight = Weights.CONTAINER`, pass-through codegen, `scopeTransition = ScopeTransition.EXITING`, `elementRole = "element"`.

#### Scenario: ListCollect emits for List target with element source
- **WHEN** `ListCollect.bridge(<Dog>, <List<Dog>>, ctx)` is invoked
- **THEN** the stream contains one `BridgeStep` whose `scopeTransition` is `EXITING`

#### Scenario: ListCollect declines non-List target
- **WHEN** `ListCollect.bridge(<Dog>, <Set<Dog>>, ctx)` is invoked
- **THEN** the result is `Stream.empty()`

### Requirement: ArrayCollect built-in

The `percolate-strategies-builtin` module SHALL ship `io.github.joke.percolate.spi.builtins.ArrayCollect` implementing `Bridge` and annotated `@AutoService(Bridge.class)`.

`ArrayCollect.bridge(from, to, ctx)` SHALL emit a single `BridgeStep` when `to` is an array type `X[]` AND `from` is `X`. Step fields: `inputType = X`, `outputType = X[]`, `weight = Weights.CONTAINER`, pass-through codegen, `scopeTransition = ScopeTransition.EXITING`, `elementRole = "element"`.

#### Scenario: ArrayCollect emits for array target with element source
- **WHEN** `ArrayCollect.bridge(<Dog>, <Dog[]>, ctx)` is invoked
- **THEN** the stream contains one `BridgeStep` whose `scopeTransition` is `EXITING`

#### Scenario: ArrayCollect declines non-array target
- **WHEN** `ArrayCollect.bridge(<Dog>, <List<Dog>>, ctx)` is invoked
- **THEN** the result is `Stream.empty()`

### Requirement: Linear container chain (no diamond)

Any expansion involving the new container built-ins SHALL produce a strictly linear REALISED chain — no diamonds, no parallel "outer" edges, no incoming-only `ElementLocation` leaves, no outgoing-only `ElementLocation` leaves except at the source-parameter-root boundary.

Specifically, for the integration mapper `~/Projects/joke/percolate-integration/mappers/src/main/java/io/github/joke/testing/PersonMapper.java` with its `mapHuman` and `mapAddress` methods, the `*.transforms.dot` view of `mapHuman`'s expansion for `tgt[addresses]:Optional<Set<HA>>` SHALL trace the following linear path through REALISED edges:

```
src[person]:Person
  → src[person.addresses]:List<Optional<Person.Address>>      (GetterPathResolver)
  → elem:Optional<Person.Address>                              (IterableUnwrap, ENTERING)
  → elem:Person.Address                                        (OptionalUnwrap, ENTERING, flatMap in same scope)
  → elem:Human.Address                                         (MethodCallBridge[mapAddress], PRESERVING)
  → src[person.addresses]:Set<Human.Address>                   (SetCollect, EXITING)
  → tgt[addresses]:Optional<Set<Human.Address>>                (OptionalWrap, PRESERVING)
  → tgt[]:Human                                                (ConstructorCall slot)
```

Every `elem(...)` node in the chain SHALL have exactly one incoming and exactly one outgoing REALISED edge in the linear sub-chain.

#### Scenario: Integration mapper transforms.dot has the expected linear chain
- **WHEN** the integration mapper is built and `PersonMapper.transforms.dot` is generated
- **THEN** the REALISED subgraph for `tgt[addresses]` matches the linear chain above
- **AND** no REALISED edge in the graph carries `strategyClassFqn` equal to `OptionalMap`, `ListMap`, or `SetMap`
- **AND** no node with `loc instanceof ElementLocation` is an incoming-only or outgoing-only leaf

#### Scenario: No outer container-map shortcut edges
- **WHEN** any container-bearing mapper is expanded
- **THEN** for every `Set<T>` / `List<T>` / `T[]` / `Optional<T>` target reached via the chain pattern (Unwrap → ... → Collect), the only REALISED edge incoming to that container node from a container-typed source is the `*Collect` edge from the element-scope chain
- **AND** no parallel REALISED edge connects the source container directly to the target container with a `*Map`-style label
