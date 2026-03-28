## Why

The codebase has convention violations introduced before coding conventions were formalized: manual boilerplate instead of Lombok annotations, qualified static references where static imports are preferred, and imperative for-loops where functional streams with immutable collections would be clearer and safer. Aligning now prevents drift and ensures consistency across the processor module.

## What Changes

- Add `@Getter` and `@RequiredArgsConstructor` to abstract base classes (`ReadAccessor`, `WriteAccessor`, `PropertyNode`), removing manual constructors and accessor methods
- Add `@Getter` to leaf subclasses (`GetterAccessor`, `FieldReadAccessor`, `FieldWriteAccessor`, `SourcePropertyNode`, `TargetPropertyNode`, `ConstructorParamAccessor`), removing manual accessor methods
- **BREAKING**: Rename fluent-style accessors (`.name()`, `.type()`, `.accessor()`, etc.) to JavaBean-style (`.getName()`, `.getType()`, `.getAccessor()`) across all call sites
- Replace ~21 qualified static references with static imports (`ElementKind.FIELD` -> `FIELD`, `Modifier.PUBLIC` -> `PUBLIC`, `Kind.ERROR` -> `ERROR`, `Comparator.comparingInt` -> `comparingInt`)
- Convert simple collection for-loops to streams with immutable collection results (`toUnmodifiableList()`, `Map.copyOf()`, etc.)
- Keep complex loops (priority-based merging in `DiscoverStage`, graph building with error accumulation in `BuildGraphStage`) as imperative code

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `property-discovery`: Accessor method signatures change from fluent to JavaBean style
- `mapping-graph`: Graph node accessor signatures change from fluent to JavaBean style

## Impact

- **Code**: All files in `processor/src/main/java` that reference model/graph accessor methods (~9 files)
- **Tests**: Spock specs referencing accessor methods will need updating (Groovy property access `.name` works with both styles, but explicit `.name()` calls will break)
- **APIs**: No public API changes (processor is internal)
- **Dependencies**: No new dependencies (Lombok already in use)
- **Teams**: Processor module only, no cross-team impact
