## MODIFIED Requirements

### Requirement: Type transformation graph per property edge
The resolver SHALL construct a JGraphT `DefaultDirectedGraph<TypeNode, TransformEdge>` for each property mapping edge. `TypeNode` SHALL wrap a `TypeMirror` and a label for debugging. `TransformEdge` SHALL carry the contributing `TypeTransformStrategy` and its `TransformProposal`. The `CodeTemplate` on `TransformEdge` SHALL be resolved lazily after `BFSShortestPath` selects the final path, not at edge-creation time.

#### Scenario: Simple direct mapping produces single-edge graph
- **WHEN** source type `String` maps to target type `String` via `DirectAssignableStrategy`
- **THEN** the type graph SHALL contain two `TypeNode`s (`String` -> `String`) connected by one `TransformEdge` carrying the `TransformProposal`, with `CodeTemplate` resolved after path selection

#### Scenario: Container mapping produces multi-edge graph
- **WHEN** source type `List<Person>` maps to target type `Set<PersonDTO>` via stream expansion
- **THEN** the type graph SHALL contain nodes for `List<Person>`, `Stream<Person>`, `Stream<PersonDTO>`, `Set<PersonDTO>` connected by three `TransformEdge`s, each carrying a `TransformProposal` with `CodeTemplate` resolved only after path selection

#### Scenario: TransformEdge exposes CodeTemplate after resolution
- **WHEN** `resolvePathTemplates` has been called on the selected path
- **THEN** each `TransformEdge` on the path SHALL have a non-null `CodeTemplate` accessible via `getCodeTemplate()`
