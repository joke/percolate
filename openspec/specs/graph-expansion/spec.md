# Graph Expansion Spec

## Purpose

This spec defines the expansion engine that resolves a mapper's abstract methods into a fully realised bipartite graph of `Value` and `Operation` vertices. Expansion **self-seeds** one return-root demand per abstract method into an **empty** graph (parameter `Value`s are materialised lazily on first reference, goal specs are read per scope) and runs a **demand work-list** over unsatisfied Values, proceeding target-to-source: each demand asks `ExpansionStrategy` matches for the Operations that could produce it, and each emitted Operation fans out a fresh demand per port.

Satisfaction is **not** computed during expansion — expansion over-emits candidate Operations and drains the work-list. Whether a demand is producible is derived later by the plan-extraction minimum-cost fold: a vertex is reachable iff its cost is finite, with base cases at parameter roots and zero-port Operations (constants). There are no groups, no `GroupOutcome` records, and no cross-group layer.

All expansion-time mutation flows through a single `Applier` interpreting `AddValue`/`AddOperation` deltas; each delta is applied immediately and each demanded Value is expanded at most once (a visited set). Candidate search is scope-confined (a method scope, or an Operation's child scope), so sibling-derived Values cannot leak as candidates; graph cycles are well-founded under the extraction cost fold's cycle guard (a Value is never reachable through its own cycle) and are harmless-but-never-chosen during extraction.

## Requirements

### Requirement: Resolution mode dispatches the single work-list

The work-list SHALL dispatch each demanded `Value` on a **resolution mode** set when the demand is
created and derivable from its `Location` (kind + access-path length). There SHALL be exactly one
dispatch site and exactly one strategy-invocation site; there SHALL be no eager, forward, or
otherwise separate descent engine, and no second strategy dispatch.

- `FREE` (a `TargetLocation` demand or a conversion intermediate): the full strategy set is run; each
  emitted Operation's ports become new demands.
- `ACCESS` (a multi-segment `SourceLocation` demand): only accessor strategies may produce it; the
  resolved accessor Operation re-demands the **parent** path. Assembly strategies SHALL NOT fire on an
  `ACCESS` demand.
- `LEAF` (a single-segment `SourceLocation` parameter, or an element root): a base case — no
  expansion.
- `CONSTANT`: only a constant strategy may produce it.

#### Scenario: A multi-segment source demand expands via the work-list, not a descent engine

- **WHEN** a `Value` at `SourceLocation[p, address, street]` is demanded
- **THEN** an accessor strategy emits the `getStreet()` Operation and a demand for
  `SourceLocation[p, address]` is enqueued on the same work-list
- **AND** no eager whole-path materialisation, no `descend`/`resolveAccessor` component, and no
  separate `descended` memo participates

#### Scenario: Assembly does not fire on an ACCESS demand

- **WHEN** an `ACCESS` (multi-segment source) demand whose type has a public constructor is processed
- **THEN** `ConstructorCall` does not emit an Operation for it; only accessor strategies may

#### Scenario: A single-segment source demand is a leaf

- **WHEN** a `Value` at `SourceLocation[p]` (a parameter) is processed
- **THEN** it is treated as a base case and is not expanded

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

### Requirement: All graph mutation flows through the Applier

All graph mutation SHALL flow through the single `Applier`, which interprets `AddValue`/`AddOperation`
deltas. This includes the **initial root demands** (each enqueued return-root `Value` is created as a
bare `AddValue` through the `Applier`) and lazily-materialised parameter `LEAF`s. No stage SHALL
mutate the graph directly via `MapperGraph.valueFor(...)` or any other bypass; the previous seed-time
`valueFor` carve-out is removed.

#### Scenario: Expanders never mutate directly
- **WHEN** expansion sources are inspected
- **THEN** no expander or strategy invokes a graph mutation method; only the `Applier` does

#### Scenario: No stage bypasses the Applier to seed the graph
- **WHEN** the processor's stage sources are inspected
- **THEN** no stage calls `MapperGraph.valueFor(...)` (or another mutation) directly to pre-populate
  the graph; root demands are landed as `AddValue` deltas through the `Applier`

### Requirement: Each demanded Value is expanded at most once

The work-list SHALL be a FIFO queue of `Value` demands drained one at a time; each delta SHALL be
applied immediately through the `Applier` and each Value SHALL be expanded at most once, guarded by a
visited set keyed on Value identity. There is no per-pass batched snapshot and no convergence loop —
the queue drains once and expansion ends when it empties.

#### Scenario: A re-demanded Value is not expanded twice
- **WHEN** two Operations both demand a Value of the same `(scope, location, type, nullness)` identity
- **THEN** that Value is dequeued and expanded exactly once; the second reference reuses the existing
  Value without re-running strategies

### Requirement: Demand work-list over Values

Expansion SHALL be driven by a work-list of `Value` demands, processed target-to-source: a demanded
`Value` asks "what produces this?", and each strategy match emits an `Operation` whose ports become
new demands. Expansion NEVER walks forward from sources. The work-list SHALL terminate because Values
are deduplicated by `(scope, location, type, nullness)` identity and each is expanded at most once
over a finite location/type space — expansion **over-emits** candidate producers and computes no
satisfaction predicate. Whether a demand is ultimately producible is decided later, by the
plan-extraction cost fold (`reachable ⟺ finite cost`).

#### Scenario: Demands expand target-to-source
- **WHEN** the demand `ret : Human.Address` is processed
- **THEN** matching emits producer Operations for `ret`, and the Operations' port Values join the
  work-list as new demands

#### Scenario: Expansion terminates without a convergence failure mode
- **WHEN** no strategy can produce some remaining demand
- **THEN** expansion ends with that demand having no producer; there is no "did not converge" outcome,
  and the demand is reported unreachable only at extraction (infinite cost)

### Requirement: The driver fans out per port and dedups Operation specs

The driver SHALL convert each accepted strategy match into one atomic `AddOperation` delta, fanning
out one demand per port, and SHALL deduplicate structurally identical Operation specs by signature
(`label`, produced output type, and port `name:type:nullness` tuples) per demand before landing them.

#### Scenario: Identical specs collapse
- **WHEN** two strategies emit structurally identical Operation specs for one demand
- **THEN** only one Operation is added to the graph

### Requirement: Conversion chains are unary Operation chains over deduped Values

A type conversion SHALL be a unary `Operation`; multi-hop conversions compose as chains through
intermediate `Value`s deduped by `(scope, location, type, nullness)`. When a port finds no in-scope
candidate of its type, the driver mints a fresh intermediate `Value` at the output location and
re-demands it (reuse-or-synthesize follows from the identity rule: an existing intermediate is fed, a
missing one is minted). Reachability needs no dedicated rule: a chain is reachable iff the
plan-extraction cost fold derives a finite cost for its head from a base case.

#### Scenario: Two-hop conversion synthesizes one intermediate
- **WHEN** `int → Long` requires `int → long → Long`
- **THEN** one intermediate `Value` of type `long` is minted (or reused) and two unary Operations
  chain through it

### Requirement: No silent sourcing — supply is directive-rooted only

Producer chains SHALL originate only from directive-rooted supply: source-path descent driven by a
binding's source path, constants, and conversions over existing supply. There SHALL be no rule that
invents source descent for a port no directive feeds; such a port's Value remains unreachable by
exhaustion (infinite extraction cost), making its Operation unreachable.

#### Scenario: Undeclared constructor parameter starves
- **WHEN** a constructor declares a port `country` and no directive declares a `country` binding
- **THEN** the port Value acquires no producers and the constructor Operation is unreachable

### Requirement: Assembly is gated by the declared-bindings goal spec

Assembly strategies SHALL interpret the demand's declared bindings (`{child name → directive}`) at
Operation-emission time. For constructors (all parameters mandatory) the gate is exact consumption:
a constructor is a candidate iff its parameter-name set equals the declared-children name set. A
zero-parameter constructor is therefore never a candidate when bindings are declared — vacuous SAT
cannot drop user mappings.

#### Scenario: Subset constructor rejected at emission
- **WHEN** `Address()` and `Address(int number, String street)` exist and `number`, `street` are
  declared
- **THEN** only the two-parameter constructor is emitted as an Operation

#### Scenario: Overloaded constructors coexist structurally
- **WHEN** `Address(int number, String street)` and `Address(long number, String street)` both pass
  the gate
- **THEN** both Operations are emitted, sharing the `street:String` port Value, with distinct
  `number:int` / `number:long` port Values, and plan extraction selects between them

### Requirement: Directive context travels with the demand

The binding `Directive` in effect SHALL be carried by the demand context on the work-list, never
stamped on a `Value` (deduped intermediates are shared across bindings). Strategies read per-binding
configuration from the demand context.

#### Scenario: Shared intermediate carries no directive
- **WHEN** two bindings' conversion chains share a deduped intermediate Value
- **THEN** the Value holds no directive and each binding's strategies observe their own demand
  context

### Requirement: The driver is a pure work-list; the engine builds no Operations

The expansion driver SHALL be a single uniform work-list: a demanded `Value` is turned into one
strategy query round (`run(all strategies, demand)`), each accepted match lands atomically as an
`AddOperation`, and each landed Operation's ports are enqueued as new demands. The driver SHALL NOT
contain a per-supply-mode branch (no `assembly` / `bridge` split) and SHALL NOT hand-build any
`Operation` itself — every plan Operation, **including nullness crossings and source accessors**,
originates from an `ExpansionStrategy` match. Misfires are prevented structurally by emission-time
gating (assembly strategies on the declared-bindings goal spec; conversions on a candidate type
match), not by a routing branch in the driver.

#### Scenario: One uniform query round per demand
- **WHEN** any target `Value` is demanded
- **THEN** the driver runs the full strategy set against the demand once and enqueues every emitted
  Operation's ports as demands, with no assembly-versus-bridge branch selecting the strategy set

#### Scenario: The driver constructs no Operation directly
- **WHEN** the expansion driver source is inspected
- **THEN** it builds no `Operation`/codegen by hand (no driver-resident `requireNonNull`/`coalesce`
  emission, no eager source-descent component); every landed Operation came from a strategy

### Requirement: A port is a demand; matchmaking is not the driver's job

Binding an Operation's port SHALL be expressed as enqueuing a demand for a `Value` of the port's
type and nullness at the appropriate location; the driver SHALL NOT perform candidate-match-or-
synthesize matchmaking. A port that no strategy can produce remains unreachable by exhaustion (its
Operation unreachable) — there is no special "no candidate ⇒ only a zero-port producer" guard,
because "unsatisfied = no producer" already yields it.

#### Scenario: An unsatisfiable port starves without a special guard
- **WHEN** a conversion Operation declares an input port whose type no in-scope source value or
  further strategy produces
- **THEN** the port Value acquires no producer and the conversion Operation is unreachable, with no
  driver-side guard consulted

### Requirement: Operation label comes from the strategy spec, not the codegen class

When the driver lands an `OperationSpec` as an `Operation`, the Operation's `label` SHALL be the
spec's strategy-supplied `label`; the driver SHALL NOT derive any label (or a strategy FQN) from the
codegen handle's runtime class. The accessor handler, which emits accessor Operations directly, SHALL
supply an equivalent typed label (e.g. `getStreet()`).

#### Scenario: Landed Operation carries the spec's label
- **WHEN** the driver lands a `WidenPrimitive` spec whose `label` is `int→long`
- **THEN** the resulting `Operation.label` is `int→long`
- **AND** no `$$Lambda` codegen class name appears in the Operation's label

#### Scenario: Accessor operations are labelled by their access
- **WHEN** the work-list resolves the `street` segment of a source path via a getter
- **THEN** the landed accessor Operation's label is the access form (e.g. `getStreet()`), not a
  codegen class name

### Requirement: Type-variable ports are sourced by grounding-by-match

When an `OperationSpec` port carries a type variable, the driver SHALL source it by **matching** the port type against the in-scope source `Value`s, grounding the variable to each matching source's concrete type, substituting it across the Operation's output and child scope, and landing one concrete Operation per match through the `Applier`. The driver SHALL NOT enqueue an unbound type. This is the same port-sourcing step as for a concrete port, generalised from exact-type matching to unification; it remains strictly target→source (`never_forward` holds) and over-emit-and-prune.

#### Scenario: A type-variable port grounds and lands concretely
- **WHEN** the driver sources a `Set<A>` port with a `Set<Person>` source in scope
- **THEN** it grounds `A := Person`, substitutes into the Operation (output and child scope), and lands a concrete `Set<Person> → …` Operation via the Applier
- **AND** it never enqueues a Value typed `Set<A>`

#### Scenario: Grounding-by-match preserves target-driven order
- **WHEN** grounding-by-match sources a type-variable port
- **THEN** the produced concrete inputs are re-demanded target→source like any other port; no forward sweep from sources occurs

### Requirement: Grounding's match set is widened by SourceProjections

Before grounding a type-variable port, the driver SHALL widen its match set: in addition to the in-scope source `Value` types it SHALL include every registered `SourceProjection`'s one-step view of each in-scope source. Unification then proceeds **unchanged** against the widened set. This is what bootstraps a cross-kind pipeline — a `Stream<A>` port has no direct `Stream` source but grounds against the `Stream<X>` a `List<X>` source projects to — while keeping the engine type-agnostic (it calls `project` generically and names no kind) and preserving `never_forward` (a projection is a declarative one-step view of an in-scope source, not a forward sweep; the grounded concrete type enters the work-list as an ordinary target-driven demand).

#### Scenario: A cross-kind port grounds via a projection
- **WHEN** a `Stream<A>` port is sourced with only a `List<Optional<Paw>>` source in scope and a collection→stream `SourceProjection` registered
- **THEN** the projection contributes `Stream<Optional<Paw>>`, the port grounds `A := Optional<Paw>`, and a concrete `Stream<Optional<Paw>>` (produced by the list's `iterate`) enters the work-list

#### Scenario: With no projections, grounding uses only the raw sources
- **WHEN** no `SourceProjection` is registered
- **THEN** the match set is exactly the in-scope source types, and a cross-kind port with no direct source grounds nothing (additive: concrete-port sourcing is unchanged)

### Requirement: The work-list holds only concrete-typed Values

Every `Value` on the work-list SHALL have a concrete type. The engine SHALL NOT create or demand a `Value` whose type is an unbound type variable; type variables exist only inside `OperationSpec` ports and are grounded before any demand is enqueued.

#### Scenario: No Value is created for a type variable
- **WHEN** expansion runs to completion
- **THEN** no `Value` in the graph has a type variable as its type
