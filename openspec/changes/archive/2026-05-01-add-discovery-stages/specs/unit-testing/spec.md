## ADDED Requirements

### Requirement: New processor classes SHALL have unit specs
The classes introduced by the discovery-stages change SHALL each have a corresponding Spock specification under `processor/src/test/groovy/...`, tagged `@Tag('unit')`, that tests the class in isolation using Spock mocks for collaborators.

#### Scenario: MapperStep unit spec exists
- **WHEN** the unit test suite runs
- **THEN** `MapperStepSpec` verifies that `annotations()` returns `{"io.github.joke.percolate.Mapper"}`, that `process(...)` invokes `Diagnostics.reset()` before any dispatch, dispatches each `@Mapper`-annotated `TypeElement` to a mocked `Pipeline`, ignores non-`TypeElement` entries, and returns an empty `Set<Element>`

#### Scenario: Diagnostics unit spec exists
- **WHEN** the unit test suite runs
- **THEN** `DiagnosticsSpec` verifies that `error(...)` forwards to a mocked `Messager` with the supplied `Element`, `AnnotationMirror`, and `AnnotationValue`; that `hasErrorsFor(element)` returns `true` for elements (and their enclosing types) that have had errors emitted; that `hasErrorsFor(unrelated)` returns `false`; and that `reset()` clears all per-round state

#### Scenario: DiscoverAbstractMethods unit spec exists
- **WHEN** the unit test suite runs
- **THEN** `DiscoverAbstractMethodsSpec` verifies that abstract methods declared on the type are returned, that default and concrete methods are filtered out, that inherited abstract methods are included, that generic super-interfaces produce methods with substituted types, that `Object` methods are skipped, and that static and private methods are skipped — using mocked `Elements`, `Types`, and synthetic `TypeElement`s where feasible

#### Scenario: DiscoverMappings unit spec exists
- **WHEN** the unit test suite runs
- **THEN** `DiscoverMappingsSpec` verifies that one `@Map` produces one `MappingDirective` carrying the mirror and value references, that multiple `@Map`s (wrapped in `@MapList` by the compiler) are unwrapped into individual directives, that methods without `@Map` produce empty directive lists, and that the implementation does not call proxy annotation APIs

#### Scenario: ValidateNoDuplicateTargets unit spec exists
- **WHEN** the unit test suite runs
- **THEN** `ValidateNoDuplicateTargetsSpec` verifies that two directives with the same `target` cause one error to be emitted via a mocked `Diagnostics` (with the offending `targetValue`), that three duplicates cause two errors, that distinct targets cause no errors, and that the validator does not throw

#### Scenario: Pipeline unit spec is updated
- **WHEN** the unit test suite runs
- **THEN** `PipelineSpec` verifies that `process(typeElement)` calls the three stages in order, threading the output of each stage into the next, and returns `null`
