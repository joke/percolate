# path-optimization Specification

## Purpose
TBD - created by archiving change value-graph-refactor. Update Purpose after archive.
## Requirements
### Requirement: OptimizePathStage runs between resolve and validate

`OptimizePathStage` SHALL run after `ResolvePathStage` and before `ValidateResolutionStage` in the pipeline. Its input SHALL be the per-assignment resolved paths (`Map<MethodMatching, List<ResolvedAssignment>>`) produced by `ResolvePathStage`; its output SHALL have the same shape. The stage SHALL NOT change which assignments resolved successfully; it only mutates the code-level shape of already-resolved paths.

#### Scenario: OptimizePathStage receives and returns resolved paths

- **WHEN** `ResolvePathStage` produces a `Map<MethodMatching, List<ResolvedAssignment>>` with `N` resolved assignments
- **THEN** `OptimizePathStage` SHALL return a `Map<MethodMatching, List<ResolvedAssignment>>` with the same `N` resolved assignments in the same order

#### Scenario: Failed resolutions pass through unchanged

- **WHEN** a `ResolvedAssignment` from `ResolvePathStage` has `path == null` (resolution failure)
- **THEN** `OptimizePathStage` SHALL pass it through unchanged without attempting optimization

### Requirement: OptimizePathStage materialises code templates on path edges

For every `TypeTransformEdge` on a resolved path and every `LiftEdge` on a resolved path, `OptimizePathStage` SHALL compute and set the `codeTemplate` field exactly once. Edges not on any resolved path SHALL NOT have their `codeTemplate` populated.

Template derivation rules:

- For `TypeTransformEdge`: the template is the `CodeTemplate` from the strategy's original `TransformProposal` (`edge.getStrategy().resolveCodeTemplate(...)` or equivalent). This is the only stage that may call this resolution method.
- For `LiftEdge`: the template is derived from the recursively materialised templates of edges on the `innerPath`, composed according to `LiftKind`:
  - `OPTIONAL`: `$src.map(x -> $innerTemplate(x))`
  - `STREAM`: `$src.map(x -> $innerTemplate(x))`
  - `COLLECTION`: loop emission that applies `$innerTemplate` element-wise to `$src`
  - `NULL_CHECK`: reserved — not constructed by this refactor.

`GenerateStage` SHALL NOT call any template resolution function; it SHALL only read already-materialised templates.

#### Scenario: TypeTransformEdge template is set exactly once

- **WHEN** an optimized path contains a `TypeTransformEdge`
- **THEN** after `OptimizePathStage` the edge's `codeTemplate` SHALL be non-null, AND invoking `OptimizePathStage` a second time on the same resolved paths SHALL NOT raise (idempotent) and SHALL NOT produce a different `codeTemplate`

#### Scenario: Off-path edges never get a template

- **WHEN** `ResolvePathStage` produces a path using 3 of 7 available `TypeTransformEdge`s
- **THEN** after `OptimizePathStage` the 4 off-path edges SHALL have `codeTemplate == null`

#### Scenario: LiftEdge template derives from innerPath

- **WHEN** an optimized path contains `LiftEdge(OPTIONAL, innerPath)` whose `innerPath` contains a single `TypeTransformEdge` with template `x -> mapFoo(x)`
- **THEN** the `LiftEdge.codeTemplate.apply(srcExpr)` SHALL render as `srcExpr.map(x -> mapFoo(x))` (modulo JavaPoet's spacing)

#### Scenario: GenerateStage does not invoke template resolution

- **WHEN** `GenerateStage` processes an optimized path
- **THEN** it SHALL only read `edge.getCodeTemplate()` and SHALL NOT call any method that would re-derive the template from a `TypeTransformStrategy` or `TransformProposal`

### Requirement: OptimizePathStage is an extension seam for future peephole rewrites

The stage's responsibility in this refactor is limited to template materialization. The stage SHALL be structured so that additional passes (e.g. null-lift fusion from the `jspecify-nullability` change) can be added without restructuring the pipeline: each pass SHALL consume and produce `Map<MethodMatching, List<ResolvedAssignment>>`. This refactor SHALL NOT implement any such rewrite itself.

#### Scenario: Template-materialization pass is the only pass in this refactor

- **WHEN** the full processor test suite runs on the value-graph-refactor branch
- **THEN** `OptimizePathStage` SHALL execute exactly one pass (template materialization) per invocation

#### Scenario: Adding a future pass is non-breaking

- **WHEN** a follow-on change adds a peephole pass to `OptimizePathStage`
- **THEN** the addition SHALL NOT require changes to `ResolvePathStage`, `ValidateResolutionStage`, or `GenerateStage`, nor to the `Map<MethodMatching, List<ResolvedAssignment>>` shape

### Requirement: OptimizePathStage emits no new diagnostics in this refactor

Template materialization is a purely internal operation. If a template cannot be derived, it indicates an earlier-stage invariant violation, not a user error. `OptimizePathStage` SHALL treat template-derivation failure as an assertion failure (internal bug) rather than a `Diagnostic`.

#### Scenario: Template derivation failure is a programmer error

- **WHEN** a `TypeTransformEdge` is on a resolved path but its `strategy.resolveCodeTemplate(...)` throws or returns null
- **THEN** `OptimizePathStage` SHALL raise an `IllegalStateException` (or equivalent) referencing the edge; it SHALL NOT emit a user-facing `Diagnostic`
