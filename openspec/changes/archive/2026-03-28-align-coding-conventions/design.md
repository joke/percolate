## Context

The processor module has 31 Java source files and 15 Groovy test files. The codebase was built incrementally and has accumulated deviations from the established Java and Groovy/Spock coding conventions. This refactor is purely structural — no behavioral changes.

## Goals / Non-Goals

**Goals:**
- Align all Java source with Java coding conventions (records, sealed classes, pattern matching, immutable collections)
- Align all Groovy/Spock tests with Spock coding conventions (`final` variables, correct block structure, property access, strict interaction verification)
- Ensure all tests pass after refactoring

**Non-Goals:**
- Changing any runtime behavior or public API contracts
- Refactoring architecture, package structure, or class responsibilities
- Adding new tests or expanding test coverage
- Modifying the annotations module (already convention-compliant)
- Removing Lombok entirely (still used for `@RequiredArgsConstructor` in DI wiring)

## Decisions

### 1. Convert Lombok `@Value` classes to Java records

**Decision**: Replace all `@Value`-annotated classes with Java records.

**Rationale**: Java records are the idiomatic way to define immutable data carriers. They provide `equals`, `hashCode`, `toString`, and accessor methods without Lombok's compile-time annotation processing overhead.

**Impact**: Record accessors use `x()` style instead of Lombok's `getX()` style. All call sites (including Groovy tests using property access like `model.mapperType`) will continue to work because Groovy resolves property access to any no-arg method matching the property name.

**Affected classes**: `Diagnostic`, `MapDirective`, `MapperModel`, `MappingMethodModel`, `DiscoveredModel`, `DiscoveredMethod`, `MappingGraph`, `MappingEdge`

**Alternative considered**: Keep `@Value` and just add missing conventions elsewhere. Rejected because records are the convention and Lombok `@Value` actively conflicts with it.

### 2. Seal abstract accessor/node hierarchies

**Decision**: Make `ReadAccessor`, `WriteAccessor`, and `PropertyNode` sealed with explicit `permits` clauses.

**Rationale**: These hierarchies have a fixed set of known subclasses within the processor module. Sealing them enables exhaustive pattern matching and communicates the closed nature of the type hierarchy.

**Affected classes**:
- `ReadAccessor` → `permits FieldReadAccessor, GetterAccessor`
- `WriteAccessor` → `permits FieldWriteAccessor, ConstructorParamAccessor`
- `PropertyNode` → `permits SourcePropertyNode, TargetPropertyNode`

### 3. Replace `instanceof` + cast with pattern matching

**Decision**: Use `instanceof` pattern matching (`if (x instanceof Foo foo)`) throughout.

**Rationale**: Eliminates redundant casts and reduces error-prone boilerplate. This is the modern Java convention.

**Affected files**: `ConstructorDiscovery`, `FieldDiscovery`, `GetterDiscovery`, `ValidateStage`, `GenerateStage`, `DiscoverStage`

### 4. Groovy test variables: `final` over `def`

**Decision**: Replace all `def` local variable declarations with `final` in test code.

**Rationale**: The Spock convention requires `final` for all local variables. `def` provides no type safety and doesn't communicate immutability intent.

### 5. Spock block structure corrections

**Decision**: Move state assertions from `then:` to trailing `expect:` blocks, and ensure every `then:` block with interactions ends with `0 * _`.

**Rationale**: `then:` blocks are for interaction verification only. State assertions belong in `expect:`. The `0 * _` terminator ensures no unexpected interactions occur.

### 6. Groovy property access in stub configurations

**Decision**: Use Groovy property access syntax in stub/mock configurations where applicable (e.g., `kind >> ...` instead of `getKind() >> ...`).

**Rationale**: Groovy convention requires property access for JavaBean getters. Spock stubs support this syntax.

## Risks / Trade-offs

**[Risk] Record accessor name change breaks Groovy property access** → Groovy resolves `obj.name` to any no-arg method `name()`, so `@Value`'s `getName()` → record's `name()` both work via `obj.name`. Verified by Groovy language spec. Mitigated by running full test suite after conversion.

**[Risk] Sealed classes require all subclasses in same module** → All subclasses are already in the processor module. No SPI implementations exist outside this module.

**[Risk] Large number of files changed in single refactor** → Mitigated by the fact that changes are mechanical and independently verifiable. Each category (records, sealed, pattern matching, test conventions) can be applied and tested incrementally.
