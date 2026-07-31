## Context

`tighten-testability-conventions` (archived 2026-07-28) confined javadoc to `annotations` and
`spi` and, finding no static-analysis rule that could express the ban, enforced it with a
`checkNoJavadoc` task in `percolate.conventions`: a `doLast` block that reads every file under
`src/main/java`, looks for `/**`, and throws a `GradleException` naming the offenders. A
per-module `ext.percolatePublicApi` flag (default `false`) told the task which modules to skip.

That mechanism is rejected. The build system is not to perform source-code checks — the rule is
about the mechanism, not its placement, so "move the task into the modules that want it" is
equally out. What remains is a scoping decision: which parts of the build are *tooling
configuration* (legitimate) and which are *source inspection written by hand* (not).

## Goals / Non-Goals

**Goals:**

- Delete `checkNoJavadoc`, its `check` wiring, and the `ext.percolatePublicApi` flag that exists
  only to feed it, leaving `percolate.conventions` to do nothing but apply plugins and configure
  their settings.
- Record the general rule in the build spec so the next person — or the next agent — does not
  reintroduce a source-scanning task under a different name.
- Keep the javadoc policy itself, and every source file, exactly as it is.

**Non-Goals:**

- Finding a replacement enforcement mechanism. None is wanted; see the decision below.
- Touching javadoc content in any module, or the `withJavadocJar()` publishing obligation.
- Revisiting the other conventions the plugin configures (spotless, PMD, Error Prone, NullAway,
  CodeNarc, pitest, test tagging, publishing) — those are tool configuration and stay.

## Decisions

**Delete outright rather than re-home or replace.** Three options existed: keep the scan but move
it into `annotations`/`spi`/each internal module; re-express it as an analyser rule; or drop
enforcement. Re-homing fails the actual objection — a hand-written source scan is unwanted
wherever it lives, and distributing it multiplies it by the module count. Re-expressing it fails
on capability, and this was already established rather than assumed: PMD's `CommentRequired`
cannot address a package-private method, which is where nearly every helper lives under the
no-private-methods rule, and PMD 7 dropped `FormalComment` from the XPath-addressable AST, so a
custom rule is not available either. ArchUnit reads bytecode, where comments do not exist. That
leaves dropping enforcement, which is also the cheapest correct answer: the invariant is
cosmetic, has never been violated in practice, and a violation is caught in review and fixed by
deleting a comment.

**Draw the line at "who reads the source".** The distinction that keeps this decision applicable
later: configuring an analyser in Gradle is fine — spotless, PMD, CodeNarc, Error Prone and
NullAway all read source, but the build script only passes them settings. What is forbidden is
build-script logic that itself opens, greps or parses project sources and decides a verdict.
`checkNoJavadoc` is the latter; `pmd { ruleSets = [...] }` is the former. This is stated as a
requirement on `isolated-projects-build`, the capability that already owns "how build
configuration is organised", rather than as a fourth requirement inside
`internal-javadoc-policy`, because it constrains the whole build and not just javadoc.

**Retire the `percolatePublicApi` flag with the task.** The flag has exactly one consumer. Left
behind it becomes a declaration that means nothing, which is worse than its absence: a module
would keep asserting a nature no mechanism reads. It goes, and `internal-javadoc-policy` loses
the "each module declares whether it is public API" clause along with it.

**Keep the policy's Purpose and its two authoring requirements.** The confinement rule and the
demote-to-`//`-comment rule are still how code is written here; only the enforcement requirement
and its scenarios are deleted, replaced with a requirement stating explicitly that the policy is
unenforced by the build and why that is acceptable. Deleting the whole capability would lose the
rationale that makes the convention followable.

## Risks / Trade-offs

- **A javadoc block is reintroduced into an internal module and no build fails** → Accepted, and
  the point of the change. The blast radius is a comment in a javadoc jar nobody opens; review
  catches it, and the fix is a deletion. This trade is recorded in the spec so a future reader
  sees it was chosen, not overlooked.
- **The spec loses a "verified by the build" claim it currently makes** → The requirement is
  rewritten rather than silently dropped, so `openspec/specs/internal-javadoc-policy/spec.md`
  keeps stating what is and is not automated.
- **Someone re-adds an equivalent task later, citing the archived design that justified it** →
  Mitigated by the new `isolated-projects-build` requirement, which is a live spec and outranks
  an archived change's design note.
- **`check` gets faster and a stale `build/reports/no-javadoc.txt` lingers in existing build
  directories** → Harmless; removed by any `clean`.
