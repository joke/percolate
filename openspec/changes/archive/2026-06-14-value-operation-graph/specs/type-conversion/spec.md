## ADDED Requirements

### Requirement: Conversions are unary Operations composing through deduped Values

Each conversion strategy match (boxing, unboxing, widening) SHALL emit a unary `Operation`;
multi-hop conversions compose as Operation chains through intermediate `Value`s deduped by
identity (`scope`, `location`, `type`, `nullness`). The lossless cross-product composes through
this chaining without per-pair strategies; the lossless boundary (no narrowing) is unchanged.

#### Scenario: Cross-product composes structurally
- **WHEN** `int → Long` is demanded and only `int → long` (widen) and `long → Long` (box) strategies
  exist
- **THEN** the chain composes through one deduped `long` intermediate Value, with no dedicated
  `int → Long` strategy

#### Scenario: Round-trip chains never self-satisfy
- **WHEN** box and unbox Operations form a cycle between deduped Values
- **THEN** SAT derives only through an acyclic path from a base case, and extraction never selects
  the cycle

## REMOVED Requirements

### Requirement: Lossless cross-products compose through the conversion type-DAG
**Reason**: Restated over Operation chains and Value dedup; the convert-bundle synthesis rules
(reuse-or-synthesize, type-dedup, expandable frontier) follow from the identity rule and need no
dedicated machinery.
**Migration**: See ADDED "Conversions are unary Operations composing through deduped Values".
