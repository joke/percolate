## Why

Percolate has no release pipeline: `version` is a hand-edited constant (`0.1.0-SNAPSHOT`) in root `build.gradle`, there is no CHANGELOG or tagging automation, and no artifact has ever been published anywhere — `maven-publish` only produces local POMs/jars today. Getting to a real Maven Central release currently requires a human to bump a version string, write release notes, tag, sign, and push by hand. Separately, the existing publish wiring in root `build.gradle` carries two accumulated workarounds worth cleaning up while the publish path is being built out for real: a hand-maintained `selfNamedArtifacts` exception list standing in for a naming rule the coordinate `group` already implies, and a `pom.withXml` XML-surgery step that strips the internal `:dependencies` version-pin platform out of `spi`'s published POM after the fact, because `spi` imports it as `api` in the first place.

## What Changes

- Add `org.shipkit.shipkit-auto-version` so `project.version` is computed from git tags at configure time, replacing the hardcoded root `version = '0.1.0-SNAPSHOT'`.
- Add `.github/workflows/release.yml` with a `release-please` job (`GoogleCloudPlatform/release-please-action`, `release-type: simple`, one repo-wide version — the repo already writes disciplined conventional commits) that opens/updates a release PR and, on merge, creates a git tag and GitHub release.
- Add a `publish` job to the same workflow, gated strictly on `release_created` — tagged releases only, **no snapshot publishing** — that runs `./gradlew check` then `./gradlew publishToMavenCentral` (the Central Portal plugin's own publish task; plain `publish` only reaches locally-configured repositories, of which there are none).
- Apply `io.github.sgtsilvio.gradle.maven-central-publishing` (Central Portal API, in-memory `signingKey`/`signingPassword` PGP signing, no legacy OSSRH staging URL, no gpg-cli import step) and `io.github.sgtsilvio.gradle.metadata` (a single `metadata {}` block in root `build.gradle` supplying POM name/description/Apache-2.0 license/developer/GitHub coordinates) to every publishable module, replacing the current minimal hand-assembled `publishing {}` POM content. Central Portal credentials, the GPG key, and the release-please token are added to GitHub repo secrets by the user directly — no secrets work is in scope here.
- **BREAKING**: Drop the `percolate-` artifactId prefix. The coordinate `group` (`io.github.joke.percolate`) already disambiguates, so every module's Maven `artifactId` defaults to Gradle's own `project.name` — e.g. `io.github.joke.percolate:bom`, `io.github.joke.percolate:reactor`, `io.github.joke.percolate:spi` — instead of `io.github.joke.percolate:percolate-bom`, etc. Delete the `selfNamedArtifacts` list, the `publishedArtifactId` derivation, and the now-unneeded `versionMapping` block (every dependency version in this build is already pinned to an exact version via the `:dependencies` constraints, so resolution-result pinning has nothing to correct) from root `build.gradle`. No module directories are renamed.
- Update the README's consumer usage snippet to the new (unprefixed) coordinates.
- Remove `spi`'s `api`/`implementation platform(project(':dependencies'))` edges (including on its `java-test-fixtures` variant, which publishes into the same POM as the main component) so the internal third-party version-pin platform stops leaking into `spi`'s published POM, then delete the `pom.withXml { asNode().dependencyManagement.each { it.parent().remove(it) } }` workaround in root `build.gradle` — the leak is fixed at its source instead of scrubbed after the fact.
- Add `io.freefair.gradle:lombok-plugin` (`io.freefair.lombok`) to every Lombok-using module: turning on javadoc jars (above) surfaced that `javadoc` had never successfully run on this codebase — Lombok-generated members and its `onConstructor_ = @Inject` constructor-annotation trick are unresolvable under javadoc's own parser. The plugin wires `javadoc` to read delomboked (expanded, Lombok-free) source instead, which the user chose over shipping empty javadoc jars or rewriting the affected production source.

## Capabilities

### New Capabilities
- `release-versioning`: git-tag-derived `project.version` (shipkit-auto-version) plus the release-please workflow that decides the next version from conventional commits and creates the tag/release that drives it.
- `maven-central-publishing`: the gated (`release_created`-only, no snapshots) CI publish job, Central Portal credentials/signing wiring, and the declarative POM metadata block applied uniformly across every publishable module.

### Modified Capabilities
- `consumer-packaging`: the "Requirement: Consumer version platform" and "Requirement: Published Maven artifacts" sections currently cite prefixed coordinates (`io.github.joke.percolate:percolate-bom`); these change to the unprefixed form (`io.github.joke.percolate:bom`) and the BOM/README examples update to match.

## Impact

- `build.gradle` (root): shipkit-auto-version plugin + removal of hardcoded version; `metadata {}` block; Central Portal publishing + signing wiring; `withSourcesJar()`/`withJavadocJar()`; `io.freefair.lombok` version pin; artifactId/versionMapping simplification; removal of the `:dependencies` POM-stripping workaround.
- `spi/build.gradle`: removed `:dependencies` platform edges (main and test-fixtures) that leaked into the published POM.
- `spi/processor/strategies-builtin/reactor/reactor-blocking/build.gradle`: apply `io.freefair.lombok`.
- `.github/workflows/release.yml`: new file.
- `README.md`: consumer coordinate examples updated.
- Every publishable module (`annotations`, `bom`, `percolate`, `percolate-javapoet`, `processor`, `reactor`, `reactor-blocking`, `spi`, `strategies-builtin`): published coordinate changes from `percolate-<name>` to `<name>` (or is unaffected where it already had no prefix).
- GitHub repo settings: new secrets required (`RELEASE_PLEASE`, `mavenCentralUsername`, `mavenCentralPassword`, `signingKey`, `signingPassword`) — added by the user outside this change.
