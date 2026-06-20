## Why

The strategy SPI currently has **two keying modes** — target-keyed producers (`WidenPrimitive`, `ConstructorCall`, `MethodCallBridge`) that emit "what produces this target", and candidate-keyed strategies (`CombinatorialMatch`/`Container`/`StreamMap`/`NullnessCrossing`/path resolvers) that iterate `demand.candidates()`. The split forces an author to understand both, makes `Demand` expose a candidate snapshot, and — most consequentially — the container pipeline **hardcodes `java.util.stream.Stream`** as its universal intermediate (`Containers.streamOf`/`streamElement`/`StreamMap`), which a reactive paradigm (`Flux`/`Mono`) cannot ride (`.stream()` blocks). So the codegen north-star's promise — *Flux/Mono as a pure third-party SPI, the same SPI the built-ins use* — is unreachable on today's foundation.

Analysis (this session) showed the engine is *already* over-emit-and-prune and can be made **uniformly target-driven**: every strategy declares "what produces this target", the engine sources every input port (a mechanic), and only one case ever needs source-element info — element-wise mapping — which resolves cleanly as a **functor-lift conversion grounded by type-variable matching** (a generic `F<B> ← F<A>` whose `A` the engine grounds by *matching* the polymorphic input port against a concrete in-scope source, never by demanding an abstract type). This keeps the engine **completely agnostic of how to connect the graph**: SPIs declare *what* to add; the engine owns *all* mechanics (dedup, AND, cost, type-var grounding) and **never chooses** one SPI over another — it instantiates every match and prunes by SPI-assigned weight.

This change makes that the architecture. It is a foundational engine + SPI redesign, so it supersedes the abandoned `spi-strategy-archetype-bases` convenience-base change (which bolted bases onto the candidate-keyed surface).

## What Changes

- **Engine becomes uniformly target-driven and graph-agnostic.** Every `ExpansionStrategy` answers one question — "what produces this demanded target?" — and returns `OperationSpec`s. The engine sources each input port (reuse / fresh-intermediate / cycle-reject), over-emits, and prunes by cost. The engine never reads strategy intent and never chooses between strategies. `never_forward` is preserved (strictly target→source).
- **Type-variable ports + grounding-by-match (the load-bearing new mechanic).** An `OperationSpec` may carry a port whose type contains a **type variable** (e.g. `F<A>`). The engine sources such a port by **unifying it against an in-scope concrete source** (`F<Person>` ⇒ `A := Person`), substituting the variable across the op's output and child scope, and instantiating concretely — one instantiation per matching source, the rest pruned. **An abstract type never enters the work-list**; the variable is grounded by the match, exactly as `MethodCallBridge` grounds its parameter type from a method signature.
- **Element mapping becomes a functor-lift conversion.** `map`/`flatMap`/`mapPresence` are declared as a generic lift — *given child `A → B`, produce `F<B> ← F<A>`* — polymorphic in `A`, grounded by match. No `java.util.stream.Stream` privilege: each container declares its **own** functor lift over its **own** intermediate. `Stream`, `Optional`, `List`/`Set`/array (via their stream), and a third party's `Flux`/`Mono` all use the identical mechanism; the engine cannot tell them apart.
- **SPI redesign — one uniform, candidate-free shape.** `Demand` no longer exposes `candidates()` to producers; `CombinatorialMatch` (candidate iteration) is removed. Strategies are distinguished by *what they read from the demand* (directive → accessor, declaredChildren → assembly, targetType → conversion), not by a keying mode. `OperationSpec` / `Port` / the codegen interfaces are retained (`Port` gains optional type-variable support).
- **`Containers` de-hardcoded.** The `Stream`-specific helpers (`streamOf`/`streamElement`) are removed from the universal path; container intermediates are author-declared. `TypeProbe` (general type-introspection) is introduced and `Containers` delegates to it.
- **Flux/Mono viability is proven, not built.** A worked `Flux`/`Mono` example in the design demonstrates a third party adds reactive support with **zero engine change**; concrete reactive containers remain out of scope.

## Capabilities

### New Capabilities
- `polymorphic-conversion`: type-variable ports and the engine's grounding-by-match mechanic (unify a polymorphic input port against an in-scope concrete source, substitute, instantiate, prune), and the functor-lift conversion model that element mapping is expressed in.

### Modified Capabilities
- `expansion-strategy-spi`: one uniform target-driven strategy surface; `Demand.candidates()` removed from the producer contract; `CombinatorialMatch` removed; `Container` reshaped as a functor-lift declaration; `TypeProbe` added.
- `graph-expansion`: the driver sources type-variable ports by grounding-by-match; the work-list stays strictly concrete; over-emit + cost-prune unchanged; dedup/termination requirements for type-var instantiation.
- `container-expansion`: containers are functor lifts over author-declared intermediates; the `java.util.stream.Stream` hardcoding is removed; cross-paradigm (collection ↔ reactive) bridges are never engine-invented.

## Impact

- **Code**: `ExpandStage`/driver (type-var port sourcing), `MapperGraph`/`Value`/`Port` (type-variable representation + unification), `spi` (`Demand` slimmed, `CombinatorialMatch` removed, `Container` reshaped, `TypeProbe` added, `Containers` de-hardcoded), `strategies-builtin` (every candidate-keyed strategy re-expressed target-driven: `DirectAssign`, `NullnessCrossing`, the path resolvers, the container ops, `StreamMap` → functor lift).
- **Engine invariant**: "the engine invents no bridges" becomes load-bearing — it is what prevents auto-generated blocking cross-paradigm conversions.
- **Output**: generated code for existing mappers must stay behaviourally equivalent (compiles + semantically equal); byte-identical is *not* required (the engine restructures).
- **Risk**: type-variable unification (representation, wildcards/bounded generics, termination) is the load-bearing risk — gated behind a design spike that is the first implementation task.
- **Supersedes**: `spi-strategy-archetype-bases` (deleted). `map-bean-mapping`'s accessor-base dependency re-homes onto this change's target-driven accessor surface.
- **Teams**: engine + SPI + built-in strategies; no consumer-facing annotation change.
