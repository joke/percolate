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
- Keep the change contained in the generate stage, behind a separable pure helper that could later
  graduate into a per-scope binding schedule.

**Non-Goals:**
- No change to plan *selection* (the cheapest-cost fold) or to which producers win.
- No graph/plan mutation, no SPI change, no strategy change.
- No codegen IR (honours the LOCKED no-IR codegen direction).
- Readable, slot-derived variable names — deferred (counter names for now).
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
(`v1 -> this.mapAddress(v1)` stays terse) and as a **block lambda** (`v -> { T a = ...; return ...; }`)
when its element transform contains nested assembly. This keeps trivial element maps unchanged while
supporting nested constructors, and makes every scope uniformly "ordered bindings + a return
expression" — the same shape a future `BindingSchedule` would take.

### D5: Topological emission, counter names
Within a scope, declarations are emitted in dependency order via a post-order walk of the chosen-
producer DAG (acyclic by extraction's cycle guard), so each local is declared before first use.
Variable names use the existing `vN` counter (already used for lambda params); readable slot-derived
names are deferred to avoid the collision-disambiguation work now.

## Risks / Trade-offs

- **`ports.size() >= 2` is a structural proxy for "assembly boundary".** A future ≥2-port non-assembly
  strategy (a binary conversion, the deferred multi-arg `AssemblyStrategy`/method bridge) would also
  have its arguments hoisted. → *Mitigation:* that is still correct and generally desirable (naming the
  args of any n-ary call reads fine); it is a stylistic call, never a miscompile. The predicate is one
  line in one helper and can later consult an explicit `OperationSpec` hint if a real exception appears.
- **Block-lambda verbosity for nested element assembly.** → *Mitigation:* a child scope block-lambdas
  only when it actually hoists; trivial element maps stay expression lambdas (D4).
- **Constant args become `String v0 = "Hello";`.** Mild noise. → *Mitigation:* acceptable and consistent
  with "every argument named"; a future "exempt trivial producers" knob can live in the same helper
  without a spec change to the leaf rule. Tracked as an open question.
- **Golden-output test churn.** Every end-to-end codegen spec that pins generated text updates from
  nested-expression to hoisted-local form, and the `percolate-integration` golden regenerates. →
  *Mitigation:* expected and mechanical; the new shape is the assertion.

## Migration Plan

Compile-time codegen only — no runtime migration. Land the helper + `Walk` rewrite, regenerate goldens,
update the end-to-end codegen specs' expected text. Rollback is a straight revert (no persisted state,
no API surface touched). `./gradlew check` is the gate.

## Open Questions

- **Trivial-producer exemption:** should a constant literal feeding an n-ary port stay inline
  (`new Human("Hello", lastName, addresses)`) rather than `String v0 = "Hello";`? A knob in the helper;
  defaulting to "hoist all n-ary args" for now.
- **Readable names (deferred):** derive locals from `Value.getLoc().slotName()` (`lastName`, `street`)
  with collision suffixes — bigger readability win, separate change.
- **Graduate to B1 (deferred):** promote the helper to a per-scope `BindingSchedule` so `.plan.dot`
  can render the hoisted variables — pursue only if dump-visibility is wanted.
