## MODIFIED Requirements

### Requirement: Stream stages render as a threaded pipeline

Each plain container Operation in the plan (`iterate`, `collect`, `wrap`, `unwrap`) SHALL render by
threading its single container snippet onto the rendered expression of its operand, so a chain of such
Operations composes into one fluent pipeline expression. The generator SHALL NOT fuse
`iterate`/`map`/`collect` into a single Operation's codegen.

The threaded pipeline expression SHALL carry a soft wrap point (JavaPoet `$Z`, a zero-width space)
immediately before each chained call's leading `.`, so that once the fully-threaded expression exceeds
JavaPoet's column limit it wraps at a call boundary onto a continuation line, rather than rendering as
one unbroken line. Below the column limit the wrap point SHALL have no visible effect — the rendered
text SHALL be identical to today's unwrapped form. This is a purely textual property of each
Operation's own codegen snippet; the composer (`BuildMethodBodies`) SHALL NOT itself insert, remove, or
otherwise manage wrap points — it continues to only thread operand expressions together.

#### Scenario: Cross-kind pipeline threads stage by stage
- **WHEN** the plan for `List<Optional<A>> → Optional<Set<B>>` is
  `wrap ⟵ collect ⟵ map ⟵ flatMap ⟵ iterate`
- **THEN** the rendered expression is a single chain
  `Optional.ofNullable(src.stream().flatMap(…).map(…).collect(…))`, each stage's snippet threaded onto
  its operand

#### Scenario: A short pipeline renders unwrapped
- **WHEN** the fully-threaded pipeline expression for a method body fits within JavaPoet's column limit
- **THEN** the generated source renders it on one line, identical to its pre-wrap-point rendering (no
  stray whitespace introduced by the wrap point)

#### Scenario: A long pipeline wraps at a call boundary
- **WHEN** the fully-threaded pipeline expression for a method body would exceed JavaPoet's column limit
  on one line
- **THEN** the generated source breaks the line immediately before a chained call's `.`, continuing on
  an indented line, rather than emitting one line longer than the column limit

## ADDED Requirements

### Requirement: Any chain-continuation codegen rendering carries a wrap point

Every first-party `OperationCodegen`/`ScopeCodegen` rendering SHALL carry a `$Z` immediately before the
leading `.` of any call it chains onto a rendered operand (its format string appends `.methodName(...)`
after the spliced-in operand) — not only container/stream pipelines, but any capability's strategy.
This covers, without limitation: the nullness `[coalesce]` crossing's `Optional.orElse(D)` form, the
accessor path resolvers
(`GetterPathResolver`, `MethodPathResolver`, `FieldPathResolver`), `MethodCallBridge`'s method-call
rendering, `PrimitiveWrapperConversion`'s unbox accessor, and the `reactor`/`reactor-blocking` scalar
bridges (`CollectList`, `FluxSingle`, `SingleOptional`, and the blocking crossings). A rendering that
prepends rather than chains (a cast `"($T) $L"`, a bare `"this"`, a `wrap`-style `"$T.of($L)"`) has no
leading `.` to mark and is unaffected. This requirement governs rendered-text composition uniformly; it
does not change any of those other capabilities' matching, weighting, or port semantics.

#### Scenario: A nullness coalesce crossing carries a wrap point
- **WHEN** the `[coalesce]` Operation's `Optional<T> → T` form is rendered
- **THEN** its `orElse(...)` call's leading `.` carries a `$Z`, so a long coalesce expression composed
  with other operand expressions wraps at that boundary rather than overflowing one line

#### Scenario: An accessor path segment carries a wrap point
- **WHEN** `GetterPathResolver`, `MethodPathResolver`, or `FieldPathResolver` renders its accessor call
  or field reference onto the parent operand
- **THEN** the leading `.` of that accessor carries a `$Z`, so a long source-path descent composed of
  several accessor segments wraps at a segment boundary rather than overflowing one line
