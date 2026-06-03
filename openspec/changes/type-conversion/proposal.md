## Why

Mapping a primitive field to its boxed counterpart or to a wider numeric type is mechanical busywork that every user absorbs. Today percolate only assigns when source and target are the *same* type (`DirectAssign`), so `int → Integer`, `Integer → int`, `int → long`, and the boxed-widening cross-products (`int → Long`, `Integer → long`, `Integer → Long`) all fail — even though every one is a lossless, compiler-sanctioned JLS conversion.

The engine resolves a slot target-to-source, but two facts stop conversion *chains* from forming. First, a `CONVERSION` step today only folds from a value already in the group's view (reuse-only); the intermediate type a cross-product needs (the `long` between `int` and `Long`) is never created. Second, a slot is treated as satisfied the instant it has one incoming edge — so even if the intermediate were synthesized, the chain would be declared done before the intermediate is itself produced. This change makes the engine **compose conversion chains within a subgroup**: a conversion synthesizes the intermediate type node, and satisfaction becomes **base-case reachability** (a complete realised path), so a chain SATs only when it is genuinely complete. Two trivial atomic conversion strategies then drop in and the engine composes every cross-product.

## What Changes

- **Engine (`graph-expansion`) — the core of this change:**
  - **Conversion-node synthesis.** A `CONVERSION` step reuses the in-view node of its input type (**type-deduped within the subgroup** — one node per type), or, when none exists, **synthesizes** a type-keyed node and folds the edge. The synthesized node becomes an **expandable frontier** (offered to strategies in later passes) but is **not** added to the group's AND-required slot set.
  - **Reachability SAT.** A slot/node satisfies iff it is a base case, OR a child sub-group is SAT, OR it has an incoming realised **conversion edge whose source node is itself satisfied** (transitively to a base case). This replaces "satisfied on first incoming edge" for conversions, so a chain `X→Y→Z` only SATs once `X` (a base case) makes the whole path realised.
  - **Stop-at-SAT unchanged.** Once a group SATs it is not expanded further. This is sound because expansion is breadth-by-hops and conversion weights are uniform (`STEP`): the shortest = cheapest path is already in the graph when the group SATs; deeper paths are strictly more expensive. Termination is bounded by type-dedup (finite primitive/wrapper lattice) plus stop-at-SAT.
- **Two atomic single-hop conversion strategies** in `strategies-builtin`, both target-to-source ("to produce `T`, from `S`"); the engine composes them into cross-products:
  - **`PrimitiveWrapperConversion`** — boxing *and* unboxing as one concept. Target wrapper `W` ⇒ input `unboxedType(W)`, codegen `W.valueOf($L)`. Target primitive `p` with a wrapper ⇒ input `boxedClass(p)`, codegen `$L.<p>Value()`.
  - **`WidenPrimitive`** — JLS 5.1.2. Target primitive `p` ⇒ one step per strictly-narrower primitive, codegen `(p) $L`.
- **Widening, no narrowing.** The full JLS 5.1.2 set, including the precision-losing IEEE legs (`int→float`, `long→float`, `long→double`). Narrowing/lossy conversions stay out of scope (user helper via `MethodCallBridge`).
- **Test enumeration extended** for the two new strategies.

## Capabilities

### New Capabilities

- `type-conversion`: The two atomic conversion strategies — `PrimitiveWrapperConversion` (box+unbox) and `WidenPrimitive` (JLS widening) — the conversion inventory, the widening lattice, the lossless boundary, and per-leg codegen. Both are single-hop and target-to-source; cross-products are composed by the engine.

### Modified Capabilities

- `graph-expansion`: Conversion-node synthesis (reuse-or-synthesize, type-deduped within the subgroup; synthesized node is an expandable frontier, not an AND-required slot) and **reachability SAT** (base-case-reachability through realised conversion edges) so conversion chains compose and SAT only when complete; stop-at-SAT and boundary/assembly termination unchanged.
- `expansion-strategy-spi`: Note that a `CONVERSION` step's single input names a type the driver **reuses-or-synthesizes** — it need not pre-exist. SPI surface (enum, factories) unchanged.
- `builtin-strategy-unit-tests`: Extend the required-specs enumeration to include `PrimitiveWrapperConversion` and `WidenPrimitive`.

## Impact

- **Code**: `FrontierMatcher.convertBundle` (reuse-or-synthesize, type-dedup); `SlotResolver` (reachability satisfaction replacing first-edge `producedInView` for conversions); `BridgeExpander`/`ExpandGroupsPhase` (expand synthesized conversion frontiers without making them AND-required); two strategy classes + Spock specs under `strategies-builtin`.
- **Engine**: This is the substantive change — it revisits the slot-SAT rule (`graph-expansion`). The plan-view already does the AND/OR + Dijkstra cheapest-path walk, so **codegen needs no change** — only the expansion/SAT side catches up to what codegen already assumes.
- **APIs**: No SPI signature change; `Intent.CONVERSION`'s contract is clarified (input may be synthesized).
- **Cycles**: box∘unbox round-trips self-close — type-dedup lands the return edge on the existing node and the `CycleDetector`/`Applier` rejects the cycle. No guard in the strategies.
- **Nullability**: Unboxing a `@Nullable` wrapper stays a latent NPE; no diagnostic here (deferred to `nullability`).
- **Users / teams**: Hand-written `Integer.valueOf` / `intValue` / widening helpers can be deleted. Fields that previously produced no mapping path begin resolving — flag in release notes.
