## ADDED Requirements

### Requirement: MapOptKey enum defines known option keys
The `annotations` module SHALL provide a `MapOptKey` enum with constant `DATE_FORMAT`. The enum SHALL be the single registry of all recognized mapping option keys. New option types SHALL be added as enum constants.

#### Scenario: DATE_FORMAT enum constant exists
- **WHEN** a developer references `MapOptKey.DATE_FORMAT`
- **THEN** the compiler SHALL resolve the constant successfully

### Requirement: @MapOpt annotation carries a typed key and string value
The `annotations` module SHALL provide a `@MapOpt` annotation with elements `MapOptKey key()` and `String value()`. The annotation SHALL have `@Retention(CLASS)` and `@Target({})` (used only as a nested annotation inside `@Map`).

#### Scenario: MapOpt with DATE_FORMAT key
- **WHEN** a developer writes `@MapOpt(key = MapOptKey.DATE_FORMAT, value = "dd.MM.yyyy")`
- **THEN** the annotation SHALL compile and carry the key `DATE_FORMAT` and value `"dd.MM.yyyy"`

### Requirement: @Map annotation accepts options array
The `@Map` annotation SHALL have an additional element `MapOpt[] options() default {}`. When no options are specified, the default SHALL be an empty array.

#### Scenario: Map with no options
- **WHEN** a developer writes `@Map(source = "name", target = "name")`
- **THEN** `map.options()` SHALL return an empty array

#### Scenario: Map with one option
- **WHEN** a developer writes `@Map(source = "date", target = "dateStr", options = @MapOpt(key = DATE_FORMAT, value = "dd.MM.yyyy"))`
- **THEN** `map.options()` SHALL return an array containing one `MapOpt` with key `DATE_FORMAT` and value `"dd.MM.yyyy"`

#### Scenario: Map with multiple options
- **WHEN** a developer writes `@Map(source = "date", target = "dateStr", options = {@MapOpt(key = DATE_FORMAT, value = "dd.MM.yyyy")})`
- **THEN** `map.options()` SHALL return all specified options

### Requirement: MapDirective carries parsed options
`MapDirective` SHALL carry a `Map<MapOptKey, String> options` field in addition to `source` and `target`. The `AnalyzeStage` SHALL parse options from each `@Map` annotation's `options()` element and populate the field. When no options are specified, the map SHALL be empty.

#### Scenario: Directive parsed from Map with options
- **WHEN** `@Map(source = "date", target = "dateStr", options = @MapOpt(key = DATE_FORMAT, value = "dd.MM.yyyy"))` is parsed
- **THEN** the resulting `MapDirective` SHALL have `options` containing `{DATE_FORMAT: "dd.MM.yyyy"}`

#### Scenario: Directive parsed from Map without options
- **WHEN** `@Map(source = "name", target = "name")` is parsed
- **THEN** the resulting `MapDirective` SHALL have an empty `options` map

### Requirement: MappingEdge carries options from its directive
`MappingEdge` SHALL carry a `Map<MapOptKey, String> options` field. The `BuildGraphStage` SHALL propagate options from the `MapDirective` to the `MappingEdge` when creating edges in `processDirectives`. Auto-mapped edges (created by `autoMap`) SHALL have empty options.

#### Scenario: Directive-driven edge carries options
- **WHEN** `BuildGraphStage` creates a `MappingEdge` from a `MapDirective` with options `{DATE_FORMAT: "dd.MM.yyyy"}`
- **THEN** the `MappingEdge` SHALL carry `options` containing `{DATE_FORMAT: "dd.MM.yyyy"}`

#### Scenario: Auto-mapped edge has empty options
- **WHEN** `BuildGraphStage` creates a `MappingEdge` via auto-mapping
- **THEN** the `MappingEdge` SHALL carry an empty `options` map

### Requirement: Duplicate option keys on a single @Map produce a compile error
The `AnalyzeStage` SHALL detect when the same `MapOptKey` appears more than once in a single `@Map` annotation's `options` array and emit a compile error.

#### Scenario: Duplicate DATE_FORMAT keys
- **WHEN** a developer writes `@Map(source = "d", target = "s", options = {@MapOpt(key = DATE_FORMAT, value = "a"), @MapOpt(key = DATE_FORMAT, value = "b")})`
- **THEN** the processor SHALL emit a compile error indicating duplicate option key `DATE_FORMAT`
