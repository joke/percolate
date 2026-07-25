## Why

Percolate mapper methods may already declare several parameters — `MethodScope` declares one input per
parameter and every source path names its root — but that capability is unspecified, untested and
undocumented, so nobody can rely on it. Worse, the moment a mapping needs a value that is *not* derived from
the object being mapped (a tenant id, a locale, an `Order` the whole conversion hangs off), there is no way to
thread it down: every conversion method is capped at a single argument by `CallableMethodFilter`, so the value
has to be smuggled through hand-written mapper bodies.

This change specifies multi-parameter mapper methods and introduces `@Ambient` — a parameter whose value is
threaded down the mapping call chain and bound by name wherever a deeper method asks for it.

## What Changes

**Multi-parameter mapper methods (foundation).**

- Specify that a mapper method may declare any number of parameters, each a named source root:
  `@Map(target = "street", source = "address.street") OrderView map(Customer customer, Address address)`.
- Two parameters of the same type are legal — every source names its root, so there is no ambiguity.
- Pin down the two places that currently pick a source by *type* rather than name, where a second
  same-typed parameter makes the choice observable: `SourceCandidates.materialiseMatchingInput`'s
  `findFirst`, and `sourceTypes(scope)` feeding `BindingEnumerator` during grounding-by-match.
- Add end-to-end coverage and a manual section.

**`@Ambient` parameters.**

- New `@Ambient` annotation in the `annotations` module, `@Target(PARAMETER)`.
- **One annotation, roles positional.** An `@Ambient` parameter is bound from the enclosing ambient
  environment when one offers a matching binding, and is published into the environment for its own subtree
  either way. There is no provider/consumer annotation split and no scope-kind branch.
- **Keyed by parameter name**, with the declared type *verified* rather than encoded into the key:
  `@Ambient Person simon` has key `simon`; `@Ambient("simon") Person p` lets a consumer rename its
  parameter. A same-key/incompatible-type pair is an error naming the key and both types — never a silent
  non-match.
- A duplicate key within one ambient environment is an error.
- An **unmatched ambient port fails loudly**, unlike `Port.Sourcing.REUSE`, whose quiet non-application would
  silently deselect a conversion method and yield a different plan.
- A fourth `Port.Sourcing.AMBIENT` mode. Strategies stay myopic: a strategy marks the port `AMBIENT` with a
  key and knows nothing else; `PortSourceResolver` grows one branch that resolves the key against the scope's
  ambient environment. `ChildScope` inherits its parent's environment, so ambients work inside container and
  element lambdas for free via Java's effectively-final capture.
- **`CallableMethodFilter`'s single-parameter gate becomes `parameterCount - ambientCount == 1`.** This is what
  makes multi-argument conversion methods work — `default Price mapPrice(Integer taxFactor, @Ambient Order
  order)` — with no multi-argument assembly strategy. **BREAKING** for the
  `callable-method-discovery` spec, which currently defers multi-parameter methods to a future
  `AssemblyStrategy`; that approach is now explicitly rejected.
- An `@Ambient` parameter remains usable as an ordinary `@Map` source. Unlike MapStruct's `@Context`,
  percolate needs no exclusion rule, because supply is directive-rooted — nothing is a source unless a `@Map`
  names it.

**Explicitly out of scope**, deferred to a later change:

- **Return-value captures.** A capture on a method *return* has no well-defined scope in a target-driven DAG:
  it is ill-defined when it escapes a child scope (N element invocations, one slot) and cyclic when the
  consumer produces part of the captured object, and it couples semantics to engine-chosen plan shape. The
  name `@Capture` is deliberately left unspent so it stays available and accurate for that change.
- **A multi-argument assembly strategy**, and name- or type-matched sourcing of multi-argument calls. Nested
  target assembly via `ConstructorCall`, single-argument delegation, and `@Ambient` together cover the use
  cases.

## Capabilities

### New Capabilities

- `ambient-parameters`: the `@Ambient` annotation, name-keyed ambient bindings with verified types, the
  per-scope ambient environment and its inheritance into child scopes, `Port.Sourcing.AMBIENT` resolution,
  the three ambient diagnostics (duplicate key, key/type mismatch, unmatched port), and `MethodCallBridge`'s
  emission and positional rendering of ambient ports.

### Modified Capabilities

- `mapper-discovery`: a mapper method may declare any number of parameters; discovery does not gate on
  parameter count.
- `callable-method-discovery`: the single-parameter gate becomes `parameterCount - ambientCount == 1`, and
  the deferral of multi-parameter methods to a future `AssemblyStrategy` is withdrawn.
- `expansion-strategy-spi`: `Port` gains the `AMBIENT` sourcing mode and its key.
- `graph-expansion`: scopes carry an ambient environment that child scopes inherit; the "no silent sourcing"
  requirement admits ambient bindings as a third declared origin alongside directives and constants; the two
  type-matched source-selection sites are pinned to deterministic behaviour.
- `user-manual`: the manual documents multi-parameter mapper methods and `@Ambient`, each backed by a
  compiling example.

`MethodCallBridge`'s ambient-port emission is specified under the new `ambient-parameters` capability rather
than as a `type-conversion` delta: `type-conversion` mentions `MethodCallBridge` only in passing and declares
no requirement governing its port shape.

## Impact

**Modules.** `annotations` (new `@Ambient`), `spi` (`Port.Sourcing`, `Port` key), `processor`
(`CallableMethodFilter`, `PortSourceResolver`, `Scope`/`MethodScope`/`ChildScope`, a new ambient validation
in the validate stage group), `strategies-builtin` (`MethodCallBridge` port emission and codegen ordering),
`docs`, and end-to-end coverage in `strategies-builtin`.

**Public API.** Additive for consumers: `@Ambient` is new, and existing mappers are unaffected. Additive but
**observable** for SPI implementors: `Port.Sourcing` gains a fourth constant, so an exhaustive `switch` over
it in a third-party strategy stops compiling.

**Known exposure.** Name keying inherits percolate's existing dependency on real parameter names. A mapper
inheriting an abstract method from a **compiled** dependency sees `arg0`-style names unless that jar was
built with `-parameters`; `@Map(source = …)` already has this exposure, and ambients make it bite in a second
place. This change adds a scenario for it rather than fixing it.

**Affected teams.** Single maintainer for the engine and built-ins. Downstream: third-party
`ExpansionStrategy` authors (the `Port.Sourcing` addition above) and mapper authors (additive only).

**Unverified premise.** That multi-parameter mapper methods compile today is inferred from reading
`MethodScope`, `ValidateSourceParametersStage`, `SourcePathDescender`, `SelfCallGuard`,
`AssembleMapperType` and `BuildMethodBodies` — it has not been witnessed. The first task probes it, and the
rest of the change is conditional on that result.
