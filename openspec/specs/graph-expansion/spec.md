# Graph Expansion Spec

## Purpose

This spec defines the expansion engine that resolves a mapper's abstract methods into a fully realised bipartite graph of `Value` and `Operation` vertices. Expansion **self-seeds** one return-root demand per abstract method into an **empty** graph (parameter `Value`s are materialised lazily on first reference, goal specs are read per scope) and runs a **demand work-list** over unsatisfied Values, proceeding target-to-source: each demand asks `ExpansionStrategy` matches for the Operations that could produce it, and each emitted Operation fans out a fresh demand per port.

Satisfaction is **not** computed during expansion — expansion over-emits candidate Operations and drains the work-list. Whether a demand is producible is derived later by the plan-extraction minimum-cost fold: a vertex is reachable iff its cost is finite, with base cases at parameter roots and zero-port Operations (constants). There are no groups, no `GroupOutcome` records, and no cross-group layer.

All expansion-time mutation flows through a single `Applier` interpreting `AddValue`/`AddOperation` deltas; each delta is applied immediately and each demanded Value is expanded at most once (a visited set). Candidate search is scope-confined (a method scope, or an Operation's child scope), so sibling-derived Values cannot leak as candidates; graph cycles are well-founded under the extraction cost fold's cycle guard (a Value is never reachable through its own cycle) and are harmless-but-never-chosen during extraction.

## Requirements

### Requirement: Resolution mode dispatches the single work-list

The work-list SHALL dispatch each demanded `Value` at a **single dispatch site**, on a **resolution mode**
derivable from its `Location`, with the driver as the **sole** strategy invoker; there SHALL be no second
strategy-invocation site in any helper (no `resolveAccessor` component) and no separate eager descent engine.

- `FREE` (a `TargetLocation` demand or a conversion intermediate): the full producer (`expand`) strategy set
  is run; each emitted Operation's ports become new demands. When the demand's binding carries a source path,
  the driver **walks that path forward** to materialise its leaf source before binding (see "Forward
  target-bound descent walks a directive path").
- `LEAF` (a single-segment `SourceLocation` parameter, or an element root): a base case — no expansion;
  materialised lazily on first reference.
- `CONSTANT`: only a constant strategy may produce it.

A multi-segment source `Value` is **produced** by forward descent, never demanded: there SHALL be no backward
parent re-demand and no `ACCESS` Value-demand mode, and producer/assembly strategies SHALL NOT fire during
descent — each segment dispatches only the accessor (`descend`) strategy set against the parent type read off
the **already-landed** parent `Value`.

#### Scenario: A multi-segment source path is produced forward, not demanded backward
- **WHEN** the source path `p.address.street` is materialised
- **THEN** the driver descends from the input root `p`: it lands `getAddress()` producing
  `SourceLocation[p, address]`, then lands `getStreet()` producing `SourceLocation[p, address, street]`,
  reading each parent's type off the `Value` landed for the previous segment
- **AND** no `Value` at `SourceLocation[p, address, street]` is demanded and then expanded by re-demanding its
  parent; there is no `descend`/`resolveAccessor` helper and no separate typing memo

#### Scenario: Producer strategies do not fire during descent
- **WHEN** a descent step resolves a segment whose type has a public constructor
- **THEN** `ConstructorCall` does not emit an Operation for it; only accessor (`descend`) strategies may

#### Scenario: A single-segment source is a leaf
- **WHEN** a `Value` at `SourceLocation[p]` (a parameter) is referenced
- **THEN** it is a base-case `LEAF` materialised on first reference and is not expanded

### Requirement: Forward target-bound descent walks a directive path

A directive source path SHALL be materialised by **forward, target-bound descent**: when a directive-bound
target is resolved, the driver walks the named path from its scope-input root toward its leaf, and at each
segment dispatches the accessor (`descend`) strategy set for that one segment against the **concrete parent
type read off the `Value` already landed for the previous segment**. The driver SHALL own the walk (segment
sequencing, landing each accessor through the `Applier`, advancing along the path); the per-segment accessor
decision SHALL remain a myopic strategy. A segment's parent type SHALL NEVER be predicted by a separate forward
typing pass or any second strategy dispatch — it is always a lookup off the landed parent `Value`.

When several accessors match one `(parentType, segment)`, the driver SHALL **over-emit** them all into the
deduped child `Value` and let plan extraction prune by weight; it SHALL NOT select one by
`findFirst`/registration order.

The walk SHALL run before the target's port binds, so each directive's leaf is an in-scope source the target
binds to. A directive-bound target SHALL prefer the leaf descended for **its own** source path, preserving
directive-preference over a same-typed sibling source.

#### Scenario: Each segment reads its parent type off the landed Value
- **WHEN** descending `p.address.street`
- **THEN** `getStreet()` is dispatched against the type of the landed `SourceLocation[p, address]` `Value`, not
  against a separately predicted type, and the accessor for `address` is landed before the accessor for `street`

#### Scenario: An ambiguous segment resolves by cost, not registration order
- **WHEN** a segment `x` is realisable both as `getX()` (weight `STEP_GETTER`) and as a public field `x` (weight
  `STEP_FIELD`)
- **THEN** both accessor Operations are over-emitted into the same child `Value` and plan extraction selects
  `getX()` by weight — never the first match in `ServiceLoader` order

#### Scenario: Directive-preference routes each target to its own descended leaf
- **WHEN** two directives bind the same target type from different paths `a.foo` and `a.bar`
- **THEN** each target binds to the leaf descended for its own source path, not to whichever same-typed source
  has the lower id

### Requirement: Expansion self-seeds root demands from an empty graph

Expansion SHALL begin with an **empty** graph and seed itself: for each abstract mapper method it
SHALL enqueue exactly one demand for the method's return type (the return-root `Value`) and SHALL
**record that seeded `Value` as the method's return root** on the graph, so downstream stages identify
the return root by this recorded identity rather than by location alone. Over-emission later mints
conversion way-points at the **same** return location but with a different type — e.g. a `Stream<E>`
intermediate minted while producing a `List<E>` root — and those way-points are ordinary intermediates,
never return roots.

A scope's **output root is an eager demand; its input roots are lazy sources.** Every base-case input
`LEAF` `Value` — a method parameter *or* a container element root — SHALL be **declared** by its scope
(see "Scopes declare base-case inputs uniformly") and materialised lazily on first reference (when an
accessor chain bottoms out at it, or a port is bound to it), so an unreferenced input never enters the
graph. This SHALL hold uniformly for method scopes and child (element) scopes; the engine SHALL NOT
special-case a scope kind when materialising input roots. There SHALL be no separate seed stage.

#### Scenario: The graph starts empty and grows by demand

- **WHEN** expansion begins for a mapper
- **THEN** the graph contains no vertices until the return-root demand is enqueued and processed

#### Scenario: An unused parameter is never materialised

- **WHEN** a method declares a parameter no binding ever sources from
- **THEN** no `Value` for that parameter exists in the graph after expansion

#### Scenario: An unused element root is never materialised

- **WHEN** a container element transform produces a value that never sources from the element (e.g. it maps each element to a constant)
- **THEN** no element param-root `Value` exists in the graph after expansion, while the element lambda is still generated binding its parameter (see code-generation "Child scopes render as lambda bodies")

#### Scenario: A typed sibling at the return location is not a return root

- **WHEN** producing a container return root `List<E>` over-emits an intermediate `Stream<E>` (and other typed candidates) at the same empty-path return location
- **THEN** only the seeded `List<E>` Value is recorded as the method's return root; the same-location intermediates are not, despite sharing the location

### Requirement: Scopes declare base-case inputs uniformly

Each `Scope` SHALL declare its base-case inputs as a lazy sequence of input declarations, where an
input declaration is a scope-relative `(Location, type, nullness)` — an `AddValue` lacking only its
scope — plus a **name** and a **visibility** (`LOCAL` or `INHERITED`). A `MethodScope` SHALL declare one per
method parameter (a single-segment `SourceLocation`, named after the parameter); a `ChildScope` SHALL declare
its single element input (an `ElementLocation` with the element-in type/nullness); the mapper-root scope SHALL
declare none. There SHALL be exactly **one** declaration type and one declaration stream per scope.

An input declaration SHALL carry its nullness already resolved. `Scope` SHALL NOT accept a nullness-resolving
callback, and SHALL be plain data.

Source-binding and grounding-by-match SHALL consume this declaration **uniformly**, with no
`instanceof` test on the scope kind:

- the in-scope source **types** offered to grounding SHALL include the declared input types, available
  **without** materialising any `Value`;
- a `BY_TYPE` port whose `(type, nullness)` matches a declaration of the **scope's own** declarations SHALL be
  fed by materialising that declaration as a `LEAF` source `Value` on demand, idempotent through the `Value`
  dedup index. Visibility SHALL NOT widen type matching: an ancestor scope's `INHERITED` declaration is **not**
  a `BY_TYPE` candidate in a descendant scope;
- a `BY_NAME` port SHALL be fed by the declaration of that name in the scope's own declarations, or failing
  that in the nearest ancestor scope declaring it `INHERITED`, materialised at that declaration's own location
  so both access paths yield the identical `Value`.

The single intentional exception is the self-call rule, which is method-scope-only **by meaning** (a
named self-call) and remains so — see "A method never calls itself on its own whole parameter".

#### Scenario: Grounding sees a declared input type without materialising it

- **WHEN** grounding-by-match gathers the in-scope source types for a type-variable port in a scope
- **THEN** the scope's declared input types are included even when no corresponding `Value` has yet been materialised

#### Scenario: A matched input declaration is materialised on demand

- **WHEN** a `BY_TYPE` port's `(type, nullness)` matches a scope input declaration and no in-scope source `Value` yet exists
- **THEN** the declaration is materialised as a `LEAF` source `Value` in that scope
- **AND** a second match for the same declaration returns the same `Value` (dedup-idempotent)

#### Scenario: Input sourcing does not branch on scope kind

- **WHEN** the driver sources a port's input in a method scope versus in a child (element) scope
- **THEN** the same declaration-driven path is taken for both, with no `instanceof MethodScope` branch in source-binding, grounding, or accessor-root typing

#### Scenario: An inherited declaration is not a type-match candidate in a descendant

- **WHEN** a child (element) scope sources a `BY_TYPE` port whose type matches an ancestor method scope's `INHERITED` declaration
- **THEN** the ancestor's declaration is not offered, and the port is sourced or missed using only the child scope's own declarations

#### Scenario: One declaration backs both access paths, at the requesting scope

- **WHEN** a parameter declared `INHERITED` is reached once by a `BY_TYPE` port in its own scope and once by a `BY_NAME` port from a child scope
- **THEN** both materialise a `Value` at that declaration's location; a same-scope match dedups to the identical `Value`, while the child scope's match is its own `Value` at that location — a `Dep` edge never crosses a scope boundary, so the same declaration cannot back a single `Value` shared across scopes

### Requirement: A method never calls itself on its own whole parameter

The engine SHALL refuse to land a self-call operation whose argument port binds to the calling method's **own parameter-root `Value`** — the whole, unchanged parameter (`this.m(src)`), which is always degenerate (runtime infinite recursion). A self-call on a **strict sub-part** of the parameter (an accessor result such as `src.getNext()`, or a container element) SHALL remain available, because structural recursion over a shrinking input terminates. The decision SHALL be made **per binding** at land time, not per scope or per site: at one target site the engine over-emits both bindings and the degenerate one is *strictly cheaper* (the parameter-root costs nothing, an accessor costs `ACCESS`), so over-emit + cost-prune alone cannot choose correctly — the binding must be refused outright.

The refusal SHALL be expressed through the engine's single admissibility mechanism (see `demand-constraints`), not through a bespoke guard collaborator, so that the engine has exactly one way to refuse a candidate.

The engine SHALL recognise a self-call structurally by comparing the operation's **call target** (carried on its `OperationSpec`, see `expansion-strategy-spi`) against the current `MethodScope`'s method, matched by signature (name + parameter types); it SHALL NOT infer identity from the spec's `label`. The refusal SHALL apply only when the call target is the scope's own method **and** the bound argument is that scope's parameter-root `Value`; it SHALL NOT apply to a container's per-element transform (a separate child scope), and delegation to a *different* method returning the same type SHALL remain available. There SHALL be no change to the `CallableMethods` / `ResolveCtx` SPI and no loss of strategy myopia.

#### Scenario: A container-return method does not self-bridge
- **WHEN** a method returning a container over a mapped element type is expanded and its own signature appears among the callable candidates
- **THEN** the degenerate self-call binding on the whole parameter is refused and no such operation is landed

#### Scenario: Structural recursion on a sub-part remains available
- **WHEN** a method calls itself on an accessor result or a container element
- **THEN** the operation lands, because the bound argument is not the scope's parameter-root `Value`

#### Scenario: The refusal uses the one admissibility mechanism
- **WHEN** the expansion collaborators are inspected
- **THEN** the self-call rule is expressed as a constraint applied where every other candidate refusal is applied, and no separate guard class exists

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

Expansion SHALL be driven by a work-list of `Value` demands, processed target-to-source: a demanded `Value`
asks "what produces this?", and each strategy match emits an `Operation` whose ports become new demands.
Expansion SHALL NOT perform a **speculative source sweep** — it SHALL NOT enumerate in-scope sources and
explore forward what they could produce. The single forward motion permitted is **target-bound descent**:
walking a directive-given, named source path root→leaf (see "Forward target-bound descent walks a directive
path"), which is bounded by the directive's segments, seeded by a target binding, and materialises only the
named path — it discovers nothing by sweeping, so `never_forward` holds. The work-list SHALL terminate because
Values are deduplicated by `(scope, location, type, nullness)` identity and each is expanded at most once over
a finite location/type space — expansion **over-emits** candidate producers and computes no satisfaction
predicate. Whether a demand is ultimately producible is decided later, by the plan-extraction cost fold
(`reachable ⟺ finite cost`).

#### Scenario: Demands expand target-to-source
- **WHEN** the demand `ret : Human.Address` is processed
- **THEN** matching emits producer Operations for `ret`, and the Operations' port Values join the work-list as
  new demands

#### Scenario: A named source path may be walked forward without a speculative sweep
- **WHEN** a directive's source path `p.address.street` is materialised
- **THEN** only the segments `address` then `street` are descended from `p`; no in-scope source is enumerated to
  discover what it could produce, so `never_forward` holds

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

Producer chains SHALL originate only from **declaration-rooted** supply — supply the developer wrote down.
There are exactly three declared origins:

1. source-path descent driven by a binding's `@Map` source path;
2. constants;
3. an **ambient binding** declared by an `@Ambient` parameter (see `ambient-parameters`).

Conversions over existing supply compose on top of these. There SHALL be no rule that invents supply for a
port no declaration feeds; such a port's Value remains unreachable by exhaustion (infinite extraction cost),
making its Operation unreachable.

An ambient binding is a declared origin precisely because `@Ambient` is written in the mapper signature. This
requirement SHALL NOT be read as licence to source a port from whatever happens to be in scope: binding a
call's arguments by matching the callee's parameter **names** against the enclosing method's parameters, or
by matching their **types** against in-scope values, is supply the developer did not write down and SHALL NOT
be introduced.

#### Scenario: Undeclared constructor parameter starves
- **WHEN** a constructor declares a port `country` and no directive declares a `country` binding
- **THEN** the port Value acquires no producers and the constructor Operation is unreachable

#### Scenario: An ambient binding is a declared origin
- **WHEN** a conversion method's `@Ambient Order order` port is fed from the enclosing scope's ambient binding
- **THEN** the supply is declaration-rooted, because `@Ambient` is declared in the mapper signature

#### Scenario: Name- or type-matched argument sourcing is not introduced
- **WHEN** a callable method declares a parameter that is neither directive-fed nor `@Ambient`
- **THEN** its port is NOT bound by matching the parameter's name or type against in-scope values

### Requirement: A mapper method may declare any number of parameters

A mapper method SHALL be permitted to declare any number of parameters. Each parameter SHALL be an
independently named source root, and each `@Map` directive SHALL name in its source path's first segment the
parameter it descends from. No stage SHALL gate an abstract mapper method on its parameter count.

Two parameters of the **same type** SHALL be legal. Because supply is declaration-rooted and every source
path names its root, a second same-typed parameter introduces no ambiguity in source-path descent.

#### Scenario: Two parameters of different types each root their own directives

- **WHEN** a mapper declares

  ```java
  @Map(target = "customerName", source = "customer.name")
  @Map(target = "street",       source = "address.street")
  OrderView map(Customer customer, Address address);
  ```

- **THEN** `customerName` descends from the `customer` parameter root and `street` from the `address`
  parameter root
- **AND** the generated body is `new OrderView(customer.getName(), address.getStreet())`

#### Scenario: Two parameters of the same type are legal

- **WHEN** a mapper declares

  ```java
  @Map(target = "oldName", source = "before.name")
  @Map(target = "newName", source = "after.name")
  Diff compare(Person before, Person after);
  ```

- **THEN** the mapper compiles and each target descends from the parameter its source path names

#### Scenario: One sub-target draws from two parameter roots

- **WHEN** a mapper declares

  ```java
  @Map(target = "summary.customerName", source = "customer.name")
  @Map(target = "summary.street",       source = "address.street")
  OrderView map(Customer customer, Address address);
  ```

- **THEN** the `summary` sub-target is assembled from both parameter roots
- **AND** the generated body is `new OrderView(new Summary(customer.getName(), address.getStreet()))`

#### Scenario: An unknown source root is still rejected

- **WHEN** a directive's source path names a segment that matches no parameter of its method
- **THEN** the directive is diagnosed and dropped, exactly as for a single-parameter method

### Requirement: Type-matched source selection SHALL be deterministic

Both source-selection sites that choose an in-scope source by **type** rather than by name SHALL be
deterministic — never dependent on hash order or on incidental collection ordering. A second same-typed
parameter is what first makes that choice observable. The two sites are:

- materialising a matching scope input for a port with no directive-pinned source SHALL select by declaration
  order of the scope's input declarations;
- the in-scope source **types** offered to grounding-by-match SHALL be gathered in declaration order,
  declared inputs before discovered graph sources, so the bindings enumerated for a template port are
  produced in a stable order.

Where two candidates remain tied after cost pruning, the engine SHALL resolve the tie by that same
declaration order rather than leaving the selection unspecified.

#### Scenario: Two same-typed parameters select deterministically

- **WHEN** a port with no directive-pinned source matches both parameters of `Diff compare(Person before, Person after)`
- **THEN** the earlier-declared parameter is selected, and the same selection is made on every compilation

#### Scenario: Grounding enumerates bindings in a stable order

- **WHEN** a template port unifies against both parameters of `Diff compare(Person before, Person after)`
- **THEN** both bindings are over-emitted in declaration order and the extracted plan is identical across
  compilations

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

The expansion driver SHALL be a single uniform work-list: a demanded `Value` is turned into one strategy query
round (`run(all strategies, demand)`), each accepted match lands atomically as an `AddOperation`, and each
landed Operation's ports are enqueued as new demands. The driver SHALL NOT contain a per-supply-mode branch (no
`assembly` / `bridge` split) and SHALL NOT hand-build any `Operation` itself — every plan Operation, **including
nullness crossings and source accessors**, originates from an `ExpansionStrategy` match (a producer's `expand`
or an accessor's `descend`). The driver MAY own descent **orchestration** — sequencing a directive path's
segments and landing each accessor through the `Applier` — but the accessor `Operation` itself always comes
from a strategy match, never hand-built. Misfires are prevented structurally by emission-time gating (assembly
strategies on the declared-bindings goal spec; conversions on a candidate type match), not by a routing branch
in the driver.

#### Scenario: One uniform query round per demand
- **WHEN** any target `Value` is demanded
- **THEN** the driver runs the full producer strategy set against the demand once and enqueues every emitted
  Operation's ports as demands, with no assembly-versus-bridge branch selecting the strategy set

#### Scenario: The driver constructs no Operation directly
- **WHEN** the expansion driver source is inspected
- **THEN** it builds no `Operation`/codegen by hand (no driver-resident `requireNonNull`/`coalesce` emission);
  every landed Operation — including each source accessor in a forward descent — came from a strategy match

### Requirement: A port is a demand; matchmaking is not the driver's job

Binding an Operation's port SHALL be a dispatch on the port's **declared axes** (see the expansion-strategy-spi requirement "Port declares an explicit sourcing mode"), never a reconstruction of the port's intent from a name-match or a boolean:

- `SUBTARGET` — the distinct third case: the driver mints a `FREE` demand for a child `Value` at the child location (the parent target path extended by the port name) and enqueues it.
- otherwise the **selector** decides what is looked for — `BY_TYPE` a matching in-scope source `Value`, `BY_NAME` the scope input published under the port's binding name (searching the requesting scope's own declarations, then the nearest ancestor's inherited ones) — and the **on-miss** rule decides what happens when nothing is found: `DECLINE` (the Operation does not apply; the port is never minted), `MINT` (a fresh `FREE` intermediate of the port's type and nullness is minted at the output location and enqueued), or `REQUIRE` (the candidate is refused and the engine records a reason in port vocabulary).

The driver SHALL NOT perform candidate-match-or-synthesize matchmaking, and SHALL NOT reconstruct a port's mode by matching the port name against the demand's declared-children set — that set gates assembly **in the demand** (see "Assembly is gated by the declared-bindings goal spec") and SHALL NOT participate in the engine's binding path. Selecting among matching in-scope sources SHALL preserve directive-preference (see "Forward target-bound descent walks a directive path"): a directive-pinned source is preferred over a same-typed sibling. A port that no in-scope source and no strategy can produce remains unreachable by exhaustion (its Operation unreachable) — there is no special "no candidate ⇒ only a zero-port producer" guard, because "unsatisfied = no producer" already yields it.

#### Scenario: Binding dispatches on the declared axes
- **WHEN** the driver binds a `SUBTARGET` port, or a port whose selector and on-miss rule are `BY_TYPE`/`DECLINE`, `BY_TYPE`/`MINT` or `BY_NAME`/`REQUIRE`
- **THEN** it respectively mints a child-target demand, binds-or-declines an in-scope source, binds-an-in-scope-source-else-mints-an-intermediate, or resolves the binding name and refuses the candidate when it cannot — chosen by the declared axes, not by a name-match

#### Scenario: The declared-children set does not participate in binding
- **WHEN** the driver binds an Operation's ports
- **THEN** it consults each port's declared axes, not whether the port name is in the demand's declared-children set; that set is read only by assembly strategies gating in the demand

#### Scenario: A directive-pinned source is preferred over a same-typed sibling
- **WHEN** a port could bind either the leaf descended for its target's own directive source path or a same-typed sibling source
- **THEN** the driver binds the directive-pinned leaf

#### Scenario: An unsatisfiable port starves without a special guard
- **WHEN** a conversion Operation declares an input port whose type no in-scope source value or further strategy produces
- **THEN** the port Value acquires no producer and the conversion Operation is unreachable, with no driver-side guard consulted

### Requirement: Operation label comes from the strategy spec, not the codegen class

When the driver lands an `OperationSpec` as an `Operation`, the Operation's `label` SHALL be the
spec's strategy-supplied `label`; the driver SHALL NOT derive any label (or a strategy FQN) from the
codegen handle's runtime class. An accessor's `OperationSpec` (from a `descend` match) SHALL carry an
equivalent typed label (e.g. `getStreet()`), landed unchanged by the driver.

#### Scenario: Landed Operation carries the spec's label
- **WHEN** the driver lands a `WidenPrimitive` spec whose `label` is `int→long`
- **THEN** the resulting `Operation.label` is `int→long`
- **AND** no `$$Lambda` codegen class name appears in the Operation's label

#### Scenario: Accessor operations are labelled by their access
- **WHEN** the driver descends the `street` segment of a source path via a getter
- **THEN** the landed accessor Operation's label is the access form (e.g. `getStreet()`), taken from the
  `descend` match's spec, not a codegen class name

### Requirement: Type-variable ports are sourced by grounding-by-match

When an `OperationSpec` port carries a type variable, the driver SHALL source it by **matching** the port type against the in-scope source `Value`s, grounding the variable to each matching source's concrete type, substituting it across the Operation's output and child scope, and landing one concrete Operation per match through the `Applier`. The driver SHALL NOT enqueue an unbound type. This is the same port-sourcing step as for a concrete port, generalised from exact-type matching to unification; it remains strictly target→source (`never_forward` holds) and over-emit-and-prune.

When a type variable carries a **bound** (see `strategy-refusals`), the driver SHALL consult it before binding the variable and SHALL NOT instantiate a spec for a grounding the bound refuses. A refused grounding SHALL contribute a refusal on the demanded `Value` and no Operation, so it never competes on cost and never reaches code generation. An unbounded variable SHALL behave exactly as before.

#### Scenario: A type-variable port grounds and lands concretely
- **WHEN** the driver sources a `Set<A>` port with a `Set<Person>` source in scope
- **THEN** it grounds `A := Person`, substitutes into the Operation (output and child scope), and lands a concrete `Set<Person> → …` Operation via the Applier
- **AND** it never enqueues a Value typed `Set<A>`

#### Scenario: Grounding-by-match preserves target-driven order
- **WHEN** grounding-by-match sources a type-variable port
- **THEN** the produced concrete inputs are re-demanded target→source like any other port; no forward sweep from sources occurs

#### Scenario: A bound refuses a grounding before it competes
- **WHEN** a bounded variable port is offered a source its bound refuses
- **THEN** no concrete Operation is instantiated for that source, and the bound's refusal is recorded on the demanded `Value`

#### Scenario: An unbounded variable grounds as before
- **WHEN** a variable port carries no bound
- **THEN** every declared or array source type in the widened match set grounds it, unchanged

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

### Requirement: A demand records the productions that were refused

A demanded `Value` SHALL carry, beside its producer `Operation`s, the **refusals** recorded against it: the
productions that were considered and found inadmissible, each with a `Subject` and a message. A refusal SHALL
arise from a strategy's refusal `Offer`, from a bound rejecting a grounding, from a `REQUIRE` port that could
not be sourced, or from a contributed constraint.

A refused production SHALL NOT become an `Operation`, SHALL NOT participate in the cost fold, and SHALL NOT be
reachable by plan extraction. The engine SHALL emit no diagnostic during expansion.

#### Scenario: A refused candidate leaves no operation
- **WHEN** any of the four refusal sources rejects a candidate
- **THEN** the demand acquires a refusal and no `Operation` vertex

#### Scenario: Expansion emits no diagnostic
- **WHEN** the expansion stage completes with refusals recorded
- **THEN** no diagnostic has been emitted; the refusals are rendered later by the realisation renderer

### Requirement: A REQUIRE port that cannot be sourced is refused with an engine-worded reason

The engine SHALL refuse the candidate and record a refusal naming the port, the binding name, and (for a type
mismatch) both types, when a port declares on-miss `REQUIRE` and no feeding `Value` can be sourced — because the
binding name is not published in scope, or the published binding's type is not assignable to the port's declared
type. The refusal SHALL be positioned at the operation's call target when it carries one, else at the mapper
type.

The message SHALL be written in port vocabulary and SHALL name no annotation, strategy, or feature. The engine
SHALL NOT re-derive which candidates a strategy considered: it reports only about specs a strategy actually
offered.

#### Scenario: An unpublished binding name is refused
- **WHEN** a candidate declares a `BY_NAME`/`REQUIRE` port for a binding name no enclosing scope publishes
- **THEN** the candidate is refused with a message naming the port and the missing binding name

#### Scenario: A type-incompatible binding is refused
- **WHEN** a `BY_NAME`/`REQUIRE` port's binding is published with a type not assignable to the port's declared type
- **THEN** the candidate is refused with a message naming both types

#### Scenario: The engine reports only about offered specs
- **WHEN** a callable method carrying an unsatisfiable named parameter is never offered by any strategy for a demand
- **THEN** no refusal is recorded for it, because the engine walks no candidate set of its own

#### Scenario: The message names no feature
- **WHEN** a `REQUIRE`-port refusal message is inspected
- **THEN** it names the port, the binding name and the types, and mentions no annotation or strategy
