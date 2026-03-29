## ADDED Requirements

### Requirement: ErrorMessages utility provides rich error formatting
`ErrorMessages` SHALL be a final utility class with a private constructor and static methods for constructing error messages. It SHALL reside in the `io.github.joke.percolate.processor` package.

#### Scenario: Class is non-instantiable
- **WHEN** `ErrorMessages` is defined
- **THEN** it SHALL be a final class with a private constructor

### Requirement: Unknown source property message includes type, available properties, and suggestions
`ErrorMessages.unknownSourceProperty(name, methodModel, availableNames)` SHALL return a multi-line message containing:
1. The unknown property name and the method signature
2. The source type name
3. The sorted list of available source property names
4. A "Did you mean:" line with the closest match(es), if any are within the similarity threshold

#### Scenario: Unknown source with close match
- **WHEN** `unknownSourceProperty("userName", method, ["name", "age", "email"])` is called and the method maps `PersonDto` to `Target` via method `toTarget`
- **THEN** the message SHALL contain `Unknown source property 'userName'`, the method context, `Source type:` with the source type, `Available source properties:` with `[age, email, name]`, and `Did you mean: name?`

#### Scenario: Unknown source with no close match
- **WHEN** `unknownSourceProperty("zzz", method, ["name", "age"])` is called
- **THEN** the message SHALL contain the unknown property, method context, source type, and available properties, but SHALL NOT contain a "Did you mean:" line

### Requirement: Unknown target property message includes type, available properties, and suggestions
`ErrorMessages.unknownTargetProperty(name, methodModel, availableNames)` SHALL return a multi-line message containing:
1. The unknown property name and the method signature
2. The target type name
3. The sorted list of available target property names
4. A "Did you mean:" line with the closest match(es), if any are within the similarity threshold

#### Scenario: Unknown target with close match
- **WHEN** `unknownTargetProperty("fullName", method, ["givenName", "familyName", "age"])` is called
- **THEN** the message SHALL contain `Unknown target property 'fullName'`, the method context, `Target type:` with the target type, `Available target properties:` with the sorted list, and a "Did you mean:" suggestion

#### Scenario: Unknown target with no close match
- **WHEN** `unknownTargetProperty("zzz", method, ["givenName", "familyName"])` is called
- **THEN** the message SHALL NOT contain a "Did you mean:" line

### Requirement: Unmapped target property message includes unmapped sources and suggestions
`ErrorMessages.unmappedTargetProperty(name, mapperType, unmappedSourceNames)` SHALL return a multi-line message containing:
1. The unmapped target property name and the mapper type
2. The sorted list of unmapped source property names (source nodes with out-degree 0), if any
3. A "Did you mean to map 'X' -> 'Y'?" suggestion if an unmapped source closely matches the target name

#### Scenario: Unmapped target with matching unmapped source
- **WHEN** `unmappedTargetProperty("middleName", mapperType, ["secondName", "suffix"])` is called
- **THEN** the message SHALL contain `Unmapped target property 'middleName'`, the mapper type, `Unmapped source properties:` with `[secondName, suffix]`, and `Did you mean to map 'secondName' -> 'middleName'?`

#### Scenario: Unmapped target with no unmapped sources
- **WHEN** `unmappedTargetProperty("middleName", mapperType, [])` is called
- **THEN** the message SHALL contain the unmapped target and mapper type, but SHALL NOT contain "Unmapped source properties:" or "Did you mean to map" lines

### Requirement: Conflicting mappings message lists conflicting sources
`ErrorMessages.conflictingMappings(name, mapperType, sourceNames)` SHALL return a multi-line message containing:
1. The target property name and the mapper type
2. The list of source property names that map to this target

#### Scenario: Two sources conflict
- **WHEN** `conflictingMappings("name", mapperType, ["firstName", "displayName"])` is called
- **THEN** the message SHALL contain `Conflicting mappings for target property 'name'` and `Mapped from: [displayName, firstName]`

### Requirement: Fuzzy matching uses Levenshtein distance with threshold
The suggestion algorithm SHALL compute Levenshtein edit distance between the unknown name and each candidate. A candidate SHALL be suggested only if its distance is ≤ 3 AND ≤ half the length of the unknown name. Results SHALL be sorted by distance (ascending), limited to the top 3.

#### Scenario: Close match within threshold
- **WHEN** the unknown name is `"userName"` (length 8) and a candidate is `"username"` (distance 1)
- **THEN** `"username"` SHALL be suggested (1 ≤ 3 AND 1 ≤ 4)

#### Scenario: Match exceeds threshold
- **WHEN** the unknown name is `"id"` (length 2) and a candidate is `"name"` (distance 4)
- **THEN** `"name"` SHALL NOT be suggested (4 > 1, exceeds half-length threshold)

### Requirement: Available properties list is capped
When the list of available properties exceeds 10 items, only the first 10 (sorted alphabetically) SHALL be displayed, followed by "and N more".

#### Scenario: Type with many properties
- **WHEN** a type has 15 discovered properties
- **THEN** the available properties line SHALL show 10 names followed by "and 5 more"

#### Scenario: Type with 10 or fewer properties
- **WHEN** a type has 8 discovered properties
- **THEN** all 8 SHALL be listed with no truncation
