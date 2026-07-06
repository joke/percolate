package io.github.joke.percolate.processor.internal.stages.expand;

import io.github.joke.percolate.spi.ResolveCtx;
import io.github.joke.percolate.spi.SourceProjection;
import java.util.ArrayList;
import java.util.List;
import javax.lang.model.type.TypeMirror;
import lombok.RequiredArgsConstructor;

/**
 * Widens a source list by each registered {@link SourceProjection}'s one-step view of it (design D8 of change
 * {@code target-driven-engine}, decomposed out of {@code Grounding} by {@code decompose-engine-stages}): the
 * grounding match set is the in-scope sources plus every projector's derived view of them, so e.g. a
 * {@code Stream<A>} port grounds against the {@code Stream<X>} a {@code List<X>} source projects to. The engine
 * consumes a projection's result structurally and names no container kind.
 */
@RequiredArgsConstructor
final class SourceWidener {

    private final ResolveCtx ctx;
    private final List<SourceProjection> projections;

    /** The in-scope {@code sources} plus each projector's one-step view of them. */
    List<TypeMirror> widen(final List<TypeMirror> sources) {
        if (projections.isEmpty()) {
            return sources;
        }
        final var widened = new ArrayList<>(sources);
        for (final var projection : projections) {
            for (final var source : sources) {
                projection.project(source, ctx).forEach(widened::add);
            }
        }
        return widened;
    }
}
