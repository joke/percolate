## Why

The codebase has drifted from the established Java and Groovy/Spock coding conventions. Data carrier classes use Lombok `@Value` instead of Java records, `instanceof` checks lack pattern matching, mutable collections are returned where unmodifiable ones should be used, and test files use `def` instead of `final` for local variables with incorrect block structure. Aligning now prevents further drift as the codebase grows.

## What Changes

### Java Source (processor module)
- Convert all Lombok `@Value` data carriers to Java records (`Diagnostic`, `MapDirective`, `MapperModel`, `MappingMethodModel`, `DiscoveredModel`, `DiscoveredMethod`, `MappingGraph`, `MappingEdge`)
- Seal abstract hierarchies (`ReadAccessor`, `WriteAccessor`, `PropertyNode`) with `permits` clauses
- Replace `instanceof` + cast with pattern matching throughout (`ConstructorDiscovery`, `FieldDiscovery`, `GetterDiscovery`, `ValidateStage`, `GenerateStage`, `DiscoverStage`)
- Replace mutable `ArrayList` returns with unmodifiable collections; use `List.of()` instead of `Collections.emptyList()`
- Replace `Collectors.toList()` with `.toList()` in `AnalyzeStage`
- Use `var` consistently for local variables where types are obvious

### Groovy/Spock Tests
- Replace all `def` local variable declarations with `final`
- Move state assertions from `then:` blocks to trailing `expect:` blocks
- Add `0 * _` to `then:` blocks that verify interactions but lack the strict terminator
- Use Groovy property access in stub/mock configurations where applicable (e.g., `kind >> ...` instead of `getKind() >> ...`)
- Use single-quoted strings consistently for plain string literals

## Capabilities

### New Capabilities

_None — this is a refactor aligning existing code to established conventions._

### Modified Capabilities

- `processor`: Implementation changes to use records, sealed classes, and pattern matching (no behavioral changes)
- `unit-testing`: Test code updated to follow Spock conventions (no behavioral changes)

## Impact

- **Code**: All Java source files in `processor/src/main/java` and all Groovy test files in `processor/src/test/groovy`
- **Dependencies**: Lombok `@Value` usage removed from model/graph classes (Lombok still used for `@RequiredArgsConstructor` in `Pipeline`, `ProcessorModule`)
- **APIs**: Record accessor style changes from `getX()` to `x()` — affects all call sites including test code
- **Teams**: Single developer project, no cross-team impact
