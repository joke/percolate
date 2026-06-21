## MODIFIED Requirements

### Requirement: Expansion self-seeds root demands from an empty graph

Expansion SHALL begin with an **empty** graph and seed itself: for each abstract mapper method it
SHALL enqueue exactly one demand for the method's return type (the return-root `Value`) and SHALL
**record that seeded `Value` as the method's return root** on the graph, so downstream stages identify
the return root by this recorded identity rather than by location alone. Over-emission later mints
conversion way-points at the **same** return location but with a different type — e.g. a `Stream<E>`
intermediate minted while producing a `List<E>` root — and those way-points are ordinary intermediates,
never return roots. Parameter `LEAF` Values SHALL be created lazily on first reference (when an accessor
chain bottoms out at them or a candidate is bound to one), so an unreferenced parameter never enters
the graph. There SHALL be no separate seed stage.

#### Scenario: The graph starts empty and grows by demand

- **WHEN** expansion begins for a mapper
- **THEN** the graph contains no vertices until the return-root demand is enqueued and processed

#### Scenario: An unused parameter is never materialised

- **WHEN** a method declares a parameter no binding ever sources from
- **THEN** no `Value` for that parameter exists in the graph after expansion

#### Scenario: A typed sibling at the return location is not a return root

- **WHEN** producing a container return root `List<E>` over-emits an intermediate `Stream<E>` (and other typed candidates) at the same empty-path return location
- **THEN** only the seeded `List<E>` Value is recorded as the method's return root; the same-location intermediates are not, despite sharing the location

## ADDED Requirements

### Requirement: A method never satisfies demands in its own method scope

The driver SHALL exclude a method from its own candidate set **throughout its own `MethodScope`**
(every location, not only the return root), because a method consuming its own parameter to produce
its own output is always a degenerate self-call (infinite recursion) — whether at the return root
(`return this.m(param)`), behind an `iterate`/`collect` round-trip at a same-location sibling, or
wrapped at a field (`List.of(this.m(param))`). The exclusion SHALL be a
driver-side, per-scope candidate-visibility concern — a filtered `CallableMethods` view keyed on the
scope's `ExecutableElement`, matched by signature (name + parameter types) — with **no change to the
`CallableMethods` / `ResolveCtx` SPI** and no loss of strategy myopia. It SHALL NOT apply to a
container's per-element transform, which is a separate child (element) scope; and delegation to a
*different* abstract method that returns the same type SHALL remain available.

#### Scenario: A container-return method does not self-bridge

- **WHEN** `List<DAO> mapMany(Set<DTO>)` is expanded and the mapper also declares `DAO mapOne(DTO)`
- **THEN** `mapMany` is not a candidate anywhere in its own scope, so the selected plan is `src.stream().map(this::mapOne).collect(...)`, never `return this.mapMany(src)` nor an `iterate`/`collect` round-trip over `this.mapMany(src)`

#### Scenario: Legitimate self-recursion through a container element is preserved

- **WHEN** a self-similar mapper `Cat mapCat(CatDto)` maps a `List<Cat> children` field from `List<CatDto>` element-wise
- **THEN** the element transform (a child scope) calls `mapCat` recursively — `src.getChildren().stream().map(e -> mapCat(e))` — while the method's own scope never self-bridges

#### Scenario: A scalar self-referential field is reported, not silently recursive

- **WHEN** a mapper `Node mapNode(NodeSrc)` maps a scalar `Node next` field from `src.next` (the recursion would live in the method's own scope, not a child scope)
- **THEN** the exclusion forbids the self-call there too, so the mapper reports a clean "no plan" rather than emitting infinite `return this.mapNode(src)` recursion (making scalar self-reference work is a separate, argument-aware follow-up)
