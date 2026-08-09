## 1. Spike the open questions before committing to the port

- [ ] 1.1 Add the three plugin ids at version `1.3.1` to `settings.gradle`'s `pluginManagement { plugins { … } }` block, alongside the existing `info.solidsoft.pitest` / `com.gradleup.shadow` / `io.freefair.lombok` entries
- [ ] 1.2 Resolve the ArchUnit version question: determine which ArchUnit `nebula-archrules-core` brings, compare against the pin in `dependencies/build.gradle`, and decide whether `architecture-tests` keeps its direct `com.tngtech.archunit:archunit` declaration or inherits the plugin's
- [ ] 1.3 Confirm `ArchCondition`, `DescribedPredicate`, `ConditionEvents`, and `SimpleConditionEvent` are usable from an `ArchRulesService` implementation — the custom Rules A/B/C/D and the raw-annotation-read rule all depend on them
- [ ] 1.4 Confirm the runner leaves ArchUnit's `resolveMissingDependenciesFromClassPath` enabled; rules 6 (`isAssignableTo` on `javax.lang.model.element.Element`) and 8 (`implement(Stage)`) need it. If disabled, record the reformulation in `design.md` before porting either rule
- [ ] 1.5 Verify the aggregate plugin's root-project configuration does not break `./gradlew check --no-configuration-cache` given the existing `shipkit-auto-version` constraint

## 2. Wire the plugins into the build

- [ ] 2.1 Add a `pluginManager.withPlugin('com.netflix.nebula.archrules.library')` block to `percolate.conventions.gradle` declaring `archRulesImplementation` on the `dependencies` platform and ArchUnit
- [ ] 2.2 Add a `pluginManager.withPlugin('com.netflix.nebula.archrules.runner')` block declaring `archRules project(':architecture-tests')`, `failureThreshold('LOW')`, `skipSourceSet('test')`, and `skipSourceSet('testFixtures')`
- [ ] 2.3 Verify neither block names a module, branches on a project name, or maintains an opt-out list
- [ ] 2.4 Apply `com.netflix.nebula.archrules.aggregate` in the root `build.gradle`

## 3. Turn architecture-tests into a rules library

- [ ] 3.1 Apply `com.netflix.nebula.archrules.library` in `architecture-tests/build.gradle`; do **not** apply the runner
- [ ] 3.2 Remove the `java-test-fixtures` plugin, the `testFixtures*` dependency declarations, and the six sibling `testImplementation project(':…')` entries
- [ ] 3.3 Create the `src/archRules/java` source set layout with the four `ArchRulesService` classes: `ModuleLayeringRules`, `EngineEncapsulationRules`, `MethodShapeRules`, `TypeBoundaryRules`
- [ ] 3.4 Move `src/test/resources/SpockConfig.groovy` to `src/archRulesTest/resources/SpockConfig.groovy`, unchanged in content

## 4. Port the rules (Groovy Spock → Java ArchRulesService)

- [ ] 4.1 `ModuleLayeringRules`: port rules 1–4 — engine ↛ strategy, harness ↛ strategy, spi ↛ engine/strategy, annotations ↛ every other module
- [ ] 4.2 `EngineEncapsulationRules`: port rule 5 (processor ↛ mapping annotations), rule 6 (no raw `getAnnotationMirrors()`/`getAnnotation(Class)` off an `Element`, exempting `processor.nullability`), and rule 7 (an `ExpansionStrategy` implementation ↛ engine graph)
- [ ] 4.3 `EngineEncapsulationRules`: fold the three duplicated `EngineEncapsulationSpec` copies into a single rule 16 — no class outside `processor..` depends on `processor.internal..`
- [ ] 4.4 `MethodShapeRules`: port Rule A (no private method) with its synthetic/bridge and Dagger `@Generated` exemptions, dropping the `notShadedLib` and `notTestFixtures` import filters
- [ ] 4.5 `MethodShapeRules`: port Rule B (method-count ceiling of 15 over the decomposed expand/generate/builtins packages)
- [ ] 4.6 `MethodShapeRules`: port Rule D (no static) with all its existing exemption shapes — published-spi publics, Dagger `@Provides`, enum `values`/`valueOf`, `main`, named constructors, and the structurally-matched stateless utility holder
- [ ] 4.7 `MethodShapeRules`: port Rule C (unused protected ⇒ `@VisibleForTesting`) **and add the published-`spi` exemption** per design D5 — `spi..` excluding `spi.builtins..`, reusing Rule D's `notPublishedSpiApi` shape
- [ ] 4.8 `TypeBoundaryRules`: port rule 8 (`*Stage` suffix naming), rule 14 (`javax.lang.model.util` confinement to the enumerated boundary packages), and rule 15 (no dependency on the unrelocated `com.palantir.javapoet..`)
- [ ] 4.9 Set `allowEmptyShould(true)` on **every** rule, and give each a stable `getRules()` key and a `because(...)` message carrying the invariant it protects
- [ ] 4.10 Do **not** port rule 13 (`slices().beFreeOfCycles()`) — it is deleted per design D6

## 5. Prove each rule fires

- [ ] 5.1 Create the `src/archRulesTest` suite with a deliberately-violating fixture per rule, checked via `Runner.check(rule, Violator.class)` asserting `hasViolation()`
- [ ] 5.2 Cover rules 6 and 8 explicitly, since both depend on classpath type resolution and would pass vacuously if that resolution were unavailable
- [ ] 5.3 Cover Rule C's new `spi` exemption in both directions: a `spi` template-method hook passes unannotated, and an unused unannotated protected method in `spi.builtins..` still fails

## 6. Enrol the modules

- [ ] 6.1 Declare `id 'com.netflix.nebula.archrules.runner'` in the `plugins {}` block of `annotations`, `spi`, `processor`, `strategies-builtin`, `reactor`, `reactor-blocking`, `test-foundation`, and `percolate`
- [ ] 6.2 Confirm `lib:javapoet`, `bom`, `dependencies`, `percolate-smoke`, and `architecture-tests` declare neither archrules plugin, and record in `lib/javapoet/build.gradle` why it abstains (its `com.palantir.javapoet.MethodSpec` overlay would trip the relocation rule)
- [ ] 6.3 Run `./gradlew checkArchRulesMain` across the enrolled modules and confirm every rule evaluates and passes — this is the false-positive gate, and must be green **before** the old specs are deleted

## 7. Delete the old mechanism

- [ ] 7.1 Delete `architecture-tests/src/test/groovy/**/ModuleBoundariesSpec.groovy` and `JavaPoetRelocationSpec.groovy`
- [ ] 7.2 Delete `architecture-tests/src/testFixtures/groovy/**/EncapsulationRules.groovy`
- [ ] 7.3 Delete the three `EngineEncapsulationSpec.groovy` copies in `strategies-builtin`, `reactor`, and `reactor-blocking`, plus each module's `testFixtures(project(':architecture-tests'))` dependency
- [ ] 7.4 Confirm no `bin/` or stale build output keeps a deleted spec alive in a later run

## 8. Verify and land

- [ ] 8.1 Deliberately introduce one violation per rule family (a private method, a static, a cross-module edge, an upstream JavaPoet import) and confirm the build fails — then revert. This is the false-negative gate that `allowEmptyShould(true)` makes necessary
- [ ] 8.2 Run `/opsx:sync` to fold the delta specs into the main specs
- [ ] 8.3 Hand-edit `openspec/specs/module-boundaries/spec.md`'s **Purpose** paragraph — a delta cannot reach it, and it currently describes the central union-classpath suite and the `testFixtures`-published rule that this change removes
- [ ] 8.4 Run `./gradlew check --no-configuration-cache` and confirm it is green. **NEVER continue if there are violations**
- [ ] 8.5 Commit with `/commit-commands:commit`
