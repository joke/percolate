## 1. Version derivation

- [ ] 1.1 Add `org.shipkit.shipkit-auto-version` to root `build.gradle`'s `pluginManagement`/`plugins` block
- [ ] 1.2 Replace the hardcoded `version = '0.1.0-SNAPSHOT'` in the `subprojects { }` block with `version rootProject.version` (or remove entirely if the plugin sets `rootProject.version` directly, matching `spock-deepmock`'s `allprojects` pattern)
- [ ] 1.3 Verify `./gradlew properties -q | grep version` (or equivalent) resolves a sensible pre-release version with no tags present

## 2. Artifact naming and versionMapping cleanup

- [ ] 2.1 Delete the `selfNamedArtifacts` list and `publishedArtifactId` derivation from root `build.gradle`'s `pluginManager.withPlugin('maven-publish')` block
- [ ] 2.2 Remove the `artifactId = publishedArtifactId` assignments (both the `components.java` and `components.javaPlatform` publication blocks) so `artifactId` defaults to `project.name`
- [ ] 2.3 Remove the `versionMapping { usage('java-api') { fromResolutionResult() }; usage('java-runtime') { fromResolutionResult() } }` block
- [ ] 2.4 Run `./gradlew publishToMavenLocal` and confirm coordinates in `~/.m2/repository/io/github/joke/percolate/` match the unprefixed names (`bom`, `spi`, `processor`, `reactor`, `reactor-blocking`, `strategies-builtin`, `annotations`, `percolate`, `percolate-javapoet`)

## 3. Fix the `:dependencies` POM leak at its source

- [ ] 3.1 In `spi/build.gradle`, change `api platform(project(':dependencies'))` to `implementation platform(project(':dependencies'))` (fall back to `compileOnly` only if `implementation` breaks compilation)
- [ ] 3.2 Run `./gradlew :spi:compileJava :spi:check` and confirm `spi` still compiles and its own unit/integration suites pass
- [ ] 3.3 Delete the `pom.withXml { asNode().dependencyManagement.each { it.parent().remove(it) } }` block from root `build.gradle`
- [ ] 3.4 Run `./gradlew publishToMavenLocal` and inspect `spi`'s generated POM to confirm no `<dependencyManagement>` import of the internal `:dependencies` platform remains

## 4. POM metadata via the metadata plugin

- [ ] 4.1 Add `io.github.sgtsilvio.gradle.metadata` (and `io.github.sgtsilvio.gradle.maven-central-publishing`) to root `build.gradle`'s `pluginManagement`/`plugins` block
- [ ] 4.2 Declare a single `metadata { }` block (readable name, description, `license { apache2() }`, `developers { register("joke") { fullName = "Joke de Buhr"; email = "joke@xckk.de" } }`, `github { org = "joke"; }`) once at the root, scoped to apply across every module that applies `maven-publish`
- [ ] 4.3 Remove any now-redundant hand-written POM population left over from the existing `publishing { publications { maven(MavenPublication) { ... } } }` block once the metadata plugin supplies it
- [ ] 4.4 Run `./gradlew publishToMavenLocal` and inspect a generated POM (e.g. `spi`) to confirm name/description/license/developer/SCM fields are present

## 5. Central Portal publishing wiring

- [ ] 5.1 Apply `io.github.sgtsilvio.gradle.maven-central-publishing` across every module that applies `maven-publish`, confirming it targets the Central Portal endpoint (no OSSRH staging URL, no GitHub Packages repository)
- [ ] 5.2 Apply core `signing`, configured with `useInMemoryPgpKeys(findProperty('signingKey'), findProperty('signingPassword'))`, required whenever the task graph includes `publish`
- [ ] 5.3 Enable `withSourcesJar()`/`withJavadocJar()` on every publishable Java module (mirroring `spock-deepmock`'s `java { withJavadocJar(); withSourcesJar() }`)
- [ ] 5.4 Confirm (by inspection, since real credentials aren't available yet) that `mavenCentralUsername`/`mavenCentralPassword` and `signingKey`/`signingPassword` are the only credential properties the build expects, matching what the user will add as GitHub secrets later

## 6. Release workflow

- [ ] 6.1 Add `.github/workflows/release.yml` with a `release-please` job using `GoogleCloudPlatform/release-please-action`, `release-type: simple`, authenticated with a `RELEASE_PLEASE` secret token, exposing `release_created`/`tag_name` outputs
- [ ] 6.2 Add a `publish` job, `needs: release-please`, gated on `if: ${{ needs.release-please.outputs.release_created }}`, checking out the release tag, setting up Java, and running `./gradlew check` then `./gradlew publish`
- [ ] 6.3 Wire `ORG_GRADLE_PROJECT_mavenCentralUsername`/`Password` and `ORG_GRADLE_PROJECT_signingKey`/`signingPassword` env vars on the `publish` step from the corresponding (not-yet-created) GitHub secrets
- [ ] 6.4 Confirm no step in the workflow can run `publish` on a non-release trigger (review the job's `if:` condition and its dependency on `release-please`'s output)

## 7. Documentation and verification

- [ ] 7.1 Update README.md's consumer usage snippet from `io.github.joke.percolate:percolate-bom` / `percolate-annotations` to the unprefixed coordinates (`io.github.joke.percolate:bom`, `io.github.joke.percolate:annotations`)
- [ ] 7.2 Run `./gradlew check` and confirm it is green, including the `consumer-packaging` module-boundary and smoke checks
