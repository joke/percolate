## MODIFIED Requirements

### Requirement: Strategy-requested class members are hoisted and deduplicated

When operations in the extracted plan declare member requests (see the `expansion-strategy-spi` capability), `GenerateStage` SHALL collect them during the same recursive plan walk that gathers hoisted locals, **deduplicate** them by their content dedup key across all method bodies of the generated type, allocate a unique class-scope name per distinct member (via a class-scoped `NameAllocator`, the sibling of the method-scoped local allocator), and emit each distinct member once on the generated type.

A **field** request SHALL emit a field with the requested type and initializer. A **method** request SHALL emit a method with the requested return type, the requested parameters in the requested order, and the requested body. Both kinds SHALL share one dedup namespace and one name allocator. The modifiers of every emitted member SHALL follow the configurable member style (see *Generated member modifiers are configurable*).

Each requesting operation's codegen SHALL reference the member by its allocated name through the same indirection used for a hoisted local, so the composer holds zero field syntax and zero method syntax. Member collection SHALL mutate neither the `MapperGraph` nor the `ExtractedPlan`.

#### Scenario: Two methods sharing a formatter emit one field

- **WHEN** two mapper methods both parse with `@Map(format = "yyyy-MM-dd")` into `java.time` targets
- **THEN** the generated type declares exactly one `DateTimeFormatter` field initialized with `DateTimeFormatter.ofPattern("yyyy-MM-dd")`
- **AND** both method bodies reference that single field

#### Scenario: Distinct patterns emit distinct fields

- **WHEN** one method uses `@Map(format = "yyyy-MM-dd")` and another uses `@Map(format = "dd.MM.yyyy")`, both into `java.time` targets
- **THEN** the generated type declares two distinct `DateTimeFormatter` fields with distinct names

#### Scenario: An inline production requests no field

- **WHEN** a mapper method formats a `java.util.Date` with a per-call `SimpleDateFormat`
- **THEN** the generated type declares no `SimpleDateFormat` field and the method body constructs it inline

#### Scenario: A method request emits a method

- **WHEN** an operation in the extracted plan declares a method member request
- **THEN** the generated type declares one method with the requested return type, parameters and body
- **AND** the requesting operation's rendered expression calls it by its allocated name

#### Scenario: Two methods sharing a helper emit one method

- **WHEN** two mapper methods both assemble the same target from the same declared children
- **THEN** the generated type declares exactly one helper method
- **AND** both method bodies call it

## ADDED Requirements

### Requirement: Generated member modifiers are configurable

The generate stage SHALL render the modifiers of every strategy-requested class member according to two independent compile-time processor options, both advertised by `getSupportedOptions()`:

- `percolate.helpers.visibility` — the member's access modifier. Accepted values are `private` (the default), `package`, `protected` and `public`. The value `package` emits no access modifier.
- `percolate.helpers.static` — when `true` (the default), each member is declared `static`.

The two options SHALL compose and SHALL apply to both member kinds. A requested **field** SHALL additionally always be `final`, which is not configurable, because a mutable shared field would change behaviour rather than style.

The defaults SHALL reproduce the previous unconditional `private static final` field exactly, so no existing generated output changes.

An unrecognised visibility value SHALL degrade to `private` rather than fail the round, matching how every other value-typed option degrades.

Neither option SHALL change **which** members are emitted, their dedup identity, their order, or their allocated names — only the modifiers. The style SHALL be invisible to strategies: a strategy declares a member request and references the member by dedup key, and cannot observe the modifiers the stage applied.

#### Scenario: Default style reproduces the previous output
- **WHEN** neither option is set
- **THEN** a requested field renders as `private static final DateTimeFormatter <name> = …;`
- **AND** a requested method renders as `private static <returnType> <name>(…)`

#### Scenario: Visibility is configurable
- **WHEN** `percolate.helpers.visibility=protected` is set
- **THEN** a requested method renders as `protected static <returnType> <name>(…)`

#### Scenario: Package visibility emits no access modifier
- **WHEN** `percolate.helpers.visibility=package` is set
- **THEN** a requested method declares no access modifier

#### Scenario: static is configurable
- **WHEN** `percolate.helpers.static=false` is set
- **THEN** a requested method renders without `static`
- **AND** a requested field renders as `private final …`

#### Scenario: The two options compose
- **WHEN** `percolate.helpers.visibility=public` and `percolate.helpers.static=false` are both set
- **THEN** a requested method renders as `public <returnType> <name>(…)`

#### Scenario: An unrecognised visibility degrades to private
- **WHEN** `percolate.helpers.visibility=wombat` is set
- **THEN** the member renders `private` and the round succeeds

#### Scenario: The style does not change member selection
- **WHEN** the same mapper is generated under differing values of both options
- **THEN** the emitted members, their count, their dedup identity and their allocated names are the same in every case
