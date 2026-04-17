## ADDED Requirements

### Requirement: MatchedModel replaces MappingGraph as the matching-layer output

The output of the matching layer (stages 2 and 3 of the pipeline) SHALL be a `MatchedModel` value object — not a graph. `MatchedModel` SHALL carry the mapper `TypeElement` and a list of `MethodMatching` entries, one per `@Mapper` method. `MatchedModel` SHALL be immutable and SHALL NOT reference any `DefaultDirectedGraph`, `ValueGraph`, or JGraphT type.

#### Scenario: MatchedModel is produced by MatchMappingsStage

- **WHEN** `MatchMappingsStage.execute(List<MethodParseResult>)` completes successfully
- **THEN** the stage SHALL return a `MatchedModel` whose `mapperType` is the mapper `TypeElement` and whose `methods` list contains one `MethodMatching` per `@Mapper` method in the input

#### Scenario: MatchedModel is the input to BuildValueGraphStage

- **WHEN** `BuildValueGraphStage.execute(...)` is invoked
- **THEN** its input parameter SHALL be a `MatchedModel` and SHALL NOT be a `MappingGraph` or any `DefaultDirectedGraph`

#### Scenario: MatchedModel does not expose a graph accessor

- **WHEN** callers inspect the `MatchedModel` API surface
- **THEN** there SHALL be no method returning `DefaultDirectedGraph`, `Graph`, or any JGraphT type

### Requirement: MethodMatching groups assignments per method

`MethodMatching` SHALL carry the method `ExecutableElement`, the parsed `MappingMethodModel`, and the ordered list of `MappingAssignment` entries for that method. The list order SHALL be deterministic: explicit `@Map` directives first in source-declaration order, followed by auto-mapped entries in target-property declaration order.

#### Scenario: MethodMatching preserves explicit-before-auto ordering

- **WHEN** a method has two explicit `@Map` directives (for targets `givenName`, `familyName`) and one auto-mapped target (`age`)
- **THEN** the resulting `MethodMatching.assignments` SHALL list the two explicit entries first (in source order), followed by the auto-mapped entry

#### Scenario: MethodMatching references the source ExecutableElement

- **WHEN** a `MethodMatching` is produced for a method `OrderDTO map(Order order)`
- **THEN** `getMethod()` SHALL return the `ExecutableElement` for that method, allowing downstream stages to attach diagnostics at the correct source location

### Requirement: MappingAssignment is the unit of matching

Each mapping decision SHALL be represented by exactly one `MappingAssignment` record carrying:

- `sourcePath: List<String>` — the dotted source path split into segments (never null, never empty)
- `targetName: String` — the target property / constructor-argument name
- `options: Map<MapOptKey, String>` — the options from `@MapOpt` annotations on this assignment (empty map when none)
- `using: @Nullable String` — the `@Map(using = "...")` method name, or `null` when not set (an empty string in the annotation SHALL be normalised to `null` here)
- `origin: AssignmentOrigin` — how this assignment was produced (see below)

`MappingAssignment` SHALL be immutable. Identity equality is by value (Lombok `@Value` or Java record).

#### Scenario: Explicit @Map produces a MappingAssignment with EXPLICIT_MAP origin

- **WHEN** a method carries `@Map(source = "customer.name", target = "customerName")`
- **THEN** `MatchMappingsStage` SHALL emit `MappingAssignment(sourcePath=["customer","name"], targetName="customerName", options={}, using=null, origin=EXPLICIT_MAP)`

#### Scenario: Auto-mapping produces a MappingAssignment with AUTO_MAPPED origin

- **WHEN** source type has a top-level property `age` and target has a slot `age` with no explicit `@Map` directive
- **THEN** `MatchMappingsStage` SHALL emit `MappingAssignment(sourcePath=["age"], targetName="age", options={}, using=null, origin=AUTO_MAPPED)`

#### Scenario: @Map(using=...) produces a MappingAssignment with USING_ROUTED origin

- **WHEN** a method carries `@Map(source = "raw", target = "normalised", using = "normalise")`
- **THEN** the emitted `MappingAssignment` SHALL have `using = "normalise"` and `origin = USING_ROUTED`

#### Scenario: @MapOpt values are collected into the options map

- **WHEN** a `@Map` directive carries `@MapOpt(key = DATE_FORMAT, value = "yyyy-MM-dd")`
- **THEN** the emitted `MappingAssignment.options` SHALL contain `{DATE_FORMAT -> "yyyy-MM-dd"}`

#### Scenario: Empty using string is normalised to null

- **WHEN** a `@Map` directive omits `using` (default `""`)
- **THEN** the emitted `MappingAssignment.using` SHALL be `null`, not an empty string

### Requirement: AssignmentOrigin enumerates the sources of matching decisions

The `AssignmentOrigin` enum SHALL have exactly these values:

- `EXPLICIT_MAP` — the assignment came from an `@Map` or `@MapList` directive on the mapper method
- `AUTO_MAPPED` — the assignment was inferred by `MatchMappingsStage` because source and target had matching top-level property names
- `USING_ROUTED` — the assignment came from an `@Map(using = ...)` directive; the method referenced by `using` is invoked as the transform

The origin SHALL be consulted by `ValidateMatchingStage` for tailored diagnostics and by `ValidateResolutionStage` for suggesting remediations to the user.

#### Scenario: AssignmentOrigin has exactly three values

- **WHEN** `AssignmentOrigin.values()` is read
- **THEN** it SHALL return exactly `[EXPLICIT_MAP, AUTO_MAPPED, USING_ROUTED]`

#### Scenario: Origin is preserved from MatchMappingsStage through to GenerateStage

- **WHEN** a `MappingAssignment` is created with `origin = AUTO_MAPPED` in `MatchMappingsStage`
- **THEN** the same `origin` SHALL be observable on the assignment during `ValidateResolutionStage` and `GenerateStage`

### Requirement: Matching layer does not carry type information

`MappingAssignment`, `MethodMatching`, and `MatchedModel` SHALL NOT carry `TypeMirror`, `ReadAccessor`, `WriteAccessor`, or any property-type information. Type resolution is the responsibility of `BuildValueGraphStage`. The matching layer records *which* source paths map to *which* target names; it is agnostic to what those names resolve to type-wise.

#### Scenario: MappingAssignment does not expose type mirrors

- **WHEN** callers inspect the `MappingAssignment` API
- **THEN** there SHALL be no method returning `TypeMirror`, `ReadAccessor`, or `WriteAccessor`

#### Scenario: Matching layer accepts unknown property names without error

- **WHEN** `MatchMappingsStage` processes `@Map(source = "nonexistent", target = "name")`
- **THEN** the stage SHALL emit a `MappingAssignment` with `sourcePath = ["nonexistent"]` without error or diagnostic (validation of name existence is deferred to `ValidateMatchingStage` and, for type-level failures, `ValidateResolutionStage`)
