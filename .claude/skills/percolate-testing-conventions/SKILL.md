---
name: percolate-testing-conventions
description: Percolate-specific testability rules that layer on top of the conventions plugin's java/groovy/spock skills. Load alongside them whenever writing or modifying Java production code or Spock specs in this repo — it covers the self-call interaction idiom and its declaration-order requirement, why `1 * subject._` is exempt from the bare-underscore prohibition, static mocking via SpyStatic (and the absence of MockStatic), and the permitted static contexts. Trigger for any Java or Spock work here even when the user never mentions conventions.
---

# Percolate testing conventions

These layer on `conventions:java-coding-conventions`, `conventions:groovy-coding-conventions`, and
`conventions:spock-coding-conventions`. Load those first — this file records only what is specific to
this repo or missing from them. Established by change `tighten-testability-conventions`.

## Never go static to dodge a Spy

Extracting a helper onto a class exercised via `Spy()` makes the new self-call an interaction, so a
strict `0 * _` starts failing even though the extraction is behaviourally a no-op. **Declare the
interaction. Do not make the helper `static` to hide it from interception.**

This is written down because the opposite advice was once recorded here and produced a meaningful share
of the repo's static methods. `INVOKESTATIC` bypassing the spy proxy is the bug, not the fix.

```groovy
when:
subject.call(x)          // subject is a Spy; call() invokes f() internally

then:
1 * subject.f(expected)  // the specific self-call — MUST be declared first
1 * subject._            // absorbs the entry call from `when:`
0 * _
```

**Declaration order is load-bearing.** Spock matches each invocation against the interactions in
declaration order and the first non-saturated match wins. Put `1 * subject._` after the specific
self-calls, or it absorbs the first one and the specific expectation fails with zero invocations.

**`1 * subject._` is exempt from the "never a bare `_`" rule.** That rule forbids an *argument*
wildcard, which would let a wrong argument through. This is a *method* wildcard scoped to one mock and
bounded by an exact cardinality — the entry call is already pinned by the `when:` block, and any
additional undeclared self-call saturates neither slot and falls through to `0 * _`. It stays tight.

## Static mocking

`SpyStatic(Class)` is the only entry point — **there is no `MockStatic`** in Spock 2.4. It is spy-only:
the real static runs unless you stub it. It works solely under the mockito mock maker, which
`percolate.conventions` configures for every module, and Mockito registers the static mock **per
thread**, which is one of the two reasons `runner { parallel { enabled false } }` must stay set.

Reach for it only for a method that is legitimately static under the rules below.

## Permitted static contexts

Prefer a `protected` instance method (with `@VisibleForTesting` when no production subclass uses it —
ArchUnit Rule C). `static` is correct only for:

- a **public** static on the published `spi` surface, which third-party strategy authors already call:
  the factories on `Port`, `PortType`, `OperationSpec`, `Offer`, `Nullability`, `DirectiveInput`,
  `ChildScopeSpec`, plus all of `LiteralCoercion` and `Subjects`. Converting these breaks the API.
- a Dagger `@Provides` method
- vendored third-party sources under `lib/`
- a genuine static context — a `main` entry point, a static initializer helper
- a method that reads or writes a `static` field of its own class
- a **named constructor** — the body is one `new` of the declaring type (`Diagnostic.error`, `Cost.finite`,
  `Dep.port`, `AccessPath.of`). Nothing to intercept: a double could only return what the constructor
  already returns. A factory that carries real logic in helpers is a different thing — decompose it into
  an injectable factory collaborator, even though Rule D cannot tell the two apart and won't flag it
- a **stateless all-static utility holder**: a `final` class, private constructor, no instance state,
  nothing but static members, annotated Lombok `@UtilityClass`. `Labels`, `Reactors`, `Blockings`,
  `LiteralCoercion` and `PercolateCompiler` are the ones that exist. There is no instance to spy and
  nothing to inject, so stub them with `SpyStatic` rather than injecting a pure function into every
  `ServiceLoader`-instantiated strategy

Two things the holder exemption does **not** license, neither of them checkable by ArchUnit:

- **Do not extract a lone method into a new holder to escape spy-testing.** A holder groups functions
  that genuinely belong together; a single-function holder is the abuse this exemption invites.
- **Do not make a method static merely because it touches no instance field.** On a class that has
  instances, interception by a test double outweighs that observation — keep it an instance method.
  (Error Prone's `MethodCanBeStatic` is off for exactly this reason.)

## Stubbing a self-call costs coverage

`1 * subject.f(_) >> value` means the caller's feature method no longer exercises `f`'s logic. The
helper needs its own feature method. A pitest drop below 85/95/90 after a conversion usually means a
helper was stubbed in its caller's spec without being separately tested.
