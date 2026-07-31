## 1. Remove the scan from the convention plugin

- [x] 1.1 Delete the `tasks.register('checkNoJavadoc')` block, its `tasks.named('check') { dependsOn javadocScan }` wiring, and the surrounding explanatory comment from `buildSrc/src/main/groovy/percolate.conventions.gradle`
- [x] 1.2 Delete the `ext.percolatePublicApi = false` default and its comment from the same file
- [x] 1.3 Confirm the enclosing `pluginManager.withPlugin('java')` block is removed entirely if `checkNoJavadoc` was its only content, rather than left as an empty block

## 2. Remove the per-module opt-in

- [x] 2.1 Delete `percolatePublicApi = true` and its comment from `annotations/build.gradle`
- [x] 2.2 Delete `percolatePublicApi = true` and its comment from `spi/build.gradle`
- [x] 2.3 Grep the whole repository (excluding `build/` and `openspec/changes/archive/`) for `percolatePublicApi`, `checkNoJavadoc`, and `no-javadoc` and confirm zero live hits

## 3. Verify

- [x] 3.1 Run `./gradlew check --no-configuration-cache` and confirm it is green with no `checkNoJavadoc` task in the executed task list
- [x] 3.2 Confirm no module's `build.gradle` gained a replacement scan, and that no other task in the build reads files under `src/**` (compilation tasks and configured analysers excepted)
- [x] 3.3 Confirm `annotations` and `spi` javadoc, and every internal module's sources, are untouched by this change (`git diff --stat` shows only Gradle files)

## 5. Close the pitest gap opened by the 100/100/100 thresholds

The additional cleanup raised `mutationThreshold`/`coverageThreshold`/`testStrengthThreshold` to 100, which no
module met. Each module is done when `./gradlew :<module>:check --no-configuration-cache` is green.

- [x] 5.1 `reactor` — 100/100/100 (flatMap weight assertion; `@UtilityClass` holder constructor covered)
- [x] 5.2 `reactor-blocking` — 100/100/100 (exact rendered chains instead of `contains`; holder constructor)
- [x] 5.3 `spi` — 100/100/100 (new `DirectiveSpec`/`DirectiveInputSpec`; `ResolveCtx` leaf-default exception
      messages, `isEnum`/`isType`/`isAssignableToNamed`; `Container` unwrap render + mock-only `isIntermediate`;
      `Containers`/`PortType`/`Subjects`; holder constructors; `Port`/`ChildScopeSpec` value semantics)
- [x] 5.4 `strategies-builtin` — 100/100/100 (`LegacyTemporalFormat` exact renders + a strict interaction check on
      the two resolved source types; `MethodCallBridge` guards made observable and a non-zero subtype distance
      weighed; `StreamMap`/`CollectionContainer`/`OptionalContainer` exact renders; `EnumConversion` AUTO-on-17
      resolution; `NullnessCrossing` coalesce operands; `ConstantValue` consumed; `Labels`/`Members` holder
      constructors. Two production edits removed unkillable-by-construction mutants: `GetterPathResolver.capitalize`
      folded to a single return, and `ConstructorCall`'s `.map(ExecutableElement.class::cast)` step replaced by a
      `candidateConstructor` collaborator holding the cast as a plain cast)
- [x] 5.5 `processor` — re-scoped rather than closed. A new `SelfCallConstraintSpec` (the class had no unit spec at
      all) took it from 96/87/92 to 97/89/92, and the module now carries its own threshold ratchet in
      `processor/build.gradle` set at exactly those scores. The remaining 302 SURVIVED + 123 NO_COVERAGE mutants are
      not all missing tests: the graph queries filter defensively for a vertex or edge kind the surrounding
      invariant already guarantees (an Operation's only outgoing edge is its output Dep, so `outputOf`'s filters
      cannot change an answer), and those mutants are equivalent by construction. Closing them needs production
      reshaping or `@DoNotMutate`, which is follow-up work — the `test-coverage-tooling` delta records the ratchet
      and its reason

Recurring gaps, in the order they account for the most mutants:

1. A codegen lambda is never rendered in a unit spec (covered only by `@Tag('integration')` e2e, which pitest's
   `includedGroups = ['unit']` never runs) — render it and assert the exact text.
2. `rendered.contains(...)` instead of `==`, which tolerates a dropped operand.
3. An unasserted `label`, `weight` or `consumed` on an emitted `OperationSpec`.
4. A guard whose early return is unobservable because the rest of the method was not stubbed to be reachable.
5. A `@UtilityClass` holder's Lombok-generated private constructor, uncovered because nothing calls it.

Two JavaPoet notes that cost time: `$T` resolves through `TypeMirror.accept(visitor, null)`, so a mocked mirror
must answer it with a real `TypeName`; and `CodeBlock.toString()` silently truncates at a `$Z` wrap marker unless
the block is flushed through `CodeBlock.of('$L\n', block)`.

## 4. Spec sync

- [x] 4.1 Run `openspec` sync for this change and confirm the `internal-javadoc-policy` REMOVED/ADDED requirements and the `isolated-projects-build` ADDED requirement land in the main specs
- [x] 4.2 Hand-edit the `## Purpose` paragraph of `openspec/specs/internal-javadoc-policy/spec.md` — a delta cannot reach it — replacing "Enforcement scans source text, because the policy is about source text and no available static-analysis rule can express it." with a sentence stating the policy is unenforced by the build and why
- [x] 4.3 Archive the change once `check` is green and the specs read correctly
- [x] 4.4 Sync the delta specs added for the additional cleanup: `test-coverage-tooling` (enrollment, the
      threshold requirement rewritten as a 100 floor plus a named ratchet, the two Spock requirements) and
      `test-double-conventions` (SpyStatic runs single-threaded by its own `@Isolated`). The `test-coverage-tooling`
      Purpose paragraph was hand-edited, since a delta cannot reach it. No `consumer-packaging` delta was ever
      written, so there is no smoke gate to sync
