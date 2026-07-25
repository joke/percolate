package io.github.joke.percolate.processor.internal.graph;

import io.github.joke.percolate.Ambient;
import javax.lang.model.element.VariableElement;
import lombok.experimental.UtilityClass;
import org.jspecify.annotations.Nullable;

/**
 * The single {@code @Ambient} key-resolution rule (design Decision 2): an explicit {@link Ambient#value()}
 * overrides the key, else the key is the parameter's own simple name. Shared between {@link MethodScope} (which
 * publishes ambient declarations) and the ambient validation stage (which re-derives the same keys to check
 * duplicates, bindings, and types) so the rule is stated exactly once.
 */
@UtilityClass
public class AmbientKeys {

    /** {@code param}'s ambient key, or {@code null} when it carries no {@code @Ambient} annotation. */
    public @Nullable String keyOf(final VariableElement param) {
        final var ambient = param.getAnnotation(Ambient.class);
        if (ambient == null) {
            return null;
        }
        return ambient.value().isEmpty() ? param.getSimpleName().toString() : ambient.value();
    }
}
