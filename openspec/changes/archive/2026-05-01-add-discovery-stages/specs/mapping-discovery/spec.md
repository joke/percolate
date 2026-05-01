## ADDED Requirements

### Requirement: @Map directives SHALL be discovered for every abstract method
`DiscoverMappings` SHALL accept a `MapperShape` and produce a `MapperMappings` containing one `MethodMappings` for each abstract method, in the same order. Each `MethodMappings` carries the `ExecutableElement` and the list of `MappingDirective`s declared on that method.

#### Scenario: A method with one @Map produces one directive
- **WHEN** an abstract method is annotated with `@Map(target = "lastName", source = "lastName")`
- **THEN** the corresponding `MethodMappings.directives` list has exactly one entry whose `target` equals `"lastName"` and `source` equals `"lastName"`

#### Scenario: A method with multiple @Maps produces multiple directives in source order
- **WHEN** an abstract method is annotated with `@Map(target = "lastName", source = "lastName")` followed by `@Map(target = "firstName", source = "firsty")`
- **THEN** the corresponding `MethodMappings.directives` list has two entries in that order

### Requirement: Each MappingDirective SHALL preserve mirror and value references
Every `MappingDirective` SHALL carry the `AnnotationMirror` for the `@Map` annotation it represents, plus the `AnnotationValue`s for the `target` and `source` annotation members, so that downstream errors can point at the exact source token.

#### Scenario: Mirror and values are populated
- **WHEN** an abstract method is annotated with `@Map(target = "lastName", source = "firsty")`
- **THEN** the resulting `MappingDirective.mirror` is the `AnnotationMirror` for that `@Map` invocation
- **AND** `MappingDirective.targetValue` is the `AnnotationValue` for `target = "lastName"`
- **AND** `MappingDirective.sourceValue` is the `AnnotationValue` for `source = "firsty"`

### Requirement: @MapList container SHALL be unwrapped transparently
When the compiler wraps multiple `@Map` annotations into the `@MapList` container, `DiscoverMappings` SHALL unwrap the container and expose each contained `@Map` as an individual `MappingDirective`. The `@MapList` annotation itself SHALL NOT appear as a directive.

#### Scenario: Two @Maps result in two directives, not one MapList directive
- **WHEN** an abstract method has two `@Map` annotations (which the compiler aggregates into `@MapList`)
- **THEN** the resulting `MethodMappings.directives` has two `MappingDirective` entries
- **AND** no directive references the `@MapList` annotation mirror

### Requirement: Methods without @Map directives SHALL produce empty directive lists
A method with no `@Map` annotation SHALL produce a `MethodMappings` whose `directives` list is empty (and not `null`).

#### Scenario: Unannotated method has empty directives
- **WHEN** an abstract method has no `@Map` annotation
- **THEN** the corresponding `MethodMappings.directives` is an empty list

### Requirement: Discovery SHALL use AnnotationMirror walking, not annotation proxies
Implementation SHALL walk `Element.getAnnotationMirrors()` (optionally via `auto-common`'s `AnnotationMirrors` helpers). Implementation SHALL NOT call `Element.getAnnotation(Map.class)` or `Element.getAnnotationsByType(Map.class)`, because those proxies discard the mirror and value information that downstream error reporting depends on.

#### Scenario: Source code does not invoke proxy annotation APIs
- **WHEN** the source of `DiscoverMappings` is reviewed
- **THEN** it contains no calls to `getAnnotation(Map.class)` or `getAnnotationsByType(Map.class)`
