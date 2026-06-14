# Graph Expansion Spec

## Purpose

This spec defines the expansion engine that resolves a seeded `MapperGraph` (parameter/return-root `Value`s plus per-level goal specs) into a fully realised bipartite graph of `Value` and `Operation` vertices. Expansion is a **demand work-list** over unsatisfied Values, proceeding target-to-source: each demand asks `ExpansionStrategy` matches for the Operations that could produce it, and each emitted Operation fans out a fresh demand per port.

SAT is **Horn unit propagation**: a `Value` is SAT iff any producer is SAT; an `Operation` is SAT iff all its ports (and any child return-root) are SAT; the base cases are parameter roots and zero-port Operations (constants). There are no groups, no `GroupOutcome` records, and no cross-group layer — the fixed point is the Horn propagation itself.

All expansion-time mutation flows through a single `Applier` interpreting `AddValue`/`AddOperation` deltas emitted by pure expanders, batch-applied at each pass boundary. Candidate search is scope-confined (a method scope, or an Operation's child scope), so sibling-derived Values cannot leak as candidates; graph cycles are well-founded under Horn propagation (a Value never SATs through its own cycle) and are harmless-but-never-chosen during extraction.

## Requirements

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
