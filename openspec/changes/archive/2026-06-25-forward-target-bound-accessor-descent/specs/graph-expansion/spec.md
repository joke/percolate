# Graph Expansion Spec (delta)

## MODIFIED Requirements

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
- **THEN** expansion ends with that demand having no producer; there is no "did not converge" outcome, and the
  demand is reported unreachable only at extraction (infinite cost)

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

### Requirement: Operation label comes from the strategy spec, not the codegen class

When the driver lands an `OperationSpec` as an `Operation`, the Operation's `label` SHALL be the spec's
strategy-supplied `label`; the driver SHALL NOT derive any label (or a strategy FQN) from the codegen handle's
runtime class. An accessor's `OperationSpec` (from a `descend` match) SHALL carry an equivalent typed label
(e.g. `getStreet()`), landed unchanged by the driver.

#### Scenario: Landed Operation carries the spec's label
- **WHEN** the driver lands a `WidenPrimitive` spec whose `label` is `int→long`
- **THEN** the resulting `Operation.label` is `int→long`
- **AND** no `$$Lambda` codegen class name appears in the Operation's label

#### Scenario: Accessor operations are labelled by their access
- **WHEN** the driver descends the `street` segment of a source path via a getter
- **THEN** the landed accessor Operation's label is the access form (e.g. `getStreet()`), taken from the
  `descend` match's spec, not a codegen class name

## ADDED Requirements

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
