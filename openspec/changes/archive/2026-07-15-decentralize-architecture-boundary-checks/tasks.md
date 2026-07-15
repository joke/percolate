## 1. Remove the JavaPoet swallow-check (D1)

- [x] 1.1 Delete `verifyJavaPoetSwallowed`, `javaPoetOffenders`, `swallowProbeModules`,
      `swallowProbeConfigurations`, the `evaluationDependsOn` loop, and the
      `tasks.named('check') { dependsOn 'verifyJavaPoetSwallowed' }` wiring from
      `architecture-tests/build.gradle`. No replacement (no `exclude`).

## 2. Add shared ArchUnit rule fixtures to architecture-tests (D2)

- [x] 2.1 Add `id 'java-test-fixtures'` to `architecture-tests/build.gradle`'s `plugins {}` block.
- [x] 2.2 Add `architecture-tests/src/testFixtures/groovy/io/github/joke/percolate/architecture/EncapsulationRules.groovy`
      (or similar name) exposing a reusable, parameterized ArchUnit rule builder equivalent to today's
      "no class outside the engine reaches a processor internal package" check — e.g. a static method taking
      the outside-package and forbidden-package names and returning an `ArchRule`. No dependency on any
      consuming module; only `com.tngtech.archunit:archunit` (+ Groovy) needed.
- [x] 2.3 Add `testFixturesImplementation 'com.tngtech.archunit:archunit'` (and Groovy/Spock if the fixture
      code needs them) to `architecture-tests/build.gradle`.

## 3. Move the boundary rule into each owning module (D3)

- [x] 3.1 Add `testImplementation testFixtures(project(':architecture-tests'))` and
      `testImplementation 'com.tngtech.archunit:archunit'` to `strategies-builtin/build.gradle`,
      `reactor/build.gradle`, and `reactor-blocking/build.gradle`.
- [x] 3.2 Add a new small spec to each of the three modules (e.g.
      `EngineEncapsulationSpec.groovy` under each module's own
      `src/test/groovy/io/github/joke/percolate/<module-package>/architecture/` or similar), each importing
      its own module's classes (`ClassFileImporter().importPackages('io.github.joke.percolate')` — the
      module's own test runtime classpath naturally includes its own main+test classes plus `processor`'s
      main classes) and calling the shared `EncapsulationRules` rule with `processor.internal` as the
      forbidden package.
- [x] 3.3 Delete the "no class outside the engine reaches a processor internal package" test method from
      `architecture-tests/src/test/groovy/.../ModuleBoundariesSpec.groovy`.

## 4. Remove the boundary-probe plumbing from architecture-tests (D3)

- [x] 4.1 Delete `boundaryProbeModules`, `boundaryProbeLayout`, and the `tasks.named('test') { ... }` block
      (the `dependsOn`/`inputs.files`/`systemProperty 'percolate.boundaryProbeClasses'` wiring) from
      `architecture-tests/build.gradle`.
- [x] 4.2 Delete the now-unused `System.getProperty('percolate.boundaryProbeClasses')` parsing logic (the
      `given:` block) from the deleted test method's former location — confirm no other test in
      `ModuleBoundariesSpec` reads that system property.

## 5. Verification

- [x] 5.1 Confirm `architecture-tests/build.gradle` no longer references `evaluationDependsOn`,
      `resolvedConfiguration`, `systemProperty 'percolate.boundaryProbeClasses'`, or
      `boundaryProbeModules`/`boundaryProbeLayout`.
- [x] 5.2 Run `./gradlew :architecture-tests:test :strategies-builtin:test :reactor:test :reactor-blocking:test`
      and confirm every architecture rule — the ones that stayed central and the three new per-module
      ones — passes.
- [x] 5.3 Temporarily introduce a deliberate violation (e.g. a throwaway class in `strategies-builtin`'s test
      sources importing a `processor.internal` type) and confirm `strategies-builtin:test` fails with a
      clear ArchUnit violation message, then remove the throwaway class — proves the relocated rule actually
      catches what it's supposed to, not just that it compiles.
- [x] 5.4 Run `./gradlew check` and confirm it passes with no violations.
- [x] 5.5 Commit the completed change with `/commit-commands:commit`.
