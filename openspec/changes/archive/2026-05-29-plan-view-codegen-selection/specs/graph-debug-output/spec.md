## ADDED Requirements

### Requirement: Plan view filter on MapperGraph

`MapperGraph` SHALL expose an accessor `planView()` that returns a `PlanView` — a non-destructive `GraphSource` exposing only the edges of the **chosen plan**. The view SHALL expose `Stream<Node> nodes()`, `Stream<Edge> edges()`, and `Stream<Node> nodesByScope(Scope)` with the same ordering guarantees as the other `MapperGraph` views, and SHALL NOT mutate the underlying graph.

An edge SHALL be eligible for the plan iff it is `EdgeKind.REALISED` AND it belongs to an `ExpansionGroup` whose recorded `GroupOutcome.kind` is `SAT`. Edges belonging only to `UNSAT_NO_PLAN` / `UNSAT_DID_NOT_CONVERGE` groups (dead multi-fire siblings) SHALL be excluded.

Among the eligible edges, the plan SHALL select the **cheapest** producer at each choice point so that every node reachable in the plan has exactly one producer:

- The selection SHALL use a cost oracle `d(n)` = the minimum total `Edge.weight` of a path from any source-parameter-leaf node to `n` over the eligible subgraph. The implementation SHALL compute `d` with JGraphT `DijkstraShortestPath` over the eligible subgraph made weighted via `AsWeightedGraph` (edge weight = `Edge.weight`), using a single virtual super-source connected to every source-parameter-leaf node with weight-0 edges.
- The plan SHALL be assembled by a target-to-source walk from each method's return-root:
  - **AND node** — a node that is the root of a single eligible group (e.g. a `ConstructorCall` group, or a one-slot bridge group with no sibling): the plan SHALL include all of that group's slot→root edges and recurse into every slot.
  - **OR node** — a node that is the root of more than one eligible (`SAT`) group (multi-fire siblings): the plan SHALL include exactly the group `g` minimising `weight(slot_g → node) + d(slot_g)`, and recurse only into `g`.

The selection rule lives in the view consumer, not in the expansion engine: the engine records all siblings and outcomes; `planView()` chooses among them at view-construction time. This is the render-time sibling selection assigned to the consumer by the expansion model.

#### Scenario: planView excludes dead-sibling edges

- **WHEN** the underlying graph has a node with two producing groups, one `SAT` and one `UNSAT_NO_PLAN`, and `MapperGraph.planView()` is queried
- **THEN** `edges()` contains the `SAT` group's edges
- **AND** `edges()` contains no edge belonging only to the `UNSAT_NO_PLAN` group

#### Scenario: planView keeps all slots of an AND node

- **WHEN** a return-root node is the root of a single `ConstructorCall` group with slots `firstName` and `lastName`
- **THEN** the plan view contains the slot→root edges for both `firstName` and `lastName`

#### Scenario: planView picks the cheapest of two SAT siblings

- **WHEN** a node is the root of two `SAT` groups whose slots have cost-to-source `d` values such that branch A's `weight + d` is strictly less than branch B's
- **THEN** the plan view contains branch A's edge into the node
- **AND** the plan view does not contain branch B's edge into the node

### Requirement: DumpPlan stage

The processor SHALL define a stage `DumpPlan` in package `io.github.joke.percolate.processor.stages.dump` that, when enabled by `ProcessorOptions.isDebugGraphs()`, writes a DOT representation of `MapperGraph.planView()` to `StandardLocation.SOURCE_OUTPUT`. `DumpPlan` SHALL be `@Inject`-constructed via Lombok `@RequiredArgsConstructor(onConstructor_ = @Inject)` and SHALL depend on `Filer`, `Diagnostics`, `ProcessorOptions`, and the deterministic DOT renderer — mirroring `DumpTransforms`.

`DumpPlan` SHALL write its file regardless of whether the mapper was scarred by validation (debug output is most valuable on failure), and SHALL NOT write a file when the graph has zero nodes and zero edges. A `Filer`/`IOException` failure SHALL be reported as a `Diagnostics` warning (not an error) and SHALL NOT abort the compile.

The `.transforms.dot` output (`MapperGraph.transformsView()`) SHALL be left unchanged and SHALL continue to include dead (`UNSAT`) multi-fire sibling branches — its purpose is debugging, and the dead branches are part of that picture. `.plan.dot` is the complementary view that shows only the chosen plan.

#### Scenario: Option off does not write a plan file

- **WHEN** `DumpPlan` runs with `ProcessorOptions.isDebugGraphs() == false`
- **THEN** no resource is created via `Filer`
- **AND** no diagnostic is emitted

#### Scenario: Option on writes a .plan.dot file at SOURCE_OUTPUT

- **WHEN** `DumpPlan` runs with `ProcessorOptions.isDebugGraphs() == true` for a non-empty `MapperGraph` and a `TypeElement` representing FQN `com.example.PersonMapper`
- **THEN** `Filer.createResource(StandardLocation.SOURCE_OUTPUT, "", "com.example.PersonMapper.plan.dot", <originating element>)` is invoked
- **AND** the resource's contents are the DOT representation of `graph.planView()` as produced by the deterministic DOT renderer

#### Scenario: plan.dot and transforms.dot coexist

- **WHEN** the pipeline completes for `com.example.PersonMapper` with `ProcessorOptions.isDebugGraphs() == true` and a non-empty graph
- **THEN** both `com.example.PersonMapper.plan.dot` and `com.example.PersonMapper.transforms.dot` are present in `SOURCE_OUTPUT`
- **AND** `com.example.PersonMapper.transforms.dot` still contains the dead-sibling edges absent from `com.example.PersonMapper.plan.dot`
