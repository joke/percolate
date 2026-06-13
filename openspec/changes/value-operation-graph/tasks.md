## 1. Graph core (design D1–D4)

- [x] 1.1 Define `GraphVertex` interface with final `Value` and `Operation` implementations (package-private constructors, instance identity) in `processor.graph`
- [x] 1.2 Define `Dep` edge payload (instance identity, optional port id, no endpoints)
- [x] 1.3 Implement `Operation` port signature (ordered: name, declared `TypeMirror`, declared `Nullability`), codegen + weight + strategy-FQN carriers, optional owned child `Scope`
- [x] 1.4 Implement `Value` (location, scope, write-once type+nullness) and `MapperGraph.valueFor(scope, location, type, nullness)` get-or-create dedup
- [x] 1.5 Extend `Scope` to a tree (method scopes, Operation-owned element scopes) and implement the no-`Dep`-crosses-scope assertion
- [x] 1.6 Rebuild `MapperGraph` over `DirectedMultigraph<GraphVertex, Dep>`: append-only surface, SAT predicate store, `MaskSubgraph` views
- [x] 1.7 Define `AddValue` / `AddOperation` deltas (atomic Operation landing: vertex + output edge + one port edge per port)
- [x] 1.8 Spock specs for identity/dedup (type-identical share, type/nullness-divergent distinct), write-once typing, scope invariant, atomic `AddOperation`

## 2. SPI reshape (design D12)

- [x] 2.1 Define `OperationSpec` in `spi` (codegen, weight, ports, output typing, optional child-scope declaration) replacing `ExpansionStep`/`Slot`/`Intent`/`ElementScope`
- [x] 2.2 Define the demand decision context (demanded type+nullness, binding `Directive`, declared bindings, candidate snapshot of in-scope Values)
- [x] 2.3 Carry the `render(VarNames, IncomingValues)` codegen contract onto Operation codegen with port-name keying
- [x] 2.4 Port all `strategies-builtin` strategies to emit `OperationSpec`s (matching logic and `Weights` carried over; container strategies declare child scopes; wrap/unwrap plain)
- [x] 2.5 Update strategy unit specs to assert `OperationSpec` metadata (ports, weight, child-scope declaration; no render calls) per the `builtin-strategy-unit-tests` delta

## 3. Seed stage (design D9)

- [x] 3.1 Rewrite `SeedStage`: parameter-root and return-root `Value`s only; no edges, groups, or target leaves
- [x] 3.2 Derive per-level declared-bindings goal specs from dotted `@Map` target paths (constants/defaults included as bindings)
- [x] 3.3 Attach goal specs to demands; remove `SEED` edge kinds and seed-group registration
- [x] 3.4 Spock specs: roots-plus-goals seeding, per-level grouping of nested targets, no silent directive loss

## 4. Expansion engine (design D6, D9, D10)

- [x] 4.1 Implement the demand work-list over unsatisfied `Value`s (target-to-source, per-pass immutable snapshot, batch apply at pass boundary)
- [x] 4.2 Implement Horn SAT propagation (Value: any producer; Operation: all ports + child return-root; bases: param roots, zero-port Operations) as a memoized vertex predicate
- [x] 4.3 Rework `FrontierMatcher`: spec → atomic `AddOperation`, per-port demand fan-out, signature dedup; delete `InputAllocator` fall-through (no silent sourcing)
- [x] 4.4 Implement the goal-spec gate in assembly matching (constructor candidate iff param-name set equals declared-children set)
- [x] 4.5 Carry the binding directive in the demand context; delete `Node.inheritDirective` successor paths
- [x] 4.6 Conversion chains as unary Operations over `valueFor`-deduped intermediates (type-keyed, bounded, stop-at-SAT)
- [x] 4.7 Rework `Applier` to `AddValue`/`AddOperation` with assertion-only cycle check (D10 gate); delete rollback
- [x] 4.8 Delete `ExpansionGroup`, `GroupId`, `GroupOutcome`, group expanders/phases, group snapshots
- [x] 4.9 Harness: rework `ExpansionResult` to the bipartite surface and the three invariant checks (single chosen producer, scope boundary, port-signature completeness)
- [x] 4.10 Spock specs: Horn propagation (all-ports rule, cycle non-self-satisfaction), no-silent-sourcing starvation, goal gate (subset/zero-arg constructor rejection), overload coexistence with shared `street` Value

## 5. Plan extraction (design D8)

- [x] 5.1 Implement bottom-up cost extraction (min over SAT producers, weight + sum over ports) with deterministic tie-break
- [x] 5.2 Implement the read-only plan view (single `chosenProducer()` per in-plan Value; losers untouched); recurse through scope-owning Operations
- [x] 5.3 Delete `PlanView` (three pruning passes), Dijkstra cost oracle, `RealisedSubgraph`
- [x] 5.4 Spock specs: sum-vs-min through an AND, cheapest-OR selection, deterministic ties, child-scope recursion, shared-Value inline duplication

## 6. Code generation

- [x] 6.1 Rewrite `BuildMethodBodies` as the plan walk (chosen producer, port-keyed `IncomingValues`, recursive operand rendering)
- [x] 6.2 Render scope-owning Operations as container weave around the child-plan lambda (handles attach to Operations)
- [x] 6.3 Delete `applyNullabilityContract`, consumer-contract resolution, `NullabilityResolver` injection from generation
- [x] 6.4 Verify generated output unchanged across the integration test suite (the behavioural contract)

## 7. Nullness and defaults as Operations (design D5)

- [x] 7.1 Emit `[requireNonNull]` Operations on `NULLABLE → NON_NULL` crossings (existing message format) at expansion time
- [x] 7.2 Emit `[coalesce]` instead when the binding directive declares `defaultValue` (ternary / `orElse` forms, constant coercion reuse)
- [x] 7.3 `ConstantValue` as zero-port Operation producing a `NON_NULL` Value
- [x] 7.4 Spock specs: crossing-with/without-default exclusivity, UNKNOWN pass-through, Optional `orElse` form, constant base-case SAT

## 8. Diagnostics and debug output (design D11)

- [x] 8.1 Rework `RealisationDiagnosticsStage`: walk unsatisfied demands; delete `UNSAT_DID_NOT_CONVERGE`
- [x] 8.2 Implement closest-miss as the deepest unsatisfied port chain (fewest-unsatisfied-ports producer, named ports, no-producer leaf)
- [x] 8.3 Rework `DotRenderer`/dump stages: Operation boxes, Value ellipses, port-labelled edges, child-scope clusters; plan dump from the extraction view
- [x] 8.4 Regenerate and freeze DOT goldens; update dump Spock specs

## 9. Cleanup, verification, spec sync

- [x] 9.1 Resolve the D10 gate: dedicated test for zero-weight cycle well-foundedness; remove the assertion-only cycle check (or re-add rejection and update design + `graph-expansion` delta if the proof fails)
- [x] 9.2 Delete all dead types and grep-verify: no `ExpansionGroup`, `GroupId`, `GroupOutcome`, `EdgeKind`, `ElementScope` (edge attribute), `ExpansionStep`, `Slot` (edge-carried), `PlanView` references remain
- [x] 9.3 Confirm `BuildMethodBodies` references no group/label surface (grep) and diff generated mappers before/after for a representative integration mapper
- [x] 9.4 Run `./gradlew check` — NEVER continue if there are violations
- [ ] 9.5 Commit the completed change with /commit-commands:commit
