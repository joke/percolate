# Notes — future plans this change deliberately leaves room for

This file captures forward-looking design notes that are out of scope
for `add-container-expansion` but whose shape this change MUST
preserve so they remain additive. Anything here is a candidate for a
future OpenSpec change.

## 1. `Map<K, V>` and other multi-role containers

### What ships today that supports Map

The infrastructure prerequisites for Map are intentionally built now
in service of single-role containers. Map (and any other key/value-
shaped container, e.g. `Multimap`, `Pair`, `BiMap`) lights up as a
**purely additive** change when it ships.

What today's change reserves for Map:

- **`ElementLocation.role`** — the discriminator field exists from
  day one. `Map` ships `ElementLocation("key")` and
  `ElementLocation("value")` element scopes. The default role
  `"element"` is used by every v1 container; Map's two roles slot in
  without changing the `ElementLocation` type or its `segment()`
  rule.
- **`BridgeStep.elementSeeds`** is a `List<ElementSeed>`, not a
  single `Optional<ElementSeed>`. A `MapMap` strategy emits two
  entries: `ElementSeed("key", K1, K2)` and
  `ElementSeed("value", V1, V2)`. The driver's element-seed
  emission loop handles arbitrary counts.
- **`Node.id()`** for element nodes includes the role and the type,
  so a key node and a value node parented by the same outer Map node
  produce distinct ids.

### The MapMap strategy when it ships

The future `MapMap` built-in:

```
emits BridgeStep:
  in           = Map<K1, V1>
  out          = Map<K2, V2>
  weight       = Weights.CONTAINER       (or a new band if needed)
  codegen      = throws until codegen capability widens
  elementSeeds = [
                  ElementSeed("key",   K1, K2),
                  ElementSeed("value", V1, V2),
                ]
```

The driver allocates four element nodes (two per outer endpoint —
parent=input with `role=key`, parent=input with `role=value`,
parent=output with `role=key`, parent=output with `role=value`) and
emits two SEEDs: `key → key` and `value → value`. The outer fixed-
point loop resolves both sub-conversions independently. Codegen
(future) renders something like:

```java
source.entrySet().stream().collect(toMap(
    e -> ⟦key path applied to e.getKey()⟧,
    e -> ⟦value path applied to e.getValue()⟧
));
```

### MapWrap (single entry → Map)

Optional but symmetric to `ListWrap`/`SetWrap`. A `MapWrap`
strategy would accept `to = Map<K, V>` and `from = Map.Entry<K, V>`
(or two parallel inputs — but that's `GroupTarget`-shaped, not
`Bridge`-shaped, so defer). Skip in the Map change unless a real
use case emerges.

### MapUnwrap

Not meaningful for `Map<K, V>` directly. A `Map` has many entries;
"unwrap to a single (K, V)" needs a policy (which entry?). Skip.

### Cross-container including Map

`ListMap` and `SetMap` could accept `Map<K, V>` as input via
`map.entrySet()`, but that's an odd default (collecting a List of
Entries from a Map). Leave it off the v1 input-shape list for
`ListMap`/`SetMap`; users wanting that conversion can write a
custom strategy or pre-flatten with a `@Map` directive.

`MapMap` should accept other map-shaped inputs (e.g.,
`SortedMap → Map`, `NavigableMap → Map`) similarly to how `ListMap`
accepts `Iterable` and friends. The role-by-role iteration model
generalises cleanly.

### Diagnostics for Map

Raw `Map` (no type arguments) and partial-wildcard `Map<K, ?>` /
`Map<?, V>` should likely be a diagnostic. The Tier-3 path-shape
checks haven't yet engaged for containers in general; once they do,
Map's two-role nature should be one of the things they check.

## 2. Other key/value or multi-role containers

The role discriminator pattern generalises beyond `Map<K, V>`:

- **`Pair<A, B>`** / **`Tuple2<A, B>`** (from third-party libs like
  Vavr, JOOL): roles `"first"` and `"second"`.
- **`Either<L, R>`** (Vavr): two parallel paths, but they're
  *alternative* not *both-required*. This is structurally different
  — the codegen needs a runtime branch, not a parallel composition.
  May need a different model than element-scope SEEDs (perhaps a
  new edge kind `EITHER_OF` or a strategy-side branch indicator).
  Notes-only for now.
- **`Try<T>` / `Result<T, E>`**: also alternative-shape (success vs.
  failure). Same caveat as `Either`. The Wrap/Unwrap pattern works
  if the strategy decides at codegen time to swallow the error case;
  a clean Try-aware design needs more thought.
- **`Multimap<K, V>` (Guava)**: a Map whose value is a Collection.
  Two-role like Map but the value role is iterable. The
  element-seed emission rule extends to "value role of a Multimap is
  a `List`/`Set`/`Collection`-shaped element scope nested inside the
  outer Map-value scope." That nests cleanly under today's model.

## 3. Codegen seam — what the next change has to do

Container `Map`-shaped strategies (`OptionalMap`, `ListMap`,
`SetMap`) emit codegen lambdas that throw
`UnsupportedOperationException`. The codegen capability — when it
ships — has to widen the rendering contract so the lambdas can
inline inner-path codegen.

Concrete options (to be decided in the codegen change, not here):

- **Widen `EdgeCodegen`** with a default-throws second method like
  `CodeBlock renderWith(VarNames, IncomingValues, Map<String,
  CodeBlock> innerByRole)`. The renderer walks the realised inner
  subgraph for each role, recursively renders the path's CodeBlock,
  and passes the result. Container `Map` strategies override the
  new method; others ignore it.
- **Make `IncomingValues` aware of element-scope inputs.** A
  container-map edge's "input" arguably IS a per-element value; the
  rendered code substitutes that into the codegen template. The
  renderer could expose an `IncomingValues.elementForRole(role)`
  yielding a `CodeBlock` of the element path. Less invasive on
  `EdgeCodegen`; more semantically loaded.
- **Two-phase render**: outer pass renders all non-container edges;
  inner pass walks element subgraphs and threads them into their
  parent containers' codegen templates.

The graph shape this change ships is the contract — codegen has to
work with parent-linked element nodes and element-scope REALISED
paths. Any of the above renderer designs satisfies that contract.

## 4. Stream / Array targets

Skipped in v1. A future change could add:

- `StreamWrap` / `StreamMap` if `Stream<T>` becomes a common
  `@Mapper` return type.
- `ArrayWrap` / `ArrayMap` if arrays show up as targets (unlikely
  for bean mappers).

The codegen templates for arrays / streams differ from collections
(no `.collect(...)` for arrays; `IntStream`/`LongStream` for
primitive arrays). These warrant their own thinking. Defer.

## 5. Null / empty / default policy

Today:

- `OptionalUnwrap` emits `.orElse(null)`. This loses information
  when the Optional is empty.
- `OptionalWrap` emits `Optional.ofNullable($1)`. Null inputs become
  empty Optionals.
- Collection wraps (`List.of($1)`, `Set.of($1)`) **throw on null**
  per `List.of` / `Set.of` semantics. This is a footgun.
- Collection maps preserve null elements iff the underlying
  collection allows them.

The user has said null/default handling will be a future change
driven by `@Nullable` / `@Default` annotation enrichment. The shape
the codegen capability has to support:

- `@Nullable` on a source field/method allows the value to be null
  on read; codegen for downstream edges may emit a presence check.
- `@Default(value = "...")` provides a fallback value when the
  source is null/empty.
- Container `Wrap` codegen could switch between `Optional.of` and
  `Optional.ofNullable` based on whether the input is annotated
  `@Nullable`.
- Container `Unwrap` codegen could switch between `.orElse(default)`
  and `.orElseThrow(...)` based on annotation presence.

For collection wraps, `List.of` should likely become
`Collections.singletonList` (null-permissive) under a `@Nullable`
input, or `List.of` (null-rejecting) otherwise. Same for `Set`.

These choices are codegen-time, not expansion-time. The graph shape
this change ships doesn't constrain them.

## 6. Raw and wildcard containers

`ListMap.bridge(<List>, <List<Pet>>, ctx)` (raw `List` source)
returns `Stream.empty()` today because `Containers.typeArgument`
isn't defined for raw types and the strategy doesn't handle them.
`<List<? extends Dog>>` is in a grey zone — Java captures the
wildcard, but the captured type may not equal the strategy's
expected element type.

A future Tier-3 diagnostic should likely:

- Detect raw containers in `@Mapper` method signatures and emit a
  warning with a "use a parameterised type" message.
- Detect partial wildcards (`Map<K, ?>`) and similarly warn.

Until then, raw and partial-wildcard containers silently fail to
match — the user sees a "no path" diagnostic rather than a "raw
container" diagnostic.

## 7. Per-shape weight bands

`Weights.CONTAINER = 2` is a single-band placeholder. Reasonable
finer-grained bands a future change could introduce:

```
   Weights.WRAP      = 2     bare → container
   Weights.UNWRAP    = 2     container → bare
   Weights.MAP       = 3     container<A> → container<B>  (iterating)
   Weights.MAP_CROSS = 4     containerA<A> → containerB<B> (iterating + collecting to different shape)
```

These would matter at Dijkstra-at-codegen-time when multiple paths
exist between the same nodes. v1 has no consumer; the values are
documentary. Bands are an additive change.

## 8. Custom container strategies

Users can ship their own container `Bridge` strategies for project-
specific containers (`Result<T>`, `Either<L, R>`, `Future<T>`, the
local-codebase `Box<T>`, etc.). The pattern:

```java
@AutoService(Bridge.class)
public final class ResultWrap implements Bridge {
    public Stream<BridgeStep> bridge(TypeMirror from, TypeMirror to, ResolveCtx ctx) {
        if (!isResult(to, ctx)) return Stream.empty();
        TypeMirror elem = typeArgument(to, 0);
        EdgeCodegen codegen = (vars, inputs) ->
            CodeBlock.of("$T.ok($L)", Result.class, inputs.single());
        return Stream.of(BridgeStep.simple(elem, to, Weights.CONTAINER, codegen));
    }
}
```

The `Containers` helper is public for exactly this — external
authors reuse `typeArgument` and the `is*` family without rebuilding
them. If user demand grows for "I want my own role-based container,"
the `ElementLocation.role` discriminator is already the right
shape; their strategy emits `ElementSeed("my-role", ...)` and the
driver allocates accordingly. No further SPI work.

## 9. Multiple competing paths and Dijkstra

The graph already lands parallel REALISED edges when multiple
strategies emit equivalent steps. Dijkstra-at-codegen-time path
selection is future; until it ships, codegen picking among parallel
edges is undefined. The weight bands are designed to be Dijkstra-
ready; once a renderer ships, it will walk the realised subgraph
from `tgt[]` backward toward `src[…]` taking the lightest path.

For container paths specifically, a sibling `MethodCallBridge`-
provided method like `List<Pet> convertAll(List<Dog>)` shortcuts
the iteration path. Today both edges land in the graph. Dijkstra
should prefer the method-call shortcut because its weight is
`Weights.METHOD = 1` vs. the container-map's `Weights.CONTAINER = 2`
plus the inner method-call edge's weight inside the element scope.

## 10. Diagnostic enrichment

Today, a container conversion that has no resolvable path produces a
generic "no path" diagnostic at element-scope endpoints — possibly
naming synthetic intermediate types the user did not author (e.g.,
"no path from `GR` to `Pet`"). The Tier-3 path-shape diagnostic
enrichment that the prior `add-method-call-bridge` change noted
remains an unsolved problem; containers exacerbate it.

A future Tier-3 enrichment that renders chain traces could
specifically walk the element scopes and produce messages like:

```
Cannot map List<Optional<GoldenRetriever>> to Optional<List<Pet>>:
  outer:  List<Optional<GR>> → List<Pet> via ListMap (OK)
          List<Pet> → Optional<List<Pet>> via OptionalWrap (OK)
  inner element scope (parent = List<Pet>):
          Optional<GR> → Pet via OptionalUnwrap → ??? (NO PATH from GR to Pet)
                                              └─ no sibling `Pet map(GR)` method,
                                                 no Bridge produces Pet from GR,
                                                 no DirectAssign match.
```

Out of scope here; noted for the diagnostics capability.
