# Percolate — Resolution Stage Design

**Date:** 2026-02-22

## Overview

This design introduces a `ResolutionStage` between `AnalysisStage` and `ValidationStage`, and promotes `TypeMappingStrategy` from a pure code-generator into a first-class participant in the type-resolution graph. The changes address three gaps in the current processor:

1. `@Map` source paths are not validated — invalid paths silently produce broken code
2. `@Map(target = ".", source = "param.*")` wildcard expansion is not implemented
3. Type-mismatch detection and converter lookup are ad-hoc and cannot handle composed conversions (e.g. `Venue → Optional<TicketVenue>` via `mapVenue` + Optional-wrapping)

---

## New Pipeline Order

```
AnalysisStage → ResolutionStage → ValidationStage → GraphStage → CodeGenStage
```

`AnalysisStage` remains a raw reader. All semantic work moves to `ResolutionStage`.

---

## `ResolutionStage` Responsibilities

Takes `AnalysisResult`, produces `ResolutionResult`.

### 1. Source and Target Path Validation

For each `@Map(target, source)` annotation:

- Parse both paths into segments split on `.` — paths may be arbitrarily deep
- **Source:** first segment is the parameter name for multi-param methods; for single-param methods a bare property name (no dot) is relative to the single parameter
- Navigate each segment: validate the named property exists on the type at that level; emit `Messager.ERROR` if any segment is missing
- **Target:** navigate from the return type the same way

### 2. Wildcard Expansion

`@Map(target = ".", source = "order.*")` expands to one concrete `ResolvedMapAnnotation` per first-level property of `order`. The `*` is non-recursive — only the immediate properties of the named parameter are included. All expanded annotations are then path-validated normally. No wildcards survive into `ResolutionResult`.

### 3. Multi-Parameter No-Auto-Match Rule

If a mapping method has more than one parameter, automatic name-matching is **disabled**. Only explicit `@Map` annotations (including expanded wildcards) contribute to coverage. This is recorded per method as `requiresExplicitMappings = true`.

Single-param methods retain automatic name-matching of first-level properties.

### 4. `ConverterRegistry` Construction

Builds a type-resolution graph in two passes:

**Pass 1 — explicit methods:** for every method on every mapper (abstract and default) with exactly one parameter, register `(paramType → returnType) → MethodConverter`.

**Pass 2+ — strategy virtual edges (fixpoint):** loop over all `(source, target)` type pairs that appear in the resolved mappings but have no registry entry. For each, ask every strategy `canContribute(source, target, registry, env)`. If true, add a `StrategyConverter` entry. Repeat until no new entries are added.

User-defined method entries are registered first and are never overwritten by strategies.

---

## `ResolutionResult` Structure

```
ResolutionResult
  ├── List<ResolvedMapperDescriptor>
  │     ├── TypeElement mapperInterface
  │     └── List<ResolvedMappingMethod>
  │           ├── ExecutableElement method
  │           ├── TypeElement targetType
  │           ├── List<VariableElement> parameters
  │           ├── boolean requiresExplicitMappings   (true when >1 param)
  │           └── List<ResolvedMapAnnotation>
  │                 ├── Property targetProperty      (resolved Property object)
  │                 ├── String sourceExpression      (e.g. "order.getVenue()")
  │                 └── TypeMirror sourceType        (resolved type at end of path)
  └── ConverterRegistry
```

---

## `ConverterRegistry`

```java
public final class ConverterRegistry {
    Optional<Converter> converterFor(TypeMirror source, TypeMirror target);
    boolean hasConverter(TypeMirror source, TypeMirror target);
}
```

A `Converter` is one of:
- **`MethodConverter`** — wraps an `ExecutableElement` (explicit mapper method)
- **`StrategyConverter`** — wraps a `TypeMappingStrategy` + the resolved `(source, target)` type pair for use at codegen time

---

## `TypeMappingStrategy` Interface Change

Old:
```java
boolean supports(TypeMirror source, TypeMirror target, ProcessingEnvironment env);
CodeBlock generate(String sourceExpr, TypeMirror source, TypeMirror target,
                   String converterMethodRef, ProcessingEnvironment env);
```

New:
```java
boolean canContribute(TypeMirror source, TypeMirror target,
                      ConverterRegistry registry, ProcessingEnvironment env);

CodeBlock generate(String sourceExpr, TypeMirror source, TypeMirror target,
                   ConverterRegistry registry, ProcessingEnvironment env);
```

The `converterMethodRef` string is dropped — strategies look up what they need from the registry directly.

### Built-in Strategy Changes

**`OptionalMappingStrategy`**

`canContribute`:
- `Optional<A> → Optional<B>`: true if `registry.hasConverter(A, B)`
- `A → Optional<B>` *(new case)*: true if `registry.hasConverter(A, B)`

`generate` for new `A → Optional<B>` case:
```java
Optional.ofNullable(this.mapVenue(sourceExpr))
// converter name resolved from registry.converterFor(A, B)
```

**`CollectionMappingStrategy`**

`canContribute`:
- `List<A> → List<B>`: true if `registry.hasConverter(A, B)`

`generate`: converter method name resolved from registry instead of `converterMethodRef` string.

**`EnumMappingStrategy`**

`canContribute`: both types are enums. Validates constant matching here (not in `generate`) so registry-build-time errors are reported early.

---

## `ValidationStage` Simplification

Takes `ResolutionResult`, checks coverage for every mapping method:

1. Collect all target properties via `PropertyMerger`
2. For each target property:
   - If `requiresExplicitMappings`: only `ResolvedMapAnnotation` entries count
   - Otherwise: also check implicit name-match from the single parameter
3. For any covered mapping, verify `registry.hasConverter(sourceType, targetPropertyType)` — type mismatch with no converter is a fatal error

### Error Categories

**Uncovered target property:**
```
[Percolate] TicketMapper.mapPerson: target property 'actors' has no mapping.
  Consider adding: @Map(target = "actors", source = "<param>.<property>")
  or a converter method: List<TicketActor> mapActors(List<Actor> actors)
```

**Type mismatch, no converter:**
```
[Percolate] TicketMapper.mapPerson: cannot convert 'java.util.List<Actor>' to 'java.util.List<TicketActor>'
  for target property 'actors' — no converter or strategy found.
  Consider adding: TicketActor mapActor(Actor actor)
```

`PartialGraphRenderer` output is appended to both message types.

`ValidationResult` passes `ResolutionResult` through (registry stays available downstream):
```
ValidationResult
  ├── ResolutionResult
  └── boolean hasFatalErrors
```

---

## `GraphStage` Simplification

The type-resolution graph is now owned by `ConverterRegistry`. `GraphStage` retains only the **method-ordering DAG**:

```
GraphResult
  ├── ConverterRegistry                                  (passed through)
  └── DirectedAcyclicGraph<MappingMethod, DefaultEdge>  (call ordering)
```

Edge `A → B` means A's generated body calls B. Topological sort gives safe call order; DAG constraint detects cycles.

---

## `CodeGenStage` Changes

`resolveExpression()` and `resolveSourcePath()` are removed. Per `ResolvedMappingMethod`:

1. Iterate `ResolvedMapAnnotation` entries — authoritative, complete list of mappings
2. `sourceExpression` is already computed — no path navigation at codegen time
3. Type conversion:
   - If `sourceType` is directly assignable to `targetPropertyType` → emit `sourceExpression` directly
   - `MethodConverter` → emit `this.mapX(sourceExpression)`
   - `StrategyConverter` → call `strategy.generate(sourceExpression, source, target, registry, env)`

---

## End-to-End: `TicketMapper.mapPerson`

| Target property | Source expression | Source type | Target type | Converter |
|---|---|---|---|---|
| `ticketId` | `ticket.getTicketId()` | `long` | `long` | none |
| `ticketNumber` | `ticket.getTicketNumber()` | `String` | `String` | none |
| `orderId` | `order.getOrderId()` | `long` | `long` | none |
| `orderNumber` | `order.getOrderNumber()` | `long` | `long` | none |
| `venue` | `order.getVenue()` | `Venue` | `Optional<TicketVenue>` | `StrategyConverter` — Optional wraps `mapVenue` |
| `actors` | `ticket.getActors()` | `List<Actor>` | `List<TicketActor>` | `StrategyConverter` — Collection via `mapActor` |

Generated body:
```java
return new FlatTicket(
    ticket.getTicketId(),
    ticket.getTicketNumber(),
    order.getOrderId(),
    order.getOrderNumber(),
    Optional.ofNullable(this.mapVenue(order.getVenue())),
    ticket.getActors().stream().map(this::mapActor).collect(Collectors.toList())
);
```
