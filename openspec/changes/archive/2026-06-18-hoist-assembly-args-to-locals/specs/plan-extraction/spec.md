## REMOVED Requirements

### Requirement: Shared Values render inline per use

**Reason**: Materialisation (inline expression vs. hoisted local) is a code-generation concern, not a
plan-selection one. The extracted plan already exposes a shared `Value` as a single node with multiple
consumers (via the "Extracted plan is a read-only single-producer view" requirement); how that node is
rendered now belongs entirely to `code-generation`. The prior rule — render inline at each use site
and assume accessor idempotency to excuse double evaluation — is superseded: a shared `Value` is now
hoisted to a local and emitted (and evaluated) once.

**Migration**: See `code-generation` → "A shared Value is materialised once" and "Assembly arguments
hoist to local variables". The plan-level guarantee (one chosen producer per in-plan `Value`) is
unchanged and remains stated by "Extracted plan is a read-only single-producer view"; plan *selection*
(the cheapest-cost fold) is unaffected by this change.
