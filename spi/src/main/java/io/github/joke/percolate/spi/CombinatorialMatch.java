package io.github.joke.percolate.spi;

import java.util.stream.Stream;
import javax.lang.model.type.TypeMirror;

/**
 * Convenience mixin for combinatorial conversion strategies that decide per {@code (source, target)} type pair.
 * The author supplies {@link #bridge}; the default {@link #expand} iterates the demand's candidates and offers
 * each candidate type against the demand. Mixing this in keeps the implementor a single {@link ExpansionStrategy}
 * to the loader — no kind-ordering is introduced.
 */
public interface CombinatorialMatch extends ExpansionStrategy {

    Stream<OperationSpec> bridge(TypeMirror from, Demand demand, ResolveCtx ctx);

    @Override
    default Stream<OperationSpec> expand(final Demand demand, final ResolveCtx ctx) {
        return demand.candidates().stream().flatMap(candidate -> bridge(candidate.getType(), demand, ctx));
    }
}
