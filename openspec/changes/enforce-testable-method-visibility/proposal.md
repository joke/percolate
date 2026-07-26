## Why

The architecture suite's "no private methods" rule (`ModuleBoundariesSpec.groovy`, design D6 of `decompose-engine-stages`) only covers `processor.internal..` and `spi.builtins..`. A `private` method is statically dispatched (`invokespecial`) and cannot be intercepted by any test double, so it is unreachable to unit tests — the same defect the rule already polices, just unpoliced everywhere else. A scan of the current tree finds ~20 classes outside the covered packages (`processor.internal.graph`, `processor.internal.stages.validate`, `processor.internal.stages.dump.GraphDumpWriter`, bare `processor` types, `processor.nullability.JspecifyNullabilityResolver`, `spi` core types, `reactorblocking.Blockings`, an `annotations` test fixture) that still declare `private` methods, each an untested/untestable seam waiting to cause the same pain the earlier decomposition changes fixed.

A second, related gap: `protected` is sometimes used as an escape hatch to make a method reachable to a test subclass without actually being an inheritance extension point real subclasses use. Nothing distinguishes "genuine extension point" from "protected-only-to-dodge-private" today. Requiring `@VisibleForTesting` (the `org.jetbrains.annotations` annotation already used on package-private test seams in `Container`, `Accessor`, and `DotRenderer`) on any `protected` method no subclass actually depends on makes that distinction build-checkable instead of tribal knowledge.

## What Changes

- Widen the existing ArchUnit "no private methods" rule from `processor.internal..` + `spi.builtins..` to every Java source package in the repo (still excluding the shaded `lib.*` third-party code, synthetic/bridge members, and private constructors, matching today's exemptions).
- Decompose/refactor the ~20 currently-flagged classes so every method is package-private, protected, or public and reachable to a test double: `processor.internal.graph` (`ExtractedPlan`, `DotRenderer`, `MapperGraph`, `Value`, `MethodScope`), `processor.internal.stages.validate` (`RealisationDiagnosticsStage`, `ValidateOptionConsumptionStage`, `ValidateEnumOverridesStage`, `ValidateSourceParametersStage`, `ValidateAmbientBindingsStage`, `ValidateMappingShapeStage`, `ValidateConstantDefaultLegalityStage`), `processor.internal.stages.dump.GraphDumpWriter`, `processor` (`MapperStep`, `ProcessorOptions`, `model.GoalSpec`), `processor.nullability.JspecifyNullabilityResolver`, `spi` (`Nullability`, `LiteralCoercion`, `ResolveCtx`), `reactorblocking.Blockings`, and the `annotations` test fixture.
- Add a new ArchUnit rule requiring `@VisibleForTesting` on any `protected` method that no subclass overrides or otherwise depends on (i.e. `protected` in name only, not a real extension point).
- Audit existing `protected` methods across `spi`, `strategies-builtin`, `reactor`, and `processor` (`Container`, `Accessor`, `Conversion`, the built-in `*Container`/`*Resolver`/`*Conversion` classes, `FluxContainer`, `MonoContainer`, `PercolateProcessor`) and annotate the ones the new rule flags.
- **BREAKING** (internal convention only, no public-API break): any third-party or internal code relying on a currently-`private` method's absence-of-visibility, or on an un-annotated `protected` method, must adapt — this is a structural/testability refactor, not a behavior change.

## Capabilities

### New Capabilities
(none — this extends the existing architecture-enforcement capability rather than introducing a new domain capability)

### Modified Capabilities
- `module-boundaries`: the "Engine internal methods are never private" requirement widens from `processor.internal..` + `spi.builtins..` to all Java sources; a new requirement is added for `@VisibleForTesting` on subclass-unused `protected` methods.

## Impact

- **Modules touched**: `processor`, `spi`, `strategies-builtin`, `reactor`, `reactor-blocking`, `annotations`, `architecture-tests`.
- **Behavior**: none — purely a visibility/testability refactor (`private` → package-private/protected, plus `@VisibleForTesting` annotations). No change to generated-mapper output or processing semantics.
- **Test surface**: each decomposed class gains direct unit-test coverage for logic previously locked behind `private`.
- **Build**: `ModuleBoundariesSpec` gains a repo-wide scope and one new rule; both rules must pass before archive.
