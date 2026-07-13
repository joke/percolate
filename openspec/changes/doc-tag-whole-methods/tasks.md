## 1. MethodSpec overlay in percolate-javapoet

- [x] 1.1 Load the java/lombok/null-safety coding-convention skills and the Spock convention skill before writing any code.
- [x] 1.2 Vendor `percolate-javapoet/src/main/java/com/palantir/javapoet/MethodSpec.java` from upstream `0.16.0` (incl. its nested `Builder`), carrying the Apache-2.0 header/attribution; keep the diff against upstream minimal and auditable.
- [x] 1.3 Add to the overlay a nullable `docTag` name field + `Builder.docTag(String)` setter, and ~4 lines in `emit()`: when present, emit `// tag::<name>[]` before the annotations/signature and `// end::<name>[]` after the closing brace. No other behaviour change; null `docTag` ⇒ output identical to upstream.
- [x] 1.4 Wire `shadowJar` in `percolate-javapoet/build.gradle`: `filesMatching('com/palantir/javapoet/MethodSpec*.class') { duplicatesStrategy = DuplicatesStrategy.EXCLUDE }` so the project overlay (project-files-first) wins over the upstream twin; leave the rest of the jar on its default strategy.
- [x] 1.5 Treat the overlay as third-party (design D6): in `percolate-javapoet/build.gradle` exclude `com/palantir/javapoet/**` from PMD and error-prone (`options.errorprone.excludedPaths` — also covers NullAway + `RequireExplicitNullMarking`, so no `package-info` needed); keep `jacocoTestCoverageVerification` disabled (do NOT hold vendored code to our coverage/complexity floors); run `spotlessApply` on the vendored file.
- [x] 1.6 Add a focused Spock spec in `processor` (reusing its harness, over the *relocated* `io.github.joke.percolate.javapoet.MethodSpec`) pinning that `MethodSpec.methodBuilder(...).docTag("m").build().toString()` emits `// tag::m[]` before the signature and `// end::m[]` after the closing brace (whole method), and that without `docTag` the emission is byte-identical to upstream. Spock house style (strict mocking ended by `0 * _`, no `given:`/`setup:` label, `where:` tables; no jqwik).
- [x] 1.7 Assert the shaded `percolate-javapoet` jar carries exactly one `io.github.joke.percolate.javapoet.MethodSpec` and that it is the overlay (has the include-tag behaviour); confirm no `com.palantir.javapoet` class remains (swallow invariant) and the relocation/ArchUnit guards from `relocate-javapoet-as-spi-api` still pass.

## 2. Rewire codegen to whole-method tagging

- [x] 2.1 Change `BuildMethodBodies.renderMethod`/`docTagged` to stop wrapping the body `CodeBlock`; instead set `methodSpecBuilder.docTag(methodName)` when `options.isDocTags()` at the point the `MethodSpec` is assembled (generate render leaf).
- [x] 2.2 Retire the `spi` `DocTags` utility and its `DocTagsSpec` (the CodeBlock body-wrap is no longer used); update any imports/references.
- [x] 2.3 Update `BuildMethodBodiesSpec` (and any generate-stage specs asserting body-level tag wrapping) to the new method-level seam — tags are no longer inside the body; drive the mocked/opaque seam per house style, no javac on the unit path.
- [x] 2.4 Run `:processor:pitest` scoped to the affected generate classes; confirm the mutation/line/test-strength floors are held (add a fixture, never lower the floor, if a mutant survives).

## 3. Re-pin generation output

- [x] 3.1 Re-pin the `docTags`-on doc/e2e fixtures (temporal + any other `docTags`-on generation) to whole-method output: the `// tag::`/`// end::` markers now sit outside the braces, bracketing the full method.
- [x] 3.2 Re-verify the `docTags`-off byte-identical invariant against the `compile-time-switches` doctags-off fixture — consumer output unchanged.

## 4. Doc-authoring fixes

- [x] 4.1 Add `indent=0` to every generated-impl `include::[tag=…]` across all feature pages (`temporal-mapping.adoc`, `conversion-methods.adoc`, `collections.adoc`, `optionals.adoc`, `map-annotation.adoc`, `defaults-and-nullness.adoc`, `default-method-conversions.adoc`, `nested-paths.adoc`, `reactive.adoc`, `reactor-blocking.adoc`, `getting-started.adoc`, `extending.adoc`, and `compile-time-switches.adoc`'s `[tag=…]` includes).
- [x] 4.2 Change `temporal-mapping.adoc`'s whole-class listing (`include::…TemporalMapperImpl.java[]`) to `[tags=**]` so marker comments are stripped from the rendered class. Leave `compile-time-switches.adoc`'s intentional doctags-on/off `[]` demonstration untouched.
- [x] 4.3 Rebuild the manual (Antora) and confirm on the temporal page: per-method snippets show complete, left-aligned methods; the whole-class listing shows the class with no `// tag::` noise; spot-check one other page (e.g. conversion-methods) renders complete methods.

## 5. Sync, verify, commit

- [x] 5.1 Run `percolate-smoke:smokeRun` and rebuild the doc-e2e outputs; confirm consumer (`docTags`-off) generated code is byte-identical and only the `docTags`-on doc snippets changed shape.
- [x] 5.2 Sync the `code-generation`, `javapoet-relocation`, and `user-manual` delta specs into the main specs (`opsx:sync`).
- [x] 5.3 Run `./gradlew check --no-configuration-cache` and resolve every violation — NEVER continue with a failing gate (confirm any Spotless/Guava-worker flake against unmodified `main` via `git stash` before attributing it here).
- [x] 5.4 Commit the completed change with `/commit-commands:commit`.
