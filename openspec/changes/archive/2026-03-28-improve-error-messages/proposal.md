## Why

When a `@Map` directive references a property that doesn't exist on the source or target type, the error messages are bare — e.g., `Unknown source property: userName`. The user has no idea what properties *are* available, whether they made a typo, or which method/type the error relates to. This makes debugging mapper interfaces unnecessarily painful, especially with multiple mapping methods.

## What Changes

- Add an `ErrorMessages` utility class with static methods that build rich, multi-line error messages including:
  - The type name (source or target) where the property was expected
  - The method signature for context
  - The list of available properties
  - "Did you mean?" suggestions using Levenshtein distance
- Update `BuildGraphStage` to use `ErrorMessages` for unknown source/target property errors
- Update `ValidateStage` to use `ErrorMessages` for unmapped target and conflicting mapping errors, including listing unmapped source properties and conflicting source names

## Capabilities

### New Capabilities
- `error-messages`: Rich, contextual error message formatting with fuzzy match suggestions for the mapping processor

### Modified Capabilities
- `mapping-graph`: Error diagnostics for unknown properties, unmapped targets, and conflicting mappings will include type names, method context, available properties, and "did you mean?" suggestions

## Impact

- `BuildGraphStage` — error message construction changes (calls `ErrorMessages` instead of inline string concatenation)
- `ValidateStage` — error message construction changes, additional graph traversal to collect unmapped sources and conflicting source names
- New `ErrorMessages` utility class in the processor module
- Existing tests for error messages will need updated assertions to match new message format
- No API or dependency changes
- Affected team: processor maintainers
