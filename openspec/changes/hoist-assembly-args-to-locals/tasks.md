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

## 4. Verify

- [x] 4.1 Run `./gradlew check` and resolve every violation — do NOT continue while any remain.
- [ ] 4.2 Commit the completed change with `/commit-commands:commit`.
