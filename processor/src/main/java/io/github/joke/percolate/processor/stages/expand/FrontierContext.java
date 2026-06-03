package io.github.joke.percolate.processor.stages.expand;

import io.github.joke.percolate.spi.Candidate;
import io.github.joke.percolate.spi.Directive;
import io.github.joke.percolate.spi.Frontier;
import java.util.List;
import java.util.Optional;
import javax.lang.model.type.TypeMirror;
import lombok.RequiredArgsConstructor;

/**
 * The driver-built {@link Frontier} handed to every {@link io.github.joke.percolate.spi.ExpansionStrategy} for one
 * frontier node. It carries only the myopic decision context (design D4): the type to produce, the in-effect
 * {@code @Map} {@link Directive}, and a flat, non-traversable snapshot of the in-scope source {@link Candidate}s
 * materialised from the current group's view. It exposes no graph, group, or node handle.
 */
@RequiredArgsConstructor
@SuppressWarnings(
        "PMD.AvoidFieldNameMatchingMethodName") // fields back the Frontier interface accessors of the same name
final class FrontierContext implements Frontier {

    private final TypeMirror targetType;
    private final Optional<Directive> directive;
    private final List<Candidate> candidates;

    @Override
    public TypeMirror targetType() {
        return targetType;
    }

    @Override
    public Optional<Directive> directive() {
        return directive;
    }

    @Override
    public List<Candidate> candidates() {
        return candidates;
    }
}
