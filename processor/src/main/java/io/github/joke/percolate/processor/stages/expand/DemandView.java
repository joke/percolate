package io.github.joke.percolate.processor.stages.expand;

import io.github.joke.percolate.processor.nullability.NullabilityResolver;
import io.github.joke.percolate.spi.Candidate;
import io.github.joke.percolate.spi.Demand;
import io.github.joke.percolate.spi.Directive;
import io.github.joke.percolate.spi.Nullability;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import javax.lang.model.element.Element;
import javax.lang.model.type.TypeMirror;
import lombok.RequiredArgsConstructor;

/**
 * The myopic {@link Demand} the driver hands a strategy for one unsatisfied {@code Value} (design D9). It exposes
 * only local decision context — the demanded type and nullness, the in-effect {@code @Map} {@link Directive}, the
 * declared-children goal set, a flat candidate snapshot of in-scope source values, and the nullness oracle — and
 * no graph or engine handle.
 */
@RequiredArgsConstructor
// each field backs the Demand accessor of the same name; this is a deliberate myopic data-carrier adapter
@SuppressWarnings({"PMD.AvoidFieldNameMatchingMethodName", "PMD.DataClass"})
final class DemandView implements Demand {

    private final TypeMirror targetType;
    private final Nullability targetNullness;
    private final Optional<Directive> directive;
    private final Set<String> declaredChildren;
    private final List<Candidate> candidates;
    private final NullabilityResolver resolver;

    @Override
    public TypeMirror targetType() {
        return targetType;
    }

    @Override
    public Nullability targetNullness() {
        return targetNullness;
    }

    @Override
    public Optional<Directive> directive() {
        return directive;
    }

    @Override
    public Set<String> declaredChildren() {
        return declaredChildren;
    }

    @Override
    public List<Candidate> candidates() {
        return candidates;
    }

    @Override
    public Nullability nullnessOf(final TypeMirror type, final Element scope) {
        return resolver.resolve(type, scope);
    }
}
