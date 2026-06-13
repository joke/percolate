## ADDED Requirements

### Requirement: Demand work-list over Values

Expansion SHALL be driven by a work-list of unsatisfied `Value` demands, processed target-to-source:
a demanded `Value` asks "what produces this?", and each strategy match emits an `Operation` whose
ports become new demands. Expansion NEVER walks forward from sources. The work-list iterates to a
fixed point; because the clause system is monotone, the fixed point always exists and is reached in
a bounded number of passes.

#### Scenario: Demands expand target-to-source
- **WHEN** the demand `ret : Human.Address` is processed
- **THEN** matching emits producer Operations for `ret`, and the Operations' port Values join the
  work-list as new demands

#### Scenario: Expansion terminates without a convergence failure mode
- **WHEN** no strategy can produce any remaining unsatisfied demand
- **THEN** expansion ends with those demands UNSAT; there is no "did not converge" outcome

### Requirement: Horn SAT propagation

Satisfaction SHALL be computed by unit propagation over definite Horn clauses: a `Value` is SAT iff
at least one producer `Operation` is SAT; an `Operation` is SAT iff all of its port `Value`s are SAT
(and, for a scope-owning Operation, its child return-root is SAT); base cases are parameter-root
`Value`s and zero-port `Operation`s. SAT is memoized engine-side as a vertex predicate; no group
outcome records exist. Derivations are well-founded: a `Value` never becomes SAT through a cycle
containing itself.

#### Scenario: Operation SAT requires all ports
- **WHEN** an Operation has ports fed by one SAT and one UNSAT Value
- **THEN** the Operation is UNSAT

#### Scenario: Cyclic producers cannot self-satisfy
- **WHEN** box and unbox Operations form a cycle between `x:int` and `x:Integer` with no acyclic
  derivation from a parameter root
- **THEN** both Values remain UNSAT

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

## MODIFIED Requirements

### Requirement: All graph mutation flows through the Applier

All expansion-time mutation SHALL flow through the single `Applier`, which interprets
`AddValue`/`AddOperation` deltas emitted by pure expanders. Until the well-foundedness verification
gate (design D10) is closed, the Applier SHALL run an assertion-only cycle check (detect and report,
no rollback); after the gate it is removed.

#### Scenario: Expanders never mutate directly
- **WHEN** expansion sources are inspected
- **THEN** no expander or strategy invokes a graph mutation method; only the Applier does

### Requirement: Per-pass snapshot semantics

Expanders SHALL read a per-pass immutable snapshot of demand and SAT state; deltas produced within a
pass are batch-applied at the pass boundary, so all matches in one pass observe the same state.

#### Scenario: In-pass reads are stable
- **WHEN** two demands are processed in the same pass
- **THEN** both observe the snapshot taken at the start of the pass, regardless of deltas emitted in
  between

## REMOVED Requirements

### Requirement: ExpandStage runs ExpansionPhases in declared order
**Reason**: The phase orchestration was group-shaped; the engine is a single demand work-list.
**Migration**: See ADDED "Demand work-list over Values".

### Requirement: ExpansionPhase contract
**Reason**: Folded into the work-list engine.
**Migration**: See ADDED "Demand work-list over Values".

### Requirement: GroupExpander interface contract
**Reason**: No groups; expanders operate on demands.
**Migration**: See ADDED "Frontier matching fans out per port and dedups Operation specs".

### Requirement: DeltaBundle atomicity
**Reason**: Restated over the bipartite mutation vocabulary.
**Migration**: See `graph-model` ADDED "Graph deltas are AddValue and AddOperation" and MODIFIED
"All graph mutation flows through the Applier" (assertion-only cycle check per design D10 gate).

### Requirement: ExpandGroupsPhase drives per-group expansion
**Reason**: No groups to drive.
**Migration**: See ADDED "Demand work-list over Values".

### Requirement: Cross-group fixed-point loop
**Reason**: The fixed point is the Horn propagation itself; there is no cross-group layer.
**Migration**: See ADDED "Horn SAT propagation".

### Requirement: Base-case SAT for parameter-root slots
**Reason**: Restated as a Horn base case (plus zero-port Operations).
**Migration**: See ADDED "Horn SAT propagation".

### Requirement: Candidate search scoped to current group's view
**Reason**: Group views are gone; candidate search is scope-confined (method or child scope).
**Migration**: Scope confinement is owned by `graph-model` "Scope tree and child-scope ownership".

### Requirement: Single round-robin strategy invocation per pass
**Reason**: Restated implicitly by per-pass snapshot + work-list; no group-round-robin remains.
**Migration**: See ADDED "Per-pass snapshot semantics".

### Requirement: Driver deduplicates structurally-identical emitted steps
**Reason**: Restated over Operation specs.
**Migration**: See ADDED "Frontier matching fans out per port and dedups Operation specs".

### Requirement: Intent-driven fold versus subgroup at the single mutation site
**Reason**: The CONVERSION/BOUNDARY fork existed to choose between edge-fold and sub-group; both
cases are uniformly "add an Operation".
**Migration**: See ADDED "Conversion chains are unary Operation chains over deduped Values".

### Requirement: Conversion folding makes round-trips structural cycles
**Reason**: Cycles are permitted in the graph and harmless: Horn derivations are well-founded.
**Migration**: See ADDED "Horn SAT propagation" (cyclic producers cannot self-satisfy).

### Requirement: Conversion-chain satisfaction is base-case reachability
**Reason**: Subsumed by Horn propagation.
**Migration**: See ADDED "Conversion chains are unary Operation chains over deduped Values".

### Requirement: Conversion expansion is type-keyed, bounded, and stops at SAT
**Reason**: Restated over Operations.
**Migration**: See ADDED "Conversion chains are unary Operation chains over deduped Values".

### Requirement: Directive propagation onto synthesized conversion nodes
**Reason**: Directives never live on vertices (deduped intermediates would collide).
**Migration**: See ADDED "Directive context travels with the demand".

### Requirement: Slot Nodes are typed at producer commit
**Reason**: Values are minted typed (identity includes type and nullness); no late typing of slots.
**Migration**: See `graph-model` ADDED "Value identity and dedup".

### Requirement: Multi-fire per frontier; parallel sub-groups coexist
**Reason**: Multi-fire survives structurally: multiple producer Operations on one Value.
**Migration**: See ADDED "Assembly is gated by the declared-bindings goal spec" (overloads coexist)
and `plan-extraction`.

### Requirement: GroupOutcome records per-group SAT/UNSAT verdicts
**Reason**: SAT is a memoized vertex predicate.
**Migration**: See ADDED "Horn SAT propagation"; diagnostics in `realisation-validation`.

### Requirement: DirectiveBindingExpander root typing and direct-assign gating
**Reason**: Directive-binding groups are gone; a binding is ordinary demand expansion with the
directive in the demand context.
**Migration**: See ADDED "Directive context travels with the demand".

### Requirement: Directive-binding declared target type pinned by the assembly producer
**Reason**: Port types are declared on the Operation; no pinning race exists.
**Migration**: See `graph-model` ADDED "Operation vertex type".

### Requirement: Assembly binds only directive-declared inputs
**Reason**: Restated as the goal-spec gate plus structural no-silent-sourcing.
**Migration**: See ADDED "Assembly is gated by the declared-bindings goal spec" and "No silent
sourcing — supply is directive-rooted only".

### Requirement: Type-divergent overloaded constructors coexist as per-type typed slots
**Reason**: Coexistence is structural (distinct typed port Values; shared type-identical ones).
**Migration**: See ADDED "Assembly is gated by the declared-bindings goal spec".

### Requirement: Satisfiable assembly yields a planned return-root; unsatisfiable assembly is diagnosed
**Reason**: Split between extraction and diagnostics.
**Migration**: Planned return-root: `plan-extraction`. Diagnosis: `realisation-validation`.

### Requirement: Deterministic cost-driven constructor selection among satisfiable overloads
**Reason**: Selection is owned by extraction.
**Migration**: See `plan-extraction` ADDED "Deterministic tie-breaking" and "Bottom-up cost
extraction".
