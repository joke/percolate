## Why

Container-valued targets generate type-incorrect bodies today: a collect/unwrap bridge ships a `$L` pass-through as its codegen, so `mapHuman` emits things like `Set.of(this.mapAddress(person.getAddresses()))` — a `List` handed to a scalar mapper. Bolting container syntax into the renderer would centralize `stream()`/`collect()`/`ofNullable` in the engine and kill the project's goal that **developers add container support by supplying a strategy**. The fix is architectural: make code generation a pure function of the solved graph, where every container snippet comes from a strategy and the composer owns only structure.

## What Changes

- Add a **container-codegen SPI**: a `Codegen` marker plus `ContainerCodegen` (`iterate`/`mapElements`/`flatMapElements`/`collect`) and `WrapperCodegen extends ContainerCodegen` (`mapPresence`/`wrap`/`unwrap`), and the bases `SequenceContainer`/`WrapperContainer` (which `implement Bridge`, derive candidacy from `matches(type)`/`element(type)`, and declare `streamCodegen()` + opt-in `loopCodegen()`).
- A **container is ONE class per type** that is both a `Bridge` (candidacy → the base emits the collect/iterate/wrap/unwrap `BridgeStep`s; the expansion driver adds nodes, unchanged) and a codegen-handle provider. A developer writes only `matches`, `element`, and the snippets; `@AutoService`, jar on the processor classpath, composer never changes.
- **BREAKING (internal):** delete the 9 per-op container bridges (`ListCollect`/`ListWrap`/`SetCollect`/`SetWrap`/`OptionalCollect`/`OptionalWrap`/`OptionalUnwrap`/`ArrayCollect`/`IterableUnwrap`); reimplement List/Set/array/Optional as 4 container classes on the new bases — the **first customers of the exact SPI** a developer uses for `Flux`/`Mono`.
- The **realised edge carries the container provider + operation**, not a frozen snippet, so a second emission paradigm can attach later. Scalar edges keep the paradigm-neutral `EdgeCodegen`; `ConstructorCall` keeps `GroupCodegen` — both unchanged.
- The **composer weaves containers** as an extension of the existing recursive `PlanView` walk in `BuildMethodBodies`, driven by a one-bit `isStream` return flag — no IR, no mutable plan graph, no lowering pass. It contains zero container syntax.
- **Three orthogonal axes**, never merged: reference-nullability (per level, existing machinery; wrapper empty→scalar collapse reads the target's nullability for `orElse(null)` vs `orElseThrow`), presence (`Optional`/`Mono` wrapper ops), sequence (`List`/`Set`/array/`Flux`).

## Capabilities

### New Capabilities

- `container-codegen-spi`: the strategy-supplied container-codegen seam — the `Codegen`/`ContainerCodegen`/`WrapperCodegen` interfaces and `SequenceContainer`/`WrapperContainer` bases, the one-class-per-container dual role (candidacy + per-paradigm codegen), the composer's container weaving (recursive walk + `isStream`), and the three-axes composition rules. Built so concrete `Mono`/`Flux` and a loop backend drop in with no composer change.

### Modified Capabilities

- `code-generation`: the method-body composition algorithm is restated — the composer obtains every container snippet from the edge's container provider and weaves via `isStream`; it hardcodes no container syntax.
- `expansion-strategy-spi`: the bridge SPI gains the `Codegen` handle family and the `SequenceContainer`/`WrapperContainer` container bases; a `BridgeStep`/realised edge carries a container provider + operation rather than a fused container `EdgeCodegen`.
- `graph-model`: a realised `Edge` carries either a paradigm-neutral `EdgeCodegen` (scalar) or a container provider + operation (container); the model no longer assumes a single fused snippet per container edge.

## Impact

- **Code**: new SPI types in `spi`; built-in `ListContainer`/`SetContainer`/`ArrayContainer`/`OptionalContainer` in `strategies-builtin` replacing the 9 deleted per-op bridges; `Edge` carries provider+operation; `BuildMethodBodies` recursive walk extended with container weaving + `isStream`. Expansion driver, `PlanView`, nullability resolution, and `MapperGraph` invariants **unchanged**.
- **APIs**: the container-codegen SPI is new public surface in `spi`. `EdgeCodegen`/`GroupCodegen`/scalar strategies unchanged.
- **Tests**: `builtin-strategy-unit-tests` and `container-expansion` specs' built-in strategy classes are restructured; their tests updated. `EndToEndCodegenSpec`/`BuildMethodBodiesSpec` keep scalar/constructor output identical.
- **Behaviour**: scalar/constructor mappers keep identical output; container mappers gain correct stream-woven bodies. Loop backend + `codegen.style`, concrete `Mono`/`Flux`, nested sequences and single-element extraction (developer's explicit converter), Maps, and filtering are out of scope — architecturally enabled, not built.
- **Supersedes**: the abandoned `plan-graph-codegen` change (its mutable op-node IR is dropped).
