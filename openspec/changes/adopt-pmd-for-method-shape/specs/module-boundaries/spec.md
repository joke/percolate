## REMOVED Requirements

### Requirement: Engine internal methods are never private

**Reason**: The invariant is unchanged; only its owner is. It becomes a property-of-one-declaration check
owned by PMD, alongside the rest of the method-shape family, so that no invariant carries two enforcers with
two exemption sets. PMD additionally reports it at the declaration's own line and surfaces it in the IDE,
and — because Gradle feeds PMD `allJava` rather than bytecode — it needs none of the synthetic, bridge, or
`@Generated` filtering the ArchUnit form required.

**Migration**: Enforced by `AvoidPrivateAndProtectedMethods` in the `joke-strict` PMD ruleset. See the
`method-shape-analysis` requirement *Methods are never private, and static only in a genuine static context*.
The shaded-`lib..` exemption is no longer needed: `lib/javapoet` is a separate module and PMD is configured
per module.

### Requirement: Protected methods unused by any subclass are marked for testing

**Reason**: The rule asked whether *no* subclass uses a `protected` method — absence of evidence over a set
that cannot be enumerated. Under the per-source-set evaluation adopted in `adopt-nebula-archrules` it could
not see cross-module subclasses at all, which is why that change added a blanket published-`spi` exemption;
and no wider import scope could ever suffice, because `spi` is a published contract whose implementors live
outside the build entirely. The rule also passed vacuously whenever any subclass happened to override,
conflating a designed extension point with an accidental one.

**Migration**: Replaced by declaration rather than inference. Every `protected` method now carries exactly
one of `@VisibleForTesting` (test seam) or `@ApiStatus.OverrideOnly` (extension point), checked per
declaration with no import scope, so it holds at module, repository and third-party-consumer range. See the
`method-shape-analysis` requirement *A protected method declares whether it is a test seam or an extension
point*. The two methods design D5 exempted by name, `Container#containerOf` and `Container#wrapNullness`,
are now marked rather than exempted.

### Requirement: Engine internal classes stay within a size ceiling

**Reason**: The ceiling is a package list plus a number — configuration wearing a rule's clothes — and
`ArchRulesService` offers no property injection to express it as anything else. Its purpose, co-enforcing the
no-private rule so a monolith cannot satisfy it by exposing its internals, is unchanged and moves with the
rest of the family.

**Migration**: Enforced by PMD's `TooManyMethods`, re-enabled in percolate's local ruleset composition with
an explicit measured `maxmethods`. It applies repo-wide rather than only to the decomposed packages, which is
a widening, not a loss. See the `method-shape-analysis` requirement *Class size is capped so the no-private
rule cannot be satisfied by exposure*, including the recorded fallback if no useful threshold can be measured.

### Requirement: Methods are static only in a genuine static context

**Reason**: Same as the no-private rule — one invariant, one owner. Its permitted contexts were already
matched as shapes, and the same shapes are expressible in PMD once the artifact recognises named constructors
and Lombok's `@UtilityClass`.

**Migration**: Enforced by `StaticMethodsModifyStaticState` in the `joke-strict` PMD ruleset. See the
`method-shape-analysis` requirement *Methods are never private, and static only in a genuine static context*.
The permitted shapes carry over intact — utility holder, named constructor, framework-mandated static, the
enum `values`/`valueOf` pair, and `main` — with the published-`spi` public-static allowance subsumed by the
named-constructor and utility-holder shapes it was standing in for.
