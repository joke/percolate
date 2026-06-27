package io.github.joke.percolate.processor.internal.stages.expand;

import io.github.joke.percolate.processor.nullability.NullabilityResolver;
import io.github.joke.percolate.spi.DescendDemand;
import io.github.joke.percolate.spi.Nullability;
import javax.lang.model.element.Element;
import javax.lang.model.type.TypeMirror;
import lombok.RequiredArgsConstructor;

/**
 * The myopic {@link DescendDemand} the driver hands an accessor strategy for one source-path segment (design D2): the
 * concrete parent type and nullness read off the {@code Value} the previous segment landed, the single segment to
 * resolve, and the nullness oracle — no graph or engine handle, no candidate snapshot. The produced output type is the
 * strategy's answer, so the parent is never punned as a {@code targetType}.
 */
@RequiredArgsConstructor
// each field backs the DescendDemand accessor of the same name; a deliberate myopic data-carrier adapter
@SuppressWarnings("PMD.AvoidFieldNameMatchingMethodName")
final class DescendView implements DescendDemand {

    private final TypeMirror parentType;
    private final Nullability parentNullness;
    private final String segment;
    private final NullabilityResolver resolver;

    @Override
    public TypeMirror parentType() {
        return parentType;
    }

    @Override
    public Nullability parentNullness() {
        return parentNullness;
    }

    @Override
    public String segment() {
        return segment;
    }

    @Override
    public Nullability nullnessOf(final TypeMirror type, final Element scope) {
        return resolver.resolve(type, scope);
    }
}
