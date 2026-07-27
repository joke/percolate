## 1. Mock-maker foundation (prerequisite for everything else)

- [ ] 1.1 Merge the six per-module `SpockConfig.groovy` files into one canonical text, preserving every distinct rationale comment (the `optimizeRunOrder` race, the parallel-execution reasoning, the reactor-blocking mutation-score finding, the mock-maker note) and adding the `SpyStatic` thread-confinement reason for keeping parallel off
- [ ] 1.2 Generate that file from `percolate.conventions` into a generated resources directory wired into `sourceSets.test.resources`, so it lands on the test classpath root for both `test` and `pitest`
- [ ] 1.3 Delete `src/test/resources/SpockConfig.groovy` from `processor`, `spi`, `strategies-builtin`, `reactor`, and `reactor-blocking`
- [ ] 1.4 Make the mockito test dependency uniform across every module with a Spock suite (`spi` has none today; `processor` has it `testRuntimeOnly`, the strategy modules `testImplementation`) — declare it once in the conventions plugin
- [ ] 1.5 Verify `annotations` and `architecture-tests` now receive the configuration too, and that their existing specs still pass
- [ ] 1.6 Prove the capability with a throwaway spec that mocks a Lombok `@Value` final class in `spi` and calls `SpyStatic(LiteralCoercion)`, then delete it
- [ ] 1.7 Confirm `./gradlew check --no-configuration-cache` is green before any production code changes

## 2. Record the conventions so they cannot regress

- [ ] 2.1 Add the self-call idiom to the Spock convention skill: the `1 * subject.f(...)` → `1 * subject._` → `0 * _` ordering, why declaration order matters, and why `1 * subject._` is exempt from the bare-`_` prohibition (method wildcard, not argument wildcard)
- [ ] 2.2 Add the `SpyStatic(Class)` guidance to the same skill, including that there is no `MockStatic` and that it requires the mockito mock maker
- [ ] 2.3 Add the "prefer protected instance methods over static; never go static to dodge a Spy" rule to the Java convention skill, with the permitted static contexts from the spec
- [ ] 2.4 Confirm Error Prone's `MethodCanBeStatic` is off and no PMD rule pushes toward static, so `-Werror` does not fight the convention

## 3. ArchUnit rule for statics

- [ ] 3.1 Add a rule to `ModuleBoundariesSpec` as the companion to Rules A and C: no non-synthetic, non-generated method in percolate's own code is `static`, exempting `@Provides` methods, `public` methods in the `spi` package, and the shaded `lib` prefix already excluded by `setupSpec`
- [ ] 3.2 State the invariant in the rule's `because(...)` message, matching how Rules A and C are written
- [ ] 3.3 Run it to produce the authoritative violation list, and reconcile that list against the counts in the design (86 `processor`, 64 `strategies-builtin`, 1 `spi`)
- [ ] 3.4 Keep the rule failing until groups 4–6 land, or gate it behind the last conversion task — decide and note which

## 4. Convert Shape 1 statics — strategies-builtin

- [ ] 4.1 `NullnessCrossing`: convert its 6 self-helpers to `protected` instance methods, adding `@VisibleForTesting` where no production subclass uses them
- [ ] 4.2 `EnumConversion` (8), `AbsoluteTemporalConversion` (7), `LegacyTemporalFormat` (6)
- [ ] 4.3 `AnnotationEntries` (5), `Members` (4), `InstantLocalDateTimeBridge` (4)
- [ ] 4.4 `TemporalFormat` (3), `PrimitiveWrapperConversion` (3), `LocalTemporalConversion` (2), `MapDirectiveReader` (2), `GetterPathResolver` (2), `ConstructorCall` (2), `ConstantValue` (2)
- [ ] 4.5 Leave `Labels` static per design D3; update any spec that needs to control it to use `SpyStatic(Labels)`
- [ ] 4.6 Update every affected spec: add a feature method spy-testing each newly-protected method directly, and add the declared self-interaction where a spied subject now calls an instance helper
- [ ] 4.7 Declare `org.jetbrains:annotations` as `compileOnly` if `@VisibleForTesting` is newly needed here
- [ ] 4.8 `./gradlew :strategies-builtin:check --no-configuration-cache` green, including pitest at 85/95/90

## 5. Convert Shape 1 statics — processor

- [ ] 5.1 `DotRenderer` (12 statics on an already `@Inject`-constructed singleton — the clearest case)
- [ ] 5.2 `GraphDumpWriter` (7, alongside its existing instance method `skipDump`)
- [ ] 5.3 `RealisationDiagnosticsStage` (6), `ValidateOptionConsumptionStage` (5), `ValidateSourceParametersStage` (3)
- [ ] 5.4 `TargetProducer` (4), `ProcessorOptions` (3), `MemberPlan` (3), `AssembleMapperType` (3)
- [ ] 5.5 The remaining one- and two-static classes (`Diagnostic`, `Dep`, `BodyRenderContextImpl`, `AccessPath`, `Unifier`, `TargetPath`, `SelfCallConstraint`, `NullabilityAnnotations`, `MapperGraph`, `Location`, `JspecifyNullabilityResolver`, `IncomingValuesImpl`, `ExtractedPlan`)
- [ ] 5.6 Leave `ProcessorModule`'s `@Provides` methods static
- [ ] 5.7 Update every affected spec as in 4.6, with particular care in `UnifierSpec`, which already uses the `1 * unifier._` idiom
- [ ] 5.8 `./gradlew :processor:check --no-configuration-cache` green, including pitest

## 6. Convert Shape 2 factory clusters

- [ ] 6.1 Extract an injectable factory from `HoistPlan`, moving `collectPortConsumers`, `hoistedValues`, `isHoistCandidate`, and `collectOps` to `protected` instance methods and leaving `HoistPlan` as data with `isHoisted`
- [ ] 6.2 Do the same for `GoalSpec` (`childLevels`, `sourcePathByTarget`, `directivesByTarget`, and the rest behind `empty`/`from`)
- [ ] 6.3 Wire the new factories through Dagger where their callers are DI-managed
- [ ] 6.4 Confirm each new class is a cohesive unit, not a bag of relocated statics, and that ArchUnit Rule B's 15-method ceiling still passes for `processor.internal.stages.generate`
- [ ] 6.5 Unit-test each factory's protected methods directly via `Spy()`
- [ ] 6.6 Convert `spi`'s single non-public static (`Nullability.either`), leaving all public spi factories untouched

## 7. Javadoc confinement — enforcement first

- [ ] 7.1 Spike PMD's `category/java/documentation.xml/CommentRequired` with `Unwanted` settings against `processor` and confirm it covers types, fields, constructors, methods, enum constants, and `package-info`; fall back to a custom XPath rule on `FormalComment` if there are gaps
- [ ] 7.2 Create `.pmd-internal.xml` including `.pmd.xml` plus the `Unwanted` settings, so the two rulesets cannot drift
- [ ] 7.3 Add a declarative per-module opt-in to `percolate.conventions` (default internal) and mark `annotations` and `spi` as the public-API modules, rather than naming modules from the plugin
- [ ] 7.4 Confirm `lib/javapoet` stays exempt via its existing vendored-path exclusions

## 8. Javadoc confinement — the demotion pass

- [ ] 8.1 `reactor` (8 public/protected + 2 package-private) and `reactor-blocking` (5 + 6) — smallest modules first, to settle the demote-versus-delete judgement on a small sample
- [ ] 8.2 `percolate` and any remaining non-exempt module
- [ ] 8.3 `strategies-builtin` (33 + 38)
- [ ] 8.4 `processor` (94 + 171), taking particular care with `ProcessorModule`'s `ServiceLoader` ordering rationale and the graph-invariant notes
- [ ] 8.5 For every block: demote to `//` if it records an invariant, a rejected alternative, a cross-change decision, or a tooling workaround; delete if it only restates the signature
- [ ] 8.6 Remove trivial comments encountered along the way — self-explanatory members carry no comment in either form
- [ ] 8.7 Verify `annotations` and `spi` javadoc is untouched and still complete

## 9. Verification

- [ ] 9.1 Enable the ArchUnit static rule unconditionally and confirm it passes with only the intended carve-outs
- [ ] 9.2 Confirm the javadoc jars for the internal published modules still build, accepting that they are near-empty
- [ ] 9.3 Confirm no `SpockConfig.groovy` remains under any module's `src/test/resources`
- [ ] 9.4 Review pitest scores per module against 85/95/90 and investigate any drop as a helper that was stubbed in its caller's spec without being separately tested
- [ ] 9.5 Run `./gradlew check --no-configuration-cache` and do NOT continue if there are any violations
- [ ] 9.6 Commit with `/commit-commands:commit`
