## ADDED Requirements

### Requirement: Same-name properties are auto-mapped
When a target property has no incoming edge from an explicit `@Map` directive, `BuildGraphStage` SHALL check if a source property with the same name exists. If found, it SHALL add a `MappingEdge` connecting the source to the target.

#### Scenario: Auto-map same-name properties without @Map
- **WHEN** source type has properties `[name, age]` and target type has properties `[name, age]` and no `@Map` directives are declared
- **THEN** the graph SHALL contain edges from `source.name` to `target.name` and from `source.age` to `target.age`

#### Scenario: Auto-map fills gaps alongside explicit mappings
- **WHEN** source has `[firstName, lastName, age]`, target has `[givenName, familyName, age]`, and directives are `@Map(source="firstName", target="givenName")` and `@Map(source="lastName", target="familyName")`
- **THEN** `givenName` and `familyName` SHALL be mapped by explicit directives, and `age` SHALL be auto-mapped by name match

### Requirement: Explicit @Map directives take priority over auto-mapping
Auto-mapping SHALL only add edges for target properties with no incoming edge (`inDegreeOf == 0`). Targets already connected by an explicit `@Map` directive SHALL NOT receive an auto-mapped edge.

#### Scenario: Explicit mapping prevents auto-map for same-name property
- **WHEN** source has `[name, fullName]`, target has `[name]`, and directive is `@Map(source="fullName", target="name")`
- **THEN** `target.name` SHALL be mapped from `source.fullName` (explicit), and `source.name` SHALL have no outgoing edge

### Requirement: Unmapped source properties after auto-mapping are silently ignored
Source properties that have no outgoing edge after both explicit and auto-mapping phases SHALL NOT produce errors or warnings.

#### Scenario: Source property with no matching target is ignored
- **WHEN** source has `[name, internalId]` and target has `[name]`
- **THEN** `name` SHALL be auto-mapped, and `internalId` SHALL be silently ignored with no diagnostic

### Requirement: Auto-mapped edges are indistinguishable from explicit edges
Auto-mapped edges SHALL use the same `MappingEdge` type as edges created from `@Map` directives. Downstream stages SHALL treat all edges identically.

#### Scenario: Auto-mapped edge has same type as directive edge
- **WHEN** `source.name` is auto-mapped to `target.name`
- **THEN** the edge SHALL be a `MappingEdge` with type `DIRECT`, identical to an edge from an explicit `@Map` directive
