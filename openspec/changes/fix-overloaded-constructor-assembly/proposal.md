## Why

When a target type declares **overloaded constructors that disagree on a parameter's type** — e.g. `Human.Address(int number, String street)` and `Human.Address(long number, String street)` — code generation fails outright:

```
code generation failed: no return-root TargetLocation node in scope
MethodScope(method=mapAddress(io.github.joke.testing.Person.Address))
```

The return-root (`tgt[]:Human.Address`) is *realised* in the graph but is *absent from the chosen plan*, so `BuildMethodBodies.findReturnRoot` throws. A mapper that maps cleanly with a single constructor stops compiling the moment a second, type-divergent overload is added — a surprising, hard-to-diagnose regression for the user, who only changed the target class.

The cause is structural. `SeedGraph` pre-creates **one untyped target leaf per field name** (`buildTargetChain` builds `tgt[number]` with `Optional.empty()` type) and registers **one** consolidated assembly group whose slots are those shared leaves. `ConstructorCall` then over-emits one boundary per constructor, each wanting to type the *same* `number` leaf from its own parameter type (`int` for one ctor, `long` for the other). A `Node` can be typed exactly once (`Node.setTyping` rejects a second typing), so the two constructors cannot both bind the single shared leaf: only one type (`int`) ever materializes (no `tgt[number]:long` node is ever created), no constructor group cleanly satisfies, and the plan drops the whole scope. The over-emit-and-prune assembly design implicitly assumes **one type per target-field name**, which overloaded constructors violate.

## What Changes

- **Make assembly tolerate constructors that disagree on a slot's type.** The fix lets overloaded constructors compete as genuine OR-siblings at the return-root: each constructor's typed slots must be able to coexist (rather than racing to type a single shared leaf), so that at least one fully-bindable constructor is realised, recorded SAT, and selected by the plan. The exact mechanism (per-constructor typed slots vs. a canonical-constructor choice vs. type-keyed leaf nodes) is a design decision — see design.md.
- **Guarantee a planned return-root whenever any constructor is satisfiable.** When at least one accessible constructor of the target can be bound to the seeded leaves, `planView()` SHALL contain the return-root node, and codegen SHALL emit a `new Target(...)` call for the selected constructor. Only when *no* constructor is satisfiable should the scope fail — and then with a diagnostic that names the unresolved target, not an internal `IllegalStateException`.
- **Deterministic selection among satisfiable overloads.** When more than one constructor is satisfiable, selection SHALL be deterministic and cost-driven (consistent with the existing cheapest-plan oracle), so the generated constructor choice is stable across runs.
- **Regression coverage.** Add an integration/processor scenario with a target exposing type-divergent overloaded constructors (the `int`/`long` `Address` case), asserting a mapper is generated and compiles.

## Capabilities

### New Capabilities
<!-- none -->

### Modified Capabilities

- `seed-graph`: The consolidated assembly seeding (one untyped leaf per target-field name, one assembly group per parent) must no longer force a single type per field name when the target's constructors disagree — so overloaded constructors can each bind their own typed slots.
- `graph-expansion`: Constructor over-emit + slot binding must let type-divergent overloaded constructors coexist as competing assembly groups (each SAT-evaluated on its own typed slots), and plan selection must yield a single satisfiable constructor at the return-root rather than dropping it.

## Impact

- **Code**: `SeedGraph` (assembly-group / target-leaf construction), `ConstructorCall` and the assembly slot-binding path in the driver (`FrontierMatcher`/Applier), and possibly `PlanView` sibling-selection among co-rooted constructor groups. `BuildMethodBodies.findReturnRoot` should degrade to a diagnostic, not an `IllegalStateException`, on a genuinely unsatisfiable target.
- **APIs**: No SPI surface change expected; `ConstructorCall` already over-emits per constructor via the existing `ExpansionStrategy` contract.
- **Behaviour**: Mappers targeting classes with overloaded constructors begin generating instead of failing the build. Constructor selection becomes observable output — flag in release notes.
- **Users / teams**: Anyone whose target classes carry overloaded constructors (very common with Lombok `@Value` + hand-written extra constructors, as in the integration project) is currently blocked; this unblocks them.
