## 1. Mock-maker foundation (prerequisite for everything else)

- [x] 1.1 Merge the six per-module `SpockConfig.groovy` files into one canonical text, preserving every distinct rationale comment (the `optimizeRunOrder` race, the parallel-execution reasoning, the reactor-blocking mutation-score finding, the mock-maker note) and adding the `SpyStatic` thread-confinement reason for keeping parallel off
- [x] 1.2 Generate that file from `percolate.conventions` into a generated resources directory wired into `sourceSets.test.resources`, so it lands on the test classpath root for both `test` and `pitest`
- [x] 1.3 Delete `src/test/resources/SpockConfig.groovy` from `processor`, `spi`, `strategies-builtin`, `reactor`, and `reactor-blocking`
- [x] 1.4 Make the mockito test dependency uniform across every module with a Spock suite (`spi` has none today; `processor` has it `testRuntimeOnly`, the strategy modules `testImplementation`) — declare it once in the conventions plugin
- [x] 1.5 Verify `annotations` and `architecture-tests` now receive the configuration too, and that their existing specs still pass
- [x] 1.6 Prove the capability with a throwaway spec that mocks a Lombok `@Value` final class in `spi` and calls `SpyStatic(LiteralCoercion)`, then delete it
- [x] 1.7 Confirm `./gradlew check --no-configuration-cache` is green before any production code changes

## 2. Record the conventions so they cannot regress

- [x] 2.1 Add the self-call idiom to the Spock convention skill: the `1 * subject.f(...)` → `1 * subject._` → `0 * _` ordering, why declaration order matters, and why `1 * subject._` is exempt from the bare-`_` prohibition (method wildcard, not argument wildcard)
- [x] 2.2 Add the `SpyStatic(Class)` guidance to the same skill, including that there is no `MockStatic` and that it requires the mockito mock maker
- [x] 2.3 Add the "prefer protected instance methods over static; never go static to dodge a Spy" rule to the Java convention skill, with the permitted static contexts from the spec
- [x] 2.4 Confirm Error Prone's `MethodCanBeStatic` is off and no PMD rule pushes toward static, so `-Werror` does not fight the convention

## 3. ArchUnit rule for statics

- [x] 3.1 Add a rule to `ModuleBoundariesSpec` as the companion to Rules A and C: no non-synthetic, non-generated method in percolate's own code is `static`, exempting `@Provides` methods, `public` methods in the `spi` package, and the shaded `lib` prefix already excluded by `setupSpec`
- [x] 3.2 State the invariant in the rule's `because(...)` message, matching how Rules A and C are written
- [x] 3.3 Run it to produce the authoritative violation list, and reconcile that list against the counts in the design (86 `processor`, 64 `strategies-builtin`, 1 `spi`) — actual 134: 80/51/3, reconciled in design.md
- [x] 3.4 Keep the rule failing until groups 4–6 land, or gate it behind the last conversion task — decide and note which — gated with `@PendingFeature`, which fails the build once the last conversion makes it pass

## 4. Convert Shape 1 statics — strategies-builtin

- [x] 4.1 `NullnessCrossing`: convert its 6 self-helpers to instance methods — package-private, not `protected`: Error Prone's `ProtectedMembersInFinalClass` rejects `protected` on the `final` strategy classes under `-Werror`, so `@VisibleForTesting` (a Rule C obligation on `protected` only) never attaches
- [x] 4.2 `EnumConversion` (8), `AbsoluteTemporalConversion` (7), `LegacyTemporalFormat` (6)
- [x] 4.3 `AnnotationEntries` (5), `Members` (4), `InstantLocalDateTimeBridge` (4) — only `InstantLocalDateTimeBridge` had any: `AnnotationEntries` and `Members` hold zero statics in the authoritative list, so the design's grep-based counts were wrong about both
- [x] 4.4 `TemporalFormat` (3), `PrimitiveWrapperConversion` (3), `LocalTemporalConversion` (2), `MapDirectiveReader` (2), `GetterPathResolver` (2), `ConstructorCall` (2), `ConstantValue` (2)
- [x] 4.5 Leave `Labels` static per design D3; update any spec that needs to control it to use `SpyStatic(Labels)` — no spec needed to control it, so none changed; Rule D exempts it by the utility-holder shape
- [x] 4.6 Update every affected spec: add a feature method spy-testing each newly-protected method directly, and add the declared self-interaction where a spied subject now calls an instance helper
- [x] 4.7 Declare `org.jetbrains:annotations` as `compileOnly` if `@VisibleForTesting` is newly needed here — already declared, and not needed after all (see 4.1)
- [x] 4.8 `./gradlew :strategies-builtin:check --no-configuration-cache` green, including pitest at 85/95/90

## 5. Convert Shape 1 statics — processor

- [x] 5.1 `DotRenderer` (12 statics on an already `@Inject`-constructed singleton — the clearest case)
- [x] 5.2 `GraphDumpWriter` (7, alongside its existing instance method `skipDump`)
- [x] 5.3 `RealisationDiagnosticsStage` (6), `ValidateOptionConsumptionStage` (5), `ValidateSourceParametersStage` (3)
- [x] 5.4 `TargetProducer` (4), `ProcessorOptions` (3), `MemberPlan` (3), `AssembleMapperType` (3) — `ProcessorOptions` and `MemberPlan` turned out to be Shape 2, not Shape 1, and moved to group 6; `TargetProducer.dedup`/`signature` are shared with `SourcePathDescender`, so they became a `SpecDeduplicator` collaborator rather than instance methods on either caller
- [x] 5.5 The remaining one- and two-static classes — converted: `Unifier`, `SelfCallConstraint`, `MapperGraph.valueKey`, `JspecifyNullabilityResolver`, `AccessPath.splitDotted`, `DiagnosticEmitter.kind`. Exempt as named constructors (user decision, Rule D's self-returning carve-out): `Diagnostic`, `Dep`, `Cost`, `AccessPath.of`, `TargetPath.of`, `Location.child`, `NullabilityAnnotations.jspecifyDefaults`, `IncomingValuesImpl.of`, `ExtractedPlan.extract`. `BodyRenderContextImpl` was Shape 2 and moved to group 6
- [x] 5.6 Leave `ProcessorModule`'s `@Provides` methods static — plus one that was NOT `@Provides`: `assembleExpansionPipeline`, a public static duplicating its sibling `@Provides` for a harness deleted long ago, was inlined and removed
- [x] 5.7 Update every affected spec as in 4.6, with particular care in `UnifierSpec`, which already uses the `1 * unifier._` idiom
- [x] 5.8 `./gradlew :processor:check --no-configuration-cache` green, including pitest

## 6. Convert Shape 2 factory clusters

- [x] 6.1 Extract an injectable factory from `HoistPlan`, moving `collectPortConsumers`, `hoistedValues`, `isHoistCandidate`, and `collectOps` to `protected` instance methods and leaving `HoistPlan` as data with `isHoisted`
- [x] 6.2 Do the same for `GoalSpec` (`childLevels`, `sourcePathByTarget`, `directivesByTarget`, and the rest behind `empty`/`from`)
- [x] 6.3 Wire the new factories through Dagger where their callers are DI-managed — five factories, not two: `HoistPlanFactory`, `MemberPlanFactory`, `BodyRenderContextFactory`, `GoalSpecFactory`, `ProcessorOptionsReader`
- [x] 6.4 Confirm each new class is a cohesive unit, not a bag of relocated statics, and that ArchUnit Rule B's 15-method ceiling still passes for `processor.internal.stages.generate`
- [x] 6.5 Unit-test each factory's protected methods directly via `Spy()` — the existing specs moved onto the factories; `SpecDeduplicatorSpec` is new and `ProcessorOptionsSpec` became `ProcessorOptionsReaderSpec`
- [x] 6.6 Convert `spi`'s non-public statics on instantiable classes — `Nullability.either` (now a receiver-based instance method: `NULLABLE.either(a, b)`) plus `Container.intermediateElement` and `Container.unary`, which the design's audit missed; all public spi factories untouched
- [x] 6.7 Leave the stateless all-static holders static per the corrected D3 (`LiteralCoercion`'s 16 non-public helpers, `Labels`, `Blockings`, `Reactors`, `PercolateCompiler`) and annotate the ones that lack it with `@UtilityClass` — done for `Labels` and `LiteralCoercion`; `PercolateCompiler` is Groovy, which Lombok does not process, so it keeps the shape without the annotation

## 7. Javadoc confinement — enforcement first

- [x] 7.1 Spike PMD's `CommentRequired` — it does NOT cover package-private methods (no property exists for them), which after Rule A is where nearly every helper lives, and PMD 7 dropped `FormalComment` from the XPath-addressable AST so the reserve fallback is gone too. Both options rejected in favour of a source scan (user decision)
- [x] 7.2 Create `.pmd-internal.xml` — superseded: the ban is a `checkNoJavadoc` task in `percolate.conventions` scanning main Java sources for a `/**` block. Recorded on the way: `<rule ref="./.pmd.xml">` cannot resolve (PMD resolves relative refs against the working directory, not the referencing file) and Gradle's Pmd task reports success on an unresolvable ruleset instead of failing — a silent pass
- [x] 7.3 Add a declarative per-module opt-in to `percolate.conventions` (default internal) and mark `annotations` and `spi` as the public-API modules, rather than naming modules from the plugin — `percolatePublicApi`, read at task-configuration time so the module's own build.gradle has had its say
- [x] 7.4 Confirm `lib/javapoet` stays exempt — it was NOT exempt for free: it applies `percolate.conventions` and does carry `src/main/java`. The scan now excludes `**/com/palantir/javapoet/**`, the same vendored path the module already excludes from Pmd and Error Prone

## 8. Javadoc confinement — the demotion pass

- [x] 8.1 `reactor` (10 blocks) and `reactor-blocking` (11) — every one demoted, none deleted: each carried a design-D reference or an invariant (never blocks, totality dominates, weighted strictly above any non-blocking alternative). The sample says the delete case is rarer than the task list assumed
- [x] 8.2 `percolate` and any remaining non-exempt module — `percolate`, `test-foundation`, `architecture-tests` and `dependencies` carry no javadoc at all, so the scan already passes for every module except `strategies-builtin` and `processor`
- [x] 8.3 `strategies-builtin` (71 blocks) — all demoted, none deleted
- [x] 8.4 `processor` (273 blocks), taking particular care with `ProcessorModule`'s `ServiceLoader` ordering rationale and the graph-invariant notes — both preserved verbatim as `//`; `percolate-smoke` (2) went the same way, it is a consumer-shaped verification module, not published API
- [x] 8.5 For every block: demote to `//` if it records an invariant, a rejected alternative, a cross-change decision, or a tooling workaround; delete if it only restates the signature — 366 blocks judged, exactly ONE deleted (`Location.role()`, "This location's resolution mode"). Rules A and C had already driven out the signature-restating comment: a method that exists as its own testable seam earns a reason for existing
- [x] 8.6 Remove trivial comments encountered along the way — see 8.5; the survey found one, not a class of them
- [x] 8.7 Verify `annotations` and `spi` javadoc is untouched and still complete — both carry `percolatePublicApi = true`, `checkNoJavadoc` skips them, and their sources are unchanged apart from `spi`'s three Rule D conversions

## 9. Verification

- [x] 9.1 Enable the ArchUnit static rule unconditionally and confirm it passes with only the intended carve-outs — `@PendingFeature` removed, zero violations; `checkNoJavadoc` likewise wired into `check`
- [x] 9.2 Confirm the javadoc jars for the internal published modules still build, accepting that they are near-empty
- [x] 9.3 Confirm no `SpockConfig.groovy` remains under any module's `src/test/resources` — none, and none tracked in git (the copies under `bin/` are untracked IDE output)
- [x] 9.4 Review pitest scores per module against 85/95/90 — clean-build scores: processor 96/87/92, spi 96/92/97, strategies-builtin 96/87/92, reactor 99/99/99, reactor-blocking 98/97/97. No drop to investigate: no conversion introduced a stubbed self-call without its own feature method
- [x] 9.5 Run `./gradlew check --no-configuration-cache` and do NOT continue if there are any violations — green from `clean`, 144 tasks
- [ ] 9.6 Commit with `/commit-commands:commit`
