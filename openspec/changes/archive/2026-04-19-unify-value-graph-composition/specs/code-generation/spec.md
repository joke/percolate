## MODIFIED Requirements

### Requirement: GenerateStage produces JavaFile from resolved model

The `GenerateStage` SHALL consume a `ResolvedModel` and produce a `JavaFile` using Palantir JavaPoet. For each method it SHALL assemble the method body via a pure, branching-free traversal of the method's `ValueGraph`:

1. Build a winning subgraph via `new AsSubgraph<>(graph, graph.vertexSet(), winningEdges)` where `winningEdges` is the union of edges across all `ResolvedAssignment.path` edge lists for the method.
2. Iterate vertices in topological order via `new TopologicalOrderIterator<>(winningSubgraph)`. For each visited node: collect incoming edges from the subgraph, apply each edge's `codeTemplate` (for `LiftEdge`, compose lazily on demand) to the already-computed `CodeBlock` of the edge's source vertex, then call `node.compose(inputs, ComposeKind.EXPRESSION)` and cache the result keyed by node identity.
3. Assemble the method body by reading the cached `CodeBlock` at each `TargetSlotNode` and passing those expressions positionally to the target constructor (discovered via `ConstructorDiscovery` SPI).

The stage SHALL NOT perform any `instanceof` check on `ValueNode` or `ValueEdge` subtypes. It SHALL NOT read any node-side accessor state (accessor rendering lives on `PropertyReadEdge` templates). It SHALL NOT invoke `strategy.resolveCodeTemplate(...)` or otherwise re-derive any template; every eager template is already on the edge, and `LiftEdge` composition is the only on-demand template work the stage triggers.

#### Scenario: GenerateStage contains no instanceof on node or edge types

- **WHEN** source inspection is performed on `GenerateStage`
- **THEN** there SHALL be no `instanceof` (or equivalent pattern-match/`getClass()` comparison) against `ValueNode`, `ValueEdge`, or any of their subtypes inside `GenerateStage`

#### Scenario: Constructor-based target generation with DIRECT transforms

- **WHEN** the target type's properties are all `ConstructorParamAccessor` write accessors and all transforms are `DirectAssignableStrategy`
- **THEN** the generated code SHALL gather the cached `CodeBlock`s at each `TargetSlotNode` and pass them as constructor arguments in parameter order

#### Scenario: SUBMAP transform emits sibling method call via edge template

- **WHEN** a resolved path contains a single `TypeTransformEdge` contributed by `MethodCallStrategy` targeting sibling method `mapAddress`
- **THEN** applying that edge's `codeTemplate` to the inbound source read expression SHALL produce `mapAddress(order.getBillingAddress())`; `GenerateStage` SHALL emit this via the topological traversal with no stage-local branching

#### Scenario: Container mapping emits composed templates via topological traversal

- **WHEN** a resolved path for `List<Person>` → `Set<PersonDTO>` contains a 3-edge chain (StreamFromCollection, mapping via `LiftEdge(STREAM)`, CollectToSet)
- **THEN** the topological traversal SHALL yield `source.getPersons().stream().map(e -> map(e)).collect(Collectors.toSet())` by applying each edge's template (including `LiftEdge`'s lazily composed template) in vertex order

#### Scenario: Optional mapping emits map call via LiftEdge template

- **WHEN** a resolved path contains a `LiftEdge(OPTIONAL)` wrapping an inner `TypeTransformEdge(MethodCallStrategy)` for `Person → PersonDTO`
- **THEN** the `LiftEdge`'s lazily composed template SHALL render as `source.getPerson().map(e -> map(e))` and that `CodeBlock` SHALL be the value at the outer `TypedValueNode` in the traversal cache

#### Scenario: Getter-based source access emitted via PropertyReadEdge template

- **WHEN** a `PropertyReadEdge` carries template `"$L.getFirstName()"` and its source `CodeBlock` is `source`
- **THEN** the edge's `apply(source)` SHALL render as `source.getFirstName()` and appear at the target `PropertyNode` in the traversal cache without `GenerateStage` reading any accessor state from the node

#### Scenario: Field-based source access emitted via PropertyReadEdge template

- **WHEN** a `PropertyReadEdge` carries template `"$L.firstName"` and its source `CodeBlock` is `source`
- **THEN** the edge's `apply(source)` SHALL render as `source.firstName` at the target `PropertyNode` in the traversal cache

### Requirement: GenerateStage composes CodeTemplates by walking GraphPath

For each method the `GenerateStage` SHALL evaluate the winning subgraph in topological order, maintaining a `Map<ValueNode, CodeBlock>` cache of composed values. At each visited node, the stage SHALL:

1. Look up the node's incoming winning edges in the subgraph.
2. For each such edge, apply `edge.apply(sourceCodeBlock)` where `sourceCodeBlock` is the cache entry for `subgraph.getEdgeSource(edge)`.
3. Invoke `node.compose(inputs, ComposeKind.EXPRESSION)` and store the result in the cache.

The initial `SourceParamNode` visit SHALL yield the parameter reference via its `compose(...)` with empty inputs. The final `TargetSlotNode` cache entries SHALL be consumed as constructor-argument `CodeBlock`s.

#### Scenario: Single-edge path applies one template

- **WHEN** the winning subgraph at a target slot is `SourceParamNode → PropertyNode → TargetSlotNode` with one `PropertyReadEdge` and one identity `TypeTransformEdge(DirectAssignableStrategy)`
- **THEN** the cached `CodeBlock` at `TargetSlotNode` SHALL be the expression produced by applying each edge's template in topological order to the parameter reference

#### Scenario: Multi-edge path composes templates in topological order

- **WHEN** the winning subgraph contains a chain `[param → propertyNode (read) → typedValue1 (StreamFromCollection) → typedValue2 (LiftEdge STREAM) → typedValue3 (CollectToSet) → targetSlot]`
- **THEN** the topological iteration SHALL produce, at the final `targetSlot`, a `CodeBlock` equal to applying each edge's template in turn: `collectToSet(liftStream(streamFromCollection(propertyRead(param))))`
