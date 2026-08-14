## 1. Unblock: consume the upstream rule fixes

- [x] 1.1 Confirm the `pmd-rules` change has shipped all three fixes: the named-constructor exemption and Lombok `@UtilityClass` recognition in `StaticMethodsModifyStaticState`, and `@ApiStatus.OverrideOnly` acceptance in `AvoidPrivateAndProtectedMethods`. **Do not start any later group before this lands** — design D6
- [x] 1.2 Pin `io.github.joke.pmd:rules` to that **released** version in the `pmd` configuration; no `-SNAPSHOT` coordinate survives anywhere — unblocked 2026-08-12: `io.github.joke.pmd:rules:0.1.0` is on Maven Central, pinned in `percolate.conventions.gradle`
- [x] 1.3 Remove the local snapshot repository added to make `0.1.0-SNAPSHOT` resolve, if nothing else needs it — the `Central Portal Snapshots` repository was added by this change and had no other consumer; removed, `:spi:dependencies --configuration pmd` resolves `io.github.joke.pmd:rules:0.1.0` from `mavenCentral()` alone
- [x] 1.4 Re-measure the full violation set across every source set and record the new baseline per rule — the exemptions should erase roughly 53 `StaticMethodsModifyStaticState` hits

**Baseline after the upstream fixes — 775 violations (was 836).**

| Rule | Count | | Source set | Count |
|---|---|---|---|---|
| `UseVisibleForTestingAnnotation` | 334 | | `processor:main` | 403 |
| `UseStaticImports` | 308 | | `strategies-builtin:main` | 184 |
| `UseVarForLocalVariables` | 65 | | `architecture-tests:archRules` | 76 |
| `StaticMethodsModifyStaticState` | 21 | | `spi:main` | 55 |
| `AvoidLambdaBlockBodies` | 20 | | `reactor:main` / `reactor-blocking:main` | 20 / 20 |
| `AvoidPrivateAndProtectedMethods` | 15 | | `architecture-tests:archRulesTest` | 6 |
| `UseTypeImports` | 8 | | `strategies-builtin:test` | 6 |
| `AvoidAnonymousClasses` | 4 | | `annotations:test` / `spi:test` | 4 / 1 |

The two overlapping rules dropped 76 → 36, and 19 of the 36 survivors sit in fixtures and
`MethodShapeRules.java` that group 4 deletes. The named-constructor and `@UtilityClass` exemptions both
verified live (`NullabilityAnnotations#jspecifyDefaults` no longer flagged).

## 2. Finalise the PMD wiring and local composition

- [x] 2.1 Finalise the in-progress `pluginManager.withPlugin('pmd')` block in `percolate.conventions.gradle`: external ruleset, explicit tool version, pinned artifact. No module name, no branch on a project — [[feedback_no_special_handling_in_conventions]]
- [x] 2.2 Measure the largest legitimate class repo-wide and set `TooManyMethods`' `maxmethods` from it, as a ratchet with a comment naming the class measured — design D4. If no threshold both admits today's classes and rejects a regression, take the recorded fallback and drop the ceiling — **fallback taken 2026-08-14, on the merits rather than on an unmeasurable threshold.** The measurement succeeded (`maxmethods = 22` from `ResolveCtx`; `MapperGraph` next at 21) and shipped for two days, then was reversed: a method count counts declarations without regard to their contents, so it is breached by a class of many small tidy methods and satisfied by one of a few fat ones. Every surviving rule in the ruleset pushes toward small, individually testable methods, and extraction — the remedy — is exactly what raises the count. See design D4's revision
- [x] 2.3 Rewrite the orphaned root `.pmd.xml` as a thin local ruleset referencing `rulesets/java/joke-strict.xml` and carrying **only** the `TooManyMethods` re-enable and its property — design D5. Delete the file outright if D4 took the fallback — **deleted**: `TooManyMethods` was its only content, so with 2.2 reversed no local composition remains and `ruleSets` names `rulesets/java/joke-strict.xml` directly
- [x] 2.4 Point `ruleSets` at that one file and confirm no module's `build.gradle` configures a PMD setting of its own
- [x] 2.5 Confirm PMD analyses every source set including `archRules` and `archRulesTest` — the rule library was never runner-enrolled and is newly covered

## 3. Declare the extension points

- [x] 3.1 Add `@ApiStatus.OverrideOnly` to `Container#containerOf` and `Container#wrapNullness` — the two methods design D5 of `adopt-nebula-archrules` exempted by name
- [x] 3.2 Add `@ApiStatus.OverrideOnly` to the eight `protected abstract` published hooks on `Accessor`, `Container`, `Conversion`, and `CollectionContainer`
- [x] 3.3 Confirm every remaining `protected` method carries `@VisibleForTesting` instead, and that no method carries both
- [x] 3.4 Declare `org.jetbrains:annotations` where a module newly needs it — watch the `compileOnly` gap previously hit in `reactor` and `strategies-builtin` ([[project_enforce_testable_method_visibility]])
- [x] 3.5 Clear the remaining `AvoidPrivateAndProtectedMethods` violations (23 at baseline, all `protected`, zero `private`)

## 4. Delete the ArchUnit method-shape rules

- [x] 4.1 Delete `architecture-tests/src/archRules/java/.../MethodShapeRules.java` in full — all four rules and all four `getRules()` entries
- [x] 4.2 Delete `MethodShapeRulesSpec.groovy`
- [x] 4.3 Delete the six negative fixtures: `violators/HasPrivateMethod`, `violators/HasStaticMethod`, `violators/HasUnusedProtectedMethod`, `spi/PublishedHook`, `spi/builtins/violators/BuiltinsUnusedProtected`, `processor/internal/stages/expand/violators/OversizedClass`
- [x] 4.4 Prune `Packages` of every coordinate no surviving rule reads — expected: `DECOMPOSED_ENGINE_PACKAGES`, `DAGGER_PROVIDES`, `DAGGER_GENERATED`, `VISIBLE_FOR_TESTING`, `LOMBOK_GENERATED`
- [x] 4.5 Run `./gradlew checkArchRulesMain --no-configuration-cache` across every enrolled module and confirm the three surviving rule classes still evaluate and pass
- [x] 4.6 Confirm this removed 51 `archRules` violations for free — actual: 52 (76 → 24), plus 5 in `archRulesTest` (6 → 1). `SHADED_LIB` turned out orphaned too and was pruned with the five expected coordinates; `NOT_SYNTHETIC_OR_BRIDGE` moved into `EngineEncapsulationRules`, its only remaining reader — `MethodShapeRules.java` was the single largest violator in the repo

## 5. Fix the rule library's own violations

- [x] 5.1 Fix the 25 remaining `archRules` violations in `ModuleLayeringRules`, `EngineEncapsulationRules`, and `TypeBoundaryRules`
- [x] 5.2 Fix the 6 `archRulesTest` violations
- [x] 5.3 Confirm `:architecture-tests:pmdArchRules` and `:architecture-tests:pmdArchRulesTest` are green

## 6. StaticMethodsModifyStaticState — the residue

- [x] 6.1 Re-run and list what survives the upstream exemptions; expect only the 5 Dagger `@Provides` statics in `ProcessorModule`
- [x] 6.2 Suppress those at the site with a comment, per the rule's own documented guidance for framework-mandated statics
- [x] 6.3 Confirm no `@UtilityClass` or named-constructor site needed a suppression — if one did, the upstream exemption is wrong and belongs back in group 1

## 7. UseStaticImports (308 at baseline)

- [x] 7.1 `processor`
- [x] 7.2 `strategies-builtin`
- [x] 7.3 `spi`
- [x] 7.4 `reactor`, `reactor-blocking`, and the remaining modules
- [x] 7.5 Confirm no import-order churn against spotless — run `./gradlew spotlessApply` and re-check. All 308 sites converted; the 33 once thought blocked are resolved in-repo — see the note below group 7

**The 33 `@UtilityClass` sites are converted, and needed no upstream fix.** They were recorded on 2026-08-11
as BLOCKED on a `UseStaticImports` exemption, on the reading that Lombok adds `static` too late for javac to
resolve `import static ...Reactors.declared;`. That diagnosis was wrong about its own cause: what javac needs
is the modifier in the **source**, not the annotation. `LiteralCoercion`, `Labels`, `Members` and
`AnnotationEntries` already wrote `static` explicitly and static-import fine today; only `Reactors.declared`
and `Blockings.declared`/`view`/`isSingleReferenceArgKind`/`isBlockableScalar` relied on Lombok to add it.
Writing `static` explicitly on those five methods — which `@UtilityClass` permits and the rest of the repo
already does — unblocks every call site, keeps `@UtilityClass` and its `StaticMethodsModifyStaticState`
exemption intact, and needs no `@SuppressWarnings`. Decided 2026-08-12 after measuring both paths; hand-rolling
the holders (final class + private constructor) was the alternative and would also have passed, via the rule's
structural utility-holder test, at the cost of the boilerplate `@UtilityClass` exists to remove.

One extra site falls out of the conversion: `EnumConversion` keeps `Labels.ARROW` qualified because
`SwitchStyle.ARROW` occupies the simple name — the same clash rule as the two pairs below, and PMD reports
neither, so no suppression is needed.

Six sites keep an explicit form behind a documented `@SuppressWarnings`, each because the rule's premise
genuinely does not hold there: two `Comparator.comparing` calls need an explicit type witness; two JavaPoet
renderers would have `CodeBlock.joining` shadow `Collectors.joining`; and two name clashes
(`Diagnostic.Severity` vs javac's `Diagnostic.Kind`, spi `Visibility` vs graph `Visibility`) mean only one of
each pair can be static-imported. `GraphDumpWriter#dimmed` likewise keeps its explicit type, because `var`
cannot infer a conditional with a lambda branch.

## 8. UseVisibleForTestingAnnotation (334 at baseline)

- [x] 8.1 Agree the approach with the user before touching source — decided 2026-08-11: **a PMD-driven script, applied and verified per pass**, rather than D8's IDE quick-fix. Each pass was followed by `compileJava`/`compileTestJava`/`test` and a fresh PMD run; 319 annotations across 75 files, and a second pass measured zero
- [x] 8.2 `processor`
- [x] 8.3 `strategies-builtin`
- [x] 8.4 `spi`
- [x] 8.5 `reactor`, `reactor-blocking`, and the remaining modules
- [x] 8.6 Spot-check a sample per module that the annotation landed on the declaration and not on an `@Override` or a JUnit lifecycle method

## 9. The remaining rules

- [x] 9.1 `UseVarForLocalVariables` (65) — respect the declared exemptions: no initializer, `null`, array shorthand, lambda or method reference, multiple declarators
- [x] 9.2 `AvoidLambdaBlockBodies` (20) — extract to a named method; an expression body suffices, a method reference is not required
- [x] 9.3 `AvoidAnonymousClasses` (4)
- [x] 9.4 `UseTypeImports` (8)

## 10. Verify and land

- [x] 10.1 **PASSES** — `./gradlew check --no-configuration-cache` is `BUILD SUCCESSFUL` with zero PMD violations across every module and source set (2026-08-12, after the group-7 note's explicit-`static` fix). Run it and confirm it is green. **NEVER continue if there are violations**
- [x] 10.2 **The four surviving probes PASS** (private → `AvoidPrivateAndProtectedMethods`, unmarked protected → same, unmarked package-private → `UseVisibleForTestingAnnotation`, arbitrary static → `StaticMethodsModifyStaticState`), plus a negative probe confirming a protected method carrying either `@VisibleForTesting` or `@ApiStatus.OverrideOnly` passes. The fifth probe — 23-method class → `TooManyMethods` — passed while the ratchet was configured, and is **withdrawn with the rule** by 2.2's reversal; the oversized-class invariant is no longer automated. Deliberately reintroduce one violation per surviving invariant and confirm each fails the build, then revert. This is the false-negative gate that replaces the deleted ArchUnit fixtures
- [x] 10.3 **PASSES** — `./gradlew pitest --continue` green across every module (`BUILD SUCCESSFUL`), including `processor`'s documented 89/97/92 ratchet; line coverage for mutated classes 100% everywhere except `processor` at 97%. Confirm pitest still meets its thresholds; the mechanical edits touch a great deal of code ([[feedback_pitest_history_plugin]])
- [x] 10.4 Run `/opsx:sync` to fold the delta specs into the main specs
- [x] 10.5 Hand-edit `openspec/specs/module-boundaries/spec.md`'s **Purpose** paragraph — a delta cannot reach it, and it currently describes the three method-shape rules this change removes
- [x] 10.6 Hand-edit `openspec/specs/architecture-rule-distribution/spec.md`'s **Purpose** paragraph for the same reason — it names method shape as one of the library's subjects
- [x] 10.7 Commit with `/commit-commands:commit`
