package io.github.joke.percolate.processor.internal.stages.expand;

import static java.util.stream.Collectors.toUnmodifiableList;

import io.github.joke.percolate.processor.internal.graph.AccessPath;
import io.github.joke.percolate.processor.internal.graph.Refusal;
import io.github.joke.percolate.processor.internal.graph.Scope;
import io.github.joke.percolate.processor.internal.graph.TargetLocation;
import io.github.joke.percolate.processor.internal.graph.Value;
import io.github.joke.percolate.processor.model.EnumOverrideDirective;
import io.github.joke.percolate.processor.model.GoalSpec;
import io.github.joke.percolate.processor.model.MappingDirective;
import io.github.joke.percolate.processor.nullability.NullabilityResolver;
import io.github.joke.percolate.spi.Directive;
import io.github.joke.percolate.spi.ExpansionStrategy;
import io.github.joke.percolate.spi.Offer;
import io.github.joke.percolate.spi.OperationSpec;
import io.github.joke.percolate.spi.ResolveCtx;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;

/**
 * Enumerates the grounded {@link OperationSpec}s a FREE target demand admits (design D6/D9 of change
 * {@code target-driven-engine}, decomposed out of {@code ExpandStage.Driver.expandFree} by
 * {@code decompose-engine-stages}): builds the myopic {@link DemandView} from the value's in-effect {@code @Map}
 * directive, asks the full strategy set, grounds every type-variable port against the in-scope source types, and
 * deduplicates by structural signature — the work-list only ever sees concrete, deduplicated specs.
 */
@RequiredArgsConstructor
final class TargetProducer {

    private final List<ExpansionStrategy> strategies;
    private final Map<Scope, GoalSpec> goalSpecs;
    private final SourceCandidates sourceCandidates;
    private final Grounding grounding;
    private final ResolveCtx resolveCtx;
    private final NullabilityResolver resolver;

    /** Every concrete, deduplicated spec the strategy set + grounding admit for the FREE demand {@code value}. */
    List<OperationSpec> produce(final Value value) {
        final var scope = value.getScope();
        final var path = ((TargetLocation) value.getLoc()).getPath().toString();
        final var goalSpec = goalSpecs.getOrDefault(scope, GoalSpec.from(List.of()));
        final var children = goalSpec.declaredChildren(path);
        final var binding = goalSpec.bindingFor(path);
        final var directive = directiveFor(binding, enumOverridesAt(goalSpec, path));
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
        return dedup(grounded);
    }

    /** Records every bound refusal {@code Grounding} collected on {@code value}'s inadmissible list. */
    static void recordRefusals(final List<Offer> refusals, final Value value) {
        for (final var refusal : refusals) {
            if (refusal instanceof Offer.Refusal) {
                final var offerRefusal = (Offer.Refusal) refusal;
                value.addInadmissible(new Refusal(offerRefusal.getSubject(), offerRefusal.getMessage()));
            }
        }
    }

    /** The walked binding's own {@link Directive} for {@code value}'s target path (design D9) — never per-segment. */
    Optional<Directive> pinnedDirective(final Value value) {
        final var scope = value.getScope();
        final var path = ((TargetLocation) value.getLoc()).getPath().toString();
        final var goalSpec = goalSpecs.getOrDefault(scope, GoalSpec.from(List.of()));
        return directiveFor(goalSpec.bindingFor(path), enumOverridesAt(goalSpec, path));
    }

    /**
     * Splits {@code offers} into their productions, recording every refusal on {@code value}'s inadmissible list
     * (design D2 of change {@code decouple-engine-from-strategy-semantics}) — a refusal never becomes an
     * {@code Operation} vertex.
     */
    static List<OperationSpec> productionsOf(final List<Offer> offers, final Value value) {
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

    /** The {@link Directive} for a binding: present iff a {@code @Map} directive or an enum override table exists. */
    static Optional<Directive> directiveFor(
            final Optional<MappingDirective> binding, final List<EnumOverrideDirective> enumOverrides) {
        if (binding.isEmpty() && enumOverrides.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(BindingDirective.from(binding, enumOverrides));
    }

    /**
     * The method's {@code @MapEnum} table, in effect only for its own return demand (the empty root path) — the
     * sole consumer of an enum-conversion method's directive; {@code @MapEnum} is method-level, not per-target-path.
     */
    static List<EnumOverrideDirective> enumOverridesAt(final GoalSpec goalSpec, final String path) {
        return path.isEmpty() ? goalSpec.getEnumOverrides() : List.of();
    }

    /** The directive-pinned source path of the FREE demand {@code value}'s binding, or none. */
    List<String> pinnedSourcePath(final Value value) {
        final var scope = value.getScope();
        final var path = ((TargetLocation) value.getLoc()).getPath().toString();
        final var goalSpec = goalSpecs.getOrDefault(scope, GoalSpec.from(List.of()));
        return goalSpec.bindingFor(path)
                .filter(MappingDirective::hasSource)
                .map(d -> AccessPath.splitDotted(d.getSource()))
                .orElse(List.of());
    }

    /** Every offer the strategy set makes for {@code demand}. */
    List<Offer> run(final DemandView demand, final ResolveCtx ctx) {
        return strategies.stream()
                .flatMap(strategy -> strategy.expand(demand, ctx))
                .collect(toUnmodifiableList());
    }

    /** {@code specs} with duplicate structural signatures dropped, preserving first-seen order. */
    static List<OperationSpec> dedup(final List<OperationSpec> specs) {
        final var seen = new LinkedHashSet<String>();
        final var unique = new ArrayList<OperationSpec>();
        for (final var spec : specs) {
            if (seen.add(signature(spec))) {
                unique.add(spec);
            }
        }
        return unique;
    }

    /** The structural signature (label, output type, port shapes) two specs share iff they are duplicates. */
    static String signature(final OperationSpec spec) {
        final var ports = spec.getPorts().stream()
                .map(port -> port.getName() + ':' + port.getType() + ':' + port.getNullness())
                .collect(Collectors.joining(","));
        return spec.getLabel() + '|' + spec.getOutputType() + '|' + ports;
    }
}
