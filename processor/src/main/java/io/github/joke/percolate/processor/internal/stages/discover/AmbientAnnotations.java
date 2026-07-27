package io.github.joke.percolate.processor.internal.stages.discover;

import io.github.joke.percolate.Ambient;
import io.github.joke.percolate.processor.internal.graph.Visibility;
import javax.lang.model.element.VariableElement;
import lombok.experimental.UtilityClass;

/**
 * The single {@code @Ambient} reading rule (design D5 of change {@code decouple-engine-from-strategy-semantics}):
 * an explicit {@link Ambient#value()} overrides the published name, else it is the parameter's own simple name; a
 * parameter carrying the annotation is {@link Visibility#INHERITED}, every other parameter {@link Visibility#LOCAL}.
 * The only two consumers are {@link CallableMethodIndexer} (counting non-ambient parameters) and whoever seeds a
 * method's {@code MethodScope} — no class in {@code internal.graph} reads this annotation.
 */
@UtilityClass
public class AmbientAnnotations {

    /** {@code param}'s published name: an explicit {@code @Ambient} override, else its own simple name. */
    public String nameOf(final VariableElement param) {
        final var ambient = param.getAnnotation(Ambient.class);
        if (ambient != null && !ambient.value().isEmpty()) {
            return ambient.value();
        }
        return param.getSimpleName().toString();
    }

    /** {@link Visibility#INHERITED} for an {@code @Ambient} parameter, {@link Visibility#LOCAL} otherwise. */
    public Visibility visibilityOf(final VariableElement param) {
        return param.getAnnotation(Ambient.class) != null ? Visibility.INHERITED : Visibility.LOCAL;
    }
}
