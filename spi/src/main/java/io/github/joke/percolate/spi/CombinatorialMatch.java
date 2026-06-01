package io.github.joke.percolate.spi;

import java.util.stream.Stream;
import javax.lang.model.type.TypeMirror;

/**
 * Convenience mixin for combinatorial conversion strategies that decide per {@code (source, target)} type pair.
 * The author supplies {@link #bridge}; the default {@link #expand} iterates the frontier's candidates and offers
 * each candidate type against the frontier's target type. Mixing this in keeps the implementor a single
 * {@link ExpansionStrategy} to the loader — no kind-ordering is introduced.
 */
public interface CombinatorialMatch extends ExpansionStrategy {

    Stream<ExpansionStep> bridge(TypeMirror from, TypeMirror to, ResolveCtx ctx);

    @Override
    default Stream<ExpansionStep> expand(final Frontier frontier, final ResolveCtx ctx) {
        return frontier.candidates().stream()
                .flatMap(candidate -> bridge(candidate.getType(), frontier.targetType(), ctx));
    }
}
