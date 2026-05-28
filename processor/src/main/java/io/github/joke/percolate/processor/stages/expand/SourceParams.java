package io.github.joke.percolate.processor.stages.expand;

import io.github.joke.percolate.processor.graph.Node;
import io.github.joke.percolate.processor.graph.SourceLocation;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.VariableElement;
import lombok.experimental.UtilityClass;
import org.jspecify.annotations.Nullable;

/**
 * Resolves a single-segment {@link SourceLocation} node back to the method parameter it names. This is the
 * base case for source-side expansion (a {@code src[person]} node satisfied directly by parameter
 * {@code person}) and the fallback the snapshot uses to recover a producer scope for parameter roots that
 * were typed outside this phase (e.g. by {@code SeedGraph}).
 */
@UtilityClass
class SourceParams {

    private static final int SINGLE_SEGMENT = 1;

    @Nullable
    VariableElement forSlot(final Node node, final @Nullable ExecutableElement method) {
        if (method == null || !(node.getLoc() instanceof SourceLocation)) {
            return null;
        }
        final var segments = ((SourceLocation) node.getLoc()).getPath().getSegments();
        if (segments.size() != SINGLE_SEGMENT) {
            return null;
        }
        final var paramName = segments.get(0);
        for (final var param : method.getParameters()) {
            if (param.getSimpleName().toString().equals(paramName)) {
                return param;
            }
        }
        return null;
    }
}
