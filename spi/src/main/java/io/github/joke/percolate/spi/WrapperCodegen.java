package io.github.joke.percolate.spi;

import com.palantir.javapoet.CodeBlock;

/**
 * Codegen handle for a presence container (Optional, Mono). Extends the shared {@link StreamOps} — a wrapper
 * participates in a stream via {@link #iterate} (its 0-or-1 element stream is how the composer drops empties with
 * a flat-map) — and adds the presence operations. It deliberately does <b>not</b> extend {@link ContainerCodegen}:
 * a presence wrapper has no {@code collect} terminal, because closing a stream into a 0-or-1 container is a
 * sequence concern, not a presence one. {@link #mapPresence} maps the wrapped value, {@link #wrap} lifts a scalar
 * in, {@link #unwrap} collapses to a scalar under the target's nullability.
 */
public interface WrapperCodegen extends StreamOps {

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
