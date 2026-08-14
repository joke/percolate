## Why

Percolate now enforces the same testability doctrine twice. ArchUnit's `MethodShapeRules` (Rules A–D: no
private, size ceiling, unused-protected-is-marked, no static) and PMD's `AvoidPrivateAndProtectedMethods` /
`StaticMethodsModifyStaticState` chase an identical goal, stated almost word for word in both — *every
authored method must be interceptable by a test double*. Running the new `joke-strict` ruleset produces 743
violations, of which the 76 raised by those two PMD rules are **entirely** shapes ArchUnit deliberately
exempts: 29 Lombok `@UtilityClass` statics, 24 named constructors, 5 Dagger `@Provides`, 8 `protected
abstract` spi hooks, 8 `@VisibleForTesting protected` methods (ArchUnit's own sanctioned form), and the 2
published-spi methods design D5 exempted by name. Zero novel findings. Two owners, two exemption sets, and
nothing but drift to look forward to.

The tie-break is not tooling preference. ArchUnit's Rule C asks *"does no subclass use this protected
method?"* — absence of evidence over a set that cannot be enumerated. Under the per-source-set runner adopted
in `adopt-nebula-archrules` it cannot see cross-module subclasses at all, which is why that change added a
blanket published-spi exemption. Restoring a union classpath would undo the module-local evaluation that
change just bought, and would still be insufficient: `spi` is a published contract, so third-party subclasses
sit outside every scope the build controls. The question is unanswerable by construction, in ArchUnit and in
PMD alike.

## What Changes

- **Replace inference with declaration.** A `protected` method SHALL declare which of two things it is:
  `@VisibleForTesting` (a test seam) or `@ApiStatus.OverrideOnly` (a genuine extension point). This needs no
  import scope, so it holds at module, repository *and* third-party-consumer range — and it is strictly
  stronger than Rule C, which today passes vacuously whenever some subclass happens to override.
- **PMD becomes the sole owner of method shape.** Every method-shape invariant moves to a per-declaration
  check: line-numbered, IDE-surfaced via the configured PMD plugin, declaratively testable, and configurable
  through `<properties>` — none of which `ArchRulesService` offers.
- **BREAKING (internal build contract):** delete ArchUnit `MethodShapeRules` in its entirety — all four rules,
  their `getRules()` entries, their six negative fixtures, and `MethodShapeRulesSpec`.
- **ArchUnit keeps what only it can see:** `ModuleLayeringRules`, `EngineEncapsulationRules` and
  `TypeBoundaryRules` are about *edges between packages*, invisible to a single-compilation-unit analyser and
  honest under per-module evaluation. They are untouched.
- **Annotate the published spi extension points** with `@ApiStatus.OverrideOnly` — the two D5 exemptions plus
  the eight `protected abstract` hooks — so the contract is documented rather than exempted.
- **Fix all 743 PMD violations**, organised per rule so they land incrementally.
- **Retire the orphaned root `.pmd.xml`**, left behind when the ruleset moved to the external artifact.

## Capabilities

### New Capabilities
- `method-shape-analysis`: which analyser owns the testability rule family and why; the two-annotation
  declaration rule for `protected`; the per-declaration invariants (no private, no static outside a genuine
  context, package-private seams are marked); and how the ruleset is sourced, versioned and composed.

### Modified Capabilities
- `module-boundaries`: removes the four method-shape requirements it currently carries — *Engine internal
  methods are never private*, *Protected methods unused by any subclass are marked for testing*, *Engine
  internal classes stay within a size ceiling*, and *Methods are static only in a genuine static context* —
  which move wholesale to `method-shape-analysis`. The size ceiling moves there only to be **retired**: design
  D4 was revised to its recorded fallback and `TooManyMethods` stays excluded, because a method count pulls
  against the small-testable-method shape the other three rules enforce. The module-edge requirements are
  unchanged.
- `architecture-rule-distribution`: the *rules are grouped by subject* scenario currently names four subjects
  including "method shape"; it drops to three.

## Impact

- **Depends on** a released `io.github.joke.pmd:rules` carrying three rule fixes, delivered by a separate
  change in that repository: a named-constructor exemption and Lombok `@UtilityClass` recognition for
  `StaticMethodsModifyStaticState`, and `@ApiStatus.OverrideOnly` acceptance in
  `AvoidPrivateAndProtectedMethods`. Released as `0.1.0` on 2026-08-12 and pinned here.
- `architecture-tests`: `MethodShapeRules.java` and `MethodShapeRulesSpec.groovy` deleted, along with the
  fixtures `violators/HasPrivateMethod`, `HasStaticMethod`, `HasUnusedProtectedMethod`, `spi/PublishedHook`,
  `spi/builtins/violators/BuiltinsUnusedProtected`, and
  `processor/internal/stages/expand/violators/OversizedClass`. `Packages` loses any coordinate no surviving
  rule reads.
- `buildSrc/src/main/groovy/percolate.conventions.gradle`: the PMD block, already mid-edit on the working
  tree, is finalised — external ruleset, tool version, and the pinned release coordinate.
- `spi`: `@ApiStatus.OverrideOnly` added to the published extension points; no signature or behaviour change,
  so no consumer break.
- All seven analysed modules take source edits from the 743 fixes — `processor` (415), `strategies-builtin`
  (196), `spi` (92) carry the bulk.
- The 318 `UseVisibleForTestingAnnotation` fixes are a mechanical annotation sweep, intended to be driven as
  an IDE-assisted structural refactoring rather than by hand.
- No change to the published API, the generated-mapper contract, or the processing surface a consumer sees.
