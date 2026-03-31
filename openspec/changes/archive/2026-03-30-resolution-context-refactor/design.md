## Context

`ResolutionContext` is the API surface for `TypeTransformStrategy` SPI implementations. Currently it exposes `List<DiscoveredMethod>` and `DiscoveredMethod currentMethod` — pipeline-internal models that couple strategies to the processor's analysis output. This prevents strategies from seeing non-abstract methods (default, concrete, inherited) since only abstract methods enter the pipeline via `AnalyzeStage`.

## Goals / Non-Goals

**Goals:**
- Decouple `ResolutionContext` from pipeline models (`DiscoveredMethod`, `MappingMethodModel`)
- Expose `TypeElement` and `ExecutableElement` so strategies can discover all methods on the mapper, including default, concrete, and inherited methods
- Keep `ResolutionContext` as a simple data bag — no convenience methods

**Non-Goals:**
- Adding convenience methods to `ResolutionContext` (strategies filter methods themselves)
- Changing how `ResolveTransformsStage` iterates mapping edges (only the context construction changes)
- Adding new strategies (this change enables them but doesn't add any)

## Decisions

### Decision: Expose TypeElement instead of List\<DiscoveredMethod\>

`ResolutionContext` carries `TypeElement mapperType` instead of `List<DiscoveredMethod> methods`. Strategies use `ctx.getElements().getAllMembers(ctx.getMapperType())` to discover all callable methods — including inherited ones from supertypes.

**Why**: `TypeElement` + `Elements.getAllMembers()` is the standard javax.lang.model API for walking a type's full method set. It covers abstract, default, concrete, and inherited methods without the processor having to pre-collect them. Strategies are free to filter as they see fit.

**Alternative considered**: Pre-filtered `List<ExecutableElement>` in the context. Rejected because it assumes all strategies only care about methods, and it removes the ability for strategies to inspect other type members (fields, annotations, supertypes).

### Decision: Replace DiscoveredMethod with ExecutableElement for currentMethod

`ResolutionContext` carries `ExecutableElement currentMethod` instead of `DiscoveredMethod currentMethod`. This is the minimal information strategies need to exclude self-calls.

**Why**: Strategies don't need property maps or directives for the current method — they just need to know which method to skip.

### Decision: Keep ResolutionContext as a data bag

No convenience methods like `findMethod(source, target)`. Strategies do their own filtering.

**Why**: The SPI is small, the filtering logic is straightforward, and premature convenience methods constrain how strategies can query the type. If a pattern emerges across multiple strategies, a convenience method can be added later.

## Risks / Trade-offs

- **[SPI breaking change]** → All `TypeTransformStrategy` implementations must update to use `TypeElement`/`ExecutableElement` instead of `DiscoveredMethod`. Mitigation: all known implementations are internal to the project.
- **[Strategy complexity]** → Strategies now do their own method filtering instead of iterating a pre-filtered list. Mitigation: the filtering is standard javax.lang.model usage (5-6 lines), and it gives strategies full control over what they consider callable.
