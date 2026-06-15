## 1. Demand SPI (in-scope candidates + binding name)

- [x] 1.1 Add a binding/slot-name accessor to `Demand` (the name the demand serves, for crossing messages); thread it through `DemandView`.
- [x] 1.2 Change `Demand.candidates()` semantics to the in-scope source Values; the driver populates it from `graph.valuesIn(scope)` filtered to source-derived Values, not a curated `DemandItem` list.
- [x] 1.3 Update `expansion-strategy-spi` strategy unit specs / `Demand` fakes to the new candidate + binding-name surface.

## 2. Crossings and accessors as strategies

- [x] 2.1 Add a nullness-crossing strategy emitting `[requireNonNull]` (partial) and, when the demand's directive declares a default, `[coalesce]` (total, ternary/`orElse`, constant coercion reused), reading the slot name from the demand.
- [x] 2.2 Make source-path accessors demand-driven: an accessor strategy produces a `SourceLocation` Value from its shallower-`SourceLocation` parent demand, bottoming out at the parameter root (location pins the source); carry over `Getter`/`Method`/`Field` matching + weights.
- [x] 2.3 Strategy unit specs for the crossing strategy (requireNonNull / coalesce / UNKNOWN pass-through) and the demand-driven accessors.

## 3. Driver becomes a pure work-list

- [x] 3.1 Collapse `ExpandStage.Driver.expand` to one uniform `run(all strategies, demand)` round (remove the assembly/bridge `if/else`); ports are uniformly enqueued demands.
- [x] 3.2 Delete `crossNonNull`, `bindPort` match-or-synthesize, the curated-candidate `DemandItem` list, and the `SourceDescent` component.
- [x] 3.3 Confirm "no silent sourcing" holds structurally (a port no strategy produces stays unreachable); cover the two-same-typed-sources case (directive `SourceLocation` pins the right one).
- [x] 3.4 Engine/harness specs to the uniform surface; grep-verify no assembly/bridge branch, `bindPort`, `crossNonNull`, or `SourceDescent` remain.

## 4. Thread C — one Container base

- [x] 4.1 Introduce a single `Container` base (`matches` + `element` + optional `iterate`/`collect`/`wrap`/`unwrap`/`mapPresence`) and one optional-operation handle family; delete `SequenceContainer`/`WrapperContainer` and `ContainerCodegen`/`WrapperCodegen`.
- [x] 4.2 Port `ListContainer`/`SetContainer`/`ArrayContainer`/`OptionalContainer` onto the one base (sequences supply `collect`; `OptionalContainer` omits it); generic `StreamMap` (`map`/`flatMap`) unchanged.
- [x] 4.3 Rewrite container unit specs to the one-base / kind-emergent contract.

## 5. Verify

- [x] 5.1 `./gradlew check` green — NEVER continue with violations.
- [x] 5.2 `percolate-integration` green incl. the `mapHuman` end-to-end shape; diff a representative generated mapper before/after to confirm semantically-equivalent output.
- [x] 5.3 Commit with /commit-commands:commit.
