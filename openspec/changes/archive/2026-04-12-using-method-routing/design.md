## Context

The processor resolves type gaps between source and target properties using a BFS expansion loop over `TypeTransformStrategy` implementations. `MethodCallStrategy` discovers callable methods on the mapper via `getAllMembers` and selects the first one with compatible parameter and return types (`.findFirst()`). There is no way to direct a specific mapping to use a specific method, and when multiple methods match the same type pair, selection is arbitrary.

The `@Map` annotation currently has `source`, `target`, and `options` (from the temporal-string-mapping change). `MapDirective` carries these fields through `MappingEdge` into `ResolutionContext`, which strategies read during BFS expansion.

## Goals / Non-Goals

**Goals:**
- Allow users to specify which mapper method handles a specific property mapping via `using` on `@Map`
- Replace arbitrary first-match method selection with most-specific-match resolution
- Compose `using` with container strategies (Optional, Stream, Collection) via the existing BFS element constraint mechanism
- Emit compile errors when a `using` method cannot be resolved

**Non-Goals:**
- `@Named` annotation on methods (method name is the identifier)
- Aliasing or renaming methods for qualification purposes
- Restricting non-method strategies when `using` is set (container strategies always participate)

## Decisions

### Decision 1: `using` as a dedicated `@Map` attribute, not a `@MapOpt`

Add `String using() default ""` to `@Map`:

```java
@Map(source = "date", target = "dateStr", using = "formatDate")
```

**Why over `@MapOpt(key = USING, value = "formatDate")`**: `using` is a routing directive — it controls which method handles the mapping. `@MapOpt` is for strategy parameters (like `DATE_FORMAT`) that configure how a strategy generates code. These are fundamentally different concerns. A dedicated attribute is more discoverable, reads naturally, and gets IDE autocomplete without enum lookup.

**Why `using` over `qualifiedByName`**: `qualifiedByName` is MapStruct jargon that implies a `@Named` annotation. Since we match on the actual method name, `using` is both shorter and more accurate. It reads naturally: "map date to dateStr using formatDate."

### Decision 2: `using` threads through `MapDirective` → `MappingEdge` → `ResolutionContext`

```
@Map(using="fmt") → MapDirective.using → MappingEdge.using → ResolutionContext.using
```

The flow mirrors how `options` already threads through the same path:

1. `AnalyzeStage.parseDirective` reads `map.using()` into `MapDirective`
2. `BuildGraphStage.processDirectives` passes `using` to `MappingEdge`
3. `ResolveTransformsStage.resolveMethod` reads `using` from `MappingEdge` and creates a per-mapping `ResolutionContext` that includes it

`ResolutionContext` gains a `String using` field (empty string = not set). Auto-mapped edges always have empty `using`.

**Why on `ResolutionContext` rather than a separate mechanism**: The context already propagates into element-level sub-BFS (via `canAddEdge` and `resolveCodeTemplate`). This means `using` automatically reaches `MethodCallStrategy` when resolving the element type inside `Optional<X> → Optional<Y>` or `Stream<X> → Stream<Y>` — no special plumbing needed.

### Decision 3: Most-specific-match resolution in `MethodCallStrategy`

Replace `.findFirst()` with a specificity ranking:

```java
// 1. Filter to candidates (name + type compatible)
// 2. Sort by param specificity (narrowest first)
// 3. Tiebreak by return type specificity (narrowest first)
// 4. Take first
```

**Param specificity**: Among candidates where `isAssignable(sourceType, paramType)`, prefer the candidate whose `paramType` is the most specific (narrowest). Concretely: candidate A is more specific than B if `isAssignable(A.paramType, B.paramType)` (A's param is a subtype of B's param).

**Return type tiebreaker**: Among candidates with equal param specificity, prefer the one whose return type is most specific by the same `isAssignable` logic.

This mirrors Java's own overload resolution (JLS 15.12.2.5 — most specific method).

```
Candidates for using = "format", sourceType = LocalDate, targetType = String:

  String format(Object o)     — Object param, String return
  String format(Temporal t)   — Temporal param, String return
  String format(LocalDate d)  — LocalDate param, String return

  LocalDate <: Temporal <: Object
  Winner: format(LocalDate) — most specific param
```

**Why this applies regardless of `using`**: The specificity ranking improves method selection even without `using`. When `using` is not set, all compatible methods are candidates. When `using` is set, only methods with the matching name are candidates. The ranking logic is the same in both cases.

### Decision 4: `using` constrains only `MethodCallStrategy`

When `using` is set on a `ResolutionContext`:
- `MethodCallStrategy` filters candidates to methods matching the name
- All other strategies (`DirectAssignableStrategy`, `OptionalMapStrategy`, `StreamMapStrategy`, etc.) ignore the `using` field entirely

This means container composition works naturally:

```
@Map(source = "dates", target = "dateStrs", using = "formatDate")
// dates: List<LocalDate>, dateStrs: List<String>

BFS resolves:
  List<LocalDate>
    → Stream<LocalDate>       (StreamFromCollectionStrategy)
    → Stream<String>          (StreamMapStrategy, element constraint:)
        └─ LocalDate → String (MethodCallStrategy, filtered to "formatDate")
    → List<String>            (CollectToListStrategy)
```

### Decision 5: Compile error when `using` method is unresolvable

When `using` is set but `MethodCallStrategy` finds no method with that name (or no method with that name has compatible types), the overall mapping resolution fails. `ValidateTransformsStage` already reports unresolved mappings as compile errors. The error message should include the `using` method name to help the user diagnose typos or signature mismatches.

**Why not fail eagerly in `AnalyzeStage`**: At analysis time we don't yet have resolved types for the source chain. We only know the method name is set. The type compatibility check requires the BFS resolution context, so validation naturally happens when `MethodCallStrategy` returns empty and no other strategy fills the gap.

**Why not fail in `MethodCallStrategy` itself**: Strategies signal "I can't handle this" via `Optional.empty()`, not via errors. The error channel is the validation stage. However, `MethodCallStrategy` could attach diagnostic metadata (e.g., "found method `formatDate` but param type `Instant` is not assignable from `LocalDate`") to aid error reporting.

## Risks / Trade-offs

**[Risk] Ambiguous specificity ranking when no single most-specific candidate exists** → Two candidates could have incomparable param types (neither is assignable to the other). Mitigation: emit a compile error listing the ambiguous candidates, similar to javac's "reference to X is ambiguous" error.

**[Risk] `using` method exists but is the current method (self-reference)** → `MethodCallStrategy` already excludes `ctx.getCurrentMethod()`. If `using` points to the current method, it will be filtered out and resolution fails with a clear error. No special handling needed.

**[Trade-off] `using` as empty string vs. Optional** → Java annotations cannot have `null` defaults, so `String using() default ""` with empty-string-means-unset is the standard pattern. This is idiomatic for annotation APIs.

**[Trade-off] Most-specific-match changes existing behavior** → Without `using`, method selection changes from arbitrary first-match to deterministic most-specific. This could change which method is selected for existing mappers that have multiple compatible methods. This is a behavioral improvement (more predictable), but technically a breaking change for anyone relying on the previous arbitrary order. Risk is low — relying on undefined order is inherently fragile.
