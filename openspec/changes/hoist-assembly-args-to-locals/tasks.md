## 1. Hoisting helper (the seam)

- [x] 1.1 Add a package-private helper under `processor/.../stages/generate/` that, given the `ExtractedPlan` (and graph), computes the **hoist predicate** per `Value`: hoist iff the Value has a chosen producer **AND** (it feeds a port of an n-ary `Operation` — `getPorts().size() >= 2` — **OR** it is consumed by more than one in-plan port). Pure function; no `MapperGraph` / `ExtractedPlan` mutation; no codegen IR. (Design D1, D2, D3, D5)
- [x] 1.2 Have the helper own counter-based variable naming (extend the existing `vN` scheme) and expose a `Value → reference CodeBlock` lookup, so the rendering decision and the naming live in one separable place (the seam toward a future per-scope binding schedule).

## 2. Rewrite `BuildMethodBodies.Walk` into a per-scope statement composer

- [x] 2.1 Change the walk so each **scope** accumulates an ordered list of local-declaration statements plus a single result expression, instead of returning one fully-nested expression. Emit declarations in dependency (post-order) order so each local precedes its first reference. (Design D5)
- [x] 2.2 For each hoisted `Value`, emit `<Type> <name> = <expr>;` and return its reference at use sites; render non-hoisted Values inline. Single-port container/conversion/accessor/crossing chains stay inline so fluent pipelines remain one chain. (code-generation: "Assembly arguments hoist to local variables")
- [x] 2.3 Render the method body as the scope's declarations followed by `return <return-root expression>;` — the return-root renders inline, never as a trailing temporary. (code-generation: return-root scenario)
- [x] 2.4 Render child (container element) scopes per-scope: an **expression lambda** when the scope hoists nothing (`v -> this.mapAddress(v)` stays terse), a **block lambda** (`v -> { …; return …; }`) when it hoists; bind the child param-root to the lambda variable as today. (Design D4)
- [x] 2.5 Guard the edges: a bare leaf (no chosen producer — parameter root / element-lambda variable) is never aliased into a local; a `Value` shared by >1 port is emitted exactly once and referenced at each site. (code-generation: bare-parameter and shared-once scenarios)

## 3. Tests

- [x] 3.1 Update the expected generated text in the end-to-end codegen specs to the hoisted-local form: `EndToEndCodegenSpec`, `SourcePathChainEndToEndSpec`, `ContainerStreamEndToEndSpec`, `OverloadedConstructorAssemblySpec`, `ConstantsAndDefaultsEndToEndSpec`, `NullnessCrossingEndToEndSpec`, `TypeConversionEndToEndSpec`. (Only `ConstantsAndDefaultsEndToEndSpec` pins a full multi-arg nested expression; the rest assert on fragments — single-arg constructors and chains stay inline, so their text is unchanged.)
- [x] 3.2 Add focused coverage for the new scenarios in `HoistAssemblyEndToEndSpec`: constructor arguments become locals; a single-port chain stays inline; the return-root renders inline; a bare parameter argument is not aliased; a shared `Value` is emitted once.
- [x] 3.3 Rebuild `percolate-integration` mappers and confirm the regenerated `PersonMapperImpl` shows the hoisted form (constructor args as locals, stream pipeline still a single chain) and compiles. (No standalone `percolate-integration` module exists; the integration goldens are the `@Tag('integration')` end-to-end specs that compile fixtures and assert on the generated `*Impl` — all pass: single-arg `new Human(person.getFirstName())` stays inline, the stream chain stays one threaded pipeline, multi-arg/constant args hoist.)

## 4. Readable, slot-derived local names (Design D6)

- [x] 4.1 Replace the `vN` counter in the hoist helper with slot-derived naming: name each hoisted local from `Location.slotName()` (target field / source segment / element role) and each container lambda parameter from its element type, falling back to `value` / `element` when absent.
- [x] 4.2 Make names unique within the method via JavaPoet's `NameAllocator`, seeded with the method's parameter names so no local shadows a parameter, collisions get a numeric suffix, and reserved words are sanitised.
- [x] 4.3 Re-pin the end-to-end specs that asserted `v0`/`v1` to the slot-derived names: `HoistAssemblyEndToEndSpec` (`street`/`first`, shared `name`), `ConstantsAndDefaultsEndToEndSpec` (`status`/`count`), `TwoSameTypedSourcesSpec` (`a`/`b`). (code-generation: "Hoisted locals are named after their slot")

## 5. Configurable declaration style: `percolate.locals.final` / `percolate.locals.var` (Design D7)

- [x] 5.1 Add the two boolean options to `ProcessorOptions` (default off) and advertise both keys from `PercolateProcessor.getSupportedOptions()`.
- [x] 5.2 Introduce a `LocalStyle` value object (`makeFinal`, `useVar`) and thread it from `ProcessorOptions` into `BuildMethodBodies`; render each hoisted local as `[final] <Type|var> <name> = <expr>;`, the two flags composing. Style is computed after the hoist decision and the names, so it changes only syntax — never which Values hoist, the order, or the identifiers.
- [x] 5.3 Cover the flags: `ProcessorOptionsSpec` parses and advertises both; `HoistAssemblyEndToEndSpec` data-drives the four `final`/`var` combinations and asserts the generated mapper still compiles. (code-generation: "Local declaration style is configurable")

## 6. Verify the extension

- [x] 6.1 Run `./gradlew check` and resolve every violation — do NOT continue while any remain.
- [x] 6.2 Commit the extension with `/commit-commands:commit`.
