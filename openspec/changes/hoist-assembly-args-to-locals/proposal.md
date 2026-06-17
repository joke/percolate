## Why

Generated mapper bodies cram the whole realisation into one nested `return` expression — e.g. `return new Human("Hello", person.getLastName(), Optional.ofNullable(person.getAddresses().stream().flatMap(...).collect(...)));`. Multi-argument constructor calls are unreadable, and a `Value` consumed by two ports is rendered (and therefore *evaluated*) twice. Hoisting each assembly argument into a named local makes the output readable and evaluates each shared source once.

## What Changes

- The generate stage SHALL hoist a plan `Value` into a local variable declaration (`T v = <expr>;`) **iff** its consumer `Operation` is n-ary (`ports.size() >= 2`) — i.e. arguments to multi-argument constructor/assembly calls get names. Single-port chains (container `iterate`/`collect`/`flatMap`, conversions, accessors, nullness crossings) stay inline, so fluent stream pipelines remain one chain. A `Value` shared by more than one in-plan port is hoisted once and referenced at each use, replacing the prior render-twice behaviour.
- The return-root renders inline (`return new Human(v0, v1, v2);`), not as a trailing temporary.
- The hoist predicate and variable naming SHALL be a **separable pure helper** inside the generate stage (the seam toward a future per-scope binding schedule), without introducing a codegen IR (honours the locked NO-IR direction) and without mutating the graph or plan.
- No change to the bipartite graph, `ExtractedPlan` selection, the SPI, or any strategy — strategies still receive operand `CodeBlock`s through `IncomingValues` and cannot observe whether an operand is a literal expression or a variable reference.

## Capabilities

### New Capabilities
<!-- none -->

### Modified Capabilities
- `code-generation`: body composition gains the hoist-to-local behaviour (assembly arguments materialise as locals; the return-root stays inline; the threaded fluent-pipeline rendering is preserved for single-port chains).
- `plan-extraction`: the "Shared Values render inline per use" requirement is relaxed — a shared `Value` is hoisted and emitted once, so the requirement no longer assumes accessor idempotency to excuse double evaluation. (Plan *selection* — the cheapest-cost fold — is unchanged.)

## Impact

- **Code**: `processor/src/main/java/io/github/joke/percolate/processor/stages/generate/BuildMethodBodies.java` (the `Walk` inner class) plus a new package-private helper for the hoist decision + naming. No other production source changes.
- **Tests**: `EndToEndCodegenSpec` / `SourcePathChainEndToEndSpec` / `ContainerStreamEndToEndSpec` and peers assert on generated text; their expected output updates from one nested expression to hoisted-local form. The `percolate-integration` golden `PersonMapperImpl` regenerates.
- **APIs / SPI**: none. `IncomingValues`, `OperationCodegen`, `ScopeCodegen`, `OperationSpec`, and every strategy are untouched.
- **Teams**: codegen/processor only; no consumer-facing API or annotation change.
