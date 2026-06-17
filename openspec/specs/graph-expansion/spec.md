# Graph Expansion Spec

## Purpose

This spec defines the expansion engine that resolves a seeded `MapperGraph` (parameter/return-root `Value`s plus per-level goal specs) into a fully realised bipartite graph of `Value` and `Operation` vertices. Expansion is a **demand work-list** over unsatisfied Values, proceeding target-to-source: each demand asks `ExpansionStrategy` matches for the Operations that could produce it, and each emitted Operation fans out a fresh demand per port.

Satisfaction is **not** computed during expansion — expansion over-emits candidate Operations and drains the work-list. Whether a demand is producible is derived later by the plan-extraction minimum-cost fold: a vertex is reachable iff its cost is finite, with base cases at parameter roots and zero-port Operations (constants). There are no groups, no `GroupOutcome` records, and no cross-group layer.

All expansion-time mutation flows through a single `Applier` interpreting `AddValue`/`AddOperation` deltas emitted by pure expanders, batch-applied at each pass boundary. Candidate search is scope-confined (a method scope, or an Operation's child scope), so sibling-derived Values cannot leak as candidates; graph cycles are well-founded under the extraction cost fold's cycle guard (a Value is never reachable through its own cycle) and are harmless-but-never-chosen during extraction.

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
SHALL enqueue exactly one demand for the method's return type (the return-root `Value`). Parameter
`LEAF` Values SHALL be created lazily on first reference (when an accessor chain bottoms out at them
or a candidate is bound to one), so an unreferenced parameter never enters the graph. There SHALL be
no separate seed stage.

#### Scenario: The graph starts empty and grows by demand

- **WHEN** expansion begins for a mapper
- **THEN** the graph contains no vertices until the return-root demand is enqueued and processed

#### Scenario: An unused parameter is never materialised

- **WHEN** a method declares a parameter no binding ever sources from
- **THEN** no `Value` for that parameter exists in the graph after expansion

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

### Requirement: Per-pass snapshot semantics

Expanders SHALL read a per-pass immutable snapshot of demand and SAT state; deltas produced within a
pass are batch-applied at the pass boundary, so all matches in one pass observe the same state.

#### Scenario: In-pass reads are stable
- **WHEN** two demands are processed in the same pass
- **THEN** both observe the snapshot taken at the start of the pass, regardless of deltas emitted in
  between

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

### Requirement: Frontier matching fans out per port and dedups Operation specs

`FrontierMatcher` SHALL convert each accepted strategy match into one atomic `AddOperation` delta,
fanning out one demand per port, and SHALL deduplicate structurally identical Operation specs by
signature (codegen class, port types, produced Value) per frontier.

#### Scenario: Identical specs collapse
- **WHEN** two strategies (or two passes) emit structurally identical Operation specs at one frontier
- **THEN** only one Operation is added to the graph

### Requirement: Conversion chains are unary Operation chains over deduped Values

A type conversion SHALL be a unary `Operation`; multi-hop conversions compose as chains through
intermediate `Value`s deduped by `(scope, location, type, nullness)`. Reuse-or-synthesize follows
from the identity rule (an existing intermediate is fed, a missing one is minted); chain search is
type-keyed, bounded, and stops at SAT. Reachability needs no dedicated rule: a chain satisfies iff
Horn propagation derives its head from a base case.

#### Scenario: Two-hop conversion synthesizes one intermediate
- **WHEN** `int → Long` requires `int → long → Long`
- **THEN** one intermediate `Value` of type `long` is minted (or reused) and two unary Operations
  chain through it

### Requirement: No silent sourcing — supply is directive-rooted only

Producer chains SHALL originate only from directive-rooted supply: source-path descent driven by a
binding's source path, constants, and conversions over existing supply. There SHALL be no rule that
invents source descent for a port no directive feeds; such a port's Value remains UNSAT by
exhaustion, making its Operation UNSAT.

#### Scenario: Undeclared constructor parameter starves
- **WHEN** a constructor declares a port `country` and no directive declares a `country` binding
- **THEN** the port Value acquires no producers and the constructor Operation is UNSAT

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
