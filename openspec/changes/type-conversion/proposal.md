## Why

Mapping a primitive field to its boxed counterpart (or to a wider numeric type) is mechanical busywork that every user currently has to absorb. Today percolate only assigns when source and target are the *same* type (`DirectAssign`), so `int → Integer`, `Integer → int`, `int → long`, and `Integer → Long` all fail to resolve even though every one of these is a lossless, compiler-sanctioned JLS conversion. Adding built-in bridges for boxing, unboxing, and widening makes primitive and wrapper fields "just work" the same way containers and (proposed) datetime types do — with **zero engine changes**, because the expansion engine already composes multi-hop paths.

## What Changes

- Introduce three small, atomic `Bridge` implementations in `strategies-builtin`, each covering exactly one JLS lossless conversion family:
  - **Boxing** (JLS 5.1.7) — primitive `T` → its wrapper, e.g. `int → Integer`. Codegen: `Integer.valueOf($L)`.
  - **Unboxing** (JLS 5.1.8) — wrapper → its primitive `T`, e.g. `Integer → int`. Codegen: `$L.intValue()`.
  - **Widening primitive** (JLS 5.1.2) — primitive → strictly wider primitive following the JLS lattice (`byte→short→int→long→float→double`, `char→int…`), e.g. `int → long`. Codegen: explicit cast `(long) $L`.
- **Composition, not enumeration.** The engine composes these atoms into longer paths, so no class is needed for the cross-products:
  - `int → Long` = Widen(`int→long`) → Box(`long→Long`)
  - `Integer → long` = Unbox(`Integer→int`) → Widen(`int→long`)
  - `Integer → Long` = Unbox(`Integer→int`) → Widen(`int→long`) → Box(`long→Long`)
- **Widening, no narrowing.** The full JLS 5.1.2 widening set is covered, including the three range-preserving-but-precision-losing IEEE legs (`int → float`, `long → float`, `long → double`) that javac performs implicitly — matching the language's own assignment behaviour. Narrowing/lossy conversions (`long → int`, `double → int`, `Integer → Byte`) are explicitly **out of scope** — they require explicit casts that silently truncate or overflow. Like datetime's cross-domain boundary, narrowing stays a user judgement call (a user-supplied helper that `MethodCallBridge` discovers).
- **No engine concern.** These are ordinary `Bridge`s registered via `@AutoService(Bridge.class)`. No SPI additions, no scaffolding/driver changes, no new processor options.
- Extend the `builtin-strategy-unit-tests` enumeration to require a tagged Spock spec for each of the three new bridges, following the existing `<StrategyClassSimpleName>Spec.groovy` convention.

## Capabilities

### New Capabilities

- `type-conversion`: Built-in `Bridge` implementations for the lossless JLS primitive conversions — boxing, unboxing, and widening primitive — plus the rule that the engine composes them into boxed-widening chains (`int → Long`, `Integer → Long`, …). Defines the conversion inventory, the widening lattice, the lossless boundary against narrowing, and per-leg codegen.

### Modified Capabilities

- `builtin-strategy-unit-tests`: Extend the required-specs enumeration to include the three new conversion bridges, so each ships with a tagged Spock spec mirroring the existing pattern.

## Impact

- **Code**: Three new `Bridge` classes under `strategies-builtin/src/main/java/io/github/joke/percolate/spi/builtins/` (`BoxBridge`, `UnboxBridge`, `WidenBridge` or similar), each paired with one Spock spec.
- **APIs**: No SPI changes. Bridges plug in via the existing `@AutoService(Bridge.class)` mechanism, alongside `DirectAssign`, `OptionalWrap`, etc.
- **Engine**: None. Multi-hop composition (`Integer → Long`) is handled by existing expansion path-finding; this change adds no engine, scaffolding, or driver code.
- **Nullability**: Unboxing a `@Nullable` wrapper (`Integer → int`) is a latent NPE — the conversion is value-correct but null-unsafe. Interaction with the `nullability` capability (warn/reject unboxing of a nullable source?) is a design-phase question; this proposal does not yet commit a requirement there.
- **Dependencies**: None new — all types are JDK primitives/wrappers on Java 11.
- **Users / teams**: Anyone hand-writing `Integer.valueOf` / `intValue` helpers or trivial widening wrappers can delete them. Fields that previously produced no mapping path will start resolving automatically — observable behaviour, flag in release notes.
