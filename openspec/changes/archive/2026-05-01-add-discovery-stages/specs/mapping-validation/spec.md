## ADDED Requirements

### Requirement: A method SHALL NOT have duplicate @Map targets
On a single abstract method, two or more `@Map` directives MUST NOT specify the same `target`. `ValidateNoDuplicateTargets` SHALL emit one error for each duplicate occurrence beyond the first.

#### Scenario: Two @Maps with the same target produce one error
- **WHEN** a method is annotated with `@Map(target = "lastName", source = "lastName")` and `@Map(target = "lastName", source = "alt")`
- **THEN** `ValidateNoDuplicateTargets` emits one error via `Diagnostics.error(...)` for the second occurrence

#### Scenario: Three @Maps with the same target produce two errors
- **WHEN** a method has three `@Map` directives all with `target = "name"`
- **THEN** `ValidateNoDuplicateTargets` emits two errors via `Diagnostics.error(...)` — one per duplicate beyond the first

#### Scenario: Different targets produce no error
- **WHEN** a method has `@Map(target = "a", ...)` and `@Map(target = "b", ...)`
- **THEN** `ValidateNoDuplicateTargets` emits no error

### Requirement: Duplicate-target errors SHALL point at the offending target literal
Each duplicate-target error SHALL be emitted with the offending `MappingDirective`'s method `Element`, `AnnotationMirror`, and the `AnnotationValue` of `target`, so that an IDE underlines the duplicated `target = "..."` literal.

#### Scenario: Error carries the targetValue
- **WHEN** `ValidateNoDuplicateTargets` reports a duplicate `target = "lastName"`
- **THEN** the `Diagnostics.error(...)` call receives the offending `AnnotationValue` (the `target` value of the duplicate `@Map`), not the value from the original (kept) directive

### Requirement: Validation SHALL NOT halt the pipeline
`ValidateNoDuplicateTargets` SHALL emit errors via `Diagnostics` and return normally. The `Pipeline` SHALL continue to invoke subsequent stages on other methods of the same mapper, and SHALL continue processing other mappers in the same round.

#### Scenario: Other methods on the same mapper continue
- **WHEN** method `m1` on a mapper has duplicate targets and method `m2` does not
- **THEN** the pipeline emits errors for `m1` and continues processing `m2` without exception

#### Scenario: Other mappers in the round continue
- **WHEN** mapper `A` has duplicate targets and mapper `B` does not
- **THEN** the pipeline emits errors for `A` and continues processing `B` without exception

### Requirement: Validators SHALL emit errors via Diagnostics, not Messager directly
Validator implementations SHALL route all error reporting through the `Diagnostics` collaborator, never through `Messager.printMessage` directly. This ensures uniform behaviour for scarring and future buffering.

#### Scenario: Source code does not call Messager directly from validators
- **WHEN** the source of any class under `io.github.joke.percolate.processor` named with the `Validate*` prefix is reviewed
- **THEN** it contains no calls to `Messager.printMessage(...)` (only calls to `Diagnostics`)
