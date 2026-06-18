## Why

Generated mapper bodies cram the whole realisation into one nested `return` expression — e.g. `return new Human("Hello", person.getLastName(), Optional.ofNullable(person.getAddresses().stream().flatMap(...).collect(...)));`. Multi-argument constructor calls are unreadable, and a `Value` consumed by two ports is rendered (and therefore *evaluated*) twice. Hoisting each assembly argument into a named local makes the output readable and evaluates each shared source once.

## What Changes

- The generate stage SHALL hoist a plan `Value` into a local variable declaration (`T v = <expr>;`) **iff** its consumer `Operation` is n-ary (`ports.size() >= 2`) — i.e. arguments to multi-argument constructor/assembly calls get names. Single-port chains (container `iterate`/`collect`/`flatMap`, conversions, accessors, nullness crossings) stay inline, so fluent stream pipelines remain one chain. A `Value` shared by more than one in-plan port is hoisted once and referenced at each use, replacing the prior render-twice behaviour.
- The return-root renders inline (`return new Human(street, first);`), not as a trailing temporary.
- Hoisted locals SHALL carry **readable, slot-derived names** — the target field, the last source path segment, or the element role (`Location.slotName()`) — instead of opaque counters (`v0`, `v1`). Names are made unique within the method (seeded with the parameter names) so none shadows a parameter, collisions get a suffix, and reserved words are sanitised; container lambda parameters are named after their element type.
- Two independent compile-time processor options SHALL control the declaration syntax, both defaulting to off and both advertised by `getSupportedOptions()`: `percolate.locals.final` declares each hoisted local `final`, and `percolate.locals.var` declares it with `var` in place of the explicit type. They compose (`final var name = …;`) and change only syntax — never which Values hoist, the order, or the names.
- The hoist predicate, variable naming, and declaration style SHALL live in a **separable pure helper** inside the generate stage (the seam toward a future per-scope binding schedule), without introducing a codegen IR (honours the locked NO-IR direction) and without mutating the graph or plan.
- No change to the bipartite graph, `ExtractedPlan` selection, the SPI, or any strategy — strategies still receive operand `CodeBlock`s through `IncomingValues` and cannot observe whether an operand is a literal expression or a variable reference, nor the declaration syntax of any local.

## Capabilities

### New Capabilities
<!-- none -->

### Modified Capabilities
- `code-generation`: body composition gains the hoist-to-local behaviour (assembly arguments materialise as locals; the return-root stays inline; the threaded fluent-pipeline rendering is preserved for single-port chains), readable slot-derived local names, and a configurable declaration style (`percolate.locals.final` / `percolate.locals.var`).
- `plan-extraction`: the "Shared Values render inline per use" requirement is relaxed — a shared `Value` is hoisted and emitted once, so the requirement no longer assumes accessor idempotency to excuse double evaluation. (Plan *selection* — the cheapest-cost fold — is unchanged.)

## Impact

- **Code**: `BuildMethodBodies.java` (the `Walk` inner class) plus the new package-private helpers `HoistPlan.java` (hoist decision + slot-derived naming via a `NameAllocator`) and `LocalStyle.java` (the `final`/`var` rendering flags). `ProcessorOptions.java` gains the two `localsFinal` / `localsVar` flags and `PercolateProcessor.java` advertises the two new option keys. No other production source changes.
- **Tests**: `EndToEndCodegenSpec` / `SourcePathChainEndToEndSpec` / `ContainerStreamEndToEndSpec` and peers assert on generated text; their expected output updates from one nested expression to hoisted-local form with slot-derived names (`HoistAssemblyEndToEndSpec`, `ConstantsAndDefaultsEndToEndSpec`, `TwoSameTypedSourcesSpec` re-pinned off `v0`/`v1`). `HoistAssemblyEndToEndSpec` gains a data-driven case for the `final`/`var` style and `ProcessorOptionsSpec` covers parsing/advertising the two flags. The `percolate-integration` golden `PersonMapperImpl` regenerates.
- **APIs / SPI**: no Java API or SPI change. Two new annotation-processor options are added to the build-facing surface: `percolate.locals.final` and `percolate.locals.var` (both opt-in, default off). `IncomingValues`, `OperationCodegen`, `ScopeCodegen`, `OperationSpec`, and every strategy are untouched.
- **Teams**: codegen/processor only; no consumer-facing API or annotation change.
