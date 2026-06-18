## Context

`BuildMethodBodies.Walk` is a pure *expression* composer: `render(Value) → CodeBlock` recurses
port-by-port and embeds each rendered operand directly into its parent's codegen, so a whole method
body is one statement — `return <fully-nested expression>`. Multi-argument constructor calls become
unreadable one-liners, and a `Value` consumed by two ports is rendered (and evaluated) twice.

The bipartite model already names these nodes `Value` = "a typed variable", and the `ExtractedPlan`
is already a DAG of Values (variables) and Operations (expressions): every operand and every
intermediate is a `Value`. So **nothing needs to be added to hoist** — hoisting is "render an existing
`Value` once as a local instead of inlining it". This change rewrites `Walk` from an expression
composer into a per-scope statement-block composer; the graph, the plan, the SPI, and all strategies
are untouched.

## Goals / Non-Goals

**Goals:**
- Generated bodies declare each multi-argument-call argument as a named local, then call with the
  references; fluent container pipelines stay a single chain.
- A `Value` shared by more than one port is evaluated once (a correctness/efficiency win, not just
  readability).
- Hoisted locals read like the code a human would write: named after the slot they materialise (target
  field / source segment / element role), unique within the method, never shadowing a parameter.
- The declaration syntax is configurable at compile time — `final` and/or `var` — without touching the
  hoist decision or the names.
- Keep the change contained in the generate stage, behind a separable pure helper that could later
  graduate into a per-scope binding schedule.

**Non-Goals:**
- No change to plan *selection* (the cheapest-cost fold) or to which producers win.
- No graph/plan mutation, no SPI change, no strategy change.
- No codegen IR (honours the LOCKED no-IR codegen direction).
- A first-class `BindingSchedule` type / showing variables in `.plan.dot` — deferred (B1), but the
  helper is shaped so it can graduate without a rewrite.

## Decisions

### D1: Renderer-only ("A with a seam"), not a plan/graph pass, not an IR
The hoist decision is a pure function of the `ExtractedPlan`, computed and applied inside the generate
stage. The hoist predicate + naming live in a **separable package-private helper**, not tangled into
the recursive emit, so the seam toward a future per-scope binding schedule (B1) exists without
building it now.
- *Alternative — B1 (derive a `BindingSchedule` plan property):* better testability and would let
  `.plan.dot` show variables, but earns its keep only if naming / block-lambda / dump-visibility pull
  on it. Graduation path preserved by the helper seam.
- *Alternative — B2 (insert binding nodes into the plan/graph):* this is the op-node IR the codegen
  direction explicitly **locked out**, and it adds nothing — the plan already contains every
  hoistable variable as a `Value`. Rejected.

### D2: Policy P1 — hoist iff the consumer Operation is n-ary (`ports.size() >= 2`), plus shared-once
Arguments to multi-argument constructor/assembly calls get names; single-port chains (containers,
conversions, accessors, crossings) stay inline so fluent stream pipelines remain one chain. Today
n-ary ⟺ assembly (a constructor): containers/conversions/accessors/crossings emit exactly one port,
constants zero. The predicate is therefore purely structural — no `OperationSpec` "kind" or strategy
identity is needed (the architecture deliberately stripped that from `Operation`).
- *Alternative — P2 (hoist every non-leaf Value):* simplest, but shatters fluent chains into
  `vA = ...; vB = vA.stream(); ...` and would contradict the existing "Stream stages render as a
  threaded pipeline" requirement. Rejected.
- *Alternative — P3 (heuristic: hoist when "big"):* fuzzy and harder to keep deterministic. Rejected.

### D3: Bare leaves are not hoisted; the return-root stays inline
A `Value` with no chosen producer (parameter root, element-lambda variable) already renders as a
simple name, so aliasing it (`Person v = person;`) is pure noise — exempt it. The method return-root
renders inline as the `return` expression (its *ports* hoist, the root itself does not). Constants
(zero-port producers) still hoist when they feed an n-ary port, matching "every argument gets a name".

### D4: Per-scope hoisting; a child scope block-lambdas only when it hoists
`Walk` already recurses per scope (a container element mapping binds the child param-root and renders
the child return-root). Hoisting is applied per scope with its own statement list: the method body is
a statement block; a child (lambda) scope renders as an **expression lambda** when it hoists nothing
(`address -> this.mapAddress(address)` stays terse) and as a **block lambda**
(`address -> { String street = ...; return ...; }`) when its element transform contains nested
assembly. This keeps trivial element maps unchanged while
supporting nested constructors, and makes every scope uniformly "ordered bindings + a return
expression" — the same shape a future `BindingSchedule` would take.

### D5: Topological emission
Within a scope, declarations are emitted in dependency order via a post-order walk of the chosen-
producer DAG (acyclic by extraction's cycle guard), so each local is declared before first use.

### D6: Readable, slot-derived names via a `NameAllocator`
Each hoisted local is named after the slot it materialises — `Location.slotName()` returns the target
field (`TargetLocation`), the last source path segment (`SourceLocation`), or the element role
(`ElementLocation`); a container lambda parameter is named after its element type. Uniqueness and
sanitisation are delegated to JavaPoet's `NameAllocator`: `HoistPlan.forMethod` seeds it with the
method's parameter names so no local shadows a parameter, and every `newName(slot)` returns a
collision-free, keyword-safe identifier (a numeric suffix on clash). Empty slot names fall back to
`value`, an unavailable element type to `element`. This keeps naming a pure function of the plan,
co-located with the hoist decision in the one helper (D1), and replaces the earlier `vN` counter
without disturbing the emission order (D5).
- *Why a `NameAllocator`, not a hand-rolled map:* it already encapsulates the collision-suffix and
  reserved-word logic this requirement needs, so the helper stays small (prefer library primitives).

### D7: Configurable declaration style (`final` / `var`) via `ProcessorOptions`
Two independent boolean processor options — `percolate.locals.final` and `percolate.locals.var`, both
default off, advertised by `getSupportedOptions()` — are parsed into `ProcessorOptions` and passed to
`BuildMethodBodies` as a small value object (`LocalStyle`). When rendering a hoisted local, `final` is
prefixed iff `makeFinal`, and the type token is `var` iff `useVar` (otherwise the Value's explicit
type). The two compose to `final var name = …;`. Style is purely syntactic: it is computed *after* the
hoist decision and the names, so it cannot change which Values hoist, the order, or the identifiers,
and a strategy never observes it (it only ever sees operand `CodeBlock`s through `IncomingValues`).
- *Alternative — a single tri-state option:* rejected; the two axes are orthogonal (`final var` is a
  valid combination) and two booleans read more clearly in a build script.
- *Default off:* preserves the existing output as the baseline; both are opt-in stylistic knobs.

## Risks / Trade-offs

- **`ports.size() >= 2` is a structural proxy for "assembly boundary".** A future ≥2-port non-assembly
  strategy (a binary conversion, the deferred multi-arg `AssemblyStrategy`/method bridge) would also
  have its arguments hoisted. → *Mitigation:* that is still correct and generally desirable (naming the
  args of any n-ary call reads fine); it is a stylistic call, never a miscompile. The predicate is one
  line in one helper and can later consult an explicit `OperationSpec` hint if a real exception appears.
- **Block-lambda verbosity for nested element assembly.** → *Mitigation:* a child scope block-lambdas
  only when it actually hoists; trivial element maps stay expression lambdas (D4).
- **Constant args become `String status = "ACTIVE";`.** Mild noise. → *Mitigation:* acceptable and
  consistent with "every argument named"; a future "exempt trivial producers" knob can live in the same
  helper without a spec change to the leaf rule. Tracked as an open question.
- **Golden-output test churn.** Every end-to-end codegen spec that pins generated text updates from
  nested-expression to hoisted-local form, and the `percolate-integration` golden regenerates. →
  *Mitigation:* expected and mechanical; the new shape is the assertion.

## Migration Plan

Compile-time codegen only — no runtime migration. Land the helper + `Walk` rewrite, regenerate goldens,
update the end-to-end codegen specs' expected text. Rollback is a straight revert (no persisted state,
no API surface touched). `./gradlew check` is the gate.

## Open Questions

- **Trivial-producer exemption:** should a constant literal feeding an n-ary port stay inline
  (`new Human("Hello", lastName, addresses)`) rather than `String status = "ACTIVE";`? A knob in the
  helper; defaulting to "hoist all n-ary args" for now.
- **Graduate to B1 (deferred):** promote the helper to a per-scope `BindingSchedule` so `.plan.dot`
  can render the hoisted variables — pursue only if dump-visibility is wanted.
