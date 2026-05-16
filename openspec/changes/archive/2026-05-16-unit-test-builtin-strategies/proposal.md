## Why

The eleven built-in strategies (`DirectAssign`, `ListMap`, `ListWrap`, `SetMap`, `SetWrap`, `OptionalMap`, `OptionalUnwrap`, `OptionalWrap`, `MethodCallBridge`, `GetterRead`, `ConstructorCall`) are today exercised only at the pipeline level — through `ExpansionFailureModesSpec` and the property specs that drive the full expansion engine. Those tests prove that the system works end-to-end with arbitrary strategies; they do not prove that any individual strategy honours its own contract, and they cannot pinpoint which strategy regresses when something breaks.

As we prepare to add many more built-in strategies (and to invite third-party authors writing against the now-extracted `percolate-spi`), the absence of per-strategy contract tests is the highest-leverage gap to close. A failing per-strategy spec names the strategy that broke, the precondition that misfired, or the codegen output that drifted — in seconds. The same failure surfaced through the pipeline takes minutes and triangulation. Establishing this layer of tests now also creates the pattern third-party strategy authors should follow, with a working reference per builtin.

## What Changes

**New per-strategy Spock specs in `percolate-strategies-builtin`**

One Spock specification per builtin, tagged `@spock.lang.Tag('unit')`, exercising the strategy directly — no `ExpansionHarness`, no pipeline. Each spec covers, where applicable:
- empty-stream / empty-Optional return for each precondition the strategy enforces;
- the shape of the produced `BridgeStep` / `Step` / `GroupBuild` on the happy path (input/output types, weight, element seeds);
- branches the strategy actually distinguishes (e.g. array vs. `Iterable` vs. `Optional` input for `ListMap`; `getX` / `isX` / field-named accessor for `GetterRead`; constructor-by-name vs. constructor-by-arity for `ConstructorCall`).

Codegen output is **not** asserted in this change. Pinning the rendered code shape is a sensible next layer of safety but mixes concerns and is deferred to a follow-up.

**`ResolveCtx` test-builder helper**

A small builder under `strategies-builtin/src/test/groovy/.../test/` for assembling a `ResolveCtx` that combines:
- the real javac-backed `Types` and `Elements` from `TypeUniverse` (consumed via `spi`'s `testFixtures`);
- a Spock-mocked `CallableMethods` configurable per spec;
- optionally a stubbed `mapperType()` / `currentMethod()` for strategies that read them.

The helper removes per-spec ceremony and gives third-party strategy authors a copyable pattern.

**`src/test/java` shape fixtures**

Real Java types compiled into the test classpath that `ConstructorCall` and `GetterRead` can introspect:
- a record with named parameters;
- a JavaBean with `getX` / `setX` accessors;
- a class with an `isFlag` boolean accessor;
- a class whose constructor is positional but whose fields are named (forces the constructor-by-arity path in `ConstructorCall`).

These live at `strategies-builtin/src/test/java/io/github/joke/percolate/spi/builtins/fixtures/` and are resolved at test time via `TypeUniverse.element('…')`.

**Findings policy (not a change in code)**

Pre-existing oddities surfaced while writing these specs (e.g. `ListMap`'s codegen that throws `UnsupportedOperationException`, `ListMap` accepting `Optional` inputs, `MethodCallBridge.subtypeDistance`'s ambiguous return) are pinned by spec scenarios that document current behaviour, then filed as follow-up changes. This change does not modify strategy behaviour.

## Capabilities

### New Capabilities

- `builtin-strategy-unit-tests`: a discipline contract for testing the strategies in `percolate-strategies-builtin` in isolation. Specifies that every built-in `Bridge` / `SourceStep` / `GroupTarget` has a corresponding Spock specification, where it lives, how it is tagged, what it must verify (preconditions, happy path, codegen output, element seeds), the test-time substrate it consumes (`TypeUniverse` + `HarnessResolveCtx` from `spi` `testFixtures`), and the boundary at which mocking is appropriate (`CallableMethods` and adjacent `ResolveCtx` surfaces; never `Types` / `Elements`).

### Modified Capabilities

None. The capabilities that describe the strategies themselves (`container-expansion`, `expansion-strategy-spi`, `callable-method-discovery`) describe behaviour that this change verifies, not behaviour that this change alters. No requirement in any existing spec changes; new specs are added under the new capability above.

## Impact

**Affected teams** — solo project at present; no cross-team coordination needed. The discipline this change establishes (one unit spec per built-in strategy, mocking only true collaborators, using `spi`'s `testFixtures` for the type substrate) becomes the template every future built-in and every third-party strategy is expected to follow.

**Code** — eleven new Spock specs under `strategies-builtin/src/test/groovy/io/github/joke/percolate/spi/builtins/`, one per builtin. A `ResolveCtxBuilder` (working name) helper in the same test tree. Four-ish fixture types under `strategies-builtin/src/test/java/io/github/joke/percolate/spi/builtins/fixtures/`. Zero changes to `main/` code.

**Build** — `strategies-builtin/build.gradle` already declares Spock + Mockito + byte-buddy + junit-platform-launcher as part of the extraction change. No new dependencies required. `testImplementation testFixtures(project(':spi'))` (also from the extraction change) covers `TypeUniverse` + `HarnessResolveCtx`.

**APIs / dependencies** — none. This change is purely additive in the test scope and does not touch any module's published surface.

**Systems** — CI: `./gradlew test` picks up the new specs automatically.

**Sequencing** — this change is implemented strictly after `extract-spi-and-builtins`. The new module (`percolate-strategies-builtin`) and the `spi` `testFixtures` configuration must exist before any artifact in this change can compile. Tasks are authored against the post-extract state.
