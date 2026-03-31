# Auto-Mapping Spec

## Purpose

Defines the automatic property mapping behavior in `BuildGraphStage`, where source and target properties with the same name are connected via `AccessEdge` + `MappingEdge` chains in the symbolic graph without requiring explicit `@Map` directives, using lightweight property name scanning.

## Requirements

### Requirement: Same-name properties are auto-mapped
When a target property has no incoming edge from an explicit `@Map` directive, `BuildGraphStage` SHALL check if a source property with the same name exists using lightweight property name scanning (not full discovery). If found, it SHALL add a `SourcePropertyNode` connected via `AccessEdge` from the `SourceRootNode` and a `MappingEdge` to the `TargetPropertyNode`. The name scan SHALL detect `getX()`/`isX()` methods and public non-static fields on the source and target types.

#### Scenario: Auto-map same-name properties without @Map
- **WHEN** source type has properties `[name, age]` and target type has properties `[name, age]` and no `@Map` directives are declared
- **THEN** the symbolic graph SHALL contain `AccessEdge` + `MappingEdge` chains from `SourceRootNode` through `SourcePropertyNode("name")` to `TargetPropertyNode("name")`, and likewise for `"age"`

#### Scenario: Auto-map fills gaps alongside explicit mappings
- **WHEN** source has `[firstName, lastName, age]`, target has `[givenName, familyName, age]`, and directives are `@Map(source="firstName", target="givenName")` and `@Map(source="lastName", target="familyName")`
- **THEN** `givenName` and `familyName` SHALL be mapped by explicit directives, and `age` SHALL be auto-mapped by name match

### Requirement: Explicit @Map directives take priority over auto-mapping
Auto-mapping SHALL only add edges for target properties with no incoming `MappingEdge`. Targets already connected by an explicit `@Map` directive SHALL NOT receive an auto-mapped edge.

#### Scenario: Explicit mapping prevents auto-map for same-name property
- **WHEN** source has `[name, fullName]`, target has `[name]`, and directive is `@Map(source="fullName", target="name")`
- **THEN** `TargetPropertyNode("name")` SHALL be mapped from `SourcePropertyNode("fullName")` (explicit), and no auto-mapped edge SHALL be added for `"name"`

### Requirement: Auto-mapping only considers top-level source properties
Auto-mapping SHALL match target property names against top-level source property names only (direct properties on the source parameter type). Nested chain properties SHALL NOT be auto-mapped — only explicit `@Map` directives can create nested chains.

#### Scenario: Nested property not auto-mapped
- **WHEN** source type `Order` has property `customer` of type `Customer`, and `Customer` has property `name`, and target has property `name`
- **THEN** auto-mapping SHALL NOT create a chain `"customer.name"` → `"name"` — only top-level `Order` properties are considered

### Requirement: Unmapped source properties after auto-mapping are silently ignored
Source properties that have no outgoing edge after both explicit and auto-mapping phases SHALL NOT produce errors or warnings.

#### Scenario: Source property with no matching target is ignored
- **WHEN** source has `[name, internalId]` and target has `[name]`
- **THEN** `name` SHALL be auto-mapped, and `internalId` SHALL be silently ignored with no diagnostic
