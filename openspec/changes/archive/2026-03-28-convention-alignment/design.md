## Context

The processor module has model and graph classes written before coding conventions were formalized. They use manual boilerplate instead of Lombok, qualified static references instead of static imports, and imperative for-loops instead of functional streams. The codebase already uses Lombok (`@Value`, `@Getter`, `@RequiredArgsConstructor`) in several classes, so the infrastructure is in place.

Key constraint: leaf subclasses (`GetterAccessor`, `FieldReadAccessor`, etc.) extend abstract bases with `super()` calls. Lombok's `@RequiredArgsConstructor` cannot generate constructors that call `super()`, so leaf constructors remain manual.

## Goals / Non-Goals

**Goals:**
- Eliminate manual getter methods and constructors where Lombok can generate them
- Use static imports for self-explanatory constants and methods
- Convert simple collection loops to streams producing immutable collections
- Maintain all existing behavior — this is a pure refactor

**Non-Goals:**
- Refactoring complex imperative loops (priority merging, graph building with error accumulation)
- Changing public APIs beyond accessor method renaming
- Adding new features or capabilities
- Refactoring `StageResult` (has custom logic in accessors)

## Decisions

### D1: Standard `@Getter` over `@Accessors(fluent = true)`

Use standard `@Getter` which generates `getName()`, `getType()`, etc.

**Alternative considered**: `@Accessors(fluent = true)` would preserve existing `.name()` call sites.

**Rationale**: Standard JavaBean getters are the project convention. They work with Groovy property access (`.name`), framework tooling, and are consistent with existing `@Value` classes that already use `getX()`.

**Impact**: All call sites change from `.name()` to `.getName()` (or `.name` in Groovy).

### D2: `@RequiredArgsConstructor` only on abstract bases

Apply `@RequiredArgsConstructor` to `ReadAccessor`, `WriteAccessor`, and `PropertyNode`. Their constructors are simple field assignments with no `super()` call.

Leaf subclasses keep manual constructors because they must call `super(name, type)`. Change visibility of base constructors from `protected` to package-private is not needed — Lombok's `@RequiredArgsConstructor` defaults match.

### D3: Static imports for self-explanatory names only

Statically import: `ElementKind.FIELD`, `ElementKind.METHOD`, `ElementKind.CONSTRUCTOR`, `Modifier.PUBLIC`, `Modifier.STATIC`, `Modifier.ABSTRACT`, `Modifier.FINAL`, `TypeKind.VOID`, `Kind.ERROR`, `Comparator.comparingInt`.

Keep qualified: `List.of()`, `Map.of()`, `MappingEdge.Type.DIRECT` (class name provides context per convention).

### D4: Streams with immutable results for simple collection operations

Convert for-loops that build a simple list/set/map from a collection to streams. Use `stream().collect(toUnmodifiableList())`, `stream().collect(toUnmodifiableMap(...))`, or `List.copyOf()` / `Map.copyOf()` for immutable results.

Keep imperative loops for:
- `DiscoverStage.discoverSourceProperties/discoverTargetProperties` — priority-based merging with conditional overwrites
- `BuildGraphStage.execute` — graph mutation + error accumulation with `continue`
- Any loop with side effects on multiple mutable structures

### D5: Protected access on Lombok-generated constructors for abstract bases

Use `@RequiredArgsConstructor(access = AccessLevel.PROTECTED)` on abstract bases to match the current `protected` constructor visibility. This ensures subclasses can call `super()` but external code cannot instantiate abstract bases directly.

## Risks / Trade-offs

- **[Breaking call sites]** All `.name()` → `.getName()` changes must be applied atomically across production and test code. → Mitigation: Single commit, compile-verify before pushing.
- **[Spec updates needed]** Existing specs reference fluent accessors (`name()`, `accessor()`, `paramIndex()`). → Mitigation: Delta specs update the accessor method names.
- **[Lombok version compatibility]** `@RequiredArgsConstructor` with `AccessLevel.PROTECTED` requires Lombok 1.18+. → Already satisfied by current Lombok dependency.
