package io.github.joke.percolate.processor.stages.expand;

import io.github.joke.percolate.processor.graph.MethodScope;
import io.github.joke.percolate.processor.graph.Node;
import io.github.joke.percolate.processor.graph.SourceLocation;
import javax.lang.model.element.VariableElement;
import lombok.experimental.UtilityClass;
import org.jspecify.annotations.Nullable;

/**
 * Resolves a single-segment {@link SourceLocation} node back to the method parameter it names, using the node's
 * own {@link MethodScope} rather than any process-global "current method". This is the base case for source-side
 * expansion (a {@code src[person]} node satisfied directly by parameter {@code person}) and the fallback the
 * snapshot uses to recover a producer scope for parameter roots typed outside this phase (e.g. by
 * {@code SeedStage}). Resolving against the node's own scope keeps multi-method mappers correct: a parameter root
 * names a parameter of the method that owns it, not of whichever method the driver happens to be visiting.
 */
@UtilityClass
class SourceParams {

    private static final int SINGLE_SEGMENT = 1;

    @Nullable
    VariableElement forSlot(final Node node) {
        if (!(node.getLoc() instanceof SourceLocation) || !(node.getScope() instanceof MethodScope)) {
            return null;
        }
        final var segments = ((SourceLocation) node.getLoc()).getPath().getSegments();
        if (segments.size() != SINGLE_SEGMENT) {
            return null;
        }
        final var paramName = segments.get(0);
        final var method = ((MethodScope) node.getScope()).getMethod();
        for (final var param : method.getParameters()) {
            if (param.getSimpleName().toString().equals(paramName)) {
                return param;
            }
        }
        return null;
    }
}
