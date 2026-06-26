## 1. Self-contained POMs

- [x] 1.1 Add `versionMapping { usage('java-api') { fromResolutionResult() }; usage('java-runtime') { fromResolutionResult() } }` to the Maven publication convention so POM dependencies carry concrete versions
- [x] 1.2 ~~Move the platform off published configs~~ — N/A: the `pom.withXml` strip (1.4) + `versionMapping` (1.1) achieve self-contained POMs without reshaping every module's configs
- [x] 1.3 Regenerate a representative POM (`:processor:generatePomFileForMavenPublication`, `:spi:...`) and confirm by inspection: concrete versions present, no `<dependencyManagement>` import of the internal platform (manual check — no committed POM test)
- [x] 1.4 If the import persists, apply the `pom.withXml` fallback to strip the `<dependencyManagement>` block

## 2. Stop publishing the internal platform

- [x] 2.1 Remove `maven-publish` from `dependencies/build.gradle`
- [x] 2.2 Confirm `publishToMavenLocal` no longer produces `percolate-dependencies` and that the other POMs still resolve as a closed set

## 3. In-build smoke module

- [x] 3.1 Add `percolate-smoke` to root `settings.gradle`; delete `percolate-smoke/settings.gradle`
- [x] 3.2 Rewrite `percolate-smoke/build.gradle`: `annotationProcessor project(':percolate')` + `compileOnly project(':annotations')`; no `maven-publish`
- [x] 3.3 Add `@NullMarked package-info.java` (and any minimal fixes) so the module's hand-written classes pass the inherited errorprone/NullAway/PMD/CodeNarc gates
- [x] 3.4 Wire the `smokeRun` JavaExec into `check` (`check.dependsOn smokeRun`); confirm it runs the generated mapper on a percolate-free runtime classpath
- [x] 3.5 Verify the smoke module declares no `maven-publish` and no published-coordinate (`mavenLocal` GAV) dependency

## 4. Prune TypeUniverse fossils

- [x] 4.1 Remove `pool()`, `TYPE_POOL`, `anyConstruct()`, and the `INSTANT` / `LOCAL_DATE_TIME` constants from `TypeUniverse`
- [x] 4.2 Remove the `TypeUniverseSpec` assertion(s) that only exist to count `pool()`; delete the spec if nothing else remains
- [x] 4.3 Confirm no remaining references to the removed members across all modules

## 5. TypeUniverse.of(Class) + fixture migration

- [x] 5.1 Add `static TypeElement of(Class<?> type)` to `TypeUniverse`, resolving via the same substrate (`lookup(type.getCanonicalName())`)
- [x] 5.2 Migrate fixture references (`io.github.joke.percolate.spi.builtins.fixtures.*`) in the strategy unit specs from `element('…')` to `of(<Fixture>.class)` (GetterPathResolverSpec, MembersSpec, ConstructorCallSpec, MethodPathResolverSpec, FieldPathResolverSpec, FixtureTypeSmokeSpec, and any others)
- [x] 5.3 Leave `element(String)` in place for JDK types / dynamic names; confirm imports for the migrated fixture Class literals resolve
- [x] 5.4 Confirm the IDE/`grep` now finds each fixture by a typed reference (no fixture reachable only via a string literal)

## 6. Validation

- [x] 6.1 Run `./gradlew check` and resolve every violation before completing — NEVER continue with violations; confirm the smoke runs as part of `check`
- [x] 6.2 Run `./gradlew publishToMavenLocal` and inspect a POM to confirm self-containment and the absence of `percolate-dependencies`
- [x] 6.3 Commit the completed change with `/commit-commands:commit`
