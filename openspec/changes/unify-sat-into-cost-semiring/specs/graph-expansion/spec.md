## MODIFIED Requirements

### Requirement: Demand work-list over Values

Expansion SHALL be driven by a work-list of `Value` demands, processed target-to-source: a demanded
`Value` asks "what produces this?", and each strategy match emits an `Operation` whose ports become
new demands. Expansion NEVER walks forward from sources. The work-list SHALL terminate because Values
are deduplicated by `(scope, location, type, nullness)` identity and each is expanded at most once
over a finite location/type space — expansion **over-emits** candidate producers and computes no
satisfaction predicate. Whether a demand is ultimately producible is decided later, by the
plan-extraction cost fold (`reachable ⟺ finite cost`).

#### Scenario: Demands expand target-to-source
- **WHEN** the demand `ret : Human.Address` is processed
- **THEN** matching emits producer Operations for `ret`, and the Operations' port Values join the
  work-list as new demands

#### Scenario: Expansion terminates without a convergence failure mode
- **WHEN** no strategy can produce some remaining demand
- **THEN** expansion ends with that demand having no producer; there is no "did not converge" outcome,
  and the demand is reported unreachable only at extraction (infinite cost)

## REMOVED Requirements

### Requirement: Horn SAT propagation
**Reason**: Satisfaction is no longer a separate computation. It is subsumed by the plan-extraction
minimum-cost fold, where `reachable(v) ⟺ cost(v) < Cost.INFINITE`; the well-foundedness of cyclic
producers is preserved by the cost fold's cycle guard rather than by a separate Horn fixpoint.
Expansion no longer computes or stores a SAT predicate.
**Migration**: See `plan-extraction` MODIFIED "Bottom-up cost extraction" and ADDED "Reachability is
derived from cost".
