package io.github.joke.percolate.spi;

import com.palantir.javapoet.CodeBlock;

/**
 * Per-operation snippets for a presence container (Optional, Mono). Extends {@link ContainerCodegen} because a
 * wrapper can also participate in a stream (its {@link #iterate} yields a 0-or-1 element stream, which is how the
 * composer drops empties via a flat-map). Adds the presence operations: {@link #mapPresence} maps the wrapped
 * value, {@link #wrap} lifts a scalar in, and {@link #unwrap} collapses to a scalar under the target's nullability.
 */
public interface WrapperCodegen extends ContainerCodegen {

    /** Map the wrapped value of {@code wrapper} through {@code body}, binding it to {@code var}. */
    CodeBlock mapPresence(CodeBlock wrapper, String var, CodeBlock body);

    /** Lift {@code scalar} into this wrapper. */
    CodeBlock wrap(CodeBlock scalar);

    /**
     * Collapse {@code wrapper} to a scalar. A non-null target collapses by throwing on empty; a {@code @Nullable}
     * target collapses to {@code null}. A wrapper that cannot collapse synchronously (e.g. Mono) MAY leave this
     * unsupported, in which case the framework offers no wrapper-to-scalar mapping for it.
     */
    CodeBlock unwrap(CodeBlock wrapper, Nullability targetNullability);
}
