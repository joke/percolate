## ADDED Requirements

### Requirement: Bottom-up cost extraction

The processor SHALL select the plan via a bottom-up cost recursion over the bipartite graph,
considering only SAT vertices: the cost of a `Value` is the **minimum** cost over its SAT producer
`Operation`s; the cost of an `Operation` is its own weight **plus the sum** of the costs of all its
port `Value`s. Extraction SHALL NOT use a shortest-path oracle: the cost through an n-ary
`Operation` is the sum over all ports, never the minimum over one.

#### Scenario: Operation cost sums all ports
- **WHEN** an `Operation` with weight `w` has ports fed by Values costing `a` and `b`
- **THEN** the Operation's cost is `w + a + b`

#### Scenario: OR resolution picks the cheapest producer
- **WHEN** a `Value` has two SAT producers whose total costs differ
- **THEN** the extracted plan records the cheaper producer as the Value's chosen producer

#### Scenario: UNSAT producers never participate
- **WHEN** a `Value` has one SAT and one UNSAT producer
- **THEN** the UNSAT producer is excluded from cost comparison regardless of weight

### Requirement: Deterministic tie-breaking

When two producers of a `Value` have equal cost, extraction SHALL break the tie by a stable,
deterministic ordering derived from vertex identity (not insertion timing), so the selected plan is
identical across compilations of identical input.

#### Scenario: Equal-cost producers select deterministically
- **WHEN** two SAT producers of one Value have equal total cost
- **THEN** repeated compilations of the same sources select the same producer

### Requirement: Extracted plan is a read-only single-producer view

Extraction SHALL produce a read-only plan view over the underlying graph. Every `Value` present in
the plan SHALL expose exactly one chosen producer `Operation`; every `Operation` present SHALL have
all of its ports present. The view SHALL NOT mutate the underlying graph: losing producers and their
subgraphs remain in the graph, unselected. Code generation consumes only this view.

#### Scenario: In-plan Value has exactly one producer
- **WHEN** code generation asks an in-plan Value for its producer
- **THEN** exactly one Operation is returned, with no shared-codegen or label-based inference

#### Scenario: Losing subgraph is not mutated
- **WHEN** extraction resolves an OR between two constructors
- **THEN** the losing constructor Operation and its exclusive ports remain in the underlying graph
  and are absent from the plan view

### Requirement: Extraction recurses through scope-owning Operations

For a scope-owning `Operation` (container element mapping), extraction SHALL recurse into the child
scope: the Operation is selectable only if its outer ports are SAT and the child scope's return-root
`Value` is SAT, and the child plan is extracted by the same recursion rooted at the child
return-root.

#### Scenario: Child plan extracted with the parent
- **WHEN** a `map` Operation owning a child scope is chosen for `List<A> → List<B>`
- **THEN** the plan view contains the child scope's extracted plan rooted at its return-root Value

### Requirement: Shared Values render inline per use

A `Value` consumed by more than one in-plan port SHALL be rendered inline at each use site. The
engine assumes accessor idempotency (re-reading a source accessor yields an equivalent value);
hoisting shared Values into local variables is explicitly out of scope for this capability.

#### Scenario: One Value feeding two ports renders twice
- **WHEN** the Value `street:String` feeds ports of two in-plan Operations
- **THEN** the generated code re-renders the producing expression at each consuming site
