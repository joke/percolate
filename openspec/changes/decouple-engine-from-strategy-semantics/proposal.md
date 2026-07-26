## Why

Three validation stages in the processor reimplement decisions that belong to SPI strategies:
`ValidateEnumOverridesStage` reimplements `EnumConversion`'s constant rules from raw `javax`,
`ValidateAmbientBindingsStage`'s own javadoc admits it works by "independently re-deriving" what
`MethodCallBridge`/`PortSourceResolver` attempted, and `ValidateConstantDefaultLegalityStage` owns the
decision of *when* to ask `LiteralCoercion`. Applying the test "if there were no SPI strategies at all,
would this stage still make sense?" — all three fail it.

The generative cause is that `ExpansionStrategy.expand` returns `Stream<OperationSpec>`, so `Stream.empty()`
conflates "not applicable here" with "applicable, and the author made a mistake". The reason is destroyed at
the return statement, and every targeted message must therefore be manufactured outside the SPI by
re-deriving the SPI's own decision. `EnumConversion` has no channel at all for its two failure modes and
throws `IllegalStateException` at render time — a **live processor crash**, reachable from
`@Mapper interface M { Status map(String tag); }`, because a bare `PortType.variable(0)` grounds against any
declared source type and nothing lets the strategy veto the grounding.

The same coupling is visible in the engine's own vocabulary. `Port.Sourcing.AMBIENT`, `Port.key`,
`AmbientDecl` and `internal/graph/AmbientKeys` (which imports `io.github.joke.percolate.Ambient`) encode one
user-facing feature into the graph engine, where `AmbientDecl` is a near-duplicate of `InputDecl` and the
only real differences are *how a port selects* (by type vs. by name) and *what a miss means* (decline, mint,
or error). Meanwhile `@Map`'s member vocabulary is hand-rolled in six places, so adding one member touches
the annotation, `RawDirective`, `MappingDirectiveBuilder`, `MappingDirective` (two fields plus a predicate),
`BindingDirective`, `Directive`, and a hardcoded key registry inside a core validation stage.

Doing this now, as one change, is deliberate: every partial version of it — a reason channel without an open
directive surface, an ambient fix without a port model, a `using` pin without a constraint contribution point
— re-surfaces the same problem in the next change. The in-flight `add-explicit-conversion-method` branch
already stalled on exactly this, having grown a feature-specific `List<Rejection>` field on `MapperGraph`.

## What Changes

**A — the SPI's answer shape.**

- **BREAKING** `expand` and `descend` return `Stream<Offer>`; an `Offer` is a production or a refusal
  (`subject`, message). `Stream.empty()` still means "not mine". 21 direct overriders change mechanically;
  the 13 strategies behind `Conversion`/`Accessor`/`Container` are insulated by those bases.
- **BREAKING** `directive()` moves up from `ProduceDemand` to `Demand`, closing the other asymmetry between the
  two shapes: an accessor can now read the configuration of the binding whose source path it is walking.
- Refusals are anchored on the demanded `Value` (`inadmissible`), never as `Operation` vertices, so code
  generation provably cannot reach them. `RealisationDiagnosticsStage` renders them at the deepest miss in
  place of its generic "no plan" line.
- **BREAKING** `Directive` becomes an open, keyed bag of `DirectiveInput`s carrying an **opaque** `Subject`
  positioning handle, replacing the enumerated `constant()`/`defaultValue()`/`format()`/`zone()` accessors.
  Adding a `@Map` member stops touching the core at all.
- **BREAKING** `Map.UNSET` is deleted. `AnnotationMirror.getElementValues()` returns only explicitly written
  members, which yields presence, empty-string-is-present, and positioning for free — the sentinel exists
  only to undo auto-common's defaults-filling. `RawDirective` and `MappingDirectiveBuilder` are deleted and
  the two annotation readers merge into one parameterised helper.
- `PortType.variable(index, Bound)` adds a bounded type variable whose `Bound` returns an optional refusal.
  `EnumConversion` uses it to reject non-enum and uncovered-constant groundings, deleting both crash sites.

**B — dissolving "ambient" into orthogonal axes.**

- **BREAKING** `Port.Sourcing`'s four modes and the `Port.key` field (dead in three of four modes) are
  replaced by two axes: a **selector** (by type / by name) and an **on-miss** rule (decline / mint / require).
  `AMBIENT` becomes by-name + require; `REUSE` becomes by-type + decline.
- `InputDecl` and `AmbientDecl` collapse into one scope-input declaration carrying a name and a
  **visibility** (local / inherited). By-type selection searches the local scope; by-name selection also
  searches ancestors' inherited declarations — reproducing today's behaviour exactly.
- `internal/graph/AmbientKeys` is deleted; `@Ambient` is read at the discovery boundary.
  **BREAKING** `ResolveCtx.ambientKey` is removed — `MethodCallBridge` reads the annotation directly.
- `ValidateAmbientBindingsStage` is deleted: an unsourceable *required* port is the engine failing its own
  declared port contract, reported in port vocabulary with no candidate re-walk.

**C — directive semantics move to the SPI.**

- New service-loaded `DirectiveReader` role with a `DirectiveSink`. `@Mapper` stays core (it decides *what to
  generate*); `@Map`, `@MapEnum` and `@Ambient` become inert vocabulary whose meaning ships beside the
  built-in strategies. Three readers replace the discovery chain's annotation logic.
- `DirectiveSink.constrain(...)` lets whoever owns a member's meaning contribute a **demand-scoped
  admissibility constraint**. This is not a new mechanism: it generalises the engine's only existing
  enforcement primitive (`SelfCallGuard`'s outright refusal), which stops being hardcoded. Constraints at a
  demand are AND-composed; "opposing constraints" is simply an empty conjunction, explained by the refusals.
- Third-party annotations become possible without touching the core — an axis the SPI has never had.

**D — diagnostics become values attributed to a unit of work.**

- `Diagnostics.error(element, …)` uses one `Element` for two purposes — where the IDE underlines, *and* which
  mapper is broken — and approximates the second with a one-level `getEnclosingElement()` heuristic. That is
  why ambient errors are positioned at the mapper type rather than at the offending parameter. Diagnostics
  become values (`severity`, opaque `position`, message, `permanent` flag) collected on the per-mapper context
  and written to the `Messager` by one flush point.
- **Forced, not opportunistic**: `MapperStep` classifies a mapper as *scarred* (consume) or *deferred* (retry
  next round, for Lombok interop), which works only because errors and deferral are today mutually exclusive.
  Refusals render at the deepest miss — exactly the deferred case — and mix messages that are wrong in every
  round with ones that may resolve next round. Deferrability becomes a per-diagnostic property, transient by
  default with an explicit `permanent` opt-out.
- Deletes the `Diagnostics` `@Singleton` and its cross-mapper mutable state, `hasErrorsFor`, `reset()`, and
  `RealisationDiagnosticsStage`'s bespoke record-then-flush, which generalises to every diagnostic.

**E — built-in strategies move into feature packages.** `spi.builtins.enumconversion/`, `…temporal/`,
`…methodcall/`, `…container/`, `…accessor/`, `…value/`, `…assembly/`, `…primitive/`, each holding a feature's
strategies and its reader. "Delete the package, delete the feature" becomes literally true — the
strategy-removal test, made structural. A pure move, sequenced last.

**Riding along** (independent, no SPI change): `MemberPlan.forMapper` currently resolves duplicate
`MemberRequest` dedup keys with `putIfAbsent` — two operations requesting one class member with different
definitions silently share the first, generating deterministically wrong code. Conflicting declarations
become an error. `DotRenderer.nullnessOf` matches any annotation whose *simple name* is `Nullable`, a second
divergent nullness rule inside the engine that bypasses `NullabilityResolver`; it is corrected.

**Net effect on validation**: eight stages become five, and one survivor (`ValidateMappingShapeStage`, which
enforces `@Map`'s own shape) leaves the processor module entirely.

## Capabilities

### New Capabilities

- `strategy-refusals`: the three-valued strategy answer (`Offer` = production | refusal | silence), the
  opaque `Subject`, refusals anchored on a demanded `Value`, and the rule that they render at the deepest
  unsatisfied miss.
- `demand-constraints`: contributed demand-scoped admissibility constraints, their AND-composition, and the
  unification of the engine's existing landing refusals onto that one primitive.
- `directive-reading`: the `DirectiveReader`/`DirectiveSink` SPI role, generic written-member annotation
  reading including repeatable-container unwrapping, and the open `Directive` bag it populates.

### Modified Capabilities

- `expansion-strategy-spi`: `expand`/`descend` return `Stream<Offer>`; `Directive` becomes an open bag;
  `Port`'s two axes replace `Sourcing`; `PortType.variable` gains a bound; `ResolveCtx.ambientKey` removed.
- `graph-expansion`: `Value` carries inadmissibility; scope inputs unify under name + visibility; by-name /
  require port sourcing replaces the ambient environment; the self-call rule becomes a contributed constraint.
- `graph-model`: one scope-input declaration type; `Scope` becomes plain data (its nullability callback goes);
  `Value` gains its refusal list.
- `mapping-discovery`: discovery runs readers rather than reading `@Map` itself; `Map.UNSET` presence testing
  is replaced by written-member reading; `RawDirective`/`MappingDirectiveBuilder` removed.
- `mapping-validation`: eight stages to five; duplicate targets become a sink conflict; source-root checking
  becomes an engine rule about its own walk; directive shape moves to the built-in reader.
- `directive-options`: the consumption rail generalises from two hardcoded keys to every declared input, at
  per-entry granularity.
- `ambient-parameters`: `@Ambient`'s meaning moves to a reader; keys become named inherited scope inputs; its
  dedicated validation stage dissolves.
- `realisation-validation`: `RealisationDiagnosticsStage` becomes the single renderer for refusals, and its
  bespoke record-then-flush generalises into the diagnostic model.
- `diagnostics`: diagnostics become values whose **position** is independent of their **attribution**;
  scarring by `getEnclosingElement()` containment is replaced by collection on the unit of work; deferrability
  becomes per-diagnostic (transient by default); one flush point replaces eager emission.
- `processor`: the pinned ordered `Stage` list changes; `MapperStep`'s outcome classification becomes
  "unrealised **and** every diagnostic transient" rather than "not scarred".
- `code-generation`: conflicting `MemberRequest` definitions under one dedup key are an error.
- `enum-conversion`: declines instead of throwing; target-side override checks ride the consumption rail;
  source-side checks become a bound.
- `constant-values`, `default-values`: `Map.UNSET` removed from their presence rules.
- `nullability`: `Scope` no longer takes a resolver callback; the resolver stays SPI-facing via `Demand`.
- `graph-debug-output`: the `full` **and** `plan` dumps render inadmissible productions, so a cost surprise is
  debuggable from the negative space; the renderer's shadow nullness rule is removed.
- `module-boundaries`: two new rules — nothing in `processor` imports `@Map`/`@MapEnum`/`@Ambient` (only
  `MapperStep` imports `@Mapper`), and no engine class calls `getAnnotationMirrors()`.
- `builtin-strategy-unit-tests`: the enumerated spec roster gains the readers and moves to feature packages.
- `expansion-test-harness`: the discovery-decomposition requirement no longer references `Map.UNSET`.
- `user-manual`: the `UNSET` presence rule is removed from the `@Map` documentation.

## Impact

**Modules**: `spi` (five breaking surface changes plus `Subjects`), `processor` (discovery chain, engine graph
package, expansion collaborators, three validation stages deleted, the `Diagnostics` singleton deleted),
`strategies-builtin` (21 mechanical edits, three new readers, a package restructure),
`reactor` and `reactor-blocking` (mechanical, 11 files), `annotations` (`Map.UNSET` deleted),
`architecture-tests` (two rules), `test-foundation` and every strategy spec suite.

**Downstream**: every third-party `ExpansionStrategy` implementation breaks and must be recompiled. Percolate
has not yet published a release with a stable SPI, so this is the moment to take the breakage.

**User-visible behaviour**: two `IllegalStateException` processor crashes become positioned compile errors;
one silently-wrong code-generation path becomes an error; ambient errors gain exact positions instead of
pointing at the mapper type; diagnostics arrive grouped per mapper rather than interleaved, so any e2e
assertion on diagnostic *order* must be rewritten to assert on content and position; several targeted messages
change wording, and a few weaken from a specific reason to "declared but had no effect" positioned at the same
token. Those trade-offs are enumerated in the design.

**Size**: this is larger than any change in the repo's history (`add-ambient-parameters`, 51 tasks).
Twenty-two spec files change across six sequenced groups (③, D, A, B, C, E), each independently revertable and
each leaving the build green. The spec rewrite is the single largest artifact and is scheduled as its own task
group.
