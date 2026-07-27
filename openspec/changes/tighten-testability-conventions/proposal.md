# Tighten Testability Conventions

## Why

Two testability conventions are enforced repo-wide (no private methods; unused protected methods carry `@VisibleForTesting`), but the escape hatch they left open — `static` — was never closed. 192 static methods in main Java are unmockable and unspyable by construction, and a documented workaround actively pushed developers toward them: when extracting a helper on a `Spy`'d class tripped strict `0 * _`, the recorded fix was "make it static so `INVOKESTATIC` bypasses interception". That was a misunderstanding. The correct fix is one more interaction line, and the static dodge has been quietly eroding the very seam the private-method ban was built to create.

Separately, the mock-maker configuration that makes final classes mockable exists in exactly one module out of six, so most of the repo cannot mock a Lombok `@Value` or a `final` stage at all — and cannot use `SpyStatic` for the statics that legitimately remain. Finally, javadoc has accumulated across internal modules where it never renders, mixing genuine design rationale with blocks that restate the signature they sit on.

## What Changes

- **Uniform mock maker.** Every module gets `mockMaker { preferredMockMaker spock.mock.MockMakers.mockito }`, hoisted with the rest of `SpockConfig.groovy` into the `percolate.conventions` buildSrc plugin instead of six near-identical copies. The mockito dependency becomes uniform too (`spi` has none today). This unblocks mocking final classes/methods and `SpyStatic` everywhere.
- **Statics converted to protected instance methods.** Of the 192 static methods in main Java, the convertible set is concentrated in `processor` (86) and `strategies-builtin` (64); they become `protected` instance methods, spy-testable in isolation, each carrying `@VisibleForTesting` where no production subclass uses it. Carve-outs stay static: the **entire published `spi` static surface** — 40 of `spi`'s 41 statics are public factories third-party strategy authors already call (`Port.byType`, `OperationSpec.of`, `Offer.refusal`, `Nullability.join`, `PortType.variable`, `DirectiveInput.scalar`, `ChildScopeSpec.lifted`, plus all of `LiteralCoercion` and `Subjects`) — along with Dagger `@Provides` and vendored `lib/javapoet`.
- **A recorded self-call idiom.** The `1 * subject.f(...)` → `1 * subject._` → `0 * _` ordering replaces the static workaround, including its deliberate carve-out from the "never a bare `_`" rule (that rule targets *argument* wildcards; this is a *method* wildcard scoped to one mock).
- **`SpyStatic` for residual statics.** Where a static legitimately remains, its spec stubs it via `SpyStatic(Class)` rather than exercising it incidentally.
- **Javadoc confined to `annotations` and `spi`** — the two modules an external developer consumes. Elsewhere, non-obvious blocks demote to `//` comments and signature-restating blocks are deleted. Enforced by PMD's `CommentRequired` with `Unwanted` settings in a second ruleset, with `lib/javapoet` exempt.
- **No new static analysis for spy-coverage.** "Every protected method is spy-tested" is not statically derivable — it needs Java production facts and Groovy spec facts simultaneously, and Spock's AST transform leaves only a string constant where the call edge would be. It ships as a convention with pitest (85/95/90) as the oracle.

## Capabilities

### New Capabilities
- `test-double-conventions`: Mocking-first test-double policy — mocks over fakes, final-class mockability in every module, protected methods spy-tested in isolation, the self-call interaction idiom, and `SpyStatic` for residual statics.
- `internal-javadoc-policy`: Javadoc is the external-contract surface of `annotations` and `spi` only; every other module documents the non-obvious in `//` comments, build-enforced.

### Modified Capabilities
- `module-boundaries`: Adds a structural rule banning `static` methods outside a genuine static context, as the companion to the existing "never private" and "unused protected methods are marked" rules.
- `test-coverage-tooling`: The uniform Spock configuration requirement extends from run-order/parallelism to the mock maker, and moves from per-module duplication to the conventions plugin.

## Impact

- **Build**: `buildSrc/src/main/groovy/percolate.conventions.gradle` gains SpockConfig generation/wiring, a uniform mockito test dependency, and a per-module PMD ruleset override hook. New `.pmd-internal.xml`. Six `SpockConfig.groovy` files removed.
- **Main sources**: method signatures in `processor` (86 statics) and `strategies-builtin` (64), minus each module's own static factories; `spi` is effectively unaffected (1 non-public static). ~355 javadoc blocks across `processor`, `strategies-builtin`, `reactor`, `reactor-blocking`, `percolate`.
- **Specs**: every spec exercising a converted static, plus specs adopting the self-call idiom. `UnifierSpec` already uses it.
- **Published artifacts**: javadoc jars for the internal published modules become near-empty. `maven-central-publishing`'s "sources and javadoc jars are published" requirement still holds — the jars are still produced and Central accepts them.
- **No public API break** — the two published-surface static holders are explicitly carved out.
- **Conventions**: the spock convention skill needs the self-call idiom and its `_`-wildcard carve-out written in, or a later session will "correct" it back to the static workaround.
