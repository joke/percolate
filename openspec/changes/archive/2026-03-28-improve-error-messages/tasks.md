## 1. ErrorMessages Utility

- [x] 1.1 Create `ErrorMessages` class with private Levenshtein distance method and `suggest` helper that returns matching candidates within threshold (distance ≤ 3 AND ≤ half name length, top 3, sorted by distance)
- [x] 1.2 Add `unknownSourceProperty(name, methodModel, availableNames)` method returning multi-line message with method context, source type, sorted available properties (capped at 10), and "Did you mean:" suggestion
- [x] 1.3 Add `unknownTargetProperty(name, methodModel, availableNames)` method returning multi-line message with method context, target type, sorted available properties (capped at 10), and "Did you mean:" suggestion
- [x] 1.4 Add `unmappedTargetProperty(name, mapperType, unmappedSourceNames)` method returning multi-line message with mapper type, sorted unmapped sources, and "Did you mean to map 'X' -> 'Y'?" suggestion
- [x] 1.5 Add `conflictingMappings(name, mapperType, sourceNames)` method returning multi-line message with mapper type and sorted conflicting source list

## 2. ErrorMessages Tests

- [x] 2.1 Write Spock spec for `unknownSourceProperty` covering close match, no close match, and many properties (cap at 10) scenarios
- [x] 2.2 Write Spock spec for `unknownTargetProperty` covering close match and no close match scenarios
- [x] 2.3 Write Spock spec for `unmappedTargetProperty` covering matching unmapped source, no unmapped sources, and no close match scenarios
- [x] 2.4 Write Spock spec for `conflictingMappings` covering basic conflicting sources scenario
- [x] 2.5 Write Spock spec for Levenshtein threshold edge cases (short names, exact match, equal distance candidates)

## 3. Stage Integration

- [x] 3.1 Update `BuildGraphStage` to call `ErrorMessages.unknownSourceProperty` and `ErrorMessages.unknownTargetProperty` instead of inline string concatenation
- [x] 3.2 Update `ValidateStage` to collect unmapped source nodes (out-degree 0) and call `ErrorMessages.unmappedTargetProperty`
- [x] 3.3 Update `ValidateStage` to walk incoming edges for conflicting targets and call `ErrorMessages.conflictingMappings`

## 4. Stage Test Updates

- [x] 4.1 Update `BuildGraphStageSpec` error assertions to match new message format (check for type name, available properties, suggestion)
- [x] 4.2 Update `ValidateStageSpec` error assertions to match new message format (check for mapper type, unmapped sources, conflicting source names)
- [x] 4.3 Run full test suite and verify all tests pass
