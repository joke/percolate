package io.github.joke.percolate.processor.internal.stages.expand;

import static java.util.stream.Collectors.toUnmodifiableList;

import io.github.joke.percolate.processor.internal.graph.Refusal;
import io.github.joke.percolate.processor.internal.graph.Scope;
import io.github.joke.percolate.processor.internal.graph.TargetLocation;
import io.github.joke.percolate.processor.internal.graph.Value;
import io.github.joke.percolate.processor.model.GoalSpec;
import io.github.joke.percolate.processor.nullability.NullabilityResolver;
import io.github.joke.percolate.spi.Constraint;
import io.github.joke.percolate.spi.Directive;
import io.github.joke.percolate.spi.ExpansionStrategy;
import io.github.joke.percolate.spi.Offer;
import io.github.joke.percolate.spi.OperationSpec;
import io.github.joke.percolate.spi.ResolveCtx;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;

// Enumerates the grounded OperationSpecs a FREE target demand admits (design D6/D9 of change target-driven-
// engine, decomposed out of ExpandStage.Driver.expandFree by decompose-engine-stages): builds the myopic
// DemandView from the value's in-effect @Map directive, asks the full strategy set, grounds every type-variable
// port against the in-scope source types, and deduplicates by structural signature — the work-list only ever
// sees concrete, deduplicated specs.
@RequiredArgsConstructor
final class TargetProducer {

    private final List<ExpansionStrategy> strategies;
    private final Map<Scope, GoalSpec> goalSpecs;
    private final SourceCandidates sourceCandidates;
    private final Grounding grounding;
    private final ResolveCtx resolveCtx;
    private final NullabilityResolver resolver;
    private final SpecDeduplicator deduplicator;

    // Every concrete, deduplicated spec the strategy set + grounding admit for the FREE demand value.
    List<OperationSpec> produce(final Value value) {
        final var scope = value.getScope();
        final var path = ((TargetLocation) value.getLoc()).getPath().toString();
        final var goalSpec = goalSpecs.getOrDefault(scope, GoalSpec.empty());
        final var children = goalSpec.declaredChildren(path);
        final var directive = goalSpec.bindingFor(path);
        final var demand = new DemandView(
                value.type(),
                value.nullness(),
                directive,
                children,
                value.getLoc().slotName(),
                resolver);
        final var sourceTypes = sourceCandidates.sourceTypes(scope);
        final var refusals = new ArrayList<Offer>();
        final var grounded = productionsOf(run(demand, resolveCtx), value).stream()
                .flatMap(spec -> grounding.ground(spec, sourceTypes, refusals))
                .collect(toUnmodifiableList());
        recordRefusals(refusals, value);
        return deduplicator.dedup(grounded);
    }

    // Records every bound refusal Grounding collected on value's inadmissible list.
    void recordRefusals(final List<Offer> refusals, final Value value) {
        for (final var refusal : refusals) {
            if (refusal instanceof Offer.Refusal) {
                final var offerRefusal = (Offer.Refusal) refusal;
                value.addInadmissible(new Refusal(offerRefusal.getSubject(), offerRefusal.getMessage()));
            }
        }
    }

    // The walked binding's own Directive for value's target path (design D9) — never per-segment.
    Optional<Directive> pinnedDirective(final Value value) {
        final var scope = value.getScope();
        final var path = ((TargetLocation) value.getLoc()).getPath().toString();
        final var goalSpec = goalSpecs.getOrDefault(scope, GoalSpec.empty());
        return goalSpec.bindingFor(path);
    }

    // The demand-scoped constraints a reader attached to value's target path (design D8).
    List<Constraint> constraintsFor(final Value value) {
        final var scope = value.getScope();
        final var path = ((TargetLocation) value.getLoc()).getPath().toString();
        return goalSpecs.getOrDefault(scope, GoalSpec.empty()).constraintsFor(path);
    }

    // Splits offers into their productions, recording every refusal on value's inadmissible list (design D2 of
    // change decouple-engine-from-strategy-semantics) — a refusal never becomes an Operation vertex.
    List<OperationSpec> productionsOf(final List<Offer> offers, final Value value) {
        final var productions = new ArrayList<OperationSpec>();
        for (final var offer : offers) {
            if (offer instanceof Offer.Production) {
                productions.add(((Offer.Production) offer).getSpec());
            } else if (offer instanceof Offer.Refusal) {
                final var refusal = (Offer.Refusal) offer;
                value.addInadmissible(new Refusal(refusal.getSubject(), refusal.getMessage()));
            }
        }
        return productions;
    }

    // The directive-pinned source path of the FREE demand value's binding, or none.
    List<String> pinnedSourcePath(final Value value) {
        final var scope = value.getScope();
        final var path = ((TargetLocation) value.getLoc()).getPath().toString();
        final var goalSpec = goalSpecs.getOrDefault(scope, GoalSpec.empty());
        return goalSpec.bindingFor(path).map(Directive::sourcePath).orElse(List.of());
    }

    // Every offer the strategy set makes for demand.
    List<Offer> run(final DemandView demand, final ResolveCtx ctx) {
        return strategies.stream()
                .flatMap(strategy -> strategy.expand(demand, ctx))
                .collect(toUnmodifiableList());
    }
}
