## Why

The user manual single-sourced its example *inputs* but left two holes that re-admit exactly the drift the manual was built to prevent. First, the example mappers live under `docs/`, and `strategies-builtin` reaches *up* into that tree (`srcDir rootProject.file('docs/modules/ROOT/examples')`) to compile them — the example source is far from the spec that compiles it, and the dependency arrow points the wrong way (docs own the code, the module borrows it). Second, generated *output* is still hand-typed prose: `nested-paths.adoc` literally hand-writes the `map(...)` method percolate is said to generate, free to diverge from real output. And the manual deploys on every push to `main` with **no gate on `./gradlew check`** — a build that fails the tests can still publish a site. The reactive examples the manual promises are absent because, under the current wiring, there is nowhere they can compile.

## What Changes

- **Invert example ownership into the owning modules.** Example fixtures move from `docs/` into the module that compiles them (`strategies-builtin/src/test/resources/...`, `reactor/src/test/resources/...`), next to their e2e specs. The `rootProject.file('docs/...')` reach is deleted. Antora pulls the fixtures back into the catalog with the **antora-collector** extension's `scan` (off-line file import, no compilation), so the page `include::`s are unchanged. The dependency arrow flips: the module owns the code, the docs borrow it.
- **Single-source generated output via opt-in doc tags.** A processor option (`-Apercolate.docTags`, **off by default**) makes the generator emit `// tag::<method>[]` / `// end::<method>[]` around generated methods. The e2e spec compiles its module-owned fixture with the option on, asserts the semantic invariant, and **materialises** the real tagged generated source to a build directory the collector scans. Pages then `include::` the generated method by tag — output is correct *by construction*, with no committed golden and no hand-typed code. Antora strips the tag-comment lines, so published snippets are clean. The hand-typed block in `nested-paths.adoc` (and any like it) is replaced by an include.
- **Gate the deploy on a green build.** The manual SHALL publish only after `./gradlew check` passes. The docs build+deploy becomes downstream of the check job (folded into the build pipeline, or triggered on its success), so a red build never publishes and no "roll back the docs" recovery is ever needed — the previous site simply stays.
- **Land the reactive examples** in `reactor`, where `Flux`/`Mono` mappers can actually compile, with their own e2e spec and a manual page — closing the promised-but-missing coverage.

This change follows `enforce-module-separation` (the fixtures land in modules whose boundaries that change makes crisp) and supersedes the manual's interim include-bridge design (D2 of `add-antora-user-manual`).

## Capabilities

### New Capabilities

<!-- None. The doc-tag emission and option attach to existing generation/option capabilities; the manual behaviour modifies the existing user-manual capability. -->

### Modified Capabilities

- `user-manual`: examples are owned by the module that compiles them and reach the site via antora-collector (not a cross-tree `srcDir`); documented generated *output* is single-sourced from real generation (not hand-typed); the site deploys only after `./gradlew check` passes; the reactive container example is present.
- `code-generation`: the generator emits AsciiDoc include tags around generated methods when the doc-tag option is enabled, and emits none (the current clean output) when it is not.
- `processor-options`: a new `docTags` processor option, off by default, that enables doc-tag emission.

## Impact

- **Toolchain**: adopt the `org.antora` Gradle plugin (managed Node) for the site build, replacing the `.mise.toml` `npm:antora` pin; the `@antora/collector-extension` is declared in the plugin's `packages` map (installed in Antora's own Node context) and registered in `antora-playbook.yml`. The site build becomes a `./gradlew antora` task.
- **Moved files**: the six existing example mappers from `docs/modules/ROOT/examples/` into their owning modules' `src/test/resources`; `strategies-builtin/build.gradle` loses the `rootProject.file('docs/...')` `srcDir`.
- **`code-generation`**: a printer-level change to wrap generated methods in tag comments under the option — additive, no effect when the option is off (the consumer contract is unchanged: real consumer builds never set it).
- **CI**: `docs.yml` and `build.yml` reconciled so the Pages deploy depends on `./gradlew check`; full git history/Pages permissions unchanged.
- **`reactor`**: gains a reactive example fixture, an e2e spec, and a manual page; the manual's collector config gains a second `scan` entry.
- **Teams**: solo maintainer; net effect is that a documented example or its output cannot diverge from compiled code, and a failing build cannot publish.
