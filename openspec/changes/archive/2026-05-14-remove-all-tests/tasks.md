## 1. Remove test files

- [x] 1.1 Delete `processor/src/test/groovy/` directory (all 74 `.groovy` spec and helper files)
- [x] 1.2 Delete `processor/src/test/resources/golden-graphs/` directory (6 `.dot` files + README)
- [x] 1.3 Delete `processor/src/test/resources/META-INF/services/org.codehaus.groovy.runtime.ExtensionModule`

## 2. Remove obsolete build task

- [x] 2.1 Remove the `updateGoldens` task from `processor/build.gradle` (lines 6–20)

## 3. Verify

- [x] 3.1 Confirm `processor/src/test/` directory no longer exists
- [x] 3.2 Confirm no changes were made to `build.gradle`, `settings.gradle`, or `gradle.properties`
- [x] 3.3 Run `./gradlew check` to verify the build still passes (with zero tests)
